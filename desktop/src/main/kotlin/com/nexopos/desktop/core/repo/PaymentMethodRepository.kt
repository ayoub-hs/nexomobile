package com.nexopos.desktop.core.repo

import com.nexopos.desktop.core.db.AppDatabase
import com.nexopos.desktop.core.db.PaymentMethods
import com.nexopos.desktop.core.network.NexoApiClient
import com.nexopos.desktop.core.network.PaymentMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*

/**
 * Payment method repository (matching Android app)
 */
class PaymentMethodRepository(private val api: NexoApiClient) {
    
    fun getAllPaymentMethods(): Flow<List<PaymentMethodEntity>> = flow {
        val methods = AppDatabase.query {
            PaymentMethods.selectAll().map { row -> rowToEntity(row) }
        }
        emit(methods)
    }.flowOn(Dispatchers.IO)
    
    suspend fun refreshPaymentMethods(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val result = api.getPaymentMethods()
            if (result.isSuccess) {
                val methods = result.getOrNull() ?: emptyList()
                savePaymentMethods(methods)
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch payment methods"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getDefaultPaymentMethod(): PaymentMethodEntity? {
        return AppDatabase.query {
            PaymentMethods.selectAll()
                .andWhere { PaymentMethods.selected eq true }
                .firstOrNull()
                ?.let { row -> rowToEntity(row) }
                ?: PaymentMethods.selectAll()
                    .andWhere { PaymentMethods.identifier eq "cash" }
                    .firstOrNull()
                    ?.let { row -> rowToEntity(row) }
        }
    }
    
    private fun savePaymentMethods(methods: List<PaymentMethod>) {
        AppDatabase.query {
            methods.forEach { method ->
                PaymentMethods.replace {
                    it[identifier] = method.identifier
                    it[label] = method.label
                    it[selected] = method.selected
                    it[readonly] = method.readonly
                    it[updatedAt] = System.currentTimeMillis()
                }
            }
        }
    }
    
    private fun rowToEntity(row: ResultRow): PaymentMethodEntity {
        return PaymentMethodEntity(
            identifier = row[PaymentMethods.identifier],
            label = row[PaymentMethods.label] ?: row[PaymentMethods.identifier],
            selected = row[PaymentMethods.selected],
            readonly = row[PaymentMethods.readonly]
        )
    }
}

data class PaymentMethodEntity(
    val identifier: String,
    val label: String,
    val selected: Boolean?,
    val readonly: Boolean?
)
