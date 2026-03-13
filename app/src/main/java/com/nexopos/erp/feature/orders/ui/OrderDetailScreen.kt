package com.nexopos.erp.feature.orders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexopos.erp.R
import com.nexopos.erp.feature.orders.vm.OrdersListItem
import com.nexopos.erp.feature.orders.vm.OrdersViewModel
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.components.ConfirmSheet
import com.nexopos.erp.ui.components.EmptyState
import com.nexopos.erp.ui.components.SummaryCard
import com.nexopos.erp.ui.components.SummaryRowData
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

@Composable
fun OrderDetailScreen(
    orderId: Long,
    viewModel: OrdersViewModel,
    onBack: () -> Unit,
    onEdit: (OrdersListItem) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val order = state.items.firstOrNull { it.id == orderId } ?: viewModel.getOrderById(orderId)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.printerMessage, state.printerMessageError) {
        if (state.printerMessage == "Order deleted successfully" && !state.printerMessageError) {
            onBack()
        }
    }

    if (order == null) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.orders_details_title),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { paddingValues ->
            EmptyState(
                title = stringResource(R.string.orders_missing_title),
                message = stringResource(R.string.orders_missing_message),
                actionLabel = stringResource(R.string.back),
                onAction = onBack,
                modifier = Modifier.padding(paddingValues)
            )
        }
        return
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.orders_details_title),
                subtitle = order.displayId,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = MaterialTheme.appSpacing.md, vertical = MaterialTheme.appSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
        ) {
            item {
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)) {
                        AppChipStatus(
                            label = paymentStatusLabel(order.paymentStatus),
                            tone = paymentStatusTone(order.paymentStatus.toString())
                        )
                        AppChipStatus(
                            label = formatStatusLabel(order.status),
                            tone = syncStatusTone(order.status.toString())
                        )
                    }
                    Text(
                        text = formatOrderDateTime(order.createdAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.muted
                    )
                    Text(
                        text = order.customerName ?: stringResource(R.string.orders_details_customer_fallback),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.appColors.text
                    )
                }
            }
            item {
                SummaryCard(
                    rows = listOf(
                        SummaryRowData(
                            label = stringResource(R.string.orders_details_subtotal),
                            value = formatCurrency(order.request.subtotal)
                        ),
                        SummaryRowData(
                            label = stringResource(R.string.orders_details_discount),
                            value = formatCurrency(order.request.discountAmount)
                        ),
                        SummaryRowData(
                            label = stringResource(R.string.orders_details_tax),
                            value = formatCurrency(order.request.taxValue)
                        ),
                        SummaryRowData(
                            label = stringResource(R.string.orders_client_reference),
                            value = order.clientReference.orEmpty()
                        )
                    ),
                    totalLabel = stringResource(R.string.orders_details_total),
                    totalValue = formatCurrency(order.request.total)
                )
            }
            item {
                AppCard {
                    Text(
                        text = stringResource(R.string.orders_items_header),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appColors.text
                    )
                    order.request.products.forEach { product ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
                            ) {
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.appColors.text
                                )
                                Text(
                                    text = stringResource(
                                        R.string.orders_item_quantity_price,
                                        product.quantity.toString(),
                                        formatCurrency(product.unitPrice)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.appColors.muted
                                )
                            }
                            Text(
                                text = formatCurrency(product.totalPriceWithTax ?: product.totalPrice),
                                style = MaterialTheme.appTypography.amountM,
                                color = MaterialTheme.appColors.primary
                            )
                        }
                    }
                }
            }
            item {
                AppCard {
                    Text(
                        text = stringResource(R.string.orders_payments_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appColors.text
                    )
                    Text(
                        text = paymentStatusLabel(order.paymentStatus),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.appColors.text
                    )
                    order.request.payments.forEach { payment ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = payment.label ?: payment.identifier,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.appColors.muted
                            )
                            Text(
                                text = formatCurrency(payment.value),
                                style = MaterialTheme.appTypography.amountM,
                                color = MaterialTheme.appColors.text
                            )
                        }
                    }
                }
            }
            item {
                AppCard {
                    Text(
                        text = stringResource(R.string.orders_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appColors.text
                    )
                    DetailLine(
                        label = stringResource(R.string.orders_history_created),
                        value = formatOrderDateTime(order.createdAt)
                    )
                    DetailLine(
                        label = stringResource(R.string.orders_history_sync),
                        value = formatStatusLabel(order.status)
                    )
                    DetailLine(
                        label = stringResource(R.string.orders_history_local_id),
                        value = order.id.toString()
                    )
                    order.serverId?.let { serverId ->
                        DetailLine(
                            label = stringResource(R.string.orders_history_server_id),
                            value = serverId.toString()
                        )
                    }
                }
            }
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm),
                    modifier = Modifier.padding(bottom = MaterialTheme.appSpacing.xl)
                ) {
                    if (viewModel.canEditOrder(order)) {
                        AppButtonPrimary(
                            onClick = { onEdit(order) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                            Text(
                                text = stringResource(R.string.orders_edit),
                                modifier = Modifier.padding(start = MaterialTheme.appSpacing.xs)
                            )
                        }
                    }
                    AppButtonSecondary(
                        onClick = { viewModel.printOrder(order) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Outlined.Print, contentDescription = null)
                        Text(
                            text = stringResource(R.string.orders_print),
                            modifier = Modifier.padding(start = MaterialTheme.appSpacing.xs)
                        )
                    }
                    AppButtonSecondary(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.appColors.danger
                        )
                        Text(
                            text = stringResource(R.string.orders_delete),
                            color = MaterialTheme.appColors.danger,
                            modifier = Modifier.padding(start = MaterialTheme.appSpacing.xs)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmSheet(
            title = stringResource(R.string.orders_delete_confirm_title),
            message = stringResource(R.string.orders_delete_confirm_message, order.displayId),
            confirmLabel = stringResource(R.string.orders_delete_confirm),
            onConfirm = {
                viewModel.deleteOrder(order)
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

private fun paymentStatusTone(status: String): AppStatusTone = when (status.lowercase()) {
    "paid" -> AppStatusTone.Success
    "partially_paid", "partial" -> AppStatusTone.Warning
    "unpaid" -> AppStatusTone.Error
    else -> AppStatusTone.Info
}

private fun syncStatusTone(status: String): AppStatusTone = when (status.lowercase()) {
    "synced", "completed" -> AppStatusTone.Success
    "pending" -> AppStatusTone.Warning
    "failed", "cancelled", "canceled" -> AppStatusTone.Error
    else -> AppStatusTone.Info
}

@Composable
private fun DetailLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.muted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.text
        )
    }
}
