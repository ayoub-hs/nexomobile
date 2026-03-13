package com.nexopos.erp.feature.specialcustomer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.Customer
import com.nexopos.erp.feature.specialcustomer.vm.SpecialCustomerViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppChipFilter
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import java.text.NumberFormat
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopupFormScreen(
    viewModel: SpecialCustomerViewModel,
    customers: List<Customer>,
    onTopupCreated: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Form state
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var receivedDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var expanded by remember { mutableStateOf(false) }

    // Validation state
    var amountError by remember { mutableStateOf<String?>(null) }
    var customerError by remember { mutableStateOf<String?>(null) }
    var receivedDateError by remember { mutableStateOf<String?>(null) }

    // Success dialog
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.topupCreateSuccess) {
        if (state.topupCreateSuccess) {
            showSuccessDialog = true
        }
    }

    LaunchedEffect(state.topupError) {
        state.topupError?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    val errorSelectCustomer = stringResource(R.string.error_select_customer)
    val errorInvalidAmount = stringResource(R.string.error_invalid_amount)
    val errorInvalidReceivedDate = stringResource(R.string.error_invalid_received_date)

    fun validateForm(): Boolean {
        var isValid = true

        if (selectedCustomer == null) {
            customerError = errorSelectCustomer
            isValid = false
        } else {
            customerError = null
        }

        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0) {
            amountError = errorInvalidAmount
            isValid = false
        } else {
            amountError = null
        }

        if (!isValidIsoDate(receivedDate)) {
            receivedDateError = errorInvalidReceivedDate
            isValid = false
        } else {
            receivedDateError = null
        }

        return isValid
    }

    fun submitTopup() {
        if (!validateForm()) return

        selectedCustomer?.let { customer ->
            viewModel.createTopup(
                customerId = customer.id,
                amount = amount.toDouble(),
                description = description.ifBlank { null },
                receivedDate = receivedDate
            )
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.create_topup),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(MaterialTheme.appSpacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                AppTextField(
                    value = selectedCustomer?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = stringResource(R.string.select_customer),
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    isError = customerError != null,
                    supportingText = customerError?.let { { Text(it) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    customers.forEach { customer ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(customer.name ?: customer.username ?: "Customer #${customer.id}")
                                    customer.email?.let { email ->
                                        Text(
                                            text = email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.appColors.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedCustomer = customer
                                expanded = false
                                customerError = null
                            }
                        )
                    }
                }
            }

            AppTextField(
                value = amount,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                        amount = newValue
                        amountError = null
                    }
                },
                label = stringResource(R.string.topup_amount),
                leadingIcon = {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                singleLine = true,
            )

            AppTextField(
                value = description,
                onValueChange = { 
                    description = it
                },
                label = stringResource(R.string.description_optional),
                singleLine = false,
                maxLines = 3,
            )

            AppTextField(
                value = receivedDate,
                onValueChange = {
                    receivedDate = it
                    receivedDateError = null
                },
                label = stringResource(R.string.received_date),
                placeholder = stringResource(R.string.date_format_hint),
                isError = receivedDateError != null,
                supportingText = {
                    Text(receivedDateError ?: stringResource(R.string.received_date_help))
                },
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppButtonPrimary(
                onClick = { submitTopup() },
                enabled = !state.isCreatingTopup,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isCreatingTopup) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.appColors.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.create_topup))
                }
            }
        }
    }

    if (showSuccessDialog) {
        AppDialog(
            onDismissRequest = {
                showSuccessDialog = false
                viewModel.clearTopupCreateState()
                onTopupCreated()
            },
            title = { Text(stringResource(R.string.topup_success)) },
            text = {
                Column {
                    Text(stringResource(R.string.topup_created_successfully))
                    if (selectedCustomer != null && amount.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val currencyFormat = NumberFormat.getCurrencyInstance()
                        Text(
                            stringResource(
                                R.string.topup_summary_format,
                                selectedCustomer?.name ?: "",
                                currencyFormat.format(amount.toDouble()),
                                receivedDate
                            )
                        )
                    }
                }
            },
            confirmButton = {
                AppButtonPrimary(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.clearTopupCreateState()
                        onTopupCreated()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentMethodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    AppChipFilter(selected = selected, onClick = onClick, label = { Text(label) })
}

private fun isValidIsoDate(value: String): Boolean {
    return runCatching { LocalDate.parse(value) }.isSuccess
}
