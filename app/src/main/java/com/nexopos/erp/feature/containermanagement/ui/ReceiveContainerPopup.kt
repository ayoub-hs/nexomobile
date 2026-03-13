package com.nexopos.erp.feature.containermanagement.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.containermanagement.vm.ContainerInventoryItem
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing

data class CustomerOption(
    val id: Long,
    val name: String
)

@Composable
fun ReceiveContainerPopup(
    containerTypes: List<ContainerInventoryItem>,
    customers: List<CustomerOption> = emptyList(),
    isLoading: Boolean = false,
    initialContainerTypeId: Long? = null,
    initialCustomerId: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (containerTypeId: Long, customerId: Long?, quantity: Int, notes: String?) -> Unit
) {
    var selectedContainerType by remember(initialContainerTypeId, containerTypes) {
        mutableStateOf(containerTypes.firstOrNull { it.id == initialContainerTypeId })
    }
    var selectedCustomer by remember(initialCustomerId, customers) {
        mutableStateOf(customers.firstOrNull { it.id == initialCustomerId })
    }
    var quantityText by remember { mutableStateOf("1") }
    var quantity by remember { mutableIntStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var containerTypeExpanded by remember { mutableStateOf(false) }
    var customerExpanded by remember { mutableStateOf(false) }
    var customerSearchQuery by remember(selectedCustomer) { mutableStateOf(selectedCustomer?.name.orEmpty()) }

    val filteredCustomers = if (customerSearchQuery.isBlank()) {
        customers
    } else {
        customers.filter { it.name.contains(customerSearchQuery, ignoreCase = true) }
    }
    val isValid = selectedContainerType != null && selectedCustomer != null && quantity > 0

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.receive_containers),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.appColors.text
            )
        },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = selectedContainerType?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = containerTypes.isNotEmpty() && !isLoading,
                        label = stringResource(R.string.container_type),
                        placeholder = stringResource(R.string.select_container_type),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(enabled = containerTypes.isNotEmpty() && !isLoading) {
                                containerTypeExpanded = true
                            }
                    )
                    DropdownMenu(
                        expanded = containerTypeExpanded,
                        onDismissRequest = { containerTypeExpanded = false }
                    ) {
                        if (containerTypes.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_container_types)) },
                                onClick = { containerTypeExpanded = false }
                            )
                        } else {
                            containerTypes.forEach { containerType ->
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Column {
                                            Text(containerType.name)
                                            Text(
                                                text = "${containerType.capacity}${containerType.capacityUnit} • ${stringResource(R.string.available_quantity)} ${containerType.availableQuantity}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.appColors.muted
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedContainerType = containerType
                                        containerTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = selectedCustomer?.name ?: customerSearchQuery,
                        onValueChange = { newValue ->
                            customerSearchQuery = newValue
                            customerExpanded = true
                            if (selectedCustomer != null && newValue != selectedCustomer?.name) {
                                selectedCustomer = null
                            }
                        },
                        enabled = !isLoading,
                        label = stringResource(R.string.customer),
                        placeholder = stringResource(R.string.search_customer),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { customerExpanded = true }, enabled = !isLoading) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        singleLine = true
                    )
                    DropdownMenu(
                        expanded = customerExpanded,
                        onDismissRequest = { customerExpanded = false }
                    ) {
                        if (filteredCustomers.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_customers_found)) },
                                onClick = { customerExpanded = false }
                            )
                        } else {
                            filteredCustomers.take(12).forEach { customer ->
                                DropdownMenuItem(
                                    text = { Text(customer.name) },
                                    onClick = {
                                        selectedCustomer = customer
                                        customerSearchQuery = customer.name
                                        customerExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                AppTextField(
                    value = quantityText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all(Char::isDigit)) {
                            quantityText = newValue
                            quantity = newValue.toIntOrNull() ?: 0
                        }
                    },
                    label = stringResource(R.string.quantity_label),
                    placeholder = stringResource(R.string.quantity_hint),
                    enabled = !isLoading,
                    isError = quantityText.isNotEmpty() && quantity <= 0,
                    singleLine = true
                )

                AppTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = stringResource(R.string.notes_optional),
                    placeholder = stringResource(R.string.container_notes_hint),
                    enabled = !isLoading,
                    minLines = 3,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    if (isValid) {
                        onConfirm(
                            selectedContainerType!!.id,
                            selectedCustomer?.id,
                            quantity,
                            notes.takeIf { it.isNotBlank() }
                        )
                    }
                },
                enabled = isValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(16.dp)
                            .height(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.appColors.onPrimary
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.s))
                }
                Text(stringResource(R.string.receive))
            }
        },
        dismissButton = {
            AppButtonSecondary(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
