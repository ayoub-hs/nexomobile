package com.nexopos.desktop.core.repo

import com.nexopos.desktop.core.db.AppDatabase
import com.nexopos.desktop.core.db.Customers
import com.nexopos.desktop.core.network.NexoApiClient
import com.nexopos.shared.models.Customer
import com.nexopos.shared.models.CustomerGroup
import com.nexopos.shared.repo.CustomerRepository as ICustomerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*

/**
 * Desktop Customer repository implementation using Exposed SQL framework.
 * Implements shared CustomerRepository interface.
 */
class CustomerRepository(private val api: NexoApiClient) : ICustomerRepository {
    
    /**
     * Observe all customers from local cache.
     * Implements: CustomerRepository.observeCustomers()
     */
    override fun observeCustomers(): Flow<List<Customer>> = flow {
        val entities = AppDatabase.query {
            Customers.selectAll().map { row -> rowToEntity(row) }
        }
        emit(entities)
    }.flowOn(Dispatchers.IO).map { entities -> entities.map { it.toModel() } }
    
    /**
     * Get all customers as one-time snapshot.
     * Implements: CustomerRepository.getAllCustomers()
     */
    override suspend fun getAllCustomers(): List<Customer> = withContext(Dispatchers.IO) {
        AppDatabase.query {
            Customers.selectAll().map { row -> rowToEntity(row).toModel() }
        }
    }
    
    /**
     * Get customer by ID.
     * Implements: CustomerRepository.getCustomerById()
     */
    override suspend fun getCustomerById(id: Long): Customer? = withContext(Dispatchers.IO) {
        getCustomerEntityById(id)?.toModel()
    }
    
    /**
     * Get the default/walk-in customer.
     * Implements: CustomerRepository.getDefaultCustomer()
     */
    override suspend fun getDefaultCustomer(): Customer? = withContext(Dispatchers.IO) {
        getDefaultCustomerEntity()?.toModel()
    }
    
    /**
     * Search customers by name, phone, or email.
     * Implements: CustomerRepository.searchCustomers()
     */
    override suspend fun searchCustomers(query: String): List<Customer> = withContext(Dispatchers.IO) {
        AppDatabase.query {
            Customers.selectAll()
                .map { row -> rowToEntity(row) }
                .filter { entity ->
                    entity.name?.contains(query, ignoreCase = true) == true ||
                    entity.firstName?.contains(query, ignoreCase = true) == true ||
                    entity.lastName?.contains(query, ignoreCase = true) == true ||
                    entity.email?.contains(query, ignoreCase = true) == true ||
                    entity.phone?.contains(query, ignoreCase = true) == true ||
                    entity.username?.contains(query, ignoreCase = true) == true
                }
                .map { it.toModel() }
        }
    }
    
    /**
     * Refresh customers from network.
     * Implements: CustomerRepository.refreshCustomers()
     */
    override suspend fun refreshCustomers(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = api.getCustomers()
            if (result.isSuccess) {
                val customers = result.getOrNull() ?: emptyList()
                saveCustomers(customers)
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch customers"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get cached customer count.
     * Implements: CustomerRepository.getCachedCustomerCount()
     */
    override suspend fun getCachedCustomerCount(): Int = withContext(Dispatchers.IO) {
        AppDatabase.query {
            Customers.selectAll().count().toInt()
        }
    }
    
    /**
     * Check if we have cached customers.
     * Implements: CustomerRepository.hasCachedCustomers()
     */
    override suspend fun hasCachedCustomers(): Boolean = withContext(Dispatchers.IO) {
        getCachedCustomerCount() > 0
    }
    
    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================
    
    private fun getDefaultCustomerEntity(): CustomerEntity? {
        return AppDatabase.query {
            Customers.selectAll()
                .andWhere { Customers.isDefault eq true }
                .firstOrNull()
                ?.let { row -> rowToEntity(row) }
        }
    }
    
    private fun getCustomerEntityById(id: Long): CustomerEntity? {
        return AppDatabase.query {
            Customers.selectAll()
                .andWhere { Customers.id eq id }
                .firstOrNull()
                ?.let { row -> rowToEntity(row) }
        }
    }
    
    private fun saveCustomers(customers: List<com.nexopos.desktop.core.network.Customer>) {
        AppDatabase.query {
            customers.forEach { customer ->
                Customers.replace {
                    it[id] = customer.id
                    it[username] = customer.username
                    it[name] = customer.name
                    it[firstName] = customer.firstName
                    it[lastName] = customer.lastName
                    it[email] = customer.email
                    it[phone] = customer.phone
                    it[groupId] = customer.group?.id
                    it[groupName] = customer.group?.name
                    it[isDefault] = customer.isDefault
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
        }
    }
    
    private fun rowToEntity(row: ResultRow): CustomerEntity {
        return CustomerEntity(
            id = row[Customers.id].value,
            username = row[Customers.username],
            name = row[Customers.name],
            firstName = row[Customers.firstName],
            lastName = row[Customers.lastName],
            email = row[Customers.email],
            phone = row[Customers.phone],
            groupId = row[Customers.groupId],
            groupName = row[Customers.groupName],
            isDefault = row[Customers.isDefault]
        )
    }
}

// ============================================================================
// ENTITY & CONVERSIONS
// ============================================================================

data class CustomerEntity(
    val id: Long,
    val username: String?,
    val name: String?,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val phone: String?,
    val groupId: Long?,
    val groupName: String?,
    val isDefault: Boolean?
) {
    fun getDisplayName(): String {
        return name ?: "${firstName ?: ""} ${lastName ?: ""}".trim().ifEmpty { "Customer #$id" }
    }
    
    /**
     * Convert entity to shared Customer model
     */
    fun toModel(): Customer {
        return Customer(
            id = id,
            username = username,
            name = name,
            firstName = firstName,
            lastName = lastName,
            email = email,
            phone = phone,
            group = if (groupId != null) {
                CustomerGroup(
                    id = groupId,
                    name = groupName,
                    description = null
                )
            } else null,
            isDefault = isDefault
        )
    }
}
