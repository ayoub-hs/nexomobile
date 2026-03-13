package com.nexopos.erp.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nexopos.erp.core.db.entities.ProcurementEntity
import com.nexopos.erp.core.db.entities.ProcurementItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for procurements.
 * Provides CRUD operations for offline procurement management.
 */
@Dao
interface ProcurementDao {
    
    // ==================== Procurement Operations ====================
    
    /**
     * Get all procurements, ordered by creation date (newest first)
     */
    @Query("SELECT * FROM procurements ORDER BY createdAt DESC")
    fun getAllProcurements(): Flow<List<ProcurementEntity>>
    
    /**
     * Get procurements by status
     */
    @Query("SELECT * FROM procurements WHERE status = :status ORDER BY createdAt DESC")
    fun getProcurementsByStatus(status: String): Flow<List<ProcurementEntity>>
    
    /**
     * Get a single procurement by ID
     */
    @Query("SELECT * FROM procurements WHERE id = :id LIMIT 1")
    suspend fun getProcurementById(id: Long): ProcurementEntity?
    
    /**
     * Get procurements by provider
     */
    @Query("SELECT * FROM procurements WHERE providerId = :providerId ORDER BY createdAt DESC")
    fun getProcurementsByProvider(providerId: Long): Flow<List<ProcurementEntity>>
    
    /**
     * Search procurements by provider name
     */
    @Query("SELECT * FROM procurements WHERE providerName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchProcurements(query: String): Flow<List<ProcurementEntity>>
    
    /**
     * Insert a single procurement (replaces on conflict)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcurement(procurement: ProcurementEntity)
    
    /**
     * Insert multiple procurements (replaces on conflict)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcurements(procurements: List<ProcurementEntity>)
    
    /**
     * Update an existing procurement
     */
    @Update
    suspend fun updateProcurement(procurement: ProcurementEntity)
    
    /**
     * Update procurement status
     */
    @Query("UPDATE procurements SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProcurementStatus(id: Long, status: String, updatedAt: Long = System.currentTimeMillis())
    
    /**
     * Delete a procurement by ID
     */
    @Query("DELETE FROM procurements WHERE id = :id")
    suspend fun deleteProcurement(id: Long)
    
    /**
     * Delete all procurements
     */
    @Query("DELETE FROM procurements")
    suspend fun deleteAllProcurements()
    
    /**
     * Get count of procurements by status
     */
    @Query("SELECT COUNT(*) FROM procurements WHERE status = :status")
    fun getProcurementCountByStatus(status: String): Flow<Int>
    
    /**
     * Get total procurement amount by status
     */
    @Query("SELECT COALESCE(SUM(totalAmount), 0.0) FROM procurements WHERE status = :status")
    fun getTotalAmountByStatus(status: String): Flow<Double>
    
    // ==================== Procurement Item Operations ====================
    
    /**
     * Get all items for a procurement
     */
    @Query("SELECT * FROM procurement_items WHERE procurementId = :procurementId")
    suspend fun getItemsForProcurement(procurementId: Long): List<ProcurementItemEntity>
    
    /**
     * Observe all items for a procurement
     */
    @Query("SELECT * FROM procurement_items WHERE procurementId = :procurementId")
    fun observeItemsForProcurement(procurementId: Long): Flow<List<ProcurementItemEntity>>
    
    /**
     * Insert a procurement item
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcurementItem(item: ProcurementItemEntity)
    
    /**
     * Insert multiple procurement items
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcurementItems(items: List<ProcurementItemEntity>)
    
    /**
     * Delete all items for a procurement
     */
    @Query("DELETE FROM procurement_items WHERE procurementId = :procurementId")
    suspend fun deleteItemsForProcurement(procurementId: Long)
    
    /**
     * Delete a specific procurement item
     */
    @Query("DELETE FROM procurement_items WHERE procurementId = :procurementId AND productId = :productId")
    suspend fun deleteProcurementItem(procurementId: Long, productId: Long)
    
    // ==================== Transaction Operations ====================
    
    /**
     * Insert a procurement with its items in a single transaction
     */
    @Transaction
    suspend fun insertProcurementWithItems(
        procurement: ProcurementEntity,
        items: List<ProcurementItemEntity>
    ) {
        insertProcurement(procurement)
        insertProcurementItems(items)
    }
    
    /**
     * Delete a procurement and all its items
     */
    @Transaction
    suspend fun deleteProcurementWithItems(procurementId: Long) {
        deleteItemsForProcurement(procurementId)
        deleteProcurement(procurementId)
    }
    
    /**
     * Update a procurement and replace all its items
     */
    @Transaction
    suspend fun updateProcurementWithItems(
        procurement: ProcurementEntity,
        items: List<ProcurementItemEntity>
    ) {
        updateProcurement(procurement)
        deleteItemsForProcurement(procurement.id)
        insertProcurementItems(items)
    }
}
