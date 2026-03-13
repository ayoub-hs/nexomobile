package com.nexopos.desktop.ui.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nexopos.desktop.core.repo.QueuedOrderStatus
import com.nexopos.shared.models.OrderProductRequest
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

private enum class OrdersUiFilter {
    All,
    Paid,
    Hold,
    PendingSync,
    Failed
}

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
    var filter by remember { mutableStateOf(OrdersUiFilter.All) }
    
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
            filter = filter,
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
            onStatusFilterChanged = { filter = it },
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
    filter: OrdersUiFilter,
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onEdit: (OrdersListItem) -> Unit,
    onDelete: (OrdersListItem) -> Unit,
    onPrint: ((OrdersListItem) -> Unit)? = null,
    canEdit: (OrdersListItem) -> Boolean,
    onOrderSelected: (OrdersListItem) -> Unit,
    onRefresh: () -> Unit,
    onFilterChanged: (String) -> Unit,
    onStatusFilterChanged: (OrdersUiFilter) -> Unit,
    onLoadMore: () -> Unit
) {
    val visibleItems = remember(state.filteredItems, filter) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Retour")
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Historique des commandes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${visibleItems.size} commande${if (visibleItems.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalIconButton(
                onClick = onRefresh,
                enabled = !state.isRefreshing
            ) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, "Rafraîchir")
                }
            }
        }
        
        // Customer filter
        OutlinedTextField(
            value = state.customerFilter,
            onValueChange = onFilterChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Filtrer par nom de client...") },
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
                            contentDescription = "Effacer le filtre",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OrdersUiFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onStatusFilterChanged(option) },
                    label = {
                        Text(
                            orderFilterLabel(option),
                            fontWeight = if (filter == option) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == option,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        
        // Content
        if (state.isLoading) {
            BoxLoading()
        } else if (visibleItems.isEmpty()) {
            if (state.customerFilter.isNotEmpty() && state.items.isNotEmpty()) {
                BoxNoResults()
            } else {
                BoxEmpty()
            }
        } else {
            // Orders list
            val listState = rememberLazyListState()
            
            // Auto-scroll to top when new orders arrive
            LaunchedEffect(visibleItems.size) {
                if (visibleItems.isNotEmpty() && listState.firstVisibleItemIndex > 3) {
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(visibleItems, key = { it.id }) { item ->
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
                            Text("Charger plus")
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.displayId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatDateTime(item.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    item.paymentStatus?.let { paymentStatus ->
                        PaymentStatusBadge(paymentStatus = paymentStatus)
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatCurrency(item.total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (item.status == QueuedOrderStatus.PENDING) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.End
                )

                if ((item.request?.discountAmount ?: 0.0) > 0) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Sous-total : ${item.request?.subtotal?.let { formatCurrency(it) } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "Remise : ${item.request?.discountAmount?.let { formatCurrency(it) } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End
                        )
                    }
                }

                Text(
                    text = item.customerName ?: "Client comptoir",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canEdit) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Modifier",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (onPrint != null) {
                        IconButton(
                            onClick = onPrint,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Print,
                                contentDescription = "Imprimer",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
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
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PaymentStatusBadge(paymentStatus: String) {
    val (backgroundColor, textColor, label) = when (paymentStatus.lowercase()) {
        "paid" -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Payée"
        )
        "unpaid" -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Impayée"
        )
        "partially_paid" -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Partielle"
        )
        "hold" -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "En attente"
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

private fun orderFilterLabel(filter: OrdersUiFilter): String = when (filter) {
    OrdersUiFilter.All -> "Toutes"
    OrdersUiFilter.Paid -> "Payées"
    OrdersUiFilter.Hold -> "En attente"
    OrdersUiFilter.PendingSync -> "En attente sync"
    OrdersUiFilter.Failed -> "Échouées"
}

@Composable
private fun OrderDetailsDialog(
    order: OrdersListItem,
    onDismiss: () -> Unit,
    onPrint: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 560.dp, max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Détails de la commande",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatOrderTitle(order),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailRow(label = "Référence", value = order.clientReference)
                        DetailRow(label = "Client", value = order.customerName ?: "Client comptoir")
                    }
                }

                order.request?.let { request ->
                    OrderItemsList(products = request.products)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DetailRow(label = "Sous-total", value = formatCurrency(request.subtotal))
                            DetailRow(
                                label = "Total",
                                value = formatCurrency(request.total),
                                emphasized = true
                            )
                        }
                    }
                } ?: run {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DetailRow(
                                label = "Total",
                                value = formatCurrency(order.total),
                                emphasized = true
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onPrint != null) {
                            OutlinedButton(onClick = onPrint) {
                                Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Imprimer")
                            }
                        }
                        Button(onClick = onDismiss) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    emphasized: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun OrderItemsList(products: List<OrderProductRequest>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Articles",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                products.forEachIndexed { index, product ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${formatQuantity(product.quantity)} x ${formatCurrency(product.unitPrice)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatCurrency(product.totalPriceWithTax ?: product.totalPrice),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
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
        title = { Text("Supprimer la commande") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Voulez-vous vraiment supprimer la commande ${order.displayId} ?")
                if (order.serverId != null && order.status == QueuedOrderStatus.SYNCED) {
                    Text(
                        text = "⚠️ Cela supprimera aussi la commande du serveur.",
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
                    Text("Supprimer")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Annuler")
            }
        }
    )
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
            text = "Aucune commande pour le moment",
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
            text = "Aucune commande correspondante trouvée",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Utility functions
private fun formatStatusLabel(status: QueuedOrderStatus): String {
    return when (status) {
        QueuedOrderStatus.PENDING -> "En attente"
        QueuedOrderStatus.SYNCED -> "Synchronisée"
        QueuedOrderStatus.FAILED -> "Échouée"
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
