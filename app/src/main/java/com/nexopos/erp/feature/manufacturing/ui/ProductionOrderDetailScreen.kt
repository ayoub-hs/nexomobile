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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.ProductionOrder
import com.nexopos.erp.feature.manufacturing.ProductionStatus
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionOrderDetailScreen(
    orderId: Long,
    order: ProductionOrder?,
    isLoading: Boolean = false,
    error: String? = null,
    onStartProduction: (Long) -> Unit = {},
    onCompleteProduction: (Long) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = order?.code ?: stringResource(R.string.production_order))
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null && order == null -> {
                    ErrorContent(
                        error = error,
                        onRetry = onBack
                    )
                }
                order != null -> {
                    ProductionOrderDetailContent(
                        order = order,
                        onStartProduction = onStartProduction,
                        onCompleteProduction = onCompleteProduction
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
private fun ProductionOrderDetailContent(
    order: ProductionOrder,
    onStartProduction: (Long) -> Unit = {},
    onCompleteProduction: (Long) -> Unit = {}
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
                    DetailProductionStatusBadge(status = status)
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
                
                // Created date
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
                
                // Started date
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
                
                // Completed date
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
        
        if (canStart) {
            AppButtonPrimary(
                onClick = { onStartProduction(order.id) },
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
                onClick = { onCompleteProduction(order.id) },
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

        Spacer(modifier = Modifier.height(16.dp))
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
private fun DetailProductionStatusBadge(status: ProductionStatus) {
    AppChipStatus(
        label = getProductionStatusLabel(status),
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
        title = stringResource(R.string.production_order_not_found),
        message = stringResource(R.string.production_order_not_found),
        modifier = Modifier.fillMaxSize(),
        icon = Icons.Default.Warning
    )
}
