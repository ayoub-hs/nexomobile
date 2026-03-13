package com.nexopos.erp.feature.procurement.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.nexopos.erp.ui.theme.appColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.ReceiveProcurementItem
import com.nexopos.erp.feature.procurement.ProcurementLineItem
import com.nexopos.erp.feature.procurement.ProcurementOrder
import com.nexopos.erp.feature.procurement.ProcurementStatus
import com.nexopos.erp.feature.procurement.vm.ProcurementDetailState
import com.nexopos.erp.feature.procurement.vm.ProcurementViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.formatAppDateTime
import com.nexopos.erp.ui.formatAppQuantity
import com.nexopos.erp.ui.formatAppUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcurementDetailScreen(
    procurementId: Long,
    viewModel: ProcurementViewModel,
    onBack: () -> Unit = {}
) {
    val detailState by viewModel.detailState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showReceiveDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    val paySuccessMessage = stringResource(R.string.procurement_paid_success)

    // Load procurement detail when screen opens
    LaunchedEffect(procurementId) {
        viewModel.loadProcurementDetail(procurementId)
    }

    LaunchedEffect(detailState.error) {
        detailState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(detailState.receiveSuccess) {
        if (detailState.receiveSuccess) {
            snackbarHostState.showSnackbar("Items received successfully")
            viewModel.clearReceiveSuccess()
            showReceiveDialog = false
        }
    }

    LaunchedEffect(detailState.cancelSuccess) {
        if (detailState.cancelSuccess) {
            snackbarHostState.showSnackbar("Procurement cancelled")
            viewModel.clearCancelSuccess()
            showCancelDialog = false
        }
    }

    LaunchedEffect(detailState.paySuccess) {
        if (detailState.paySuccess) {
            snackbarHostState.showSnackbar(paySuccessMessage)
            viewModel.clearPaySuccess()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.procurement_details),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                detailState.isLoading && detailState.procurement == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                detailState.error != null && detailState.procurement == null -> {
                    ErrorContent(
                        error = detailState.error!!,
                        onRetry = onBack
                    )
                }
                detailState.procurement != null -> {
                    ProcurementDetailContent(
                        procurement = detailState.procurement!!,
                        isReceiving = detailState.isReceiving,
                        isCancelling = detailState.isCancelling,
                        onApprove = { viewModel.approveProcurement(detailState.procurement!!.id) },
                        onMarkDelivered = { viewModel.markProcurementDelivered(detailState.procurement!!.id) },
                        onReceive = { showReceiveDialog = true },
                        onPay = { viewModel.markProcurementPaid(detailState.procurement!!.id) },
                        isPaying = detailState.isPaying,
                        onCancel = { showCancelDialog = true }
                    )
                }
                else -> {
                    EmptyContent()
                }
            }
        }
    }

    // Receive Dialog
    if (showReceiveDialog && detailState.procurement != null) {
        ReceiveItemsDialog(
            procurement = detailState.procurement!!,
            isLoading = detailState.isReceiving,
            onDismiss = { showReceiveDialog = false },
            onConfirm = { items ->
                viewModel.receiveProcurementItems(detailState.procurement!!.id, items)
            }
        )
    }

    // Cancel Confirmation Dialog
    if (showCancelDialog) {
        AppDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.cancel_procurement)) },
            text = { Text(stringResource(R.string.cancel_procurement_confirmation)) },
            confirmButton = {
                AppButtonPrimary(
                    onClick = { 
                        detailState.procurement?.let { viewModel.cancelProcurement(it.id) }
                    }
                ) {
                    if (detailState.isCancelling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.appColors.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
            dismissButton = {
                AppButtonSecondary(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.keep_procurement))
                }
            }
        )
    }
}

