package com.nexopos.erp.feature.orders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexopos.erp.R
import com.nexopos.erp.core.db.entities.QueuedOrderStatus
import com.nexopos.erp.feature.orders.vm.OrdersListItem
import com.nexopos.erp.feature.orders.vm.OrdersState
import com.nexopos.erp.feature.orders.vm.OrdersViewModel
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.ChipOption
import com.nexopos.erp.ui.components.ChipRow
import com.nexopos.erp.ui.components.ConfirmSheet
import com.nexopos.erp.ui.components.EmptyState
import com.nexopos.erp.ui.components.ListRow
import com.nexopos.erp.ui.components.SearchField
import com.nexopos.erp.ui.components.SkeletonListRows
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.formatAppDateTime
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing

private enum class OrdersUiFilter {
    All,
    Paid,
    Hold,
    PendingSync,
    Failed
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onOrderClick: (OrdersListItem) -> Unit,
    onEditOrder: ((OrdersListItem) -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var filter by rememberSaveable { mutableStateOf(OrdersUiFilter.All) }
    var orderToDelete by remember { mutableStateOf<OrdersListItem?>(null) }

    LaunchedEffect(state.printerMessage) {
        val message = state.printerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearPrinterMessage()
    }

    LaunchedEffect(state.deleteError) {
        val error = state.deleteError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearDeleteError()
    }

    val filteredOrders = rememberFilteredOrders(state = state, filter = filter)
    val horizontalPadding = if (widthSizeClass == WindowWidthSizeClass.Compact) 16.dp else 24.dp
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refreshFromServer() }
    )
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.hasMorePages, state.isLoadingMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 3 && state.hasMorePages && !state.isLoadingMore
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                viewModel.loadMoreOrders()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = MaterialTheme.appSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
            ) {
                SearchField(
                    value = state.customerFilter,
                    onValueChange = viewModel::setCustomerFilter,
                    placeholder = stringResource(R.string.orders_filter_customer_placeholder),
                    leadingIcon = Icons.Filled.Search,
                    clearDescription = stringResource(R.string.orders_filter_clear),
                    onClear = { viewModel.setCustomerFilter("") }
                )
                ChipRow(
                    options = listOf(
                        ChipOption(OrdersUiFilter.All, stringResource(R.string.orders_filter_all)),
                        ChipOption(OrdersUiFilter.Paid, stringResource(R.string.orders_payment_status_paid)),
                        ChipOption(OrdersUiFilter.Hold, stringResource(R.string.orders_payment_status_hold)),
                        ChipOption(OrdersUiFilter.PendingSync, stringResource(R.string.orders_filter_pending_sync)),
                        ChipOption(OrdersUiFilter.Failed, stringResource(R.string.orders_status_failed))
                    ),
                    selected = filter,
                    onSelected = { filter = it }
                )

                when {
                    state.isLoading && state.items.isEmpty() -> {
                        SkeletonListRows(count = 5)
                    }

                    filteredOrders.isEmpty() && state.customerFilter.isNotBlank() -> {
                        EmptyState(
                            title = stringResource(R.string.orders_no_results_title),
                            message = stringResource(R.string.orders_no_results),
                            actionLabel = stringResource(R.string.orders_filter_clear),
                            onAction = { viewModel.setCustomerFilter("") }
                        )
                    }

                    filteredOrders.isEmpty() -> {
                        EmptyState(
                            title = stringResource(R.string.orders_empty_title),
                            message = stringResource(R.string.orders_empty)
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm),
                            contentPadding = PaddingValues(bottom = MaterialTheme.appSpacing.xl)
                        ) {
                            items(filteredOrders, key = { it.id }) { item ->
                                OrderRow(
                                    item = item,
                                    onClick = { onOrderClick(item) },
                                    onPrint = { viewModel.printOrder(item) },
                                    onEdit = { if (viewModel.canEditOrder(item)) onEditOrder?.invoke(item) },
                                    onDelete = { orderToDelete = item },
                                    canEdit = viewModel.canEditOrder(item)
                                )
                            }
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(MaterialTheme.appSpacing.md),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator()
                                    }
                                }
                            }
                        }
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

    orderToDelete?.let { order ->
        ConfirmSheet(
            title = stringResource(R.string.orders_delete_confirm_title),
            message = stringResource(R.string.orders_delete_confirm_message, order.displayId),
            confirmLabel = stringResource(R.string.orders_delete_confirm),
            onConfirm = {
                viewModel.deleteOrder(order)
                orderToDelete = null
            },
            onDismiss = { orderToDelete = null }
        )
    }
}

