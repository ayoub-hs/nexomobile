package com.nexopos.erp.feature.manufacturing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import com.nexopos.erp.R
import com.nexopos.erp.core.network.ManufacturingBom
import com.nexopos.erp.feature.manufacturing.vm.ManufacturingViewModel
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BomListScreen(
    viewModel: ManufacturingViewModel,
    canCreate: Boolean = false,
    onBack: () -> Unit = {},
    onBomClick: (Long) -> Unit = {},
    onCreateClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadBoms()
        viewModel.loadFormOptions()
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.bomCreateSuccess) {
        if (state.bomCreateSuccess) {
            snackbarHostState.showSnackbar("BOM created successfully")
            viewModel.clearBomCreateSuccess()
            showCreateDialog = false
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshingBoms,
        onRefresh = { viewModel.refreshBoms() }
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.boms),
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
        floatingActionButton = {
            if (canCreate && state.boms.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        showCreateDialog = true
                        viewModel.loadFormOptions()
                        onCreateClick()
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(MaterialTheme.appRadii.fab),
                    containerColor = MaterialTheme.appColors.primary,
                    contentColor = MaterialTheme.appColors.onPrimary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_bom)
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
                state.isLoadingBoms && state.boms.isEmpty() -> LoadingContent()
                state.error != null && state.boms.isEmpty() -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = { viewModel.loadBoms() }
                    )
                }
                state.boms.isEmpty() -> {
                    EmptyContent(
                        canCreate = canCreate,
                        onCreate = {
                            showCreateDialog = true
                            viewModel.loadFormOptions()
                            onCreateClick()
                        }
                    )
                }
                else -> {
                    BomListContent(
                        boms = state.boms,
                        onBomClick = onBomClick
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshingBoms,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    if (showCreateDialog) {
        CreateBomPopup(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, productId, unitId, quantity, isActive, description ->
                viewModel.createBom(
                    name = name,
                    productId = productId,
                    unitId = unitId,
                    quantity = quantity,
                    isActive = isActive,
                    description = description
                )
            },
            products = state.products,
            isLoading = state.isCreatingBom
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
            title = stringResource(R.string.no_boms),
            message = stringResource(R.string.no_boms_available),
            actionLabel = if (canCreate) stringResource(R.string.create_bom) else null,
            onAction = if (canCreate) onCreate else null,
            icon = Icons.Default.Inventory
        )
    }
}

@Composable
private fun BomListContent(
    boms: List<ManufacturingBom>,
    onBomClick: (Long) -> Unit
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
        items(boms, key = { it.id }) { bom ->
            BomCard(
                bom = bom,
                onClick = { onBomClick(bom.id) }
            )
        }
    }
}

@Composable
private fun BomCard(
    bom: ManufacturingBom,
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
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
            ) {
                Text(
                    text = bom.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.appColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bom.product?.name ?: stringResource(R.string.unknown_product),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.text
                )
                bom.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = bom.quantity.toString(),
                style = MaterialTheme.appTypography.amountM,
                color = MaterialTheme.appColors.primary
            )
        }
        AppChipStatus(
            label = stringResource(
                R.string.bom_items_count,
                bom.itemsCount ?: (bom.items?.size ?: 0)
            ),
            tone = AppStatusTone.Info
        )
    }
}
