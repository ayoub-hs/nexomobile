package com.nexopos.erp.feature.specialcustomer.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.specialcustomer.OutstandingTicket
import com.nexopos.erp.feature.specialcustomer.TicketStatus
import com.nexopos.erp.feature.specialcustomer.vm.SpecialCustomerViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    ticketId: Long,
    viewModel: SpecialCustomerViewModel,
    onPaymentComplete: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPaymentDialog by remember { mutableStateOf(false) }
    
    // Find the ticket from state
    val ticket = state.tickets.find { it.id.toLong() == ticketId }
    val isLoading = state.isLoading
    val error = state.error

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = ticket?.code ?: stringResource(R.string.ticket_details))
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && ticket == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null && ticket == null -> {
                    ErrorContent(
                        error = error,
                        onRetry = onBack
                    )
                }
                ticket != null -> {
                    TicketDetailContent(
                        ticket = ticket,
                        onPaymentClick = { showPaymentDialog = true }
                    )
                }
                else -> {
                    EmptyContent()
                }
            }
        }
    }

    if (showPaymentDialog && ticket != null) {
        PaymentDialog(
            ticket = ticket,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount ->
                viewModel.recordPayment(ticket.id.toLong(), amount)
                showPaymentDialog = false
            }
        )
    }
}

@Composable
private fun TicketDetailContent(
    ticket: OutstandingTicket,
    onPaymentClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val dateFormat = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.appSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
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
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.appColors.onSurface
                    )
                    StatusBadge(status = ticket.paymentStatus)
                }
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth()
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
                    Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.m))
                    Column {
                        Text(
                            text = stringResource(R.string.customer),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.onSurfaceVariant
                        )
                        Text(
                            text = ticket.customer?.name ?: stringResource(R.string.unknown_customer),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.appColors.onSurface
                        )
                        ticket.customer?.phone?.let { phone ->
                            Text(
                                text = phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.payment_summary),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.appColors.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PaymentRow(
                    label = stringResource(R.string.total_amount),
                    value = currencyFormat.format(ticket.total),
                    valueColor = MaterialTheme.appColors.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                PaymentRow(
                    label = stringResource(R.string.paid_amount),
                    value = currencyFormat.format(ticket.paidAmount),
                    valueColor = MaterialTheme.appColors.success
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                PaymentRow(
                    label = stringResource(R.string.due_amount),
                    value = currencyFormat.format(ticket.dueAmount),
                    valueColor = MaterialTheme.appColors.error,
                    isBold = true
                )
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.timeline),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.appColors.onSurface
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                TimelineItem(
                    label = stringResource(R.string.created),
                    value = try {
                        dateFormat.format(Instant.parse(ticket.createdAt))
                    } catch (e: Exception) {
                        ticket.createdAt
                    }
                )
                
                ticket.dueDate?.let { dueDate ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TimelineItem(
                        label = stringResource(R.string.due_date),
                        value = try {
                            dateFormat.format(Instant.parse(dueDate))
                        } catch (e: Exception) {
                            dueDate
                        }
                    )
                }
            }
        }

        ticket.description?.let { description ->
            if (description.isNotBlank()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.description),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.appColors.onSurface
                        )
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

        if (ticket.paymentStatus != TicketStatus.PAID) {
            Spacer(modifier = Modifier.height(8.dp))
            AppButtonPrimary(
                onClick = onPaymentClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Payment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.record_payment))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PaymentRow(
    label: String,
    value: String,
    valueColor: Color,
    isBold: Boolean = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDialog(
    ticket: OutstandingTicket,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var paymentAmount by remember { mutableDoubleStateOf(ticket.dueAmount) }
    var paymentAmountText by remember { mutableStateOf(ticket.dueAmount.toString()) }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_payment)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        R.string.maximum_payment_amount,
                        currencyFormat.format(ticket.dueAmount)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    value = paymentAmountText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                            paymentAmountText = newValue
                            paymentAmount = newValue.toDoubleOrNull() ?: 0.0
                        }
                    },
                    label = stringResource(R.string.payment_amount),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            AppButtonPrimary(
                onClick = {
                    if (paymentAmount > 0 && paymentAmount <= ticket.dueAmount) {
                        onConfirm(paymentAmount)
                    }
                },
                enabled = paymentAmount > 0 && paymentAmount <= ticket.dueAmount
            ) {
                Text(stringResource(R.string.confirm))
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
            Text(stringResource(R.string.go_back))
        }
    }
}

@Composable
private fun EmptyContent() {
    AppEmptyState(
        title = stringResource(R.string.ticket_not_found),
        message = stringResource(R.string.ticket_not_found),
        modifier = Modifier.fillMaxSize(),
        icon = Icons.Default.Receipt
    )
}
