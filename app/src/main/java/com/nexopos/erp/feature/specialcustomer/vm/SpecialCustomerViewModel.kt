package com.nexopos.erp.feature.specialcustomer.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.db.dao.QueuedTicketPaymentDao
import com.nexopos.erp.core.db.entities.QueuedPaymentStatus
import com.nexopos.erp.core.db.entities.QueuedTicketPaymentEntity
import com.nexopos.erp.core.network.CreateTopupRequest
import com.nexopos.erp.core.network.CustomerBalance
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.PayTicketRequest
import com.nexopos.erp.core.network.PayTicketWithMethodRequest
import com.nexopos.erp.core.network.ServiceLocator
import com.nexopos.erp.core.network.WalletTopup
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.feature.specialcustomer.CustomerDto
import com.nexopos.erp.feature.specialcustomer.OutstandingTicket
import com.nexopos.erp.feature.specialcustomer.TicketStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class SpecialCustomerState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val tickets: List<OutstandingTicket> = emptyList(),
    val filteredTickets: List<OutstandingTicket> = emptyList(),
    val selectedTicket: OutstandingTicket? = null,
    val selectedStatusFilter: TicketStatus? = null,
    val error: String? = null,
    val detailError: String? = null,
    // Payment state
    val isProcessingPayment: Boolean = false,
    val paymentError: String? = null,
    val paymentSuccess: Boolean = false,
    // Offline state
    val pendingPaymentsCount: Int = 0,
    // Wallet Topup state
    val topups: List<WalletTopup> = emptyList(),
    val isLoadingTopups: Boolean = false,
    val isRefreshingTopups: Boolean = false,
    val topupError: String? = null,
    val isCreatingTopup: Boolean = false,
    val topupCreateSuccess: Boolean = false,
    // Customer-specific state
    val customerBalance: CustomerBalance? = null,
    val isLoadingBalance: Boolean = false,
    val balanceError: String? = null
)