@Composable
private fun OrderRow(
    item: OrdersListItem,
    onClick: () -> Unit,
    onPrint: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canEdit: Boolean
) {
    val fallbackCustomer = stringResource(R.string.orders_details_customer_fallback)
    ListRow(
        title = item.displayId,
        subtitle = buildString {
            append(item.customerName ?: fallbackCustomer)
            append(" • ")
            append(formatOrderDateTime(item.createdAt))
        },
        trailingValue = formatCurrency(item.total),
        onClick = onClick,
        chips = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
            ) {
                AppChipStatus(
                    label = paymentStatusLabel(item.paymentStatus),
                    tone = paymentStatusTone(item.paymentStatus)
                )
                AppChipStatus(
                    label = formatStatusLabel(item.status),
                    tone = syncStatusTone(item.status)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onPrint,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Print,
                        contentDescription = stringResource(R.string.orders_print)
                    )
                }
                if (canEdit) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.orders_edit)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.orders_delete),
                        tint = MaterialTheme.appColors.danger
                    )
                }
            }
        }
    )
}

private fun paymentStatusTone(paymentStatus: String?): AppStatusTone = when (paymentStatus?.lowercase()) {
    "paid" -> AppStatusTone.Success
    "partially_paid", "partial", "hold" -> AppStatusTone.Warning
    "unpaid" -> AppStatusTone.Error
    else -> AppStatusTone.Info
}

private fun syncStatusTone(status: QueuedOrderStatus): AppStatusTone = when (status) {
    QueuedOrderStatus.PENDING -> AppStatusTone.Info
    QueuedOrderStatus.SYNCED -> AppStatusTone.Success
    QueuedOrderStatus.FAILED -> AppStatusTone.Error
}

@Composable
private fun rememberFilteredOrders(
    state: OrdersState,
    filter: OrdersUiFilter
): List<OrdersListItem> {
    return remember(state.filteredItems, filter) {
        state.filteredItems.filter { item ->
            when (filter) {
                OrdersUiFilter.All -> true
                OrdersUiFilter.Paid -> item.paymentStatus.equals("paid", ignoreCase = true)
                OrdersUiFilter.Hold -> item.paymentStatus.equals("hold", ignoreCase = true)
                OrdersUiFilter.PendingSync -> item.status == QueuedOrderStatus.PENDING
                OrdersUiFilter.Failed -> item.status == QueuedOrderStatus.FAILED
            }
        }
    }
}

internal fun formatStatusLabel(status: QueuedOrderStatus): String = when (status) {
    QueuedOrderStatus.PENDING -> "Pending"
    QueuedOrderStatus.SYNCED -> "Synced"
    QueuedOrderStatus.FAILED -> "Failed"
}

internal fun paymentStatusLabel(paymentStatus: String?): String = when (paymentStatus?.lowercase()) {
    "paid" -> "Paid"
    "unpaid" -> "Unpaid"
    "partially_paid" -> "Partial"
    "hold" -> "Hold"
    else -> "Unknown"
}

internal fun formatOrderDateTime(timestamp: Long): String {
    return formatAppDateTime(timestamp)
}

internal fun formatCurrency(value: Double): String {
    return formatAppCurrency(value)
}
