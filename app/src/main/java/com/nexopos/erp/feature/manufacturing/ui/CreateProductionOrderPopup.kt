package com.nexopos.erp.feature.manufacturing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.ManufacturingBom
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing

/**
 * Create Production Order Popup
 * 
 * AlertDialog for quick production order creation.
 * Fields: Select BOM (dropdown), Quantity (number input), Status (planned)
 */
@Composable
fun CreateProductionOrderPopup(
    onDismiss: () -> Unit,
    onConfirm: (bomId: Long, quantity: Double, unitId: Long?, dueDate: String?) -> Unit,
    boms: List<ManufacturingBom> = emptyList(),
    isLoading: Boolean = false
) {
    var selectedBom by remember { mutableStateOf<ManufacturingBom?>(null) }
    var quantity by remember { mutableDoubleStateOf(1.0) }
    var quantityText by remember { mutableStateOf("1") }
    var bomExpanded by remember { mutableStateOf(false) }

    val isValid = selectedBom != null && selectedBom?.unitId != null && quantity > 0

    AppDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(stringResource(R.string.create_production_order)) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = selectedBom?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = boms.isNotEmpty() && !isLoading,
                        label = stringResource(R.string.select_bom),
                        placeholder = stringResource(R.string.select_bom_hint),
                        trailingIcon = { 
                            Icon(
                                Icons.Default.ArrowDropDown, 
                                contentDescription = null 
                            ) 
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(enabled = boms.isNotEmpty() && !isLoading) { bomExpanded = true }
                    )
                    DropdownMenu(
                        expanded = bomExpanded,
                        onDismissRequest = { bomExpanded = false }
                    ) {
                        if (isLoading) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.procurement_form_loading_options)) },
                                onClick = { bomExpanded = false }
                            )
                        } else if (boms.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_boms_available)) },
                                onClick = { bomExpanded = false }
                            )
                        } else {
                            boms.forEach { bom ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(bom.name)
                                            bom.product?.name?.let { productName ->
                                                Text(
                                                    text = productName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.appColors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedBom = bom
                                        bomExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                AppTextField(
                    value = quantityText,
                    onValueChange = { newValue ->
                        quantityText = newValue
                        quantity = newValue.toDoubleOrNull() ?: 0.0
                    },
                    label = stringResource(R.string.quantity_label),
                    placeholder = stringResource(R.string.quantity_hint),
                    enabled = !isLoading,
                    isError = quantityText.isNotEmpty() && quantity <= 0,
                    singleLine = true
                )

                AppTextField(
                    value = stringResource(R.string.production_status_pending),
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = stringResource(R.string.production_status_label),
                    singleLine = true
                )

                selectedBom?.let { bom ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.appColors.surfaceVariant,
                        elevated = false
                    ) {
                        Text(
                            text = stringResource(R.string.selected_bom_info),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.bom_produces_format,
                                bom.product?.name ?: stringResource(R.string.unknown_product),
                                bom.quantity
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.onSurface
                        )
                        bom.description?.let { desc ->
                            if (desc.isNotBlank()) {
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.appColors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    selectedBom?.let { bom ->
                        onConfirm(
                            bom.id,
                            quantity,
                            bom.unitId,
                            null
                        )
                    }
                },
                enabled = isValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.appColors.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.create))
                }
            }
        },
        dismissButton = {
            AppButtonTertiary(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Simplified version of Create Production Order Popup
 * Used when BOM list is managed externally
 */
@Composable
fun CreateProductionOrderPopupSimple(
    onDismiss: () -> Unit,
    onConfirm: (bomId: Long, quantity: Double) -> Unit,
    boms: List<ManufacturingBom> = emptyList(),
    isLoading: Boolean = false
) {
    var selectedBom by remember { mutableStateOf<ManufacturingBom?>(null) }
    var quantity by remember { mutableDoubleStateOf(1.0) }
    var quantityText by remember { mutableStateOf("1") }
    var bomExpanded by remember { mutableStateOf(false) }

    val isValid = selectedBom != null && quantity > 0

    AppDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(stringResource(R.string.create_production_order)) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = selectedBom?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = boms.isNotEmpty() && !isLoading,
                        label = stringResource(R.string.select_bom),
                        trailingIcon = { 
                            Icon(
                                Icons.Default.ArrowDropDown, 
                                contentDescription = null 
                            ) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = boms.isNotEmpty() && !isLoading) { bomExpanded = true }
                    )
                    DropdownMenu(
                        expanded = bomExpanded,
                        onDismissRequest = { bomExpanded = false }
                    ) {
                        boms.forEach { bom ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(bom.name)
                                        bom.product?.name?.let { productName ->
                                            Text(
                                                text = productName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.appColors.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedBom = bom
                                    bomExpanded = false
                                }
                            )
                        }
                    }
                }

                AppTextField(
                    value = quantityText,
                    onValueChange = { newValue ->
                        quantityText = newValue
                        quantity = newValue.toDoubleOrNull() ?: 0.0
                    },
                    label = stringResource(R.string.quantity_label),
                    enabled = !isLoading,
                    isError = quantityText.isNotEmpty() && quantity <= 0,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    selectedBom?.let { bom ->
                        onConfirm(bom.id, quantity)
                    }
                },
                enabled = isValid && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.appColors.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.create))
                }
            }
        },
        dismissButton = {
            AppButtonTertiary(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
