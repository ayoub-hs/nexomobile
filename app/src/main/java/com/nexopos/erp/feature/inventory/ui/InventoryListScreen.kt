package com.nexopos.erp.feature.inventory.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.inventory.InventoryFeature
import com.nexopos.erp.feature.inventory.InventoryItem
import com.nexopos.erp.feature.inventory.InventoryOperationType
import com.nexopos.erp.feature.inventory.StockStatus
import com.nexopos.erp.feature.inventory.vm.InventoryViewModel
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.ChipOption
import com.nexopos.erp.ui.components.ChipRow
import com.nexopos.erp.ui.components.EmptyState
import com.nexopos.erp.ui.components.SearchField
import com.nexopos.erp.ui.formatAppCountWithUnit
import com.nexopos.erp.ui.formatAppUnit
import com.nexopos.erp.ui.components.SkeletonListRows
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun InventoryListScreen(
    viewModel: InventoryViewModel,
    featureFlags: com.nexopos.erp.core.prefs.FeatureFlags,
    canAdjust: Boolean = false,
    onBack: () -> Unit = {},
    onItemClick: (Long) -> Unit = {},
    onAdjustClick: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val canAdjustStock = canAdjust || InventoryFeature.FeatureFlags.canAdjustStock(featureFlags)
    var searchQuery by rememberSaveable { mutableStateOf(state.searchQuery) }
    val stockOptions = listOf(
        ChipOption(StockStatus.ALL, stringResource(R.string.all_statuses)),
        ChipOption(StockStatus.NORMAL, stringResource(R.string.stock_status_normal)),
        ChipOption(StockStatus.LOW_STOCK, stringResource(R.string.stock_status_low_stock)),
        ChipOption(StockStatus.OUT_OF_STOCK, stringResource(R.string.stock_status_out_of_stock))
    )
    val adjustSuccessMessage = stringResource(R.string.inventory_adjust_success)
    var showAdjustDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.adjustSuccess) {
        if (state.adjustSuccess) {
            snackbarHostState.showSnackbar(adjustSuccessMessage)
            viewModel.clearAdjustSuccess()
        }
    }

    LaunchedEffect(state.searchQuery) {
        if (state.searchQuery != searchQuery) {
            searchQuery = state.searchQuery
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refreshInventory() }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(
                        horizontal = MaterialTheme.appSpacing.md,
                        vertical = MaterialTheme.appSpacing.sm
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
            ) {
                SearchField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.setSearchQuery(it)
                    },
                    placeholder = stringResource(R.string.inventory_search_placeholder),
                    leadingIcon = Icons.Filled.Search,
                    clearDescription = stringResource(R.string.clear_search_description),
                    onClear = {
                        searchQuery = ""
                        viewModel.setSearchQuery("")
                    }
                )
                ChipRow(
                    options = stockOptions,
                    selected = state.selectedStockFilter,
                    onSelected = viewModel::setStockFilter
                )

                when {
                    state.isLoading && state.items.isEmpty() -> {
                        SkeletonListRows(
                            count = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    state.error != null && state.items.isEmpty() -> {
                        EmptyState(
                            title = stringResource(R.string.inventory_load_failed_title),
                            message = state.error ?: "",
                            actionLabel = stringResource(R.string.retry),
                            onAction = { viewModel.loadInventory() },
                            icon = Icons.Filled.Warning,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    state.filteredItems.isEmpty() -> {
                        val hasFilters = state.searchQuery.isNotBlank() || state.selectedStockFilter != StockStatus.ALL
                        EmptyState(
                            title = stringResource(
                                if (hasFilters) R.string.inventory_no_results_title else R.string.inventory_empty_title
                            ),
                            message = stringResource(
                                if (hasFilters) R.string.inventory_no_results_message else R.string.no_inventory_items
                            ),
                            actionLabel = if (hasFilters) stringResource(R.string.inventory_clear_filters) else null,
                            onAction = if (hasFilters) {
                                {
                                    searchQuery = ""
                                    viewModel.setSearchQuery("")
                                    viewModel.setStockFilter(StockStatus.ALL)
                                }
                            } else {
                                null
                            },
                            icon = Icons.Filled.Inventory,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    else -> {
                        InventoryListContent(
                            items = state.filteredItems,
                            isLoadingMore = state.isLoadingMore,
                            hasMore = state.hasMore,
                            canAdjust = canAdjustStock,
                            onItemClick = { item ->
                                onAdjustClick(item.productId)
                                onItemClick(item.productId)
                                selectedItem = item
                                showAdjustDialog = true
                            },
                            onLoadMore = viewModel::loadMoreInventory
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showAdjustDialog && selectedItem != null) {
        StockAdjustmentDialog(
            item = selectedItem!!,
            onDismiss = {
                showAdjustDialog = false
                selectedItem = null
            },
            onConfirm = { quantity, operationType, reason ->
                viewModel.createStockAdjustment(
                    productId = selectedItem!!.productId,
                    unitQuantityId = selectedItem!!.unitQuantityId,
                    quantity = quantity,
                    operationType = operationType,
                    reason = reason
                )
                showAdjustDialog = false
                selectedItem = null
            }
        )
    }
}

@Composable
private fun InventoryListContent(
    items: List<InventoryItem>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    canAdjust: Boolean,
    onItemClick: (InventoryItem) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = MaterialTheme.appSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
    ) {
        items(items, key = { it.productId }) { item ->
            InventoryCard(
                item = item,
                canAdjust = canAdjust,
                onClick = { onItemClick(item) }
            )
        }

        if (isLoadingMore) {
            item(key = "inventory-loading-more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.appSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (hasMore) {
            item(key = "inventory-load-more") {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLoadMore
                ) {
                    Text(
                        text = stringResource(R.string.inventory_load_more),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appColors.primary
                    )
                    Text(
                        text = stringResource(R.string.inventory_load_more_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryCard(
    item: InventoryItem,
    canAdjust: Boolean,
    onClick: () -> Unit
) {
    val displayName = item.productName.ifBlank {
        item.sku.ifBlank {
            item.barcode?.takeIf { it.isNotBlank() } ?: "Product #${item.productId}"
        }
    }
    val subtitle = buildInventorySubtitle(
        item = item,
        canAdjust = canAdjust,
        adjustHint = stringResource(R.string.inventory_tap_to_adjust)
    )

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.appColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            StockStatusBadge(status = item.stockStatus)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.stock),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.appColors.muted
                )
                Text(
                    text = formatAppCountWithUnit(item.stockQuantity, item.unitName),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (item.isLowStock) MaterialTheme.appColors.danger else MaterialTheme.appColors.primary
                )
                if (item.lowStockThreshold > 0) {
                    Text(
                        text = stringResource(R.string.low_stock_threshold_format, item.lowStockThreshold),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun StockStatusBadge(status: StockStatus) {
    val tone = when (status) {
        StockStatus.ALL -> AppStatusTone.Info
        StockStatus.NORMAL -> AppStatusTone.Success
        StockStatus.LOW_STOCK -> AppStatusTone.Warning
        StockStatus.OUT_OF_STOCK -> AppStatusTone.Error
    }

    AppChipStatus(
        label = getStockStatusLabel(status),
        tone = tone,
        modifier = Modifier.padding(start = MaterialTheme.appSpacing.sm)
    )
}

@Composable
private fun getStockStatusLabel(status: StockStatus): String {
    return when (status) {
        StockStatus.ALL -> stringResource(R.string.all_statuses)
        StockStatus.NORMAL -> stringResource(R.string.stock_status_normal)
        StockStatus.LOW_STOCK -> stringResource(R.string.stock_status_low_stock)
        StockStatus.OUT_OF_STOCK -> stringResource(R.string.stock_status_out_of_stock)
    }
}

private fun buildInventorySubtitle(
    item: InventoryItem,
    canAdjust: Boolean,
    adjustHint: String
): String {
    val parts = mutableListOf<String>()
    if (item.sku.isNotBlank()) {
        parts += "SKU: ${item.sku}"
    }
    item.barcode?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (canAdjust) {
        parts += adjustHint
    }
    return parts.joinToString(" • ")
}

@Composable
private fun StockAdjustmentDialog(
    item: InventoryItem,
    onDismiss: () -> Unit,
    onConfirm: (Int, InventoryOperationType, String?) -> Unit
) {
    var quantity by rememberSaveable(item.productId) { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var operationType by remember { mutableStateOf(InventoryOperationType.SET) }
    var operationExpanded by remember { mutableStateOf(false) }
    val parsedQuantity = quantity.toIntOrNull()
    val quantityValid = parsedQuantity != null && parsedQuantity > 0

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.stock_adjustment),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.appColors.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
                Text(
                    text = item.productName.ifBlank { "Product #${item.productId}" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.appColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.current_stock_value,
                        item.stockQuantity,
                        formatAppUnit(item.unitName).orEmpty()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.primary
                )
                AppTextField(
                    value = quantity,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.all(Char::isDigit)) {
                            quantity = input
                        }
                    },
                    label = stringResource(R.string.quantity_label),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = quantity.isNotBlank() && !quantityValid
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = inventoryOperationLabel(operationType),
                        onValueChange = {},
                        readOnly = true,
                        label = stringResource(R.string.adjustment_operation),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { operationExpanded = true },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(R.string.adjustment_operation)
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = operationExpanded,
                        onDismissRequest = { operationExpanded = false }
                    ) {
                        inventoryOperationOptions().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(text = option.second) },
                                onClick = {
                                    operationType = option.first
                                    operationExpanded = false
                                }
                            )
                        }
                    }
                }
                AppTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = stringResource(R.string.adjustment_reason_optional),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    parsedQuantity?.let { onConfirm(it, operationType, reason.ifBlank { null }) }
                },
                enabled = quantityValid
            ) {
                Text(stringResource(R.string.adjustment_confirm))
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
private fun inventoryOperationOptions(): List<Pair<InventoryOperationType, String>> {
    return listOf(
        InventoryOperationType.SET to stringResource(R.string.adjustment_operation_set),
        InventoryOperationType.ADD to stringResource(R.string.adjustment_operation_add),
        InventoryOperationType.DELETE to stringResource(R.string.adjustment_operation_delete),
        InventoryOperationType.DEFECTIVE to stringResource(R.string.adjustment_operation_defective),
        InventoryOperationType.LOST to stringResource(R.string.adjustment_operation_lost)
    )
}

@Composable
private fun inventoryOperationLabel(type: InventoryOperationType): String {
    return inventoryOperationOptions().first { it.first == type }.second
}
