package com.nexopos.desktop.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.desktop.core.repo.QueuedOrderStatus
import com.nexopos.shared.models.OrderProductRequest
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Complete Orders Screen with all Android features
 */
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel,
    onBack: () -> Unit,
    onEditOrder: ((OrdersListItem) -> Unit)? = null,
    onPrintOrder: ((OrdersListItem) -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedOrder by remember { mutableStateOf<OrdersListItem?>(null) }
    var orderToDelete by remember { mutableStateOf<OrdersListItem?>(null) }
    
    // Show messages
    LaunchedEffect(state.message) {
        val message = state.message
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    
    LaunchedEffect(state.deleteError) {
        val error = state.deleteError
        if (!error.isNullOrBlank()) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearDeleteError()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        OrdersContent(
            state = state,
            paddingValues = paddingValues,
            onBack = onBack,
            onEdit = { item ->
                if (viewModel.canEditOrder(item)) {
                    onEditOrder?.invoke(item)
                }
            },
            onDelete = { item -> orderToDelete = item },
            onPrint = onPrintOrder?.let { callback -> { item -> callback(item) } },
            canEdit = { item -> viewModel.canEditOrder(item) },
            onOrderSelected = { selectedOrder = it },
            onRefresh = { viewModel.refreshFromServer() },
            onFilterChanged = { viewModel.setCustomerFilter(it) },
            onLoadMore = { viewModel.loadMoreOrders() }
        )
    }
    
    // Order details dialog
    selectedOrder?.let { order ->
        OrderDetailsDialog(
            order = order,
            onDismiss = { selectedOrder = null },
            onPrint = if (onPrintOrder != null) {
                { onPrintOrder(order); selectedOrder = null }
            } else null
        )
    }
    
    // Delete confirmation dialog
    orderToDelete?.let { order ->
        DeleteOrderConfirmDialog(
            order = order,
            isDeleting = state.isDeleting,
            onConfirm = {
                viewModel.deleteOrder(order)
                orderToDelete = null
            },
            onDismiss = { orderToDelete = null }
        )
    }
}

@Composable
private fun OrdersContent(
    state: OrdersState,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onEdit: (OrdersListItem) -> Unit,
    onDelete: (OrdersListItem) -> Unit,
    onPrint: ((OrdersListItem) -> Unit)? = null,
    canEdit: (OrdersListItem) -> Boolean,
    onOrderSelected: (OrdersListItem) -> Unit,
    onRefresh: () -> Unit,
    onFilterChanged: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Order History",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            IconButton(
                onClick = onRefresh,
                enabled = !state.isRefreshing
            ) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, "Refresh")
                }
            }
        }
        
        // Customer filter
        OutlinedTextField(
            value = state.customerFilter,
            onValueChange = onFilterChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Filter by customer name...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (state.customerFilter.isNotEmpty()) {
                    IconButton(onClick = { onFilterChanged("") }) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear filter",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true
        )
        
        // Content
        if (state.isLoading) {
            BoxLoading()
        } else if (state.filteredItems.isEmpty()) {
            if (state.customerFilter.isNotEmpty() && state.items.isNotEmpty()) {
                BoxNoResults()
            } else {
                BoxEmpty()
            }
        } else {
            // Orders list
            val listState = rememberLazyListState()
            
            // Auto-scroll to top when new orders arrive
            LaunchedEffect(state.filteredItems.size) {
                if (state.filteredItems.isNotEmpty() && listState.firstVisibleItemIndex > 3) {
                    // Only scroll if user is not already at the top
                    // This prevents annoying scrolling during normal browsing
                }
            }
            
            // Auto-load more when scrolling near the end
            LaunchedEffect(listState) {
                snapshotFlow {
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisibleItem >= totalItems - 3 && state.hasMorePages && !state.isLoadingMore
                }.collectLatest { shouldLoadMore ->
                    if (shouldLoadMore) {
                        onLoadMore()
                    }
                }
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.filteredItems, key = { it.id }) { item ->
                    OrderCard(
                        item = item,
                        onEdit = { onEdit(item) },
                        onDelete = { onDelete(item) },
                        onPrint = onPrint?.let { { onPrint(item) } },
                        canEdit = canEdit(item),
                        onClick = { onOrderSelected(item) }
                    )
                }
                
                if (state.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                
                if (state.hasMorePages && !state.isLoadingMore) {
                    item {
                        TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load More")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(
    item: OrdersListItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrint: (() -> Unit)? = null,
    canEdit: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Order ID and Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.displayId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatCurrency(item.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if ((item.request?.discountAmount ?: 0.0) > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (item.status == QueuedOrderStatus.PENDING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            
            if ((item.request?.discountAmount ?: 0.0) > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discount: ${item.request?.discountAmount?.let { formatCurrency(it) } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Subtotal: ${item.request?.subtotal?.let { formatCurrency(it) } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Date and Customer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(item.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.customerName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Status badges and action icons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sync status badge
                StatusBadge(
                    text = formatStatusLabel(item.status),
                    backgroundColor = when (item.status) {
                        QueuedOrderStatus.PENDING -> MaterialTheme.colorScheme.primaryContainer
                        QueuedOrderStatus.SYNCED -> MaterialTheme.colorScheme.tertiaryContainer
                        QueuedOrderStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                    },
                    textColor = when (item.status) {
                        QueuedOrderStatus.PENDING -> MaterialTheme.colorScheme.onPrimaryContainer
                        QueuedOrderStatus.SYNCED -> MaterialTheme.colorScheme.onTertiaryContainer
                        QueuedOrderStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                
                // Payment status badge
                item.paymentStatus?.let { paymentStatus ->
                    PaymentStatusBadge(paymentStatus = paymentStatus)
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Action icons row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit icon (only for pending/failed orders)
                    if (canEdit) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Print icon
                    if (onPrint != null) {
                        IconButton(
                            onClick = onPrint,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Print,
                                contentDescription = "Print",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Delete icon
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun PaymentStatusBadge(paymentStatus: String) {
    val (backgroundColor, textColor, label) = when (paymentStatus.lowercase()) {
        "paid" -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Paid"
        )
        "unpaid" -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Unpaid"
        )
        "partially_paid" -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Partial"
        )
        "hold" -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Hold"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            paymentStatus
        )
    }
    
    StatusBadge(
        text = label,
        backgroundColor = backgroundColor,
        textColor = textColor
    )
}

@Composable
private fun OrderDetailsDialog(
    order: OrdersListItem,
    onDismiss: () -> Unit,
    onPrint: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 600.dp),
        title = { Text("Order Details") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = formatOrderTitle(order),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Reference: ${order.clientReference}",
                    style = MaterialTheme.typography.bodyMedium
                )
                val customer = order.customerName ?: "Walk-in Customer"
                Text(
                    text = "Customer: $customer",
                    style = MaterialTheme.typography.bodyMedium
                )
                order.request?.let { request ->
                    OrderItemsList(products = request.products)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Subtotal: ${formatCurrency(request.subtotal)}"
                        )
                        Text(
                            text = "Total: ${formatCurrency(request.total)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } ?: run {
                    Text(
                        text = "Total: ${formatCurrency(order.total)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onPrint != null) {
                    TextButton(onClick = onPrint) {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Print")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun DeleteOrderConfirmDialog(
    order: OrdersListItem,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        modifier = Modifier.widthIn(max = 400.dp),
        title = { Text("Delete Order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Are you sure you want to delete order ${order.displayId}?")
                if (order.serverId != null && order.status == QueuedOrderStatus.SYNCED) {
                    Text(
                        text = "⚠️ This will also delete the order from the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Delete")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun OrderItemsList(products: List<OrderProductRequest>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Items:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        products.forEach { product ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${formatQuantity(product.quantity)} x ${formatCurrency(product.unitPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatCurrency(product.totalPriceWithTax ?: product.totalPrice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BoxLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BoxEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No orders yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BoxNoResults() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No matching orders found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Utility functions
private fun formatStatusLabel(status: QueuedOrderStatus): String {
    return when (status) {
        QueuedOrderStatus.PENDING -> "Pending"
        QueuedOrderStatus.SYNCED -> "Synced"
        QueuedOrderStatus.FAILED -> "Failed"
    }
}

private fun formatOrderTitle(item: OrdersListItem): String {
    val date = formatDateTime(item.createdAt)
    return "$date • ${formatCurrency(item.total)}"
}

private fun formatDateTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatCurrency(value: Double): String {
    return String.format(Locale.getDefault(), "%.3f DT", value)
}

private fun formatQuantity(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.getDefault(), "%.3f", value)
            .trimEnd('0')
            .trimEnd('.')
    }
}
