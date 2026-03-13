package com.nexopos.shared.repo

import com.nexopos.shared.models.Customer
import kotlinx.coroutines.flow.Flow

/**
 * Customer repository interface for cross-platform customer data access.
 * 
 * Platform implementations:
 * - Android: RoomCustomerRepository (using Room database)
 * - Desktop: ExposedCustomerRepository (using Exposed SQL framework)
 * 
 * Strategy: Cache-first with network sync
 */
interface CustomerRepository {
    
    /**
     * Observe all customers from local cache.
     * Returns a Flow that emits whenever the customer list changes.
     * 
     * @return Flow of customer list (reactive)
     */
    fun observeCustomers(): Flow<List<Customer>>
    
    /**
     * Get all customers from local cache as a one-time snapshot.
     * 
     * @return List of all cached customers
     */
    suspend fun getAllCustomers(): List<Customer>
    
    /**
     * Get a specific customer by ID from local cache.
     * 
     * @param id Customer ID
     * @return Customer if found, null otherwise
     */
    suspend fun getCustomerById(id: Long): Customer?
    
    /**
     * Get the default customer (walk-in customer).
     * This is typically the customer used when no specific customer is selected.
     * 
     * @return Default customer if exists, null otherwise
     */
    suspend fun getDefaultCustomer(): Customer?
    
    /**
     * Search customers by name, phone, or email.
     * Searches local cache only.
     * 
     * @param query Search term
     * @return List of matching customers
     */
    suspend fun searchCustomers(query: String): List<Customer>
    
    /**
     * Refresh customers from network and update local cache.
     * This is an explicit pull-to-refresh operation.
     * 
     * @return Result indicating success or failure
     */
    suspend fun refreshCustomers(): Result<Unit>
    
    /**
     * Get the count of cached customers.
     * 
     * @return Number of customers in local cache
     */
    suspend fun getCachedCustomerCount(): Int
    
    /**
     * Check if we have any cached customers.
     * 
     * @return true if cache has customers, false otherwise
     */
    suspend fun hasCachedCustomers(): Boolean
}
