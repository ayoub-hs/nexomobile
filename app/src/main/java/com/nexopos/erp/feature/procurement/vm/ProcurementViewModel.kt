package com.nexopos.erp.feature.procurement.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.CreateProcurementRequest
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.ProductSummary
import com.nexopos.erp.core.network.ProcurementProductRequestDto
import com.nexopos.erp.core.network.MobileProduct
import com.nexopos.erp.core.network.ProviderSummary
import com.nexopos.erp.core.network.ReceiveProcurementItem
import com.nexopos.erp.core.network.ReceiveProcurementRequest
import com.nexopos.erp.core.network.SearchRequest
import com.nexopos.erp.core.network.UpdateProcurementRequest
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.feature.procurement.ProcurementLineItem
import com.nexopos.erp.feature.procurement.ProcurementOrder
import com.nexopos.erp.feature.procurement.ProcurementStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProcurementState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingFormOptions: Boolean = false,
    val isCreating: Boolean = false,
    val isReceiving: Boolean = false,
    val procurements: List<ProcurementOrder> = emptyList(),
    val filteredProcurements: List<ProcurementOrder> = emptyList(),
    val providers: List<ProviderSummary> = emptyList(),
    val products: List<ProductSummary> = emptyList(),
    val selectedStatusFilter: ProcurementStatus? = null,
    val searchQuery: String = "",
    val error: String? = null,
    val createSuccess: Boolean = false,
    val createdProcurementId: Long? = null,
    val receiveSuccess: Boolean = false,
    val cancelSuccess: Boolean = false
)

data class ProcurementDetailState(
    val isLoading: Boolean = true,
    val procurement: ProcurementOrder? = null,
    val error: String? = null,
    val isReceiving: Boolean = false,
    val receiveSuccess: Boolean = false,
    val isPaying: Boolean = false,
    val paySuccess: Boolean = false,
    val isCancelling: Boolean = false,
    val cancelSuccess: Boolean = false
)

