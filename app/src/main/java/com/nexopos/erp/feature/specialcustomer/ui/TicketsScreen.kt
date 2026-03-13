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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import com.nexopos.erp.feature.specialcustomer.OutstandingTicket
import com.nexopos.erp.feature.specialcustomer.SpecialCustomerFeature
import com.nexopos.erp.feature.specialcustomer.TicketStatus
import com.nexopos.erp.feature.specialcustomer.vm.SpecialCustomerViewModel
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TicketsScreen(
    viewModel: SpecialCustomerViewModel,
    featureFlags: com.nexopos.erp.core.prefs.FeatureFlags,
    onTicketClick: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStatusFilter by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadTickets()
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refreshTickets() }
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.special_customer_tickets),
                actions = {
                    Box {
                        IconButton(onClick = { showStatusFilter = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter)
                            )
                        }
                        DropdownMenu(
                            expanded = showStatusFilter,
                            onDismissRequest = { showStatusFilter = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.all_statuses)) },
                                onClick = {
                                    viewModel.setStatusFilter(null)
                                    showStatusFilter = false
                                }
                            )
                            TicketStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(getStatusLabel(status)) },
                                    onClick = {
                                        viewModel.setStatusFilter(status)
                                        showStatusFilter = false
                                    }
                                )
                            }
                        }
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
                .pullRefresh(pullRefreshState)
        ) {
            when {
                state.isLoading && state.tickets.isEmpty() -> {
                    LoadingContent()
                }
                state.error != null && state.tickets.isEmpty() -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = { viewModel.loadTickets() }
                    )
                }
                state.filteredTickets.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    TicketsListContent(
                        tickets = state.filteredTickets,
                        onTicketClick = onTicketClick
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
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
private fun EmptyContent() {
    AppEmptyState(
        title = stringResource(R.string.no_tickets),
        message = stringResource(R.string.no_tickets),
        modifier = Modifier.fillMaxSize(),
        icon = Icons.Default.Receipt
    )
}

@Composable
private fun TicketsListContent(
    tickets: List<OutstandingTicket>,
    onTicketClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.appSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        items(tickets, key = { it.id }) { ticket ->
            TicketCard(
                ticket = ticket,
                onClick = { onTicketClick(ticket.id.toLong()) }
            )
        }
    }
}

@Composable
private fun TicketCard(
    ticket: OutstandingTicket,
    onClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val dateFormat = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

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

            Text(
                    text = ticket.customer?.name ?: stringResource(R.string.unknown_customer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.appColors.onSurface
                )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currencyFormat.format(ticket.dueAmount),
                    style = MaterialTheme.appTypography.amountM,
                    color = MaterialTheme.appColors.primary
                )
                Text(
                    text = dateFormat.format(Instant.parse(ticket.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
            }

            ticket.dueDate?.let { dueDate ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.due_date_format, dateFormat.format(Instant.parse(dueDate))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
            }

            ticket.description?.let { description ->
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: TicketStatus) {
    AppChipStatus(
        label = getStatusLabel(status),
        tone = when (status) {
            TicketStatus.UNPAID -> AppStatusTone.Error
            TicketStatus.PARTIALLY_PAID -> AppStatusTone.Warning
            TicketStatus.PAID -> AppStatusTone.Success
        }
    )
}

@Composable
fun getStatusLabel(status: TicketStatus): String {
    return when (status) {
        TicketStatus.UNPAID -> stringResource(R.string.status_open)
        TicketStatus.PARTIALLY_PAID -> stringResource(R.string.status_in_progress)
        TicketStatus.PAID -> stringResource(R.string.status_resolved)
    }
}
