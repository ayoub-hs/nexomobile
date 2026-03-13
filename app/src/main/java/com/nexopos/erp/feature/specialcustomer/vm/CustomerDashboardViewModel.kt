package com.nexopos.erp.feature.specialcustomer.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.CreateTopupRequest
import com.nexopos.erp.core.network.Customer
import com.nexopos.erp.core.network.CustomerBalance
import com.nexopos.erp.core.network.MobileApi
import com.nexopos.erp.core.network.PayTicketWithMethodRequest
import com.nexopos.erp.core.network.WalletTopup
import com.nexopos.erp.core.repo.CustomerRepository
import com.nexopos.erp.feature.specialcustomer.OutstandingTicket
import com.nexopos.erp.feature.specialcustomer.TicketStatus
import com.nexopos.erp.feature.specialcustomer.CustomerDto
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Customer Dashboard State
 * 
 * Holds all state for the Customer Dashboard screen including
 * customer details, wallet balance, topups, and outstanding tickets.
 */
data class CustomerDashboardState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    
    // Customer details
    val customerId: Long = 0,
    val customer: Customer? = null,
    
    // Wallet balance
    val walletBalance: Double = 0.0,
    val isLoadingBalance: Boolean = false,
    val balanceError: String? = null,
    
    // Total due from outstanding tickets
    val totalDue: Double = 0.0,
    
    // Topups
    val topups: List<WalletTopup> = emptyList(),
    val isLoadingTopups: Boolean = false,
    val topupsError: String? = null,
    
    // Outstanding tickets
    val outstandingTickets: List<OutstandingTicket> = emptyList(),
    val isLoadingTickets: Boolean = false,
    val ticketsError: String? = null,
    val selectedTickets: Set<Int> = emptySet(),
    
    // Topup creation
    val isCreatingTopup: Boolean = false,
    val topupCreateError: String? = null,
    val topupCreateSuccess: Boolean = false,
    
    // Payment from wallet
    val isProcessingPayment: Boolean = false,
    val paymentError: String? = null,
    val paymentSuccess: Boolean = false,
    val paymentBatchSummary: TicketPaymentBatchSummary? = null
)

data class TicketPaymentAttemptResult(
    val ticketId: Int,
    val ticketCode: String,
    val amount: Double,
    val message: String? = null
)

data class TicketPaymentBatchSummary(
    val succeeded: List<TicketPaymentAttemptResult> = emptyList(),
    val failed: List<TicketPaymentAttemptResult> = emptyList()
) {
    val hasPartialSuccess: Boolean
        get() = succeeded.isNotEmpty() && failed.isNotEmpty()
}

/**
 * Customer Dashboard ViewModel
 * 
 * Manages state for the Customer Dashboard screen.
 * Loads customer details, wallet balance, topups, and outstanding tickets.
 * Handles topup creation and payment from wallet.
 */
