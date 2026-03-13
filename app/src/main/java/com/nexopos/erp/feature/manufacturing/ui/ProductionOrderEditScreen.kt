package com.nexopos.erp.feature.manufacturing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.BomItem
import com.nexopos.erp.core.network.ProductionOrder
import com.nexopos.erp.feature.manufacturing.ProductionStatus
import com.nexopos.erp.feature.manufacturing.vm.ProductionOrderEditViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Production Order Edit Screen
 * 
 * Edit screen for production orders with state management.
 * Shows current state: pending → started → completed (with visual indicator)
 * Displays BOM information and required materials.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderEditScreen(
    orderId: Long,
    viewModel: ProductionOrderEditViewModel,
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(orderId) {
        viewModel.loadOrder(orderId)
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.statusUpdated) {
        if (state.statusUpdated) {
            snackbarHostState.showSnackbar("Status updated successfully")
            viewModel.clearStatusUpdated()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = state.order?.code ?: stringResource(R.string.production_order),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
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
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null && state.order == null -> {
                    ErrorContent(
                        error = state.error!!,
                        onRetry = { viewModel.loadOrder(orderId) }
                    )
                }
                state.order != null -> {
                    ProductionOrderEditContent(
                        order = state.order!!,
                        bomItems = state.bomItems,
                        isUpdating = state.isUpdating,
                        onStartProduction = { viewModel.startProduction() },
                        onCompleteProduction = { viewModel.completeProduction() }
                    )
                }
                else -> {
                    EmptyContent()
                }
            }
        }
    }
}

@Composable
private fun ProductionOrderEditContent(
    order: ProductionOrder,
    bomItems: List<BomItem>,
    isUpdating: Boolean,
    onStartProduction: () -> Unit,
    onCompleteProduction: () -> Unit
) {
    val dateFormat = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }
    
    val status = ProductionStatus.fromValue(order.status)
    val canStart = status == ProductionStatus.DRAFT || status == ProductionStatus.PLANNED
    val canComplete = status == ProductionStatus.IN_PROGRESS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.appSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
    ) {
        StatusProgressIndicator(currentStatus = status)

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
                        text = order.code,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.appColors.onSurface
                    )
                    EditScreenStatusBadge(status = status)
                }
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.product),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = order.product?.name ?: stringResource(R.string.unknown_product),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.onSurface
                )
                order.product?.sku?.let { sku ->
                    Text(
                        text = stringResource(R.string.sku_format, sku),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                }
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.quantity_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = order.quantity.toString(),
                        style = MaterialTheme.appTypography.amountL,
                        color = MaterialTheme.appColors.primary
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.unit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = order.unit?.name ?: "-",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appColors.onSurface
                    )
                }
            }
        }

        // BOM Info Card (if linked)
        order.bom?.let { bom ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.bom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bom.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.appColors.onSurface
                    )
                }
            }
        }

        // Required Materials Card
        if (bomItems.isNotEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.required_materials),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.appColors.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    bomItems.forEach { item ->
                        MaterialItemRow(item = item)
                        if (item != bomItems.last()) {
                            Spacer(modifier = Modifier.height(8.dp))
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
                    text = stringResource(R.string.timeline),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                TimelineItem(
                    label = stringResource(R.string.created),
                    value = order.createdAt.let { 
                        try {
                            dateFormat.format(Instant.parse(it))
                        } catch (e: Exception) {
                            it
                        }
                    }
                )
                
                order.startedAt?.let { startedAt ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TimelineItem(
                        label = stringResource(R.string.started),
                        value = try {
                            dateFormat.format(Instant.parse(startedAt))
                        } catch (e: Exception) {
                            startedAt
                        }
                    )
                }
                
                order.completedAt?.let { completedAt ->
                    Spacer(modifier = Modifier.height(8.dp))
                    TimelineItem(
                        label = stringResource(R.string.completed),
                        value = try {
                            dateFormat.format(Instant.parse(completedAt))
                        } catch (e: Exception) {
                            completedAt
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        if (isUpdating) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            if (canStart) {
                AppButtonPrimary(
                    onClick = onStartProduction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.start_production))
                }
            }
            
            if (canComplete) {
                AppButtonPrimary(
                    onClick = onCompleteProduction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.complete_production))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusProgressIndicator(currentStatus: ProductionStatus) {
    val statuses = listOf(
        ProductionStatus.DRAFT to "Draft",
        ProductionStatus.PLANNED to "Planned",
        ProductionStatus.IN_PROGRESS to "In Progress",
        ProductionStatus.COMPLETED to "Completed"
    )
    
    val currentIndex = statuses.indexOfFirst { it.first == currentStatus }.coerceAtLeast(0)

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.appColors.surfaceVariant,
        elevated = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            statuses.forEachIndexed { index, (_, label) ->
                val isCompleted = index <= currentIndex
                val isCurrent = index == currentIndex
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    isCurrent -> MaterialTheme.appColors.primary
                                    isCompleted -> MaterialTheme.appColors.primary.copy(alpha = 0.6f)
                                    else -> MaterialTheme.appColors.outline.copy(alpha = 0.3f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.appColors.onPrimary
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.appColors.outline
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.appColors.primary 
                               else MaterialTheme.appColors.onSurfaceVariant
                    )
                }
                
                if (index < statuses.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                if (index < currentIndex) MaterialTheme.appColors.primary
                                else MaterialTheme.appColors.outline.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialItemRow(item: BomItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.Inventory,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.appColors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = item.componentProduct?.name ?: item.product?.name 
                           ?: stringResource(R.string.unknown_product),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.onSurface
                )
            }
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${item.quantity}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.appColors.primary
            )
            item.unit?.name?.let { unitName ->
                Text(
                    text = unitName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
            }
        }
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

@Composable
private fun EditScreenStatusBadge(status: ProductionStatus) {
    AppChipStatus(
        label = getEditStatusLabel(status),
        tone = when (status) {
            ProductionStatus.DRAFT -> AppStatusTone.Info
            ProductionStatus.PLANNED -> AppStatusTone.Warning
            ProductionStatus.IN_PROGRESS -> AppStatusTone.Info
            ProductionStatus.COMPLETED -> AppStatusTone.Success
            ProductionStatus.ON_HOLD -> AppStatusTone.Warning
            ProductionStatus.CANCELLED -> AppStatusTone.Error
        }
    )
}

@Composable
private fun getEditStatusLabel(status: ProductionStatus): String {
    return when (status) {
        ProductionStatus.DRAFT -> stringResource(R.string.status_draft)
        ProductionStatus.PLANNED -> stringResource(R.string.status_pending)
        ProductionStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
        ProductionStatus.COMPLETED -> stringResource(R.string.status_completed)
        ProductionStatus.ON_HOLD -> stringResource(R.string.status_on_hold)
        ProductionStatus.CANCELLED -> stringResource(R.string.status_cancelled)
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
        title = stringResource(R.string.production_order_not_found),
        message = stringResource(R.string.production_order_not_found),
        modifier = Modifier.fillMaxSize(),
        icon = Icons.Default.Warning
    )
}
