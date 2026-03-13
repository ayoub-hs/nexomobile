package com.nexopos.erp.feature.containermanagement.ui

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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.nexopos.erp.ui.theme.appColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.containermanagement.vm.ContainerUiModel
import com.nexopos.erp.feature.containermanagement.ContainerStatus
import com.nexopos.erp.feature.containermanagement.TransactionType
import com.nexopos.erp.feature.containermanagement.vm.ContainerViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.AppTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ContainerListScreen(
    viewModel: ContainerViewModel,
    featureFlags: com.nexopos.erp.core.prefs.FeatureFlags,
    canDeposit: Boolean = false,
    canReturn: Boolean = false,
    onContainerClick: (Long) -> Unit = {},
    onScanClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStatusFilter by remember { mutableStateOf(false) }
    var showTransactionDialog by remember { mutableStateOf(false) }
    var selectedContainer by remember { mutableStateOf<ContainerUiModel?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.transactionSuccess) {
        if (state.transactionSuccess) {
            snackbarHostState.showSnackbar("Transaction processed successfully")
            viewModel.clearTransactionSuccess()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refreshContainers() }
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.containers),
                actions = {
                    IconButton(onClick = onScanClick) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan)
                        )
                    }
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
                            ContainerStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(getContainerStatusLabel(status)) },
                                    onClick = {
                                        viewModel.setStatusFilter(status)
                                        showStatusFilter = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.refreshContainers() }) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = stringResource(R.string.button_refresh)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (canDeposit || canReturn) {
                FloatingActionButton(
                    onClick = onScanClick,
                    containerColor = MaterialTheme.appColors.primary
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.scan),
                        tint = MaterialTheme.appColors.onPrimary
                    )
                }
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
                state.isLoading && state.containers.isEmpty() -> {
                    LoadingContent()
                }
                state.error != null && state.containers.isEmpty() -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = { viewModel.loadContainers() }
                    )
                }
                state.filteredContainers.isEmpty() -> {
                    EmptyContent()
                }
                else -> {
                    ContainerListContent(
                        containers = state.filteredContainers,
                        onContainerClick = onContainerClick,
                        onDepositClick = { container ->
                            selectedContainer = container
                            showTransactionDialog = true
                        },
                        onReturnClick = { container ->
                            selectedContainer = container
                            showTransactionDialog = true
                        }
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

    // Transaction Dialog
    if (showTransactionDialog && selectedContainer != null) {
        ContainerTransactionDialog(
            container = selectedContainer!!,
            canDeposit = canDeposit,
            canReturn = canReturn,
            onDismiss = {
                showTransactionDialog = false
                selectedContainer = null
            },
            onConfirm = { transactionType, quantity ->
                viewModel.processTransaction(
                    containerId = selectedContainer!!.id,
                    transactionType = transactionType,
                    quantity = quantity
                )
                showTransactionDialog = false
                selectedContainer = null
            }
        )
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
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.retry))
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
            Icons.Default.Inventory,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.appColors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_containers),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appColors.onSurfaceVariant
        )
    }
}

@Composable
private fun ContainerListContent(
    containers: List<ContainerUiModel>,
    onContainerClick: (Long) -> Unit,
    onDepositClick: (ContainerUiModel) -> Unit,
    onReturnClick: (ContainerUiModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(containers, key = { it.id }) { container ->
            ContainerCard(
                container = container,
                onClick = { onContainerClick(container.id) },
                onDepositClick = { onDepositClick(container) },
                onReturnClick = { onReturnClick(container) }
            )
        }
    }
}

@Composable
private fun ContainerCard(
    container: ContainerUiModel,
    onClick: () -> Unit,
    onDepositClick: () -> Unit,
    onReturnClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    text = container.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.appColors.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ContainerStatusBadge(status = container.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.quantity_on_hand),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Text(
                        text = container.totalQuantity.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.appColors.primary
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.quantity_reserved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Text(
                        text = container.inCirculation.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.appColors.secondary
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.available_quantity),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Text(
                        text = container.availableQuantity.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.appColors.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun ContainerStatusBadge(status: ContainerStatus) {
    val (backgroundColor, textColor) = when (status) {
        ContainerStatus.AVAILABLE -> MaterialTheme.appColors.successDim to MaterialTheme.appColors.success
        ContainerStatus.RESERVED -> MaterialTheme.appColors.warningDim to MaterialTheme.appColors.warning
        ContainerStatus.IN_USE -> MaterialTheme.appColors.surfaceOverlay to MaterialTheme.appColors.info
        ContainerStatus.RETURNED -> MaterialTheme.appColors.surfaceOverlay to MaterialTheme.appColors.info
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = getContainerStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
private fun getContainerStatusLabel(status: ContainerStatus): String {
    return when (status) {
        ContainerStatus.AVAILABLE -> stringResource(R.string.status_available)
        ContainerStatus.RESERVED -> stringResource(R.string.status_reserved)
        ContainerStatus.IN_USE -> stringResource(R.string.status_in_use)
        ContainerStatus.RETURNED -> stringResource(R.string.status_returned)
    }
}

@Composable
private fun ContainerTransactionDialog(
    container: ContainerUiModel,
    canDeposit: Boolean,
    canReturn: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TransactionType, Int) -> Unit
) {
    var quantity by remember { mutableIntStateOf(1) }
    var selectedTransactionType by remember { mutableStateOf(TransactionType.GIVE) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.container_transaction)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.container_format, container.name),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(
                    value = quantity.toString(),
                    onValueChange = { quantity = it.toIntOrNull() ?: 1 },
                    label = stringResource(R.string.quantity_label),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            if (canDeposit) {
                AppButtonPrimary(
                    onClick = { onConfirm(TransactionType.GIVE, quantity) }
                ) {
                    Text(stringResource(R.string.deposit))
                }
            }
            if (canReturn) {
                AppButtonSecondary(
                    onClick = { onConfirm(TransactionType.RECEIVE, quantity) }
                ) {
                    Text(stringResource(R.string.return_text))
                }
            }
        },
        dismissButton = {
            AppButtonSecondary(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
