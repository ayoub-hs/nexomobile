package com.nexopos.erp.feature.containermanagement.ui

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.nexopos.erp.R
import com.nexopos.erp.feature.containermanagement.vm.ContainerMovementsViewModel
import com.nexopos.erp.feature.containermanagement.vm.MovementUiModel
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.ChipOption
import com.nexopos.erp.ui.components.ChipRow
import com.nexopos.erp.ui.components.SearchField
import com.nexopos.erp.ui.components.SkeletonListRows
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.formatRemoteDateTime
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

private enum class ContainerMovementFilter(
    val labelRes: Int
) {
    ALL(R.string.container_movement_filter_all),
    RECEIVED(R.string.container_movement_filter_received),
    GIVEN(R.string.container_movement_filter_given),
    ADJUSTED(R.string.container_movement_filter_adjusted)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ContainerMovementsScreen(
    typeId: Long,
    viewModel: ContainerMovementsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(ContainerMovementFilter.ALL) }

    LaunchedEffect(typeId) {
        viewModel.loadMovements(typeId)
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val filteredMovements by remember(state.movements, searchQuery, selectedFilter) {
        derivedStateOf {
            state.movements.filter { movement ->
                val matchesSearch = searchQuery.isBlank() ||
                    movement.customerName?.contains(searchQuery, ignoreCase = true) == true ||
                    movement.containerTypeName.contains(searchQuery, ignoreCase = true) ||
                    movement.notes?.contains(searchQuery, ignoreCase = true) == true

                val matchesType = when (selectedFilter) {
                    ContainerMovementFilter.ALL -> true
                    ContainerMovementFilter.RECEIVED -> movement.isReceived
                    ContainerMovementFilter.GIVEN -> movement.isDispatched
                    ContainerMovementFilter.ADJUSTED -> !movement.isReceived && !movement.isDispatched
                }

                matchesSearch && matchesType
            }
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, filteredMovements.size, state.hasMore, state.isLoading) {
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (lastVisibleIndex >= filteredMovements.size - 3 && state.hasMore && !state.isLoading) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                state.isLoading && state.movements.isEmpty() -> {
                    SkeletonListRows(
                        count = 5,
                        modifier = Modifier.padding(MaterialTheme.appSpacing.screen)
                    )
                }

                state.error != null && state.movements.isEmpty() -> {
                    MovementError(
                        error = state.error!!,
                        onRetry = { viewModel.loadMovements(typeId) }
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
                    ) {
                        if (state.containerTypeName.isNotBlank()) {
                            AppCard(
                                modifier = Modifier.padding(
                                    start = MaterialTheme.appSpacing.screen,
                                    end = MaterialTheme.appSpacing.screen,
                                    top = MaterialTheme.appSpacing.l
                                )
                            ) {
                                Text(
                                    text = state.containerTypeName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.appColors.text
                                )
                                Text(
                                    text = stringResource(R.string.container_movements_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.appColors.muted
                                )
                            }
                        }

                        SearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = stringResource(R.string.container_movements_search),
                            modifier = Modifier.padding(horizontal = MaterialTheme.appSpacing.screen),
                            leadingIcon = Icons.Default.Search,
                            clearDescription = stringResource(R.string.clear_search_description),
                            onClear = { searchQuery = "" }
                        )

                        ChipRow(
                            options = ContainerMovementFilter.entries.map {
                                ChipOption(it, stringResource(it.labelRes))
                            },
                            selected = selectedFilter,
                            onSelected = { selectedFilter = it },
                            modifier = Modifier.padding(horizontal = MaterialTheme.appSpacing.screen)
                        )

                        if (filteredMovements.isEmpty()) {
                            AppEmptyState(
                                title = stringResource(R.string.no_movements_found),
                                message = stringResource(R.string.container_movements_empty_hint),
                                icon = Icons.Default.History
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = MaterialTheme.appSpacing.screen,
                                    end = MaterialTheme.appSpacing.screen,
                                    bottom = MaterialTheme.appSpacing.xxl
                                ),
                                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
                            ) {
                                items(filteredMovements, key = { it.id }) { movement ->
                                    MovementCard(movement = movement)
                                }

                                if (state.isLoading && state.movements.isNotEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(MaterialTheme.appSpacing.l),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }

                                if (state.hasMore && !state.isLoading) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AppButtonTertiary(onClick = { viewModel.loadMore() }) {
                                                Text(stringResource(R.string.load_more))
                                            }
                                        }
                                    }
                                }
                            }
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
private fun MovementCard(
    movement: MovementUiModel
) {
    val (tone, accent) = when {
        movement.isReceived -> AppStatusTone.Success to MaterialTheme.appColors.success
        movement.isDispatched -> AppStatusTone.Error to MaterialTheme.appColors.error
        else -> AppStatusTone.Warning to MaterialTheme.appColors.warning
    }
    val subtitle = buildString {
        append(movement.customerName ?: stringResource(R.string.container_movements_no_customer))
        movement.notes?.takeIf { it.isNotBlank() }?.let {
            append(" • ")
            append(it)
        }
    }

    AppCard {
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
                    text = movement.containerTypeName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.appColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = movement.quantityDisplay,
                style = MaterialTheme.appTypography.amountM.copy(textAlign = TextAlign.End),
                color = accent
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppChipStatus(
                label = compactMovementType(movement),
                tone = tone
            )
            Text(
                text = movement.createdAt?.let(::compactTimestamp).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted
            )
        }
    }
}

@Composable
private fun MovementError(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AppEmptyState(
            title = error,
            message = stringResource(R.string.container_movements_error_hint),
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry,
            icon = Icons.Default.Warning
        )
    }
}

@Composable
private fun compactMovementType(movement: MovementUiModel): String {
    return when {
        movement.isReceived -> stringResource(R.string.container_movement_filter_received)
        movement.isDispatched -> stringResource(R.string.container_movement_filter_given)
        else -> stringResource(R.string.container_movement_filter_adjusted)
    }
}

private fun compactTimestamp(value: String): String = formatRemoteDateTime(value)
