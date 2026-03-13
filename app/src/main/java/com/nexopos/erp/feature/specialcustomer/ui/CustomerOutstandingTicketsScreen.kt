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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.CustomerBalance
import com.nexopos.erp.feature.specialcustomer.OutstandingTicket
import com.nexopos.erp.feature.specialcustomer.TicketStatus
import com.nexopos.erp.feature.specialcustomer.vm.SpecialCustomerViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Screen showing outstanding tickets for a specific customer with wallet balance
 * and pay from wallet functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerOutstandingTicketsScreen(
    customerId: Long,
    customerName: String,
    viewModel: SpecialCustomerViewModel,
    onBack: () -> Unit = {},
    onTicketClick: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPayFromWalletDialog by remember { mutableStateOf<OutstandingTicket?>(null) }
    val paymentSuccessfulText = stringResource(R.string.payment_successful)

    // Load customer tickets and balance on init
    LaunchedEffect(customerId) {
        viewModel.loadCustomerOutstandingTickets(customerId)
        viewModel.loadCustomerBalance(customerId)
    }

    // Handle payment success
    LaunchedEffect(state.paymentSuccess) {
        if (state.paymentSuccess) {
            snackbarHostState.showSnackbar(
                message = paymentSuccessfulText
            )
            viewModel.clearPaymentState()
            // Refresh data after successful payment
            viewModel.loadCustomerOutstandingTickets(customerId)
            viewModel.loadCustomerBalance(customerId)
        }
    }

    // Handle payment error
    LaunchedEffect(state.paymentError) {
        state.paymentError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearPaymentState()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = customerName,
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
                state.isLoading && state.tickets.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null && state.tickets.isEmpty() -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = { viewModel.loadCustomerOutstandingTickets(customerId) }
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Wallet Balance Card
                        state.customerBalance?.let { balance ->
                            WalletBalanceCard(
                                balance = balance,
                                isLoading = state.isLoadingBalance
                            )
                        }

                        // Tickets List
                        if (state.filteredTickets.isEmpty()) {
                            NoTicketsContent()
                        } else {
                            TicketsListWithPayFromWallet(
                                tickets = state.filteredTickets,
                                customerBalance = state.customerBalance?.balance ?: 0.0,
                                onTicketClick = onTicketClick,
                                onPayFromWalletClick = { ticket ->
                                    showPayFromWalletDialog = ticket
                                },
                                isProcessingPayment = state.isProcessingPayment
                            )
                        }
                    }
                }
            }
        }
    }

    // Pay from Wallet Dialog
    showPayFromWalletDialog?.let { ticket ->
        PayFromWalletDialog(
            ticket = ticket,
            customerBalance = state.customerBalance?.balance ?: 0.0,
            isProcessing = state.isProcessingPayment,
            onDismiss = { showPayFromWalletDialog = null },
            onConfirm = { amount ->
                viewModel.payTicketFromWallet(
                    customerId = customerId,
                    ticketId = ticket.id.toLong(),
                    amount = amount
                )
                showPayFromWalletDialog = null
            }
        )
    }
}

@Composable
private fun WalletBalanceCard(
    balance: CustomerBalance,
    isLoading: Boolean
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.appSpacing.screen),
        containerColor = MaterialTheme.appColors.surfaceRaised
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.appColors.primary
            )
            Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.l))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wallet_balance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.muted
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = currencyFormat.format(balance.balance),
                        style = MaterialTheme.appTypography.amountXL,
                        color = MaterialTheme.appColors.text
                    )
                }
            }
        }
    }
}

@Composable
private fun TicketsListWithPayFromWallet(
    tickets: List<OutstandingTicket>,
    customerBalance: Double,
    onTicketClick: (Long) -> Unit,
    onPayFromWalletClick: (OutstandingTicket) -> Unit,
    isProcessingPayment: Boolean
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val dateFormat = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MaterialTheme.appSpacing.screen,
            vertical = MaterialTheme.appSpacing.s
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        items(tickets, key = { it.id }) { ticket ->
            TicketWithPayFromWalletCard(
                ticket = ticket,
                customerBalance = customerBalance,
                currencyFormat = currencyFormat,
                dateFormat = dateFormat,
                onClick = { onTicketClick(ticket.id.toLong()) },
                onPayFromWalletClick = { onPayFromWalletClick(ticket) },
                isProcessingPayment = isProcessingPayment
            )
        }
    }
}

@Composable
private fun TicketWithPayFromWalletCard(
    ticket: OutstandingTicket,
    customerBalance: Double,
    currencyFormat: NumberFormat,
    dateFormat: DateTimeFormatter,
    onClick: () -> Unit,
    onPayFromWalletClick: () -> Unit,
    isProcessingPayment: Boolean
) {
    val canPayFromWallet = ticket.dueAmount <= customerBalance && 
        ticket.paymentStatus != TicketStatus.PAID

    AppCard(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ticket.code,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.appColors.onSurface
                )
                StatusBadge(status = ticket.paymentStatus)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.due_amount_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Text(
                        text = currencyFormat.format(ticket.dueAmount),
                        style = MaterialTheme.appTypography.amountM,
                        color = MaterialTheme.appColors.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.created_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(Instant.parse(ticket.createdAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.onSurface
                    )
                }
            }

            // Pay from Wallet Button
            if (ticket.paymentStatus != TicketStatus.PAID) {
                Spacer(modifier = Modifier.height(12.dp))
                
                if (canPayFromWallet) {
                    AppButtonPrimary(
                        onClick = onPayFromWalletClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessingPayment
                    ) {
                        if (isProcessingPayment) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.appColors.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.pay_from_wallet))
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.appColors.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.appColors.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.insufficient_wallet_balance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PayFromWalletDialog(
    ticket: OutstandingTicket,
    customerBalance: Double,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val amountToPay = ticket.dueAmount
    val remainingBalance = customerBalance - amountToPay

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pay_from_wallet)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.confirm_pay_from_wallet_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.appColors.surfaceVariant,
                    elevated = false
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PaymentSummaryRow(
                            label = stringResource(R.string.ticket),
                            value = ticket.code
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PaymentSummaryRow(
                            label = stringResource(R.string.amount_to_pay),
                            value = currencyFormat.format(amountToPay),
                            isBold = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PaymentSummaryRow(
                            label = stringResource(R.string.current_balance),
                            value = currencyFormat.format(customerBalance)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PaymentSummaryRow(
                            label = stringResource(R.string.remaining_balance),
                            value = currencyFormat.format(remainingBalance),
                            valueColor = if (remainingBalance >= 0) 
                                MaterialTheme.appColors.success 
                            else 
                                MaterialTheme.appColors.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = { onConfirm(amountToPay) },
                enabled = !isProcessing && remainingBalance >= 0
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.confirm_payment))
            }
        },
        dismissButton = {
            AppButtonSecondary(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun PaymentSummaryRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = MaterialTheme.appColors.onSurface
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
            style = if (isBold) {
                MaterialTheme.appTypography.amountM.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = valueColor
        )
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
private fun NoTicketsContent() {
    AppEmptyState(
        title = stringResource(R.string.no_outstanding_tickets),
        message = stringResource(R.string.customer_fully_paid_description),
        modifier = Modifier.fillMaxSize(),
        icon = Icons.Default.Receipt
    )
}