@Composable
private fun ProcurementDetailContent(
    procurement: ProcurementOrder,
    isReceiving: Boolean = false,
    isPaying: Boolean = false,
    isCancelling: Boolean = false,
    onApprove: () -> Unit,
    onMarkDelivered: () -> Unit,
    onReceive: () -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit
) {
    val canApprove = procurement.status == ProcurementStatus.PENDING
    val canMarkDelivered = procurement.status == ProcurementStatus.APPROVED || procurement.status == ProcurementStatus.ORDERED
    val canReceive = procurement.status == ProcurementStatus.APPROVED || 
                     procurement.status == ProcurementStatus.ORDERED || 
                     procurement.status == ProcurementStatus.PARTIAL
    val canCancel = procurement.status != ProcurementStatus.COMPLETED &&
        procurement.status != ProcurementStatus.CANCELLED &&
        procurement.status != ProcurementStatus.STOCKED
    val paymentStatus = procurement.paymentStatus?.lowercase()
    val canPay = paymentStatus != "paid" && procurement.status != ProcurementStatus.CANCELLED
    val headerText = procurement.invoiceReference?.takeIf { it.isNotBlank() } ?: "#${procurement.id}"
    val totalBuy = procurement.products.sumOf { it.totalPrice }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.appColors.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailProcurementStatusBadge(status = procurement.status)
                        PaymentStatusBadge(status = procurement.paymentStatus)
                    }
                }
            }
        }

        // Provider Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.appColors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.provider),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.onSurfaceVariant
                        )
                        Text(
                            text = procurement.providerName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.appColors.onSurface
                        )
                    }
                }
            }
        }

        // Financial Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.financial_summary),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.appColors.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.total_sale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Text(
                        text = formatAppCurrency(procurement.totalAmount),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.appColors.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.total_buy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Text(
                        text = formatAppCurrency(totalBuy),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.appColors.onSurface
                    )
                }
            }
        }

        // Timeline Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.timeline),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.appColors.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TimelineItem(
                    label = stringResource(R.string.created),
                    value = formatAppDateTime(procurement.createdAt)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TimelineItem(
                    label = stringResource(R.string.invoice_date),
                    value = procurement.invoiceDate?.let { formatAppDateTime(it) } ?: "-"
                )
            }
        }

        // Line Items Card
        if (procurement.products.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.line_items),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.appColors.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    procurement.products.forEachIndexed { index, product ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        LineItemRow(
                            product = product
                        )
                    }
                }
            }
        }

        // Notes Card
        procurement.notes?.let { notes ->
            if (notes.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.notes),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.appColors.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.appColors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Action Buttons
        Spacer(modifier = Modifier.height(8.dp))
        
        if (canApprove) {
            Spacer(modifier = Modifier.height(8.dp))
            AppButtonPrimary(
                onClick = onApprove,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Approval,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.approve_procurement))
            }
        }
        
        if (canReceive) {
            Spacer(modifier = Modifier.height(8.dp))
            AppButtonPrimary(
                onClick = onReceive,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReceiving
            ) {
                if (isReceiving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.appColors.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.receive_items))
            }
        }
        
        if (canMarkDelivered) {
            Spacer(modifier = Modifier.height(8.dp))
            AppButtonPrimary(
                onClick = onMarkDelivered,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mark_delivered))
            }
        }

        if (canPay) {
            Spacer(modifier = Modifier.height(8.dp))
            AppButtonPrimary(
                onClick = onPay,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPaying
            ) {
                if (isPaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.appColors.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mark_paid))
            }
        }
        
        if (canCancel) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCancelling,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.appColors.error
                )
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.appColors.error,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cancel_procurement))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LineItemRow(
    product: ProcurementLineItem
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = product.productName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.appColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatAppCurrency(product.totalPrice),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.primary
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatAppQuantity(product.quantity)} × ${formatAppCurrency(product.unitPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.onSurfaceVariant
            )
            formatAppUnit(product.unitName)?.let { unit ->
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
            }
        }
        
        // Show received quantity progress
        if (product.receivedQuantity > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.received_progress,
                        product.receivedQuantity,
                        product.quantity
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (product.isFullyReceived) {
                        MaterialTheme.appColors.success
                    } else {
                        MaterialTheme.appColors.primary
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { (product.receivedQuantity / product.quantity).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                    color = if (product.isFullyReceived) {
                        MaterialTheme.appColors.success
                    } else {
                        MaterialTheme.appColors.primary
                    },
                    trackColor = MaterialTheme.appColors.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimelineItem(
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
            color = MaterialTheme.appColors.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.onSurface
        )
    }
}

@Composable
private fun DetailProcurementStatusBadge(status: ProcurementStatus) {
    val (backgroundColor, textColor) = when (status) {
        ProcurementStatus.DRAFT -> MaterialTheme.appColors.surfaceOverlay to MaterialTheme.appColors.onSurface
        ProcurementStatus.PENDING -> MaterialTheme.appColors.warningDim to MaterialTheme.appColors.warning
        ProcurementStatus.DELIVERED -> MaterialTheme.appColors.surfaceOverlay to MaterialTheme.appColors.info
        ProcurementStatus.STOCKED -> MaterialTheme.appColors.successDim to MaterialTheme.appColors.success
        ProcurementStatus.APPROVED -> MaterialTheme.appColors.surfaceOverlay to MaterialTheme.appColors.info
        ProcurementStatus.ORDERED -> MaterialTheme.appColors.surfaceOverlay to MaterialTheme.appColors.info
        ProcurementStatus.PARTIAL -> MaterialTheme.appColors.warningDim to MaterialTheme.appColors.warning
        ProcurementStatus.COMPLETED -> MaterialTheme.appColors.successDim to MaterialTheme.appColors.success
        ProcurementStatus.CANCELLED -> MaterialTheme.appColors.errorDim to MaterialTheme.appColors.error
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = getProcurementStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
private fun PaymentStatusBadge(status: String?) {
    if (status.isNullOrBlank()) return

    val normalized = status.lowercase()
    val (backgroundColor, textColor, label) = when (normalized) {
        "paid" -> Triple(
            MaterialTheme.appColors.successDim,
            MaterialTheme.appColors.success,
            stringResource(R.string.status_paid)
        )
        "unpaid" -> Triple(
            MaterialTheme.appColors.warningDim,
            MaterialTheme.appColors.warning,
            stringResource(R.string.status_unpaid)
        )
        "partial" -> Triple(
            MaterialTheme.appColors.warningDim,
            MaterialTheme.appColors.warning,
            stringResource(R.string.status_partially_paid)
        )
        else -> Triple(
            MaterialTheme.appColors.surfaceOverlay,
            MaterialTheme.appColors.onSurface,
            status
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
private fun getProcurementStatusLabel(status: ProcurementStatus): String {
    return when (status) {
        ProcurementStatus.DRAFT -> stringResource(R.string.status_draft)
        ProcurementStatus.PENDING -> stringResource(R.string.status_pending)
        ProcurementStatus.DELIVERED -> stringResource(R.string.status_delivered)
        ProcurementStatus.STOCKED -> stringResource(R.string.status_stocked)
        ProcurementStatus.APPROVED -> stringResource(R.string.status_approved)
        ProcurementStatus.ORDERED -> stringResource(R.string.status_ordered)
        ProcurementStatus.PARTIAL -> stringResource(R.string.status_partial)
        ProcurementStatus.COMPLETED -> stringResource(R.string.status_completed)
        ProcurementStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.go_back))
        }
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.appColors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.procurement_not_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appColors.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveItemsDialog(
    procurement: ProcurementOrder,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<ReceiveProcurementItem>) -> Unit
) {
    val receiveQuantities = remember {
        mutableStateMapOf<Long, String>().apply {
            procurement.products.forEach { product ->
                val remaining = product.quantity - product.receivedQuantity
                put(product.productId, remaining.toString())
            }
        }
    }

    AppDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.receive_items)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.enter_received_quantities),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(procurement.products) { product ->
                        val remaining = product.quantity - product.receivedQuantity
                        
                        Column {
                            Text(
                                text = product.productName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = stringResource(
                                    R.string.ordered_remaining,
                                    product.quantity,
                                    remaining
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            AppTextField(
                                value = receiveQuantities[product.productId] ?: "0",
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.toDoubleOrNull() != null) {
                                        receiveQuantities[product.productId] = newValue
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                enabled = !isLoading
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    val items = procurement.products.mapNotNull { product ->
                        val quantity = receiveQuantities[product.productId]?.toDoubleOrNull() ?: 0.0
                        if (quantity > 0) {
                            ReceiveProcurementItem(
                                productId = product.productId,
                                receivedQuantity = quantity,
                                unitId = product.unitId
                            )
                        } else null
                    }.filter { it.receivedQuantity > 0 }
                    
                    if (items.isNotEmpty()) {
                        onConfirm(items)
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.appColors.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.confirm_receive))
                }
            }
        },
        dismissButton = {
            AppButtonSecondary(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
