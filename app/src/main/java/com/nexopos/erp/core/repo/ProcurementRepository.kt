package com.nexopos.erp.core.repo

import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.ProcurementResponse
import com.nexopos.erp.core.network.CreateProcurementRequest
import com.nexopos.erp.core.network.UpdateProcurementRequest
import com.nexopos.erp.feature.procurement.ProcurementOrder
import com.nexopos.erp.feature.procurement.ProcurementStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for procurement operations.
 * Follows Clean Architecture pattern similar to OrderRepository.
 */
class ProcurementRepository(
    private val mobileApi: MobileApi
) {
    private val _procurements = MutableStateFlow<List<ProcurementOrder>>(emptyList())
    val procurements: StateFlow<List<ProcurementOrder>> = _procurements.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Load all procurements from the API
     */
    suspend fun loadProcurements(): Result<List<ProcurementOrder>> {
        _isLoading.value = true
        _error.value = null
        
        return try {
            val response = mobileApi.getProcurements()
            val orders = response.data.map { it.toDomainModel() }
            _procurements.value = orders
            _isLoading.value = false
            Result.success(orders)
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load procurements"
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Get a single procurement by ID
     */
    suspend fun getProcurementById(id: Long): Result<ProcurementOrder> {
        return try {
            val response = mobileApi.getProcurement(id)
            Result.success(response.data.toDomainModel())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new procurement
     */
    suspend fun createProcurement(request: CreateProcurementRequest): Result<ProcurementOrder> {
        _isLoading.value = true
        _error.value = null
        
        return try {
            val response = mobileApi.createProcurement(request)
            val newOrder = response.data.toDomainModel()
            
            // Add to local list
            val currentList = _procurements.value.toMutableList()
            currentList.add(0, newOrder)
            _procurements.value = currentList
            
            _isLoading.value = false
            Result.success(newOrder)
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to create procurement"
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Update an existing procurement
     */
    suspend fun updateProcurement(id: Long, request: UpdateProcurementRequest): Result<ProcurementOrder> {
        _isLoading.value = true
        _error.value = null
        
        return try {
            val response = mobileApi.updateProcurement(id, request)
            val updatedOrder = response.data.toDomainModel()
            
            // Update in local list
            val currentList = _procurements.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index >= 0) {
                currentList[index] = updatedOrder
                _procurements.value = currentList
            }
            
            _isLoading.value = false
            Result.success(updatedOrder)
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to update procurement"
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Update procurement status
     */
    suspend fun updateStatus(id: Long, status: ProcurementStatus): Result<ProcurementOrder> {
        return try {
            val response = mobileApi.updateProcurementStatus(id, status.name.lowercase())
            val updatedOrder = response.data.toDomainModel()
            
            // Update in local list
            val currentList = _procurements.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == id }
            if (index >= 0) {
                currentList[index] = updatedOrder
                _procurements.value = currentList
            }
            
            Result.success(updatedOrder)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a procurement
     */
    suspend fun deleteProcurement(id: Long): Result<Unit> {
        return try {
            mobileApi.deleteProcurement(id)
            
            // Remove from local list
            val currentList = _procurements.value.toMutableList()
            currentList.removeAll { it.id == id }
            _procurements.value = currentList
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear any error
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Extension function to convert API response to domain model
     */
    private fun ProcurementResponse.toDomainModel(): ProcurementOrder {
        return ProcurementOrder(
            id = this.id,
            providerId = this.providerId,
            providerName = this.provider?.name ?: "Unknown Provider",
            status = this.status.toProcurementStatus(),
            totalAmount = this.total ?: 0.0,
            currency = this.currency ?: "USD",
            paymentStatus = this.paymentStatus,
            createdAt = this.createdAt.toEpochMilliseconds(),
            updatedAt = this.updatedAt.toEpochMilliseconds(),
            expectedDelivery = this.deliveryDate?.toEpochMilliseconds(),
            notes = this.description,
            invoiceReference = this.invoiceReference,
            invoiceDate = this.invoiceDate?.toEpochMillisecondsDateOnly()
        )
    }
}

/**
 * Extension to convert ISO date string to epoch milliseconds
 */
private fun String?.toEpochMilliseconds(): Long {
    if (this == null) return System.currentTimeMillis()
    return try {
        java.time.Instant.parse(this).toEpochMilli()
    } catch (e: Exception) {
        try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val localDateTime = java.time.LocalDateTime.parse(this, formatter)
            localDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (ignored: Exception) {
            System.currentTimeMillis()
        }
    }
}

/**
 * Parse a date-only value without timezone shifts.
 */
private fun String?.toEpochMillisecondsDateOnly(): Long? {
    val normalized = this?.trim().orEmpty()
    if (normalized.isBlank()) return null
    val datePart = normalized.take(10)
    return runCatching {
        java.time.LocalDate.parse(datePart)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrElse {
        runCatching { normalized.toEpochMilliseconds() }.getOrNull()
    }
}

private fun String?.toProcurementStatus(): ProcurementStatus {
    return when (this?.lowercase()) {
        "draft" -> ProcurementStatus.DRAFT
        "pending" -> ProcurementStatus.PENDING
        "delivered" -> ProcurementStatus.DELIVERED
        "stocked" -> ProcurementStatus.STOCKED
        "approved" -> ProcurementStatus.APPROVED
        "ordered" -> ProcurementStatus.ORDERED
        "partial" -> ProcurementStatus.PARTIAL
        "completed" -> ProcurementStatus.COMPLETED
        "cancelled", "canceled" -> ProcurementStatus.CANCELLED
        else -> ProcurementStatus.PENDING
    }
}