class SpecialCustomerViewModel(
    private val api: MobileApi,
    private val settingsRepository: SettingsRepository,
    private val queuedPaymentDao: QueuedTicketPaymentDao? = null
) : ViewModel() {

    companion object {
        private const val TAG = "SpecialCustomerViewModel"
    }

    private val _state = MutableStateFlow(SpecialCustomerState())
    val state: StateFlow<SpecialCustomerState> = _state.asStateFlow()

    init {
        // Observe pending payments count for offline indicator
        queuedPaymentDao?.let { dao ->
            viewModelScope.launch {
                dao.getPendingCount().collect { count ->
                    _state.value = _state.value.copy(pendingPaymentsCount = count)
                }
            }
        }
    }

    fun loadTickets() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // Determine status string from filter
                val statusParam = _state.value.selectedStatusFilter?.value
                
                val response = api.getOutstandingTickets(status = statusParam)
                
                // Map network model to domain model
                val tickets = response.data.map { dto ->
                     OutstandingTicket(
                        id = dto.id.toInt(),
                        code = dto.code,
                        customerId = dto.customerId.toInt(),
                        customer = dto.customer?.let { 
                            CustomerDto(it.id.toInt(), it.name, it.email, it.phone) 
                        },
                        total = dto.total,
                        paidAmount = dto.paidAmount,
                        dueAmount = dto.dueAmount,
                        paymentStatus = TicketStatus.fromValue(dto.paymentStatus),
                        createdAt = dto.createdAt,
                        dueDate = null 
                    )
                }
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    tickets = tickets,
                    filteredTickets = tickets
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Failed to load tickets")
                )
            }
        }
    }

    fun refreshTickets() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val statusParam = _state.value.selectedStatusFilter?.value
                val response = api.getOutstandingTickets(status = statusParam)
                
                val tickets = response.data.map { dto ->
                     OutstandingTicket(
                        id = dto.id.toInt(),
                        code = dto.code,
                        customerId = dto.customerId.toInt(),
                        customer = dto.customer?.let { 
                            CustomerDto(it.id.toInt(), it.name, it.email, it.phone) 
                        },
                        total = dto.total,
                        paidAmount = dto.paidAmount,
                        dueAmount = dto.dueAmount,
                        paymentStatus = TicketStatus.fromValue(dto.paymentStatus),
                        createdAt = dto.createdAt,
                        dueDate = null 
                    )
                }
                
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    tickets = tickets,
                    filteredTickets = tickets
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = e.onlineOnlyMessage("Failed to refresh tickets")
                )
            }
        }
    }

    fun loadTicketDetail(ticketId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingDetail = true, detailError = null)
            try {
                val response = api.getOutstandingTicket(ticketId)
                val detail = response.data
                
                val ticket = OutstandingTicket(
                    id = detail.id.toInt(),
                    code = detail.code,
                    customerId = detail.customerId.toInt(),
                    customer = detail.customer?.let {
                        CustomerDto(it.id.toInt(), it.name, it.email, it.phone)
                    },
                    total = detail.total,
                    paidAmount = detail.paidAmount,
                    dueAmount = detail.dueAmount,
                    paymentStatus = TicketStatus.fromValue(detail.paymentStatus),
                    createdAt = detail.createdAt,
                    dueDate = null
                )
                
                _state.value = _state.value.copy(
                    isLoadingDetail = false,
                    selectedTicket = ticket
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingDetail = false,
                    detailError = e.onlineOnlyMessage("Failed to load ticket detail")
                )
            }
        }
    }

    fun setStatusFilter(status: TicketStatus?) {
        _state.value = _state.value.copy(selectedStatusFilter = status)
        loadTickets()
    }
    
    fun clearStatusFilter() {
        setStatusFilter(null)
    }

    fun clearSelectedTicket() {
        _state.value = _state.value.copy(selectedTicket = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Record a payment for an outstanding ticket.
     * @param ticketId The ID of the ticket to pay
     * @param amount The amount to pay
     */
    fun recordPayment(ticketId: Long, amount: Double) {
        val ticket = _state.value.tickets.find { it.id.toLong() == ticketId }
        
        if (ticket == null) {
            _state.value = _state.value.copy(
                paymentError = "Ticket not found"
            )
            return
        }
        
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessingPayment = true,
                paymentError = null,
                paymentSuccess = false
            )
            
            try {
                // Try online payment first
                val request = PayTicketRequest(
                    amount = amount,
                    paymentMethod = "cash",
                    reference = null
                )
                val response = api.payOutstandingTicket(ticketId.toInt(), request)
                
                if (response.status == "success") {
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        paymentSuccess = true
                    )
                    // Refresh the tickets list to reflect the payment
                    loadTickets()
                } else {
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        paymentError = response.message ?: "Payment failed"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Online payment failed", e)
                _state.value = _state.value.copy(
                    isProcessingPayment = false,
                    paymentError = e.onlineOnlyMessage("Payment failed")
                )
            }
        }
    }

    /**
     * Queue a payment for offline sync
     */
    private suspend fun queuePaymentForOffline(ticketId: Long, ticketCode: String, amount: Double) {
        if (queuedPaymentDao == null) {
            _state.value = _state.value.copy(
                isProcessingPayment = false,
                paymentError = "Offline payment not available"
            )
            return
        }
        
        try {
            val clientReference = UUID.randomUUID().toString()
            val queuedPayment = QueuedTicketPaymentEntity(
                ticketId = ticketId,
                ticketCode = ticketCode,
                amount = amount,
                paymentMethod = "cash",
                clientReference = clientReference,
                status = QueuedPaymentStatus.PENDING.name
            )
            
            queuedPaymentDao.insert(queuedPayment)
            
            _state.value = _state.value.copy(
                isProcessingPayment = false,
                paymentSuccess = true // Show success - payment is queued
            )
            
            Log.i(TAG, "Payment queued for offline sync: $clientReference")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue payment for offline", e)
            _state.value = _state.value.copy(
                isProcessingPayment = false,
                paymentError = "Failed to record payment: ${e.message}"
            )
        }
    }

    /**
     * Pay an outstanding ticket.
     * @param ticketId The ID of the ticket to pay
     * @param amount The amount to pay
     * @param paymentMethod The payment method (e.g., "cash", "card")
     * @param reference Optional payment reference
     */
    fun payTicket(ticketId: Int, amount: Double, paymentMethod: String, reference: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessingPayment = true,
                paymentError = null,
                paymentSuccess = false
            )
            try {
                val request = PayTicketRequest(
                    amount = amount,
                    paymentMethod = paymentMethod,
                    reference = reference
                )
                val response = api.payOutstandingTicket(ticketId, request)
                if (response.status == "success") {
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        paymentSuccess = true
                    )
                    // Refresh the tickets list to reflect the payment
                    loadTickets()
                } else {
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        paymentError = response.message ?: "Payment failed"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessingPayment = false,
                    paymentError = e.onlineOnlyMessage("Payment failed")
                )
            }
        }
    }

    /**
     * Clear payment state after handling success/error
     */
    fun clearPaymentState() {
        _state.value = _state.value.copy(
            paymentError = null,
            paymentSuccess = false
        )
    }

    // ========================================================================
    // WALLET TOPUP METHODS
    // ========================================================================

    /**
     * Load wallet topups list
     * @param customerId Optional customer ID to filter topups
     */
    fun loadTopups(customerId: Long? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingTopups = true, topupError = null)
            try {
                Log.d(TAG, "Loading topups for customerId: $customerId")
                val response = api.getWalletTopups(customerId = customerId)
                Log.d(TAG, "Topup response received - status: ${response.status}, message: ${response.message}")
                Log.d(TAG, "Topup response data size: ${response.data.items.size} items")
                
                _state.value = _state.value.copy(
                    isLoadingTopups = false,
                    topups = response.getTopups()
                )
                Log.d(TAG, "Topups loaded successfully: ${_state.value.topups.size} items")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load topups", e)
                _state.value = _state.value.copy(
                    isLoadingTopups = false,
                    topupError = e.message ?: "Failed to load topups"
                )
            }
        }
    }

    /**
     * Refresh wallet topups list (pull-to-refresh)
     * @param customerId Optional customer ID to filter topups
     */
    fun refreshTopups(customerId: Long? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshingTopups = true, topupError = null)
            try {
                val response = api.getWalletTopups(customerId = customerId)
                
                _state.value = _state.value.copy(
                    isRefreshingTopups = false,
                    topups = response.getTopups()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh topups", e)
                _state.value = _state.value.copy(
                    isRefreshingTopups = false,
                    topupError = e.message ?: "Failed to refresh topups"
                )
            }
        }
    }

    /**
     * Create a new wallet topup
     * @param customerId The ID of the customer
     * @param amount The topup amount
     * @param description Optional description/notes for the topup
     * @param receivedDate Date the payment was received, in YYYY-MM-DD format
     */
    fun createTopup(
        customerId: Long,
        amount: Double,
        description: String? = null,
        receivedDate: String
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isCreatingTopup = true,
                topupError = null,
                topupCreateSuccess = false
            )
            try {
                val request = CreateTopupRequest(
                    customerId = customerId,
                    amount = amount,
                    description = description,
                    receivedDate = receivedDate
                )
                val response = api.createWalletTopup(request)
                
                if (response.status == "success" && response.data?.success == true) {
                    _state.value = _state.value.copy(
                        isCreatingTopup = false,
                        topupCreateSuccess = true
                    )
                    // Refresh the topups list
                    loadTopups()
                } else {
                    _state.value = _state.value.copy(
                        isCreatingTopup = false,
                        topupError = response.message ?: "Failed to create topup"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create topup", e)
                _state.value = _state.value.copy(
                    isCreatingTopup = false,
                    topupError = e.message ?: "Failed to create topup"
                )
            }
        }
    }

    /**
     * Clear topup error state
     */
    fun clearTopupError() {
        _state.value = _state.value.copy(topupError = null)
    }

    /**
     * Clear topup create success state
     */
    fun clearTopupCreateState() {
        _state.value = _state.value.copy(
            topupCreateSuccess = false,
            topupError = null
        )
    }

    // ========================================================================
    // CUSTOMER OUTSTANDING TICKETS METHODS
    // ========================================================================

    /**
     * Load outstanding tickets for a specific customer
     * @param customerId The ID of the customer
     */
    fun loadCustomerOutstandingTickets(customerId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                Log.d(TAG, "Loading outstanding tickets for customer: $customerId")
                val response = api.getOutstandingTickets(customerId = customerId.toInt())
                
                val tickets = response.data.map { dto ->
                    OutstandingTicket(
                        id = dto.id.toInt(),
                        code = dto.code,
                        customerId = dto.customerId.toInt(),
                        customer = dto.customer?.let {
                            CustomerDto(it.id.toInt(), it.name, it.email, it.phone)
                        },
                        total = dto.total,
                        paidAmount = dto.paidAmount,
                        dueAmount = dto.dueAmount,
                        paymentStatus = TicketStatus.fromValue(dto.paymentStatus),
                        createdAt = dto.createdAt,
                        dueDate = null
                    )
                }
                
                _state.value = _state.value.copy(
                    isLoading = false,
                    tickets = tickets,
                    filteredTickets = tickets
                )
                Log.d(TAG, "Loaded ${tickets.size} outstanding tickets for customer $customerId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load customer outstanding tickets", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load tickets"
                )
            }
        }
    }

    /**
     * Load customer wallet balance
     * @param customerId The ID of the customer
     */
    fun loadCustomerBalance(customerId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingBalance = true, balanceError = null)
            try {
                Log.d(TAG, "Loading wallet balance for customer: $customerId")
                val response = api.getCustomerWalletBalance(customerId)
                
                _state.value = _state.value.copy(
                    isLoadingBalance = false,
                    customerBalance = response.data
                )
                Log.d(TAG, "Wallet balance loaded: ${response.data.balance}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load customer balance", e)
                _state.value = _state.value.copy(
                    isLoadingBalance = false,
                    balanceError = e.message ?: "Failed to load balance"
                )
            }
        }
    }

    /**
     * Pay an outstanding ticket from customer's wallet balance.
     * @param customerId The ID of the customer
     * @param ticketId The ID of the ticket to pay
     * @param amount The amount to pay
     */
    fun payTicketFromWallet(customerId: Long, ticketId: Long, amount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessingPayment = true,
                paymentError = null,
                paymentSuccess = false
            )
            
            try {
                Log.d(TAG, "Paying ticket $ticketId from wallet for customer $customerId, amount: $amount")
                
                // Use pay-with-method endpoint (same as webapp)
                val request = PayTicketWithMethodRequest(
                    orderId = ticketId.toLong(),
                    customerId = customerId,
                    amount = amount,
                    paymentMethod = "wallet"
                )
                val response = api.payOutstandingTicketWithMethod(request)
                
                if (response.status == "success") {
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        paymentSuccess = true
                    )
                    Log.i(TAG, "Ticket $ticketId paid from wallet successfully")
                } else {
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        paymentError = response.message ?: "Payment failed"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pay ticket from wallet", e)
                _state.value = _state.value.copy(
                    isProcessingPayment = false,
                    paymentError = e.message ?: "Payment failed"
                )
            }
        }
    }
}
