package com.nexopos.erp.core.repo

import android.content.Context
import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.db.toModel
import com.nexopos.erp.core.network.Customer
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.network.SpecialCustomerDto
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.shared.repo.CustomerRepository as ICustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Android Customer repository implementation using Room database.
 * Implements shared CustomerRepository interface.
 * 
 * Loading strategy:
 * 1. Always return cached data immediately if available
 * 2. Fetch from network only on explicit refresh (pull-to-refresh)
 * 3. Falls back to cache on network error
 */
class CustomerRepository(
    context: Context,
    private val tokenStorage: SecureTokenStorage,
    settings: SettingsRepository,
    private val syncRepository: MobileSyncRepository = MobileSyncRepository(context.applicationContext, tokenStorage)
) : ICustomerRepository {
    private val appContext = context.applicationContext
    private val mobileApi: MobileApi = ServiceLocator.mobileApi(appContext, tokenStorage)
    private val customerDao = AppDatabase.get(appContext).customerDao()

    companion object {
        private const val TAG = "CustomerRepository"
    }

    /**
     * Get customers with cache-first strategy.
     * 
     * @param forceRefresh If true, fetches from network (pull-to-refresh)
     */
    suspend fun listCustomers(forceRefresh: Boolean = false): Result<List<Customer>> = runCatching {
        val cached = customerDao.getAll()
        
        // Return cache immediately if not forcing refresh
        if (!forceRefresh && cached.isNotEmpty()) {
            Log.d(TAG, "Returning ${cached.size} cached customers")
            return@runCatching cached.map { it.toModel() }
        }

        syncRepository.smartSync().getOrThrow()
        val synced = customerDao.getAll()
        Log.d(TAG, "Loaded ${synced.size} customers from mobile sync")
        synced.map { it.toModel() }
    }.recoverCatching { throwable ->
        Log.w(TAG, "Network fetch failed, using cache", throwable)
        val fallback = customerDao.getAll()
        if (fallback.isNotEmpty()) fallback.map { it.toModel() } else throw throwable
    }

    /**
     * Observe customers from cache for reactive UI updates.
     * Implements: CustomerRepository.observeCustomers()
     */
    override fun observeCustomers(): Flow<List<Customer>> {
        return customerDao.observeCustomers().map { entities ->
            entities.map { it.toModel() }
        }
    }

    /**
     * Get all customers as one-time snapshot.
     * Implements: CustomerRepository.getAllCustomers()
     */
    override suspend fun getAllCustomers(): List<Customer> {
        return customerDao.getAll().map { it.toModel() }
    }
    
    /**
     * Get customer by ID from cache.
     * Implements: CustomerRepository.getCustomerById()
     */
    override suspend fun getCustomerById(id: Long): Customer? {
        return customerDao.getById(id)?.toModel()
    }

    /**
     * Get default walk-in customer.
     * Implements: CustomerRepository.getDefaultCustomer()
     */
    override suspend fun getDefaultCustomer(): Customer? {
        // Android doesn't track default customer in DB - return first customer as fallback
        return customerDao.getAll().firstOrNull()?.toModel()
    }
    
    /**
     * Search customers by name, phone, or email.
     * Implements: CustomerRepository.searchCustomers()
     */
    override suspend fun searchCustomers(query: String): List<Customer> {
        return customerDao.getAll()
            .map { it.toModel() }
            .filter { customer ->
                customer.name?.contains(query, ignoreCase = true) == true ||
                customer.firstName?.contains(query, ignoreCase = true) == true ||
                customer.lastName?.contains(query, ignoreCase = true) == true ||
                customer.email?.contains(query, ignoreCase = true) == true ||
                customer.phone?.contains(query, ignoreCase = true) == true
            }
    }
    
    /**
     * Refresh customers from network.
     * Implements: CustomerRepository.refreshCustomers()
     */
    override suspend fun refreshCustomers(): Result<Unit> {
        return listCustomers(forceRefresh = true).map { Unit }
    }
    
    /**
     * Check if we have cached customers.
     * Implements: CustomerRepository.hasCachedCustomers()
     */
    override suspend fun hasCachedCustomers(): Boolean {
        return customerDao.count() > 0
    }

    /**
     * Get cached customer count.
     * Implements: CustomerRepository.getCachedCustomerCount()
     */
    override suspend fun getCachedCustomerCount(): Int {
        return customerDao.count()
    }

    // ========================================================================
    // ANDROID-SPECIFIC METHODS (not in interface)
    // ========================================================================

    /**
     * Get special customers (customers belonging to special customer group).
     * This fetches directly from the special customer API endpoint.
     * 
     * @return Result containing list of special customers with wallet info
     *         Returns empty list on 404 (endpoint not available on server)
     */
    suspend fun getSpecialCustomers(): Result<List<SpecialCustomerDto>> = runCatching {
        val response = mobileApi.getSpecialCustomers()
        val customers = response.data?.data.orEmpty()
        Log.d(TAG, "Fetched ${customers.size} special customers from network")
        customers
    }.recoverCatching { throwable ->
        // Handle 404 gracefully - endpoint may not exist on all servers
        if (throwable is retrofit2.HttpException && throwable.code() == 404) {
            Log.w(TAG, "Special customers endpoint not available (404) - returning empty list")
            emptyList()
        } else {
            Log.e(TAG, "Failed to fetch special customers", throwable)
            throw throwable
        }
    }
}
