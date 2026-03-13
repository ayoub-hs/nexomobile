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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.WalletTopup
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TopupListScreen(
    viewModel: SpecialCustomerViewModel,
    onCreateTopup: () -> Unit = {},
    onTopupClick: (Long) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadTopups()
    }

    LaunchedEffect(state.topupError) {
        state.topupError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearTopupError()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshingTopups,
        onRefresh = { viewModel.refreshTopups() }
    )

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.wallet_topups))
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTopup
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.create_topup)
                )
            }
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
                state.isLoadingTopups && state.topups.isEmpty() -> {
                    LoadingContent()
                }
                state.topupError != null && state.topups.isEmpty() -> {
                    ErrorContent(
                        error = state.topupError!!,
                        onRetry = { viewModel.loadTopups() }
                    )
                }
                state.topups.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    TopupsListContent(
                        topups = state.topups,
                        onTopupClick = onTopupClick
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshingTopups,
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
        title = stringResource(R.string.no_topups),
        message = stringResource(R.string.no_topups),
        modifier = Modifier.fillMaxSize(),
        icon = Icons.Default.AccountBalanceWallet
    )
}

@Composable
private fun TopupsListContent(
    topups: List<WalletTopup>,
    onTopupClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.appSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        items(topups, key = { it.id }) { topup ->
            TopupCard(
                topup = topup,
                onClick = { onTopupClick(topup.id) }
            )
        }
    }
}

@Composable
private fun TopupCard(
    topup: WalletTopup,
    onClick: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance() }
    val dateFormat = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
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
                    text = stringResource(R.string.topup_id_format, topup.id),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.appColors.onSurface
                )
                PaymentMethodBadge(paymentMethod = topup.paymentMethod)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = topup.customerName ?: stringResource(R.string.unknown_customer),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.appColors.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = currencyFormat.format(topup.amount),
                    style = MaterialTheme.appTypography.amountM,
                    color = MaterialTheme.appColors.primary
                )
                Text(
                    text = dateFormat.format(Instant.parse(topup.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
            }

            topup.reference?.let { reference ->
                if (reference.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.reference_format, reference),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentMethodBadge(paymentMethod: String?) {
    // Default to cash if payment method is null
    val method = paymentMethod ?: "cash"
    val (label, tone) = when (method.lowercase()) {
        "cash" -> stringResource(R.string.payment_cash) to AppStatusTone.Success
        "card", "credit_card" -> stringResource(R.string.payment_card) to AppStatusTone.Info
        "bank_transfer", "transfer" -> stringResource(R.string.payment_bank_transfer) to AppStatusTone.Info
        else -> method to AppStatusTone.Info
    }
    AppChipStatus(label = label, tone = tone)
}