class ProcurementViewModel(
    private val api: MobileApi,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProcurementState())
    val state: StateFlow<ProcurementState> = _state.asStateFlow()

    private val _detailState = MutableStateFlow(ProcurementDetailState())
    val detailState: StateFlow<ProcurementDetailState> = _detailState.asStateFlow()

    init {
        loadProcurements()
    }

    /**
     * Parse date string to timestamp
     */
    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                val localDateTime = LocalDateTime.parse(dateStr, formatter)
                localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (ignored: Exception) {
                try {
                    val localDate = LocalDate.parse(dateStr)
                    localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (ignoredAgain: Exception) {
                    dateStr.toLongOrNull() ?: System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * Parse a date-only value without timezone shifts.
     * Keeps the YYYY-MM-DD portion as-is when present.
     */
    private fun parseDateOnly(dateStr: String?): Long? {
        val normalized = dateStr?.trim().orEmpty()
        if (normalized.isBlank()) return null
        val datePart = normalized.take(10)
        return runCatching {
            LocalDate.parse(datePart)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrElse {
            runCatching { parseDate(normalized) }.getOrNull()
        }
    }

    /**
     * Map status string to ProcurementStatus enum
     */
    private fun mapStatus(status: String): ProcurementStatus {
        return when (status.uppercase()) {
            "DRAFT" -> ProcurementStatus.DRAFT
            "PENDING" -> ProcurementStatus.PENDING
            "DELIVERED" -> ProcurementStatus.DELIVERED
            "STOCKED" -> ProcurementStatus.STOCKED
            "APPROVED" -> ProcurementStatus.APPROVED
            "ORDERED" -> ProcurementStatus.ORDERED
            "PARTIAL" -> ProcurementStatus.PARTIAL
            "COMPLETED" -> ProcurementStatus.COMPLETED
            "CANCELLED" -> ProcurementStatus.CANCELLED
            else -> ProcurementStatus.DRAFT
        }
    }

    /**
     * Convert DTO to domain model
     */
    private fun toDomainModel(dto: com.nexopos.erp.core.network.ProcurementResponse): ProcurementOrder {
        val products = dto.products?.map { product ->
            ProcurementLineItem(
                id = product.id,
                productId = product.productId,
                productName = product.productName ?: "Unknown Product",
                quantity = product.quantity,
                receivedQuantity = 0.0, // Will be populated from API when available
                unitPrice = product.unitPrice,
                totalPrice = product.totalPrice ?: (product.quantity * product.unitPrice),
                unitId = product.unitId,
                unitName = null
            )
        } ?: emptyList()

        return ProcurementOrder(
            id = dto.id,
            providerId = dto.providerId,
            providerName = dto.provider?.name ?: "Unknown Provider",
            status = mapStatus(dto.status),
            totalAmount = dto.total ?: 0.0,
            currency = dto.currency ?: "USD",
            paymentStatus = dto.paymentStatus,
            createdAt = parseDate(dto.createdAt),
            updatedAt = parseDate(dto.updatedAt),
            expectedDelivery = dto.deliveryDate?.let { parseDate(it) },
            notes = dto.description,
            products = products,
            invoiceReference = dto.invoiceReference,
            invoiceDate = parseDateOnly(dto.invoiceDate)
        )
    }

    /**
     * Load all procurement orders from the API.
     */
    fun loadProcurements() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getProcurements()
                }
                
                val procurements = response.data.map { toDomainModel(it) }
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    procurements = procurements,
                    filteredProcurements = filterProcurements(
                        procurements, 
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load procurements")
                )
            }
        }
    }

    /**
     * Load single procurement detail from the API.
     */
    fun loadProcurementDetail(procurementId: Long) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isLoading = true, error = null)
            
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getProcurement(procurementId)
                }
                
                val procurement = toDomainModel(response.data)
                
                _detailState.value = _detailState.value.copy(
                    isLoading = false,
                    procurement = procurement
                )
                
                // Also update the item in the list state
                val updatedProcurements = _state.value.procurements.map { 
                    if (it.id == procurementId) procurement else it 
                }
                _state.value = _state.value.copy(
                    procurements = updatedProcurements,
                    filteredProcurements = filterProcurements(
                        updatedProcurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load procurement details")
                )
            }
        }
    }

    /**
     * Refresh procurements (pull-to-refresh).
     */
    fun refreshProcurements() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getProcurements()
                }
                
                val procurements = response.data.map { toDomainModel(it) }
                
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    procurements = procurements,
                    filteredProcurements = filterProcurements(
                        procurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = e.onlineOnlyMessage("Failed to refresh procurements")
                )
            }
        }
    }

    fun loadFormOptions() {
        val currentState = _state.value
        if (currentState.isLoadingFormOptions) {
            return
        }
        if (currentState.providers.isNotEmpty()) {
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingFormOptions = true, error = null)

            try {
                val procurementsResponse = if (_state.value.procurements.isNotEmpty()) {
                    null
                } else {
                    withContext(Dispatchers.IO) { api.getProcurements() }
                }
                val providersResponse = runCatching {
                    withContext(Dispatchers.IO) { api.getProviders() }
                }.getOrNull()
                val procurementSource = procurementsResponse?.data ?: _state.value.procurements.map { procurement ->
                    com.nexopos.erp.core.network.ProcurementResponse(
                        id = procurement.id,
                        providerId = procurement.providerId,
                        provider = ProviderSummary(procurement.providerId, procurement.providerName),
                        status = procurement.status.name,
                        total = procurement.totalAmount,
                        currency = procurement.currency,
                        createdAt = null,
                        updatedAt = null,
                        deliveryDate = null,
                        description = procurement.notes,
                        products = null
                    )
                }

                val providerOptions = providersResponse?.data
                    ?.distinctBy { it.id }
                    ?.sortedBy { it.name.lowercase(Locale.ROOT) }
                    ?: procurementSource
                        .mapNotNull { response ->
                            val provider = response.provider
                            val providerName = provider?.name ?: return@mapNotNull null
                            ProviderSummary(
                                id = provider.id,
                                name = providerName
                            )
                        }
                        .distinctBy { it.id }
                        .sortedBy { it.name.lowercase(Locale.ROOT) }

                val productOptions = currentState.products

                val mappedProcurements = procurementsResponse?.data?.map { toDomainModel(it) }
                val procurements = mappedProcurements ?: _state.value.procurements

                _state.value = _state.value.copy(
                    isLoadingFormOptions = false,
                    products = productOptions,
                    providers = providerOptions,
                    procurements = procurements,
                    filteredProcurements = filterProcurements(
                        procurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingFormOptions = false,
                    error = e.onlineOnlyMessage("Failed to load procurement form options")
                )
            }
        }
    }

    suspend fun searchProducts(term: String): List<MobileProduct> {
        val query = term.trim()
        if (query.length < 3) return emptyList()
        return runCatching {
            val response = withContext(Dispatchers.IO) {
                api.searchProducts(SearchRequest(search = query))
            }
            response.results
        }.getOrDefault(emptyList())
    }

    fun createProcurement(
        providerId: Long,
        notes: String?,
        products: List<ProcurementProductRequestDto>,
        name: String? = null,
        invoiceReference: String? = null,
        invoiceDate: String? = null,
        deliveryStatus: String? = null,
        paymentStatus: String? = null,
        expectedDelivery: String? = null
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isCreating = true,
                error = null,
                createSuccess = false,
                createdProcurementId = null
            )

            try {
                val createdProcurement = withContext(Dispatchers.IO) {
                    api.createProcurement(
                        CreateProcurementRequest(
                            providerId = providerId,
                            products = products,
                            notes = notes?.takeIf { it.isNotBlank() },
                            expectedDelivery = expectedDelivery?.takeIf { it.isNotBlank() },
                            name = name?.takeIf { it.isNotBlank() },
                            invoiceReference = invoiceReference?.takeIf { it.isNotBlank() },
                            invoiceDate = invoiceDate?.takeIf { it.isNotBlank() },
                            status = deliveryStatus?.takeIf { it.isNotBlank() },
                            paymentStatus = paymentStatus?.takeIf { it.isNotBlank() }
                        )
                    )
                }.data

                val mappedProcurement = toDomainModel(createdProcurement)
                val updatedProcurements = listOf(mappedProcurement) + _state.value.procurements
                    .filterNot { it.id == mappedProcurement.id }

                _state.value = _state.value.copy(
                    isCreating = false,
                    createSuccess = true,
                    createdProcurementId = mappedProcurement.id,
                    procurements = updatedProcurements,
                    filteredProcurements = filterProcurements(
                        updatedProcurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isCreating = false,
                    error = e.onlineOnlyMessage("Failed to create procurement")
                )
            }
        }
    }

    /**
     * Set status filter for the procurement list.
     */
    fun setStatusFilter(status: ProcurementStatus?) {
        val filtered = filterProcurements(
            _state.value.procurements, 
            status,
            _state.value.searchQuery
        )
        _state.value = _state.value.copy(
            selectedStatusFilter = status,
            filteredProcurements = filtered
        )
    }

    /**
     * Set search query for the procurement list.
     */
    fun setSearchQuery(query: String) {
        val filtered = filterProcurements(
            _state.value.procurements,
            _state.value.selectedStatusFilter,
            query
        )
        _state.value = _state.value.copy(
            searchQuery = query,
            filteredProcurements = filtered
        )
    }

    /**
     * Filter procurements by status and search query.
     */
    private fun filterProcurements(
        procurements: List<ProcurementOrder>,
        status: ProcurementStatus?,
        searchQuery: String
    ): List<ProcurementOrder> {
        var filtered = procurements
        
        // Filter by status
        if (status != null) {
            filtered = filtered.filter { it.status == status }
        }
        
        // Filter by search query (search in ID and provider name)
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase()
            filtered = filtered.filter { procurement ->
                procurement.id.toString().contains(query) ||
                procurement.providerName.lowercase().contains(query)
            }
        }
        
        return filtered
    }

    /**
     * Receive procurement items (partial or full delivery).
     */
    fun receiveProcurementItems(
        procurementId: Long,
        items: List<ReceiveProcurementItem>
    ) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isReceiving = true, error = null)
            
            try {
                val request = ReceiveProcurementRequest(items = items)
                
                val response = withContext(Dispatchers.IO) {
                    api.receiveProcurement(procurementId, request)
                }
                
                val updatedProcurement = toDomainModel(response.data)
                
                _detailState.value = _detailState.value.copy(
                    isReceiving = false,
                    procurement = updatedProcurement,
                    receiveSuccess = true
                )
                
                // Update the item in the list state
                val updatedProcurements = _state.value.procurements.map { 
                    if (it.id == procurementId) updatedProcurement else it 
                }
                _state.value = _state.value.copy(
                    procurements = updatedProcurements,
                    filteredProcurements = filterProcurements(
                        updatedProcurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    isReceiving = false,
                    error = e.onlineOnlyMessage("Failed to receive items")
                )
            }
        }
    }

    /**
     * Cancel a procurement order.
     */
    fun cancelProcurement(procurementId: Long) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isCancelling = true, error = null)
            
            try {
                withContext(Dispatchers.IO) {
                    api.cancelProcurement(procurementId)
                }

                val cancelledProcurement = _detailState.value.procurement?.copy(
                    status = ProcurementStatus.CANCELLED
                )

                _detailState.value = _detailState.value.copy(
                    isCancelling = false,
                    procurement = cancelledProcurement,
                    cancelSuccess = true
                )
                
                // Update the item in the list state
                val updatedProcurements = _state.value.procurements.filterNot { it.id == procurementId }
                _state.value = _state.value.copy(
                    procurements = updatedProcurements,
                    filteredProcurements = filterProcurements(
                        updatedProcurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    isCancelling = false,
                    error = e.onlineOnlyMessage("Failed to cancel procurement")
                )
            }
        }
    }

    /**
     * Approve a procurement order.
     */
    fun approveProcurement(procurementId: Long) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(error = null)
            
            try {
                val response = withContext(Dispatchers.IO) {
                    api.updateProcurementStatus(procurementId, "DELIVERED")
                }
                
                val updatedProcurement = toDomainModel(response.data)
                
                _detailState.value = _detailState.value.copy(
                    procurement = updatedProcurement
                )
                
                // Update the item in the list state
                val updatedProcurements = _state.value.procurements.map { 
                    if (it.id == procurementId) updatedProcurement else it 
                }
                _state.value = _state.value.copy(
                    procurements = updatedProcurements,
                    filteredProcurements = filterProcurements(
                        updatedProcurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    error = e.onlineOnlyMessage("Failed to approve procurement")
                )
            }
        }
    }

    /**
     * Mark a procurement as paid.
     */
    fun markProcurementPaid(procurementId: Long) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isPaying = true, error = null)

            try {
                val response = withContext(Dispatchers.IO) {
                    api.updateProcurement(
                        procurementId,
                        UpdateProcurementRequest(paymentStatus = "paid")
                    )
                }

                val updatedProcurement = toDomainModel(response.data)

                _detailState.value = _detailState.value.copy(
                    isPaying = false,
                    procurement = updatedProcurement,
                    paySuccess = true
                )

                val updatedProcurements = _state.value.procurements.map {
                    if (it.id == procurementId) updatedProcurement else it
                }
                _state.value = _state.value.copy(
                    procurements = updatedProcurements,
                    filteredProcurements = filterProcurements(
                        updatedProcurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    isPaying = false,
                    error = e.onlineOnlyMessage("Failed to mark procurement as paid")
                )
            }
        }
    }

    /**
     * Mark a procurement order as delivered (completed).
     */
    fun markProcurementDelivered(procurementId: Long) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(error = null)
            
            try {
                val response = withContext(Dispatchers.IO) {
                    api.updateProcurementStatus(procurementId, "COMPLETED")
                }
                
                val updatedProcurement = toDomainModel(response.data)
                
                _detailState.value = _detailState.value.copy(
                    procurement = updatedProcurement
                )
                
                // Update the item in the list state
                val updatedProcurements = _state.value.procurements.map { 
                    if (it.id == procurementId) updatedProcurement else it 
                }
                _state.value = _state.value.copy(
                    procurements = updatedProcurements,
                    filteredProcurements = filterProcurements(
                        updatedProcurements,
                        _state.value.selectedStatusFilter,
                        _state.value.searchQuery
                    )
                )
            } catch (e: Exception) {
                _detailState.value = _detailState.value.copy(
                    error = e.onlineOnlyMessage("Failed to mark procurement as delivered")
                )
            }
        }
    }

    /**
     * Clear the receive success flag.
     */
    fun clearReceiveSuccess() {
        _detailState.value = _detailState.value.copy(receiveSuccess = false)
    }

    /**
     * Clear the pay success flag.
     */
    fun clearPaySuccess() {
        _detailState.value = _detailState.value.copy(paySuccess = false)
    }

    /**
     * Clear the cancel success flag.
     */
    fun clearCancelSuccess() {
        _detailState.value = _detailState.value.copy(cancelSuccess = false)
    }

    /**
     * Clear the create success flag.
     */
    fun clearCreateSuccess() {
        _state.value = _state.value.copy(createSuccess = false, createdProcurementId = null)
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
        _detailState.value = _detailState.value.copy(error = null)
    }
}

data class ProcurementProductRequest(
    val productId: Long,
    val quantity: Int,
    val unitPrice: Double
)
