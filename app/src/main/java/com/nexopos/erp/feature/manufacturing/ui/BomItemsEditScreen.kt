package com.nexopos.erp.feature.manufacturing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.BomItem
import com.nexopos.erp.core.network.ManufacturingBom
import com.nexopos.erp.feature.manufacturing.vm.BomItemsEditViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

/**
 * BOM Items Edit Screen
 * 
 * Edit screen for BOM items (Bill of Materials).
 * Shows BOM name and product being produced.
 * Allows Add/Remove/Edit items functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BomItemsEditScreen(
    bomId: Long,
    viewModel: BomItemsEditViewModel,
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BomItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<BomItem?>(null) }

    LaunchedEffect(bomId) {
        viewModel.loadBom(bomId)
        viewModel.loadFormOptions()
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar("BOM items saved successfully")
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = state.bom?.name ?: stringResource(R.string.bom_details),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                actions = {
                    if (state.hasChanges) {
                        IconButton(onClick = { viewModel.saveBomItems() }) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = stringResource(R.string.save)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.bom != null) {
                FloatingActionButton(
                    onClick = { showAddItemDialog = true },
                    containerColor = MaterialTheme.appColors.primary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_item),
                        tint = MaterialTheme.appColors.onPrimary
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null && state.bom == null -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = { viewModel.loadBom(bomId) }
                    )
                }
                state.bom != null -> {
                    BomItemsEditContent(
                        bom = state.bom!!,
                        items = state.items,
                        isSaving = state.isSaving,
                        onEditItem = { editingItem = it },
                        onDeleteItem = { showDeleteConfirmDialog = it }
                    )
                }
                else -> {
                    EmptyContent()
                }
            }
        }
    }

    if (showAddItemDialog) {
        AddBomItemDialog(
            onDismiss = { showAddItemDialog = false },
            onAdd = { product, quantity, unit ->
                viewModel.addItem(product, quantity, unit)
                showAddItemDialog = false
            },
            products = state.rawProducts,
            isLoading = state.isLoadingFormOptions
        )
    }

    editingItem?.let { item ->
        EditBomItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { quantity ->
                viewModel.updateItem(item.id, quantity)
                editingItem = null
            }
        )
    }

    showDeleteConfirmDialog?.let { item ->
        AppDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text(stringResource(R.string.delete_item)) },
            text = { 
                Text(
                    stringResource(
                        R.string.delete_bom_item_confirm,
                        item.product?.name ?: item.componentProduct?.name ?: ""
                    )
                )
            },
            confirmButton = {
                AppButtonTertiary(
                    onClick = {
                        viewModel.removeItem(item.id)
                        showDeleteConfirmDialog = null
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.appColors.error
                    )
                }
            },
            dismissButton = {
                AppButtonSecondary(onClick = { showDeleteConfirmDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun BomItemsEditContent(
    bom: ManufacturingBom,
    items: List<BomItem>,
    isSaving: Boolean,
    onEditItem: (BomItem) -> Unit,
    onDeleteItem: (BomItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.appSpacing.screen)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = bom.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.appColors.onSurface
                )
                
                bom.description?.let { description ->
                    if (description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.appColors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.appSpacing.screen)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.produced_product),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bom.product?.name ?: stringResource(R.string.unknown_product),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.onSurface
                )
                bom.product?.sku?.let { sku ->
                    Text(
                        text = stringResource(R.string.sku_format, sku),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.appSpacing.screen,
                    vertical = MaterialTheme.appSpacing.s
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.bom_items),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.appColors.onSurface
            )
            Text(
                text = stringResource(R.string.items_count, items.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.onSurfaceVariant
            )
        }

        // Items List
        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (items.isEmpty()) {
            EmptyItemsContent()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.appSpacing.screen,
                    vertical = MaterialTheme.appSpacing.s
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
            ) {
                items(items, key = { it.id }) { item ->
                    BomItemEditCard(
                        item = item,
                        onEdit = { onEditItem(item) },
                        onDelete = { onDeleteItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BomItemEditCard(
    item: BomItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Inventory,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.appColors.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.componentProduct?.name ?: item.product?.name 
                               ?: stringResource(R.string.unknown_product),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.appColors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.componentProduct?.sku?.let { sku ->
                        Text(
                            text = stringResource(R.string.sku_format, sku),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.onSurfaceVariant
                        )
                    }
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.quantity}",
                        style = MaterialTheme.appTypography.amountM,
                        color = MaterialTheme.appColors.primary
                    )
                    item.unit?.name?.let { unitName ->
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = unitName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.s))
            
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.appColors.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.appColors.error
                )
            }
        }
    }
}

@Composable
private fun EmptyItemsContent() {
    AppEmptyState(
        title = stringResource(R.string.no_bom_items),
        message = stringResource(R.string.tap_fab_to_add_item),
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Default.Inventory
    )
}

@Composable
private fun AddBomItemDialog(
    onDismiss: () -> Unit,
    onAdd: (product: com.nexopos.erp.feature.manufacturing.ProductDto, quantity: Double, unitQuantity: com.nexopos.shared.models.UnitQuantity) -> Unit,
    products: List<com.nexopos.erp.feature.manufacturing.ProductDto> = emptyList(),
    isLoading: Boolean = false
) {
    var selectedProduct by remember { mutableStateOf<com.nexopos.erp.feature.manufacturing.ProductDto?>(null) }
    var selectedUnitQuantity by remember { mutableStateOf<com.nexopos.shared.models.UnitQuantity?>(null) }
    var quantityText by remember { mutableStateOf("1") }
    var productSearch by remember { mutableStateOf("") }
    var productExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }

    val quantity = quantityText.toDoubleOrNull() ?: 0.0
    val allUnitQuantities = selectedProduct?.unitQuantities ?: emptyList()
    val rawUnits = allUnitQuantities.filter { it.isRawMaterial == true }
    val availableUnitQuantities = if (rawUnits.isNotEmpty()) rawUnits else allUnitQuantities
    val filteredProducts = if (productSearch.isBlank()) {
        products
    } else {
        products.filter { product ->
            product.name.contains(productSearch, ignoreCase = true) ||
                product.sku?.contains(productSearch, ignoreCase = true) == true
        }
    }
    val isValid = selectedProduct != null && selectedUnitQuantity != null && quantity > 0

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_bom_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = selectedProduct?.name ?: productSearch,
                        onValueChange = {
                            productSearch = it
                            if (!productExpanded) productExpanded = true
                        },
                        label = stringResource(R.string.product),
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
                                text = { Text(stringResource(R.string.no_products_found)) },
                                onClick = { productExpanded = false }
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
                                        if (selectedUnitQuantity == null) {
                                            val allUnits = product.unitQuantities ?: emptyList()
                                            val preferredUnits = allUnits.filter { it.isRawMaterial == true }
                                            selectedUnitQuantity = (if (preferredUnits.isNotEmpty()) preferredUnits else allUnits)
                                                .firstOrNull()
                                        }
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
                        modifier = Modifier
                            .fillMaxWidth()
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
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = stringResource(R.string.quantity_label),
                    singleLine = true,
                    isError = quantityText.isNotEmpty() && quantity <= 0
                )
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    val product = selectedProduct ?: return@AppButtonPrimary
                    val unitQuantity = selectedUnitQuantity ?: return@AppButtonPrimary
                    if (quantity > 0) {
                        onAdd(product, quantity, unitQuantity)
                    }
                },
                enabled = isValid && !isLoading
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            AppButtonSecondary(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun EditBomItemDialog(
    item: BomItem,
    onDismiss: () -> Unit,
    onSave: (quantity: Double) -> Unit
) {
    var quantity by remember { mutableStateOf(item.quantity.toString()) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_bom_item)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)) {
                Text(
                    text = item.componentProduct?.name ?: item.product?.name 
                           ?: stringResource(R.string.unknown_product),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.onSurface
                )
                AppTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = stringResource(R.string.quantity_label)
                )
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    val qty = quantity.toDoubleOrNull() ?: 0.0
                    if (qty > 0) {
                        onSave(qty)
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            AppButtonSecondary(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.appSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.appColors.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.appColors.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        AppButtonSecondary(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun EmptyContent() {
    AppEmptyState(
        title = stringResource(R.string.bom_not_found),
        message = stringResource(R.string.bom_not_found),
        modifier = Modifier.fillMaxSize(),
        icon = Icons.Default.Inventory
    )
}
