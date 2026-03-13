package com.nexopos.erp.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexopos.erp.core.db.entities.PaymentMethodEntity

@Dao
interface PaymentMethodDao {

    @Query("SELECT * FROM payment_methods ORDER BY identifier")
    suspend fun getAll(): List<PaymentMethodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(methods: List<PaymentMethodEntity>)

    @Query("DELETE FROM payment_methods")
    suspend fun clearAll()

    @Query("DELETE FROM payment_methods")
    suspend fun deleteAll()

    @Query("DELETE FROM payment_methods WHERE identifier IN (:identifiers)")
    suspend fun deleteByIdentifiers(identifiers: List<String>)

    @Query("DELETE FROM payment_methods WHERE updated_at != :timestamp")
    suspend fun deleteNotUpdatedAt(timestamp: Long)

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun count(): Int
}
