package com.nexopos.erp.feature.containermanagement.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.containermanagement.vm.ContainerInventoryItem
import com.nexopos.erp.feature.containermanagement.vm.ContainerInventoryViewModel
import com.nexopos.erp.feature.containermanagement.vm.CustomerContainerBalanceItem
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppMetricTile
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.ChipOption
import com.nexopos.erp.ui.components.ChipRow
import com.nexopos.erp.ui.components.SkeletonListRows
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.formatAppQuantityWithUnit
import com.nexopos.erp.ui.formatRemoteDateTime
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

private enum class ContainerInventoryTab {
    STOCK,
    CUSTOMERS
}

private enum class ContainerAdjustmentOperation(
    val labelRes: Int,
    val negative: Boolean
) {
    SET(R.string.adjustment_operation_set, false),
    ADD(R.string.adjustment_operation_add, false),
    DELETE(R.string.adjustment_operation_delete, true),
    DEFECTIVE(R.string.adjustment_operation_defective, true),
    LOST(R.string.adjustment_operation_lost, true)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ContainerInventoryScreen(
    viewModel: ContainerInventoryViewModel,
    onBack: () -> Unit = {},
    onNavigateToMovements: (typeId: Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableStateOf(ContainerInventoryTab.STOCK) }
    var showReceivePopup by remember { mutableStateOf(false) }
    var receiveSeed by remember { mutableStateOf<ReceiveSeed?>(null) }
    var selectedAdjustmentItem by remember { mutableStateOf<ContainerInventoryItem?>(null) }

    val customerOptions = remember(state.balances) { viewModel.customerOptions() }
    val filteredBalances by remember(state.balances) {
        derivedStateOf { state.balances }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.receiveSuccess) {
        if (state.receiveSuccess) {
            snackbarHostState.showSnackbar(message = "Containers received successfully")
            viewModel.clearReceiveSuccess()
        }
    }

    LaunchedEffect(state.adjustSuccess) {
        if (state.adjustSuccess) {
            snackbarHostState.showSnackbar(message = "Container stock updated successfully")
            viewModel.clearAdjustSuccess()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    Scaffold(
        floatingActionButton = {
            if (!showReceivePopup && selectedAdjustmentItem == null) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        receiveSeed = null
                        showReceivePopup = true
                    },
                    shape = RoundedCornerShape(MaterialTheme.appRadii.fab),
                    containerColor = MaterialTheme.appColors.primary,
                    contentColor = MaterialTheme.appColors.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.receive_containers)
                    )
                }
            }
        },
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
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
            ) {
                ChipRow(
                    options = listOf(
                        ChipOption(ContainerInventoryTab.STOCK, stringResource(R.string.container_tab_stock)),
                        ChipOption(ContainerInventoryTab.CUSTOMERS, stringResource(R.string.container_tab_balances))
                    ),
                    selected = activeTab,
                    onSelected = { activeTab = it },
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.appSpacing.screen,
                        vertical = MaterialTheme.appSpacing.l
                    )
                )

                when {
                    state.isLoading && state.inventory.isEmpty() && state.balances.isEmpty() -> {
                        SkeletonListRows(
                            count = 4,
                            modifier = Modifier.padding(horizontal = MaterialTheme.appSpacing.screen)
                        )
                    }

                    state.error != null && state.inventory.isEmpty() && state.balances.isEmpty() -> {
                        ErrorContent(
                            error = state.error!!,
                            onRetry = { viewModel.loadInventory() }
                        )
                    }

                    activeTab == ContainerInventoryTab.STOCK -> {
                        StockTabContent(
                            inventory = state.filteredInventory,
                            onAdjust = { selectedAdjustmentItem = it },
                            onMovements = { onNavigateToMovements(it.id) }
                        )
                    }

                    else -> {
                        CustomerBalancesTabContent(
                            balances = filteredBalances,
                            onReceive = { balance ->
                                receiveSeed = ReceiveSeed(
                                    containerTypeId = balance.containerTypeId,
                                    customerId = balance.customerId
                                )
                                showReceivePopup = true
                            }
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

    if (showReceivePopup) {
        ReceiveContainerPopup(
            containerTypes = state.inventory,
            customers = customerOptions,
            initialContainerTypeId = receiveSeed?.containerTypeId,
            initialCustomerId = receiveSeed?.customerId,
            onDismiss = {
                showReceivePopup = false
                receiveSeed = null
            },
            onConfirm = { containerTypeId, customerId, quantity, notes ->
                viewModel.receiveContainers(containerTypeId, customerId, quantity, notes)
                showReceivePopup = false
                receiveSeed = null
            }
        )
    }

    selectedAdjustmentItem?.let { item ->
        AdjustContainerStockDialog(
            item = item,
            onDismiss = { selectedAdjustmentItem = null },
            onConfirm = { adjustment, reason ->
                viewModel.adjustInventory(
                    containerTypeId = item.id,
                    adjustment = adjustment,
                    reason = reason
                )
                selectedAdjustmentItem = null
            }
        )
    }
}

@Composable
private fun StockTabContent(
    inventory: List<ContainerInventoryItem>,
    onAdjust: (ContainerInventoryItem) -> Unit,
    onMovements: (ContainerInventoryItem) -> Unit
) {
    if (inventory.isEmpty()) {
        AppEmptyState(
            title = stringResource(R.string.no_containers_found),
            message = stringResource(R.string.container_empty_inventory_hint),
            icon = Icons.Default.Inventory
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MaterialTheme.appSpacing.screen,
            end = MaterialTheme.appSpacing.screen,
            bottom = MaterialTheme.appSpacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        items(inventory, key = { it.id }) { item ->
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.appColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatAppQuantityWithUnit(item.capacity, item.capacityUnit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.muted
                        )
                    }
                    Text(
                        text = formatCurrency(item.depositAmount),
                        style = MaterialTheme.appTypography.amountM.copy(textAlign = TextAlign.End),
                        color = MaterialTheme.appColors.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
                ) {
                    StockMetric(
                        label = stringResource(R.string.total_quantity),
                        value = item.totalQuantity,
                        modifier = Modifier.weight(1f)
                    )
                    StockMetric(
                        label = stringResource(R.string.available_quantity),
                        value = item.availableQuantity,
                        modifier = Modifier.weight(1f)
                    )
                    StockMetric(
                        label = stringResource(R.string.in_circulation),
                        value = item.inCirculation,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
                ) {
                    AppButtonSecondary(
                        onClick = { onAdjust(item) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.container_action_adjust))
                    }
                    AppButtonSecondary(
                        onClick = { onMovements(item) },
                        modifier = Modifier.weight(1f),
                        tonal = true
                    ) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(modifier = Modifier.size(MaterialTheme.appSpacing.s))
                        Text(stringResource(R.string.container_action_movements))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerBalancesTabContent(
    balances: List<CustomerContainerBalanceItem>,
    onReceive: (CustomerContainerBalanceItem) -> Unit
) {
    if (balances.isEmpty()) {
        AppEmptyState(
            title = stringResource(R.string.container_balances_empty_title),
            message = stringResource(R.string.container_balances_empty_message),
            icon = Icons.Default.People
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MaterialTheme.appSpacing.screen,
            end = MaterialTheme.appSpacing.screen,
            bottom = MaterialTheme.appSpacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        items(balances, key = { "${it.customerId}-${it.containerTypeId}" }) { item ->
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                    ) {
                        Text(
                            text = item.customerName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.appColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.containerTypeName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.appColors.muted
                        )
                        Text(
                            text = stringResource(R.string.container_balance_value_label, formatCurrency(item.depositTotal)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.muted
                        )
                        item.lastTransactionAt?.takeIf { it.isNotBlank() }?.let { timestamp ->
                            Text(
                                text = compactTimestamp(timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.muted
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
                    ) {
                        Text(
                            text = item.quantityHeld.toString(),
                            style = MaterialTheme.appTypography.amountXL.copy(textAlign = TextAlign.End),
                            color = MaterialTheme.appColors.primary
                        )
                        Text(
                            text = stringResource(R.string.container_balance_count_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.muted
                        )
                    }
                }

                AppButtonPrimary(
                    onClick = { onReceive(item) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.size(MaterialTheme.appSpacing.s))
                    Text(stringResource(R.string.container_action_receive))
                }
            }
        }
    }
}

@Composable
private fun StockMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    AppMetricTile(
        label = label,
        value = value.toString(),
        modifier = modifier
    )
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AppEmptyState(
            title = error,
            message = stringResource(R.string.retry),
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry,
            icon = Icons.Default.Warning
        )
    }
}

@Composable
private fun AdjustContainerStockDialog(
    item: ContainerInventoryItem,
    onDismiss: () -> Unit,
    onConfirm: (adjustment: Int, reason: String?) -> Unit
) {
    var operation by remember { mutableStateOf(ContainerAdjustmentOperation.ADD) }
    var quantityText by remember { mutableStateOf("") }
    var quantityValue by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf("") }

    val quantityLabel = if (operation == ContainerAdjustmentOperation.SET) {
        stringResource(R.string.container_adjust_target_quantity)
    } else {
        stringResource(R.string.adjustment_quantity)
    }
    val adjustment = remember(operation, quantityValue, item.totalQuantity) {
        when (operation) {
            ContainerAdjustmentOperation.SET -> quantityValue - item.totalQuantity
            else -> if (operation.negative) -quantityValue else quantityValue
        }
    }
    val canConfirm = quantityText.isNotBlank() &&
        (operation == ContainerAdjustmentOperation.SET || quantityValue > 0) &&
        adjustment != 0

    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)) {
                Text(
                    text = stringResource(R.string.container_action_adjust),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.appColors.text
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.muted
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)) {
                Text(
                    text = stringResource(
                        R.string.container_current_stock_format,
                        item.totalQuantity,
                        item.availableQuantity,
                        item.inCirculation
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.muted
                )

                ChipRow(
                    options = ContainerAdjustmentOperation.entries.map {
                        ChipOption(it, stringResource(it.labelRes))
                    },
                    selected = operation,
                    onSelected = { operation = it }
                )

                AppTextField(
                    value = quantityText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all(Char::isDigit)) {
                            quantityText = newValue
                            quantityValue = newValue.toIntOrNull() ?: 0
                        }
                    },
                    label = quantityLabel,
                    singleLine = true,
                    placeholder = stringResource(R.string.quantity_hint)
                )

                AppTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = stringResource(R.string.adjustment_reason_optional),
                    placeholder = stringResource(R.string.container_notes_hint)
                )
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = { onConfirm(adjustment, reason.takeIf { it.isNotBlank() }) },
                enabled = canConfirm
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

private data class ReceiveSeed(
    val containerTypeId: Long?,
    val customerId: Long?
)

private fun formatCurrency(value: Double): String = formatAppCurrency(value)

private fun compactTimestamp(value: String): String = formatRemoteDateTime(value)
