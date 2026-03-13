package com.nexopos.erp.feature.manufacturing.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.feature.manufacturing.ProductionOrder
import com.nexopos.erp.feature.manufacturing.ProductionStatus
import com.nexopos.erp.feature.manufacturing.vm.ManufacturingViewModel
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.formatRemoteDateTime
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProductionListScreen(
    viewModel: ManufacturingViewModel,
    featureFlags: com.nexopos.erp.core.prefs.FeatureFlags,
    canCreate: Boolean = false,
    onBack: () -> Unit = {},
    onOrderClick: (Long) -> Unit = {},
    onCreateClick: () -> Unit = {},
    onBomsClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStatusFilter by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadProductionOrders()
        viewModel.loadFormOptions()
        viewModel.loadBoms()
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.createSuccess) {
        if (state.createSuccess) {
            snackbarHostState.showSnackbar("Production order created successfully")
            viewModel.clearCreateSuccess()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refreshProductionOrders() }
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.production),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
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
                            ProductionStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(getProductionStatusLabel(status)) },
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
        floatingActionButton = {
            val showFabColumn = !showCreateDialog
            if (showFabColumn) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
                ) {
                    FloatingActionButton(
                        onClick = onBomsClick,
                        shape = RoundedCornerShape(MaterialTheme.appRadii.fab),
                        containerColor = MaterialTheme.appColors.surfaceOverlay,
                        contentColor = MaterialTheme.appColors.text
                    ) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = stringResource(R.string.boms)
                        )
                    }
                    if (canCreate && state.filteredOrders.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = {
                                viewModel.loadFormOptions()
                                viewModel.loadBoms()
                                showCreateDialog = true
                            },
                            shape = RoundedCornerShape(MaterialTheme.appRadii.fab),
                            containerColor = MaterialTheme.appColors.primary,
                            contentColor = MaterialTheme.appColors.onPrimary
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.create_production)
                            )
                        }
                    }
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
                state.isLoading && state.orders.isEmpty() -> LoadingContent()
                state.error != null && state.orders.isEmpty() -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = { viewModel.loadProductionOrders() }
                    )
                }
                state.filteredOrders.isEmpty() -> {
                    EmptyContent(
                        canCreate = canCreate,
                        onCreate = {
                            viewModel.loadFormOptions()
                            viewModel.loadBoms()
                            showCreateDialog = true
                        }
                    )
                }
                else -> {
                    ProductionListContent(
                        orders = state.filteredOrders,
                        onOrderClick = onOrderClick
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

    if (showCreateDialog) {
        CreateProductionOrderPopup(
            onDismiss = { showCreateDialog = false },
            onConfirm = { bomId, quantity, unitId, _ ->
                viewModel.createProductionOrder(
                    bomId = bomId,
                    quantity = quantity,
                    unitId = unitId
                )
                showCreateDialog = false
            },
            boms = state.boms,
            isLoading = state.isCreating || state.isLoadingBoms
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AppEmptyState(
            title = error,
            message = stringResource(R.string.retry),
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry,
            icon = Icons.Default.Warning
        )
    }
}

@Composable
private fun EmptyContent(
    canCreate: Boolean,
    onCreate: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AppEmptyState(
            title = stringResource(R.string.no_production_orders),
            message = stringResource(R.string.create_production_order),
            actionLabel = if (canCreate) stringResource(R.string.create_production) else null,
            onAction = if (canCreate) onCreate else null,
            icon = Icons.Default.Factory
        )
    }
}

@Composable
private fun ProductionListContent(
    orders: List<ProductionOrder>,
    onOrderClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MaterialTheme.appSpacing.screen,
            end = MaterialTheme.appSpacing.screen,
            bottom = MaterialTheme.appSpacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        items(orders, key = { it.id }) { order ->
            ProductionOrderCard(
                order = order,
                onClick = { onOrderClick(order.id) }
            )
        }
    }
}

@Composable
private fun ProductionOrderCard(
    order: ProductionOrder,
    onClick: () -> Unit
) {
    val status = ProductionStatus.fromValue(order.status)
    val statusTone = when (status) {
        ProductionStatus.DRAFT -> AppStatusTone.Info
        ProductionStatus.PLANNED -> AppStatusTone.Warning
        ProductionStatus.IN_PROGRESS -> AppStatusTone.Info
        ProductionStatus.COMPLETED -> AppStatusTone.Success
        ProductionStatus.ON_HOLD -> AppStatusTone.Warning
        ProductionStatus.CANCELLED -> AppStatusTone.Error
    }
    val timestamp = order.startedAt ?: order.completedAt ?: order.createdAt

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
            ) {
                Text(
                    text = order.product?.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.appColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "#${order.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.muted
                )
            }
            Text(
                text = "${order.quantity} ${order.unit?.name.orEmpty()}",
                style = MaterialTheme.appTypography.amountM.copy(textAlign = TextAlign.End),
                color = MaterialTheme.appColors.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppChipStatus(
                label = getProductionStatusLabel(status),
                tone = statusTone
            )
            Text(
                text = formatRemoteDateTime(timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted
            )
        }

        order.completedAt?.let {
            Text(
                text = stringResource(R.string.status_completed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.success
            )
        }
    }
}

@Composable
private fun getProductionStatusLabel(status: ProductionStatus): String {
    return when (status) {
        ProductionStatus.DRAFT -> stringResource(R.string.status_draft)
        ProductionStatus.PLANNED -> stringResource(R.string.status_pending)
        ProductionStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
        ProductionStatus.COMPLETED -> stringResource(R.string.status_completed)
        ProductionStatus.ON_HOLD -> stringResource(R.string.status_on_hold)
        ProductionStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    }
}

// Legacy product-based creation dialog removed in favor of BOM selection.
