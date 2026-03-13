package com.nexopos.erp.feature.procurement.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
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
import com.nexopos.erp.R
import com.nexopos.erp.core.prefs.FeatureFlags
import com.nexopos.erp.feature.procurement.ProcurementOrder
import com.nexopos.erp.feature.procurement.ProcurementStatus
import com.nexopos.erp.feature.procurement.vm.ProcurementViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.components.SearchField
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.formatAppDate
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ProcurementListScreen(
    viewModel: ProcurementViewModel,
    featureFlags: FeatureFlags,
    canCreate: Boolean = false,
    onBack: () -> Unit = {},
    onProcurementClick: (Long) -> Unit = {},
    onCreateClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStatusFilter by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.createSuccess) {
        if (state.createSuccess) {
            snackbarHostState.showSnackbar("Procurement created successfully")
            viewModel.clearCreateSuccess()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refreshProcurements() }
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.procurements),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search)
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
                            ProcurementStatus.entries.forEach { status ->
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
        floatingActionButton = {
            if (canCreate && state.filteredProcurements.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onCreateClick,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(MaterialTheme.appRadii.fab),
                    containerColor = MaterialTheme.appColors.primary,
                    contentColor = MaterialTheme.appColors.onPrimary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_procurement)
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
                state.isLoading && state.procurements.isEmpty() -> LoadingContent()
                state.error != null && state.procurements.isEmpty() -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = { viewModel.loadProcurements() }
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
                    ) {
                        if (showSearch) {
                            SearchField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = stringResource(R.string.search_procurements),
                                modifier = Modifier.padding(horizontal = MaterialTheme.appSpacing.screen),
                                leadingIcon = Icons.Default.Search,
                                clearDescription = stringResource(R.string.clear_search),
                                onClear = { viewModel.setSearchQuery("") }
                            )
                        }

                        if (state.filteredProcurements.isEmpty()) {
                            EmptyContent(
                                hasFilter = state.selectedStatusFilter != null || state.searchQuery.isNotBlank(),
                                canCreate = canCreate,
                                onClearFilter = {
                                    viewModel.setStatusFilter(null)
                                    viewModel.setSearchQuery("")
                                },
                                onCreate = onCreateClick
                            )
                        } else {
                            ProcurementListContent(
                                procurements = state.filteredProcurements,
                                onProcurementClick = onProcurementClick
                            )
                        }
                    }
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
    hasFilter: Boolean,
    canCreate: Boolean,
    onClearFilter: () -> Unit,
    onCreate: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val actionLabel = when {
            hasFilter -> stringResource(R.string.clear_filter)
            canCreate -> stringResource(R.string.create_procurement)
            else -> null
        }
        val action = when {
            hasFilter -> onClearFilter
            canCreate -> onCreate
            else -> null
        }
        AppEmptyState(
            title = if (hasFilter) {
                stringResource(R.string.no_procurements_match_filter)
            } else {
                stringResource(R.string.no_procurements)
            },
            message = if (hasFilter) {
                stringResource(R.string.search_procurements)
            } else {
                stringResource(R.string.create_procurement)
            },
            actionLabel = actionLabel,
            onAction = action,
            icon = Icons.Default.Inventory
        )
    }
}

@Composable
private fun ProcurementListContent(
    procurements: List<ProcurementOrder>,
    onProcurementClick: (Long) -> Unit
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
        items(procurements, key = { it.id }) { procurement ->
            ProcurementCard(
                procurement = procurement,
                onClick = { onProcurementClick(procurement.id) }
            )
        }
    }
}

@Composable
private fun ProcurementCard(
    procurement: ProcurementOrder,
    onClick: () -> Unit
) {
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
                    text = procurement.providerName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.appColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "#${procurement.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.muted
                )
            }
            Text(
                text = formatAppCurrency(procurement.totalAmount),
                style = MaterialTheme.appTypography.amountM.copy(textAlign = TextAlign.End),
                color = MaterialTheme.appColors.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProcurementStatusBadge(status = procurement.status)
            Text(
                text = formatAppDate(procurement.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted
            )
        }

        procurement.products.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = stringResource(R.string.products_count, procurement.products.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted
            )
        }

        procurement.expectedDelivery?.let { deliveryDate ->
            Text(
                text = stringResource(R.string.expected_delivery_format, formatAppDate(deliveryDate)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted
            )
        }

        procurement.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ProcurementStatusBadge(status: ProcurementStatus) {
    val tone = when (status) {
        ProcurementStatus.DRAFT -> AppStatusTone.Info
        ProcurementStatus.PENDING -> AppStatusTone.Warning
        ProcurementStatus.DELIVERED -> AppStatusTone.Info
        ProcurementStatus.STOCKED -> AppStatusTone.Success
        ProcurementStatus.APPROVED -> AppStatusTone.Success
        ProcurementStatus.ORDERED -> AppStatusTone.Info
        ProcurementStatus.PARTIAL -> AppStatusTone.Warning
        ProcurementStatus.COMPLETED -> AppStatusTone.Success
        ProcurementStatus.CANCELLED -> AppStatusTone.Error
    }

    AppChipStatus(
        label = getStatusLabel(status),
        tone = tone
    )
}

@Composable
fun getStatusLabel(status: ProcurementStatus): String {
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
