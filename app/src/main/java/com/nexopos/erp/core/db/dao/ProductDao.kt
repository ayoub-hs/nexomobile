package com.nexopos.erp.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nexopos.erp.core.db.entities.CategoryProductEntity
import com.nexopos.erp.core.db.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY name")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: Long): ProductEntity?

    @Query("SELECT * FROM products ORDER BY name")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :term || '%' ORDER BY name")
    suspend fun searchByName(term: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query(
        "SELECT p.* FROM products p " +
            "INNER JOIN category_products cp ON p.id = cp.product_id " +
            "WHERE cp.category_id = :categoryId " +
            "ORDER BY cp.position"
    )
    suspend fun getCategoryProducts(categoryId: Long): List<ProductEntity>

    @Query("DELETE FROM category_products WHERE category_id = :categoryId")
    suspend fun clearCategoryProducts(categoryId: Long)

    @Query("DELETE FROM category_products")
    suspend fun clearAllCategoryProducts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryProducts(mappings: List<CategoryProductEntity>)

    /**
     * MED-003: Transaction boundary for category product sync.
     * Atomically clears and re-inserts category-product mappings.
     */
    @Transaction
    suspend fun replaceCategoryProducts(categoryId: Long, mappings: List<CategoryProductEntity>) {
        clearCategoryProducts(categoryId)
        upsertCategoryProducts(mappings)
    }

    @Query("DELETE FROM products")
    suspend fun clearAll()

    @Query("DELETE FROM products")
    suspend fun deleteAll()

    @Query("DELETE FROM products WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM products WHERE updated_at != :timestamp")
    suspend fun deleteNotUpdatedAt(timestamp: Long)

    @Query("SELECT * FROM products WHERE category_id = :categoryId ORDER BY name")
    suspend fun getProductsByCategoryId(categoryId: Long): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM products WHERE is_deleted = 0")
    suspend fun countActive(): Int
    
    /**
     * TASK_MED_004: Pagination support
     */
    @Query("SELECT * FROM products WHERE is_deleted = 0 ORDER BY name LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<ProductEntity>
    
    @Query("SELECT * FROM products WHERE category_id = :categoryId AND is_deleted = 0 ORDER BY name LIMIT :limit OFFSET :offset")
    suspend fun getCategoryPage(categoryId: Long, limit: Int, offset: Int): List<ProductEntity>
    
    @Query("SELECT COUNT(*) FROM products WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun countByCategory(categoryId: Long): Int
}
