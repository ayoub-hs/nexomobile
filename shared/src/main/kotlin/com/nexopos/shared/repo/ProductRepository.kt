package com.nexopos.shared.repo

import com.nexopos.shared.models.Product
import kotlinx.coroutines.flow.Flow

/**
 * Product repository interface for cross-platform product data access.
 * 
 * Platform implementations:
 * - Android: RoomProductRepository (using Room database)
 * - Desktop: ExposedProductRepository (using Exposed SQL framework)
 * 
 * Strategy: Cache-first approach
 * 1. Always return cached data immediately if available
 * 2. Fetch from network only on explicit refresh
 * 3. Use platform-specific database for local cache
 */
interface ProductRepository {
    
    /**
     * Observe all products from local cache.
     * Returns a Flow that emits whenever the product list changes.
     * 
     * @return Flow of product list (reactive)
     */
    fun observeProducts(): Flow<List<Product>>
    
    /**
     * Get all products from local cache as a one-time snapshot.
     * 
     * @return List of all cached products
     */
    suspend fun getAllProducts(): List<Product>
    
    /**
     * Get a specific product by ID from local cache.
     * 
     * @param id Product ID
     * @return Product if found, null otherwise
     */
    suspend fun getProductById(id: Long): Product?
    
    /**
     * Search for a product by barcode.
     * Checks local cache first, then falls back to network if not found.
     * 
     * @param barcode Product barcode (can be product barcode or unit quantity barcode)
     * @return Result with Product if found, null if not found, or error
     */
    suspend fun searchByBarcode(barcode: String): Result<Product?>
    
    /**
     * Search products by name or SKU.
     * Searches local cache only.
     * 
     * @param query Search term
     * @return List of matching products
     */
    suspend fun searchByName(query: String): List<Product>
    
    /**
     * Refresh products from network and update local cache.
     * This is an explicit pull-to-refresh operation.
     * 
     * @return Result indicating success or failure
     */
    suspend fun refreshProducts(): Result<Unit>
    
    /**
     * Get the count of cached products.
     * 
     * @return Number of products in local cache
     */
    suspend fun getCachedProductCount(): Int
    
    /**
     * Check if we have any cached products.
     * 
     * @return true if cache has products, false otherwise
     */
    suspend fun hasCachedProducts(): Boolean
}
