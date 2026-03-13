package com.nexopos.desktop.ui.pos

import com.nexopos.desktop.core.network.ContainerReceiveRequest
import com.nexopos.desktop.core.network.ContainerType
import com.nexopos.desktop.core.network.CustomerContainerBalance
import com.nexopos.desktop.core.network.NexoApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receive containers state for desktop.
 */
data class ContainerReceiveState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val customers: List<CustomerOption> = emptyList(),
    val containerTypes: List<ContainerType> = emptyList(),
    val selectedCustomerId: Long? = null,
    val balances: List<CustomerContainerBalance> = emptyList(),
    val selectedContainerTypeId: Long? = null,
    val quantityText: String = "1",
    val quantityValue: Int = 1,
    val notes: String = "",
    val message: String? = null,
    val error: String? = null
)

data class CustomerOption(
    val id: Long,
    val name: String
)

class ReceiveContainersViewModel(
    private val api: NexoApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(ContainerReceiveState())
    val state: StateFlow<ContainerReceiveState> = _state.asStateFlow()

    private var allBalances: List<CustomerContainerBalance> = emptyList()

    init {
        loadInitial()
    }

    fun loadInitial() {
        scope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                message = null
            )

            val containerTypesResult = withContext(Dispatchers.IO) {
                api.getContainerTypes()
            }

            val balancesResult = withContext(Dispatchers.IO) {
                api.getCustomerContainerBalances(limit = 100, offset = 0)
            }

            var containerTypes: List<ContainerType> = emptyList()
            var balances: List<CustomerContainerBalance> = emptyList()
            var errorMessage: String? = null

            if (containerTypesResult.isSuccess) {
                val response = containerTypesResult.getOrNull()
                if (response?.status == "success" || response?.status == null) {
                    containerTypes = response?.data ?: emptyList()
                } else {
                    errorMessage = response.message ?: "Failed to load container types"
                }
            } else {
                errorMessage = containerTypesResult.exceptionOrNull()?.message
                    ?: "Failed to load container types"
            }

            if (balancesResult.isSuccess) {
                val response = balancesResult.getOrNull()
                if (response?.status == "success" || response?.status == null) {
                    balances = response?.data ?: emptyList()
                } else if (errorMessage == null) {
                    errorMessage = response?.message ?: "Failed to load customer balances"
                }
            } else if (errorMessage == null) {
                errorMessage = balancesResult.exceptionOrNull()?.message
                    ?: "Failed to load customer balances"
            }

            allBalances = balances
            val customers = buildCustomerOptions(allBalances)
            val selectedCustomerId = _state.value.selectedCustomerId
            val selectedBalances = selectedCustomerId?.let { id ->
                balances.filter { it.customerId == id }
            } ?: emptyList()

            _state.value = _state.value.copy(
                isLoading = false,
                containerTypes = containerTypes,
                customers = customers,
                balances = selectedBalances,
                error = errorMessage
            )
        }
    }

    fun refreshSelectedCustomer(keepMessage: Boolean = false) {
        val customerId = _state.value.selectedCustomerId ?: return
        scope.launch {
            _state.value = _state.value.copy(
                isRefreshing = true,
                error = null,
                message = if (keepMessage) _state.value.message else null
            )

            val result = withContext(Dispatchers.IO) {
                api.getCustomerContainerBalances(customerId = customerId, limit = 100, offset = 0)
            }

            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response?.status == "success" || response?.status == null) {
                    val balances = response?.data ?: emptyList()
                    allBalances = allBalances.filterNot { it.customerId == customerId } + balances
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        balances = balances,
                        customers = buildCustomerOptions(allBalances)
                    )
                } else {
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        balances = allBalances.filter { it.customerId == customerId },
                        error = response?.message ?: "Failed to load customer balances"
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    balances = allBalances.filter { it.customerId == customerId },
                    error = result.exceptionOrNull()?.message ?: "Failed to load customer balances"
                )
            }
        }
    }

    fun selectCustomer(customerId: Long?) {
        if (customerId == null) {
            _state.value = _state.value.copy(
                selectedCustomerId = null,
                balances = emptyList()
            )
            return
        }

        _state.value = _state.value.copy(
            selectedCustomerId = customerId
        )

        refreshSelectedCustomer()
    }

    fun selectContainerType(containerTypeId: Long?) {
        _state.value = _state.value.copy(
            selectedContainerTypeId = containerTypeId
        )
    }

    fun updateQuantityText(value: String) {
        if (value.isEmpty() || value.all(Char::isDigit)) {
            _state.value = _state.value.copy(
                quantityText = value,
                quantityValue = value.toIntOrNull() ?: 0
            )
        }
    }

    fun updateNotes(value: String) {
        _state.value = _state.value.copy(notes = value)
    }

    fun submitReceive() {
        val current = _state.value
        val customerId = current.selectedCustomerId
        if (customerId == null) {
            _state.value = current.copy(error = "Veuillez sélectionner un client.")
            return
        }
        val containerTypeId = current.selectedContainerTypeId
        if (containerTypeId == null) {
            _state.value = current.copy(error = "Veuillez sélectionner un type de contenant.")
            return
        }
        if (current.quantityValue <= 0) {
            _state.value = current.copy(error = "La quantité doit être supérieure à 0.")
            return
        }

        scope.launch {
            _state.value = current.copy(
                isSubmitting = true,
                error = null,
                message = null
            )

            val request = ContainerReceiveRequest(
                customerId = customerId,
                containerTypeId = containerTypeId,
                quantity = current.quantityValue,
                note = current.notes.takeIf { it.isNotBlank() }
            )

            val result = withContext(Dispatchers.IO) {
                api.receiveContainers(request)
            }

            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response?.status == "success" || response?.status == null) {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        message = response?.message ?: "Contenants reçus avec succès.",
                        quantityText = "1",
                        quantityValue = 1,
                        notes = ""
                    )
                    refreshSelectedCustomer(keepMessage = true)
                } else {
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        error = response?.message ?: "Échec de la réception des contenants."
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = result.exceptionOrNull()?.message ?: "Échec de la réception des contenants."
                )
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun buildCustomerOptions(balances: List<CustomerContainerBalance>): List<CustomerOption> {
        return balances
            .distinctBy { it.customerId }
            .map { CustomerOption(it.customerId, it.customerName) }
            .sortedBy { it.name.lowercase() }
    }
}
