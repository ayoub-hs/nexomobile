package com.nexopos.erp.core.repo

import android.content.Context
import android.util.Log
import com.nexopos.erp.core.db.AppDatabase
import com.nexopos.erp.core.db.entities.CategoryEntity
import com.nexopos.erp.core.db.entities.CategoryProductEntity
import com.nexopos.erp.core.db.entities.ProductEntity
import com.nexopos.erp.core.db.toEntity
import com.nexopos.erp.core.db.toModel
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.MobileProduct
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.core.network.SearchRequest
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.prefs.SecureTokenStorage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.shared.repo.ProductRepository as IProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Android Product repository implementation using Room database.
 * Implements shared ProductRepository interface.
 * 
 * Loading strategy:
 * 1. Always return cached data immediately if available
 * 2. Fetch from network only on explicit refresh (pull-to-refresh)
 * 3. Use mobile-optimized endpoints when available
 */
class ProductRepository(
    context: Context,
    private val tokenStorage: SecureTokenStorage,
    settings: SettingsRepository
) : IProductRepository {
    private val appContext = context.applicationContext
    private val mobileApi: MobileApi = ServiceLocator.mobileApi(appContext, tokenStorage)
    private val db = AppDatabase.get(appContext)
    private val productDao = db.productDao()
    private val categoryDao = db.categoryDao()

    companion object {
        private const val TAG = "ProductRepository"
        private const val PRODUCT_PAGE_SIZE = 100
    }

    // ========================================================================
    // CACHE-FIRST LOADING
    // ========================================================================

    /**
     * Observe all products from cache.
     * Implements: ProductRepository.observeProducts()
     */
    override fun observeProducts(): Flow<List<Product>> {
        return productDao.observeProducts().map { entities ->
            entities.filter { !it.isDeleted }.map { it.toModel() }
        }
    }

    /**
     * Get all products as one-time snapshot.
     * Implements: ProductRepository.getAllProducts()
     */
    override suspend fun getAllProducts(): List<Product> {
        return productDao.getAll().filter { !it.isDeleted }.map { it.toModel() }
    }
    
    /**
     * Get product by ID.
     * Implements: ProductRepository.getProductById()
     */
    override suspend fun getProductById(id: Long): Product? {
        return productDao.findById(id)?.takeIf { !it.isDeleted }?.toModel()
    }
    
    /**
     * Get cached product count.
     * Implements: ProductRepository.getCachedProductCount()
     */
    override suspend fun getCachedProductCount(): Int {
        return productDao.countActive()
    }

    /**
     * Check if we have cached products.
     * Implements: ProductRepository.hasCachedProducts()
     */
    override suspend fun hasCachedProducts(): Boolean {
        return productDao.countActive() > 0
    }

    /**
     * Search products by name or SKU.
     * Implements: ProductRepository.searchByName()
     */
    override suspend fun searchByName(query: String): List<Product> {
        return searchByTerm(query)
    }
    
    /**
     * Search by barcode.
     * Implements: ProductRepository.searchByBarcode()
     */
    override suspend fun searchByBarcode(barcode: String): Result<Product?> {
        return runCatching {
            searchByBarcodeInternal(barcode)
        }
    }
    
    /**
     * Refresh products from network.
     * Implements: ProductRepository.refreshProducts()
     */
    override suspend fun refreshProducts(): Result<Unit> {
        return runCatching {
            syncAllProducts()
        }
    }
    
    // ========================================================================
    // ANDROID-SPECIFIC METHODS (not in interface)
    // ========================================================================
    
    suspend fun searchByTerm(term: String): List<Product> {
        return runCatching {
            val remote = mobileApi.searchProducts(SearchRequest(search = term))
            cacheMobileProducts(remote.results)
            remote.results.map { it.toProduct() }
        }.getOrElse { throwable ->
            val cached = productDao.searchByName(term).map(ProductEntity::toModel)
            if (cached.isNotEmpty()) cached else throw throwable
        }
    }

    /**
     * Get products for a category.
     * 
     * Cache-first strategy:
     * - Returns cached data immediately if available
     * - Only fetches from network if forceRefresh=true (pull-to-refresh)
     * - Falls back to cache on network error
     */
    suspend fun getCategoryProducts(categoryId: Long, forceRefresh: Boolean = false): List<Product> {
        // Try cache first (using category_products join table)
        val cachedFromJoin = productDao.getCategoryProducts(categoryId).map(ProductEntity::toModel)
        
        // Also try direct categoryId lookup (from mobile API sync)
        val cachedDirect = if (cachedFromJoin.isEmpty()) {
            productDao.getProductsByCategoryId(categoryId)
                .filter { !it.isDeleted }
                .map(ProductEntity::toModel)
        } else {
            emptyList()
        }
        
        val cached = cachedFromJoin.ifEmpty { cachedDirect }
        
        // Return cache immediately if not forcing refresh
        if (!forceRefresh && cached.isNotEmpty()) {
            Log.d(TAG, "Returning ${cached.size} cached products for category $categoryId")
            return cached
        }

        return runCatching {
            fetchCategoryProductsMobile(categoryId)
        }.getOrElse { throwable ->
            Log.w(TAG, "Network fetch failed for category $categoryId", throwable)
            if (cached.isNotEmpty()) cached else throw throwable
        }
    }

    private suspend fun fetchCategoryProductsMobile(categoryId: Long): List<Product> {
        val response = mobileApi.getCategoryProducts(categoryId)
        val now = System.currentTimeMillis()
        
        // Cache products with categoryId
        val entities = response.products.map { it.toEntity(now) }
        productDao.upsertAll(entities)
        
        // MED-003: Update category-product mappings atomically
        // Ensure category exists before inserting mappings to avoid FK constraint failure
        ensureCategoryExists(categoryId)
        productDao.replaceCategoryProducts(
            categoryId,
            response.products.mapIndexed { index, product ->
                CategoryProductEntity(
                    categoryId = categoryId,
                    productId = product.id,
                    position = index
                )
            }
        )
        
        Log.d(TAG, "Fetched ${response.products.size} products for category $categoryId via mobile API")
        return response.products.map { it.toProduct() }
    }

    suspend fun getProduct(productId: Long, forceRefresh: Boolean = false): Product {
        if (!forceRefresh) {
            productDao.findById(productId)?.toModel()?.let { return it }
        }

        return runCatching {
            val remote = mobileApi.getProduct(productId)
            cacheMobileProducts(listOf(remote))
            remote.toProduct()
        }.getOrElse { throwable ->
            val cached = productDao.findById(productId)?.toModel()
            cached ?: throw throwable
        }
    }

    private suspend fun searchByBarcodeInternal(barcode: String): Product? {
        // First check local cache
        productDao.findByBarcode(barcode)?.toModel()?.let { return it }

        // Then try mobile API
        return runCatching {
            mobileApi.searchByBarcode(barcode)?.toProduct().also { product ->
                if (product != null) {
                    productDao.upsertAll(listOf(product.toEntity(System.currentTimeMillis())))
                }
            }
        }.getOrElse { throwable ->
            // Fallback to cache on error
            productDao.findByBarcode(barcode)?.toModel() ?: throw throwable
        }
    }

    suspend fun ensureProductUnits(product: Product): Product {
        if (!product.unitQuantities.isNullOrEmpty()) {
            return product
        }

        val detailed = runCatching { getProduct(product.id, forceRefresh = true) }.getOrNull()
        if (detailed != null && !detailed.unitQuantities.isNullOrEmpty()) {
            return detailed
        }

        val searchTerms = listOfNotNull(
            product.barcode?.takeIf { it.length >= 2 },
            product.sku?.takeIf { it.length >= 2 },
            product.name.takeIf { it.length >= 2 }
        )

        for (term in searchTerms) {
            val matches = runCatching { searchByTerm(term) }.getOrElse { emptyList() }
            val match = matches.firstOrNull { it.id == product.id }
                ?: matches.firstOrNull { it.name.equals(product.name, ignoreCase = true) }
            if (match != null && !match.unitQuantities.isNullOrEmpty()) {
                cacheProducts(listOf(match))
                return match
            }
        }

        return detailed ?: product
    }

    suspend fun syncAllProducts(onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }) {
        var cursor: Long? = null
        var processed = 0

        do {
            val response = mobileApi.getProducts(
                limit = PRODUCT_PAGE_SIZE,
                cursor = cursor
            )
            cacheProducts(response.data)
            processed += response.data.size
            onProgress(processed, 0)
            cursor = response.meta.nextCursor
        } while (response.meta.hasMore && cursor != null)

        onProgress(processed, processed)
    }

    private suspend fun cacheProducts(products: List<Product>, categoryId: Long? = null) {
        if (products.isEmpty()) return
        val now = System.currentTimeMillis()
        val enriched = products.map { product ->
            if (product.unitQuantities.isNullOrEmpty()) {
                val existing = productDao.findById(product.id)?.toModel()
                if (existing != null && !existing.unitQuantities.isNullOrEmpty()) {
                    product.copy(unitQuantities = existing.unitQuantities)
                } else {
                    product
                }
            } else {
                product
            }
        }
        productDao.upsertAll(enriched.map { it.toEntity(now, categoryId) })
    }

    private suspend fun cacheMobileProducts(products: List<MobileProduct>) {
        if (products.isEmpty()) return
        val now = System.currentTimeMillis()
        productDao.upsertAll(products.map { it.toEntity(now) })
    }

    // ========================================================================
    // CATEGORY SYNC
    // ========================================================================

    /**
     * Sync all categories from server.
     * This should be called BEFORE syncing products to avoid FK constraint issues.
     */
    suspend fun syncCategories(): Result<Int> = runCatching {
        val now = System.currentTimeMillis()
        val categories = mobileApi.getCategories().map { category ->
            CategoryEntity(
                id = category.id,
                name = category.name,
                description = null,
                productsCount = 0,
                displayOrder = 0,
                updatedAt = now
            )
        }

        categoryDao.upsertAll(categories)

        Log.d(TAG, "Synced ${categories.size} categories")
        categories.size
    }

    /**
     * Ensure a category exists in the local database before inserting category-product mappings.
     * This prevents FK constraint failures when products are synced before their categories.
     * If the category doesn't exist locally, it's fetched from the server.
     */
    private suspend fun ensureCategoryExists(categoryId: Long) {
        // Check if category already exists
        if (categoryDao.getById(categoryId) != null) {
            return
        }
        
        // Category doesn't exist - try to fetch all categories from server
        Log.d(TAG, "Category $categoryId not found locally, syncing categories from server")
        runCatching {
            syncCategories()
        }.onFailure { error ->
            Log.w(TAG, "Failed to sync categories, creating placeholder for category $categoryId", error)
            // Create a placeholder category to satisfy FK constraint
            // This allows products to be cached even if category sync fails
            val placeholder = CategoryEntity(
                id = categoryId,
                name = "Category $categoryId",
                description = null,
                productsCount = 0,
                displayOrder = 0,
                updatedAt = System.currentTimeMillis()
            )
            categoryDao.upsert(placeholder)
        }
    }

    // ========================================================================
    // EXTENSION FUNCTIONS
    // ========================================================================

    /**
     * Convert MobileProduct to Product for UI compatibility.
     */
    private fun MobileProduct.toProduct(): Product {
        return Product(
            id = id,
            name = name,
            barcode = barcode,
            barcodeType = barcodeType,
            sku = sku,
            status = status,
            unitQuantities = unitQuantities
        )
    }
}
