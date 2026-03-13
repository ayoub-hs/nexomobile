package com.nexopos.erp.feature.specialcustomer.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.specialcustomer.OutstandingTicket
import com.nexopos.erp.feature.specialcustomer.TicketStatus
import com.nexopos.erp.feature.specialcustomer.vm.TicketPaymentAttemptResult
import com.nexopos.erp.feature.specialcustomer.vm.TicketPaymentBatchSummary
import com.nexopos.erp.feature.specialcustomer.vm.CustomerDashboardViewModel
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.EmptyState
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.rememberAppCurrencyFormatter
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography
import java.text.NumberFormat

/**
 * Customer Dashboard Screen
 * 
 * Displays customer details with wallet balance, total due,
 * and tabs for topups and outstanding tickets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDashboardScreen(
    customerId: Long,
    viewModel: CustomerDashboardViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Tab state
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.topups_tab),
        stringResource(R.string.outstanding_tickets_tab)
    )
    
    // Popup states
    var showAddTopupPopup by remember { mutableStateOf(false) }
    var showPayFromWalletPopup by remember { mutableStateOf(false) }
    var showPaymentBatchSummary by remember { mutableStateOf(false) }
    
    // Initialize view model
    LaunchedEffect(customerId) {
        viewModel.initialize(customerId)
    }
    
    // Show success/error messages
    LaunchedEffect(state.topupCreateSuccess, state.paymentSuccess) {
        if (state.topupCreateSuccess) {
            snackbarHostState.showSnackbar("Topup created successfully")
            viewModel.clearTopupCreateState()
            showAddTopupPopup = false
        }
        if (state.paymentSuccess) {
            snackbarHostState.showSnackbar("Payment successful")
            viewModel.clearPaymentState()
            showPayFromWalletPopup = false
        }
    }

    LaunchedEffect(state.paymentBatchSummary) {
        if (state.paymentBatchSummary != null) {
            showPayFromWalletPopup = false
            showPaymentBatchSummary = true
        }
    }
    
    LaunchedEffect(state.topupCreateError, state.paymentError) {
        state.topupCreateError?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
        if (state.paymentBatchSummary == null) {
            state.paymentError?.let { error ->
                snackbarHostState.showSnackbar(error)
            }
        }
    }
    
    val currencyFormat = rememberAppCurrencyFormatter()
    
    Scaffold(
        topBar = {
            AppTopBar(
                title = state.customer?.name ?: state.customer?.username ?: stringResource(R.string.customer_details),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.button_refresh)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val modalVisible = showAddTopupPopup || showPayFromWalletPopup
            if (!modalVisible && selectedTabIndex == 0) {
                FloatingActionButton(
                    onClick = { showAddTopupPopup = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_topup))
                }
            } else if (!modalVisible && selectedTabIndex == 1 && state.selectedTickets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showPayFromWalletPopup = true }
                ) {
                    Icon(Icons.Default.Payment, contentDescription = stringResource(R.string.pay_from_wallet))
                }
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.appColors.error
                    )
                    Text(
                        text = state.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.appColors.error
                    )
                    AppButtonSecondary(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BalanceCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.wallet_balance),
                        amount = state.walletBalance,
                        icon = Icons.Default.AccountBalanceWallet,
                        color = MaterialTheme.appColors.primary,
                        currencyFormat = currencyFormat,
                        isLoading = state.isLoadingBalance
                    )
                    
                    BalanceCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.total_due),
                        amount = state.totalDue,
                        icon = Icons.Default.Payment,
                        color = if (state.totalDue > 0) MaterialTheme.appColors.error else MaterialTheme.appColors.outline,
                        currencyFormat = currencyFormat,
                        isLoading = state.isLoadingTickets
                    )
                }
                
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                when (selectedTabIndex) {
                    0 -> TopupsTab(
                        topups = state.topups,
                        isLoading = state.isLoadingTopups,
                        error = state.topupsError,
                        currencyFormat = currencyFormat
                    )
                    1 -> OutstandingTicketsTab(
                        tickets = state.outstandingTickets,
                        selectedTickets = state.selectedTickets,
                        isLoading = state.isLoadingTickets,
                        error = state.ticketsError,
                        currencyFormat = currencyFormat,
                        onTicketSelect = { viewModel.toggleTicketSelection(it) },
                        onSelectAll = { viewModel.selectAllUnpaidTickets() },
                        onClearSelection = { viewModel.clearTicketSelection() }
                    )
                }
            }
        }
    }

    if (showAddTopupPopup) {
        AddTopupPopup(
            isCreating = state.isCreatingTopup,
            error = state.topupCreateError,
            onDismiss = { 
                showAddTopupPopup = false
                viewModel.clearTopupCreateState()
            },
            onConfirm = { amount, description, receivedDate ->
                viewModel.createTopup(amount, description, receivedDate)
            }
        )
    }

    if (showPayFromWalletPopup) {
        val selectedTickets = state.outstandingTickets.filter { it.id in state.selectedTickets }
        val totalAmount = selectedTickets.sumOf { it.dueAmount }
        
        PayFromWalletPopup(
            selectedTickets = selectedTickets,
            totalAmount = totalAmount,
            walletBalance = state.walletBalance,
            isProcessing = state.isProcessingPayment,
            error = state.paymentError,
            currencyFormat = currencyFormat,
            onDismiss = {
                showPayFromWalletPopup = false
                viewModel.clearPaymentState()
            },
            onConfirm = {
                viewModel.paySelectedTicketsFromWallet()
            }
        )
    }

    if (showPaymentBatchSummary && state.paymentBatchSummary != null) {
        PaymentBatchSummaryDialog(
            summary = state.paymentBatchSummary!!,
            currencyFormat = currencyFormat,
            onDismiss = {
                showPaymentBatchSummary = false
                viewModel.clearPaymentState()
            }
        )
    }
}

@Composable
private fun BalanceCard(
    modifier: Modifier = Modifier,
    title: String,
    amount: Double,
    icon: ImageVector,
    color: Color,
    currencyFormat: NumberFormat,
    isLoading: Boolean
) {
    AppCard(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.surfaceRaised,
        elevated = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = currencyFormat.format(amount),
                    style = MaterialTheme.appTypography.amountL,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun TopupsTab(
    topups: List<com.nexopos.erp.core.network.WalletTopup>,
    isLoading: Boolean,
    error: String?,
    currencyFormat: NumberFormat
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.appColors.error
                )
            }
        }
        topups.isEmpty() -> {
            EmptyState(
                title = stringResource(R.string.no_topups_found),
                message = stringResource(R.string.current_balance),
                icon = Icons.Default.AccountBalanceWallet,
                modifier = Modifier.fillMaxSize()
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm),
                contentPadding = PaddingValues(MaterialTheme.appSpacing.md)
            ) {
                items(topups, key = { it.id }) { topup ->
                    TopupItem(
                        topup = topup,
                        currencyFormat = currencyFormat
                    )
                }
            }
        }
    }
}

@Composable
private fun TopupItem(
    topup: com.nexopos.erp.core.network.WalletTopup,
    currencyFormat: NumberFormat
) {
    val createdLabel = remember(topup.createdAt) { compactDateTime(topup.createdAt) }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currencyFormat.format(topup.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.appColors.text
            )
            Text(
                text = createdLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OutstandingTicketsTab(
    tickets: List<OutstandingTicket>,
    selectedTickets: Set<Int>,
    isLoading: Boolean,
    error: String?,
    currencyFormat: NumberFormat,
    onTicketSelect: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (tickets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.appSpacing.md, vertical = MaterialTheme.appSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedTickets.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.selected_tickets, selectedTickets.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.primary
                    )
                    AppButtonSecondary(onClick = onClearSelection) {
                        Text(stringResource(R.string.clear_selection))
                    }
                } else {
                    AppButtonSecondary(onClick = onSelectAll) {
                        Text(stringResource(R.string.select_all))
                    }
                }
            }
        }
        
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.appColors.error
                    )
                }
            }
            tickets.isEmpty() -> {
                EmptyState(
                    title = stringResource(R.string.no_outstanding_tickets),
                    message = stringResource(R.string.customer_fully_paid_description),
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm),
                    contentPadding = PaddingValues(
                        start = MaterialTheme.appSpacing.md,
                        end = MaterialTheme.appSpacing.md,
                        bottom = 80.dp
                    )
                ) {
                    items(tickets, key = { it.id }) { ticket ->
                        OutstandingTicketItem(
                            ticket = ticket,
                            isSelected = selectedTickets.contains(ticket.id),
                            currencyFormat = currencyFormat,
                            onClick = { onTicketSelect(ticket.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutstandingTicketItem(
    ticket: OutstandingTicket,
    isSelected: Boolean,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    val dueLabel = currencyFormat.format(ticket.dueAmount)
    val totalLabel = currencyFormat.format(ticket.total)

    AppCard(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick,
        containerColor = if (isSelected) MaterialTheme.appColors.surfaceOverlay else MaterialTheme.appColors.surfaceRaised,
        elevated = isSelected
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (isSelected) MaterialTheme.appColors.primary else MaterialTheme.appColors.border,
                        RoundedCornerShape(MaterialTheme.appRadii.md)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.appColors.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
            ) {
                Text(
                    text = ticket.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.appColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    TicketStatusPill(ticket.paymentStatus)
                    Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.sm))
                    Column(
                        modifier = Modifier.width(120.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
                    ) {
                        Text(
                            text = stringResource(R.string.total_amount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.muted
                        )
                        Text(
                            text = totalLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.appColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.appSpacing.xxs))
                        Text(
                            text = stringResource(R.string.total_due),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.muted
                        )
                        Text(
                            text = dueLabel,
                            style = MaterialTheme.appTypography.amountM.copy(fontWeight = FontWeight.SemiBold),
                            color = if (ticket.dueAmount > 0) MaterialTheme.appColors.danger else MaterialTheme.appColors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ticketStatusLabel(status: TicketStatus): String {
    return when (status) {
        TicketStatus.PAID -> stringResource(R.string.status_paid)
        TicketStatus.PARTIALLY_PAID -> stringResource(R.string.status_partially_paid)
        TicketStatus.UNPAID -> stringResource(R.string.status_unpaid)
    }
}

@Composable
private fun ticketStatusColor(status: TicketStatus): Color {
    return when (status) {
        TicketStatus.PAID -> MaterialTheme.appColors.primary
        TicketStatus.PARTIALLY_PAID -> MaterialTheme.appColors.warning
        TicketStatus.UNPAID -> MaterialTheme.appColors.danger
    }
}

@Composable
private fun TicketStatusPill(status: TicketStatus) {
    AppChipStatus(
        label = ticketStatusLabel(status),
        tone = when (status) {
            TicketStatus.PAID -> AppStatusTone.Success
            TicketStatus.PARTIALLY_PAID -> AppStatusTone.Warning
            TicketStatus.UNPAID -> AppStatusTone.Error
        }
    )
}

@Composable
private fun PaymentBatchSummaryDialog(
    summary: TicketPaymentBatchSummary,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    when {
                        summary.failed.isEmpty() -> R.string.payment_batch_success_title
                        summary.succeeded.isEmpty() -> R.string.payment_batch_failed_title
                        else -> R.string.payment_batch_partial_title
                    }
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
            ) {
                Text(
                    text = stringResource(
                        R.string.payment_batch_counts,
                        summary.succeeded.size,
                        summary.failed.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )

                if (summary.succeeded.isNotEmpty()) {
                    PaymentBatchSection(
                        title = stringResource(R.string.payment_batch_succeeded, summary.succeeded.size),
                        items = summary.succeeded,
                        currencyFormat = currencyFormat,
                        tone = MaterialTheme.appColors.primary
                    )
                }

                if (summary.failed.isNotEmpty()) {
                    PaymentBatchSection(
                        title = stringResource(R.string.payment_batch_failed, summary.failed.size),
                        items = summary.failed,
                        currencyFormat = currencyFormat,
                        tone = MaterialTheme.appColors.error
                    )
                }

                if (summary.hasPartialSuccess) {
                    Text(
                        text = stringResource(R.string.payment_batch_refresh_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.muted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
private fun PaymentBatchSection(
    title: String,
    items: List<TicketPaymentAttemptResult>,
    currencyFormat: NumberFormat,
    tone: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tone
        )

        items.forEach { item ->
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.appColors.surfaceRaised,
                elevated = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.ticketCode,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.appColors.text
                        )
                        Text(
                            text = currencyFormat.format(item.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tone
                        )
                    }

                    item.message?.takeIf { it.isNotBlank() }?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.muted
                        )
                    }
                }
            }
        }
    }
}

private fun compactDateTime(value: String): String {
    val date = value.substringBefore('T').ifBlank { value }
    val time = value
        .substringAfter('T', "")
        .substringBefore('.')
        .substringBefore('+')
        .substringBefore('Z')
        .takeIf { it.isNotBlank() }
    return if (time != null) "$date $time" else date
}
