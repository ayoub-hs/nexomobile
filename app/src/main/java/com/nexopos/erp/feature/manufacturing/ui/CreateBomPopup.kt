package com.nexopos.erp.feature.manufacturing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.manufacturing.ProductDto
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing

/**
 * Create BOM Popup
 * 
 * AlertDialog for quick BOM creation.
 * Fields: BOM Name, Product being produced (dropdown/search), Description (optional)
 */
@Composable
fun CreateBomPopup(
    onDismiss: () -> Unit,
    onConfirm: (name: String, productId: Long, unitId: Long, quantity: Double, isActive: Boolean, description: String?) -> Unit,
    products: List<ProductDto> = emptyList(),
    isLoading: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<ProductDto?>(null) }
    var selectedUnitQuantity by remember { mutableStateOf<com.nexopos.shared.models.UnitQuantity?>(null) }
    var description by remember { mutableStateOf("") }
    var productExpanded by remember { mutableStateOf(false) }
    var productSearch by remember { mutableStateOf("") }
    var unitExpanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("1") }
    var isActive by remember { mutableStateOf(true) }

    val allUnitQuantities = selectedProduct?.unitQuantities ?: emptyList()
    val manufacturedUnits = allUnitQuantities.filter { it.isManufactured == true }
    val availableUnitQuantities = if (manufacturedUnits.isNotEmpty()) manufacturedUnits else allUnitQuantities
    val filteredProducts = if (productSearch.isBlank()) {
        products
    } else {
        products.filter { 
            it.name.contains(productSearch, ignoreCase = true) ||
            it.sku?.contains(productSearch, ignoreCase = true) == true
        }
    }

    val quantityValue = quantity.toDoubleOrNull() ?: 0.0
    val isValid = name.isNotBlank() && selectedProduct != null && selectedUnitQuantity != null && quantityValue > 0

    AppDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(stringResource(R.string.create_bom)) 
        },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MaterialTheme.appSpacing.l)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.bom_name),
                    placeholder = stringResource(R.string.bom_name_hint),
                    enabled = !isLoading,
                    singleLine = true
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = selectedProduct?.name ?: productSearch,
                        onValueChange = { 
                            productSearch = it
                            if (!productExpanded) productExpanded = true
                        },
                        label = stringResource(R.string.product_to_produce),
                        placeholder = stringResource(R.string.search_products),
                        enabled = products.isNotEmpty() && !isLoading,
                        trailingIcon = { 
                            Icon(
                                Icons.Default.ArrowDropDown, 
                                contentDescription = null 
                            ) 
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = products.isNotEmpty() && !isLoading) { 
                                productExpanded = true 
                            }
                    )
                    DropdownMenu(
                        expanded = productExpanded,
                        onDismissRequest = { productExpanded = false }
                    ) {
                        if (filteredProducts.isEmpty()) {
                            DropdownMenuItem(
                                text = { 
                                    Text(stringResource(R.string.no_products_found)) 
                                },
                                onClick = { 
                                    productExpanded = false 
                                }
                            )
                        } else {
                            filteredProducts.forEach { product ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(product.name)
                                            product.sku?.let { sku ->
                                                Text(
                                                    text = stringResource(R.string.sku_format, sku),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.appColors.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedProduct = product
                                        productSearch = product.name
                                        val allUnits = product.unitQuantities ?: emptyList()
                                        val preferredUnits = allUnits.filter { it.isManufactured == true }
                                        selectedUnitQuantity = (if (preferredUnits.isNotEmpty()) preferredUnits else allUnits)
                                            .firstOrNull()
                                        productExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = selectedUnitQuantity?.unit?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = availableUnitQuantities.isNotEmpty() && !isLoading,
                        label = stringResource(R.string.unit),
                        placeholder = stringResource(R.string.select_unit),
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(enabled = availableUnitQuantities.isNotEmpty() && !isLoading) {
                                unitExpanded = true
                            }
                    )
                    DropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        if (availableUnitQuantities.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.toast_no_units_for, selectedProduct?.name ?: "")) },
                                onClick = { unitExpanded = false }
                            )
                        } else {
                            availableUnitQuantities.forEach { unitQuantity ->
                                DropdownMenuItem(
                                    text = { Text(unitQuantity.unit?.name ?: "") },
                                    onClick = {
                                        selectedUnitQuantity = unitQuantity
                                        unitExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                AppTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = stringResource(R.string.quantity_label),
                    placeholder = stringResource(R.string.quantity_hint),
                    enabled = !isLoading,
                    singleLine = true,
                    isError = quantity.isNotEmpty() && quantityValue <= 0
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.active_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.onSurface
                    )
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }

                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.description_optional),
                    placeholder = stringResource(R.string.bom_description_hint),
                    enabled = !isLoading,
                    minLines = 2,
                    maxLines = 4
                )

                if (selectedProduct != null) {
                    Text(
                        text = stringResource(
                            R.string.bom_will_produce_format,
                            selectedProduct!!.name
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    val product = selectedProduct ?: return@AppButtonPrimary
                    val unitQuantity = selectedUnitQuantity ?: return@AppButtonPrimary
                    onConfirm(
                        name.trim(),
                        product.id.toLong(),
                        unitQuantity.unitId,
                        quantityValue,
                        isActive,
                        description.ifBlank { null }
                    )
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
 * Simplified Create BOM Popup
 * Used when product list is managed externally
 */
@Composable
fun CreateBomPopupSimple(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit,
    isLoading: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val isValid = name.isNotBlank()

    AppDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(stringResource(R.string.create_bom)) 
        },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MaterialTheme.appSpacing.l)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.bom_name),
                    placeholder = stringResource(R.string.bom_name_hint),
                    enabled = !isLoading,
                    singleLine = true
                )

                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.description_optional),
                    placeholder = stringResource(R.string.bom_description_hint),
                    enabled = !isLoading,
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    onConfirm(
                        name.trim(),
                        description.ifBlank { null }
                    )
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