class CustomerDashboardViewModel(
    private val api: MobileApi,
    private val customerRepository: CustomerRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "CustomerDashboardVM"
    }
    
    private val _state = MutableStateFlow(CustomerDashboardState())
    val state: StateFlow<CustomerDashboardState> = _state.asStateFlow()
    
    /**
     * Initialize the dashboard with customer ID
     */
    fun initialize(customerId: Long) {
        if (_state.value.customerId == customerId && _state.value.customer != null) {
            // Already initialized
            return
        }
        
        _state.value = _state.value.copy(customerId = customerId)
        loadCustomerData(customerId)
    }
    
    /**
     * Load all customer data
     */
    private fun loadCustomerData(customerId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                // Load customer details
                val customer = customerRepository.getCustomerById(customerId)
                if (customer == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Customer not found"
                    )
                    return@launch
                }
                
                _state.value = _state.value.copy(customer = customer)
                
                refreshSupplementalData(customerId)
                
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load customer data", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load customer data"
                )
            }
        }
    }
    
    /**
     * Refresh all data
     * Fetches fresh data from the special customer API endpoint
     */
    fun refresh() {
        val customerId = _state.value.customerId
        if (customerId == 0L) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            
            try {
                // Fetch fresh data from special customer API
                val result = customerRepository.getSpecialCustomers()
                result.getOrNull()?.find { it.id == customerId }?.let { updatedCustomer ->
                    // Convert SpecialCustomerDto to Customer for the state
                    val customer = Customer(
                        id = updatedCustomer.id,
                        username = null,
                        name = updatedCustomer.name,
                        firstName = updatedCustomer.firstName,
                        lastName = updatedCustomer.lastName,
                        email = updatedCustomer.email,
                        phone = updatedCustomer.phone
                    )
                    _state.value = _state.value.copy(customer = customer)
                }
                
                refreshSupplementalData(customerId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh", e)
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    private suspend fun refreshSupplementalData(customerId: Long) = coroutineScope {
        launch { loadWalletBalance(customerId) }
        launch { loadTopups(customerId) }
        launch { loadOutstandingTickets(customerId) }
    }
    
    /**
     * Load customer wallet balance
     */
    private suspend fun loadWalletBalance(customerId: Long) {
        _state.value = _state.value.copy(isLoadingBalance = true, balanceError = null)
        
        try {
            val response = api.getCustomerWalletBalance(customerId)
            _state.value = _state.value.copy(
                isLoadingBalance = false,
                walletBalance = response.data.balance
            )
            Log.d(TAG, "Wallet balance loaded: ${response.data.balance}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load wallet balance", e)
            _state.value = _state.value.copy(
                isLoadingBalance = false,
                balanceError = e.message ?: "Failed to load balance"
            )
        }
    }
    
    /**
     * Load customer topups
     */
    private suspend fun loadTopups(customerId: Long) {
        _state.value = _state.value.copy(isLoadingTopups = true, topupsError = null)
        
        try {
            val response = api.getWalletTopups(customerId = customerId)
            _state.value = _state.value.copy(
                isLoadingTopups = false,
                topups = response.getTopups()
            )
            Log.d(TAG, "Topups loaded: ${response.getTopups().size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load topups", e)
            _state.value = _state.value.copy(
                isLoadingTopups = false,
                topupsError = e.message ?: "Failed to load topups"
            )
        }
    }
    
    /**
     * Load customer outstanding tickets
     */
    private suspend fun loadOutstandingTickets(customerId: Long) {
        _state.value = _state.value.copy(isLoadingTickets = true, ticketsError = null)
        
        try {
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
            
            // Calculate total due
            val totalDue = tickets.filter { 
                it.paymentStatus == TicketStatus.UNPAID || it.paymentStatus == TicketStatus.PARTIALLY_PAID 
            }.sumOf { it.dueAmount }
            
            _state.value = _state.value.copy(
                isLoadingTickets = false,
                outstandingTickets = tickets,
                totalDue = totalDue
            )
            Log.d(TAG, "Outstanding tickets loaded: ${tickets.size}, total due: $totalDue")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load outstanding tickets", e)
            _state.value = _state.value.copy(
                isLoadingTickets = false,
                ticketsError = e.message ?: "Failed to load tickets"
            )
        }
    }
    
    /**
     * Create a new topup
     * @param amount The topup amount
     * @param description Optional description/notes for the topup
     * @param receivedDate Date the payment was received, in YYYY-MM-DD format
     */
    fun createTopup(amount: Double, description: String? = null, receivedDate: String) {
        val customerId = _state.value.customerId
        if (customerId == 0L) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isCreatingTopup = true,
                topupCreateError = null,
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
                    
                    // Refresh balance and topups (launch in separate coroutines to ensure execution)
                    viewModelScope.launch {
                        loadWalletBalance(customerId)
                    }
                    viewModelScope.launch {
                        loadTopups(customerId)
                    }
                    
                    Log.i(TAG, "Topup created successfully")
                } else {
                    _state.value = _state.value.copy(
                        isCreatingTopup = false,
                        topupCreateError = response.message ?: "Failed to create topup"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create topup", e)
                _state.value = _state.value.copy(
                    isCreatingTopup = false,
                    topupCreateError = e.message ?: "Failed to create topup"
                )
            }
        }
    }
    
    /**
     * Toggle ticket selection for payment
     */
    fun toggleTicketSelection(ticketId: Int) {
        val currentSelection = _state.value.selectedTickets
        val newSelection = if (currentSelection.contains(ticketId)) {
            currentSelection - ticketId
        } else {
            currentSelection + ticketId
        }
        _state.value = _state.value.copy(selectedTickets = newSelection)
    }
    
    /**
     * Select all unpaid tickets
     */
    fun selectAllUnpaidTickets() {
        val unpaidTicketIds = _state.value.outstandingTickets
            .filter { it.paymentStatus == TicketStatus.UNPAID || it.paymentStatus == TicketStatus.PARTIALLY_PAID }
            .map { it.id }
            .toSet()
        _state.value = _state.value.copy(selectedTickets = unpaidTicketIds)
    }
    
    /**
     * Clear ticket selection
     */
    fun clearTicketSelection() {
        _state.value = _state.value.copy(selectedTickets = emptySet())
    }
    
    /**
     * Pay selected tickets from wallet
     */
    fun paySelectedTicketsFromWallet() {
        val customerId = _state.value.customerId
        val selectedTicketIds = _state.value.selectedTickets
        
        if (customerId == 0L || selectedTicketIds.isEmpty()) return
        
        val selectedTickets = _state.value.outstandingTickets.filter { it.id in selectedTicketIds }
        val totalAmount = selectedTickets.sumOf { it.dueAmount }
        val walletBalance = _state.value.walletBalance
        
        if (totalAmount > walletBalance) {
            _state.value = _state.value.copy(
                paymentError = "Insufficient wallet balance"
            )
            return
        }
        
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessingPayment = true,
                paymentError = null,
                paymentSuccess = false,
                paymentBatchSummary = null
            )
            
            try {
                val successfulPayments = mutableListOf<TicketPaymentAttemptResult>()
                val failedPayments = mutableListOf<TicketPaymentAttemptResult>()

                for (ticket in selectedTickets) {
                    try {
                        val request = PayTicketWithMethodRequest(
                            orderId = ticket.id.toLong(),
                            customerId = customerId,
                            amount = ticket.dueAmount,
                            paymentMethod = "wallet"
                        )

                        val response = api.payOutstandingTicketWithMethod(request)
                        if (response.status == "success") {
                            successfulPayments += TicketPaymentAttemptResult(
                                ticketId = ticket.id,
                                ticketCode = ticket.code,
                                amount = ticket.dueAmount
                            )
                        } else {
                            failedPayments += TicketPaymentAttemptResult(
                                ticketId = ticket.id,
                                ticketCode = ticket.code,
                                amount = ticket.dueAmount,
                                message = response.message ?: "Payment failed"
                            )
                            Log.w(TAG, "Failed to pay ticket ${ticket.id}: ${response.message}")
                        }
                    } catch (e: Exception) {
                        failedPayments += TicketPaymentAttemptResult(
                            ticketId = ticket.id,
                            ticketCode = ticket.code,
                            amount = ticket.dueAmount,
                            message = e.message ?: "Payment failed"
                        )
                        Log.e(TAG, "Failed to pay ticket ${ticket.id} from wallet", e)
                    }
                }

                if (successfulPayments.isNotEmpty()) {
                    refreshPaymentData(customerId)
                }

                _state.value = _state.value.copy(
                    isProcessingPayment = false,
                    paymentSuccess = false,
                    paymentBatchSummary = TicketPaymentBatchSummary(
                        succeeded = successfulPayments,
                        failed = failedPayments
                    ),
                    selectedTickets = failedPayments.mapTo(linkedSetOf()) { it.ticketId }
                )

                Log.i(
                    TAG,
                    "Batch wallet payment completed. Success: ${successfulPayments.size}, Failed: ${failedPayments.size}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pay tickets from wallet", e)
                _state.value = _state.value.copy(
                    isProcessingPayment = false,
                    paymentError = e.message ?: "Payment failed"
                )
            }
        }
    }
    
    /**
     * Pay a single ticket from wallet
     */
    fun payTicketFromWallet(ticketId: Int) {
        val customerId = _state.value.customerId
        val ticket = _state.value.outstandingTickets.find { it.id == ticketId }
        
        if (customerId == 0L || ticket == null) return
        
        if (ticket.dueAmount > _state.value.walletBalance) {
            _state.value = _state.value.copy(
                paymentError = "Insufficient wallet balance"
            )
            return
        }
        
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isProcessingPayment = true,
                paymentError = null,
                paymentSuccess = false,
                paymentBatchSummary = null
            )
            
            try {
                // Use pay-with-method endpoint (same as webapp)
                val request = PayTicketWithMethodRequest(
                    orderId = ticketId.toLong(),
                    customerId = customerId,
                    amount = ticket.dueAmount,
                    paymentMethod = "wallet"
                )
                
                val response = api.payOutstandingTicketWithMethod(request)
                
                if (response.status == "success") {
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        paymentSuccess = true
                    )
                    
                    // Refresh data (launch in separate coroutines to ensure execution)
                    viewModelScope.launch {
                        loadWalletBalance(customerId)
                    }
                    viewModelScope.launch {
                        loadOutstandingTickets(customerId)
                    }
                    
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
    
    /**
     * Clear topup creation state
     */
    fun clearTopupCreateState() {
        _state.value = _state.value.copy(
            topupCreateSuccess = false,
            topupCreateError = null
        )
    }
    
    /**
     * Clear payment state
     */
    fun clearPaymentState() {
        _state.value = _state.value.copy(
            paymentSuccess = false,
            paymentError = null,
            paymentBatchSummary = null
        )
    }

    private fun refreshPaymentData(customerId: Long) {
        viewModelScope.launch {
            loadWalletBalance(customerId)
        }
        viewModelScope.launch {
            loadOutstandingTickets(customerId)
        }
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
