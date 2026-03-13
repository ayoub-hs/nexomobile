package com.nexopos.erp.feature.manufacturing.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.BomItem
import com.nexopos.erp.core.network.ManufacturingBom
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BomDetailScreen(
    bomId: Long,
    bom: ManufacturingBom?,
    isLoading: Boolean = false,
    error: String? = null,
    onCreateProductionOrder: (Long) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = bom?.name ?: stringResource(R.string.bom_details))
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
                error != null && bom == null -> {
                    ErrorContent(
                        error = error,
                        onRetry = { /* Retry loading BOM */ }
                    )
                }
                bom != null -> {
                    BomDetailContent(
                        bom = bom,
                        onCreateProductionOrder = onCreateProductionOrder
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
private fun BomDetailContent(
    bom: ManufacturingBom,
    onCreateProductionOrder: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MaterialTheme.appSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.l)
    ) {
        item {
            BomHeaderSection(bom = bom)
        }

        item {
            BomProductSection(bom = bom)
        }

        item {
            Text(
                text = stringResource(R.string.bom_items),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.appColors.onSurface
            )
        }

        if (bom.items.isNullOrEmpty()) {
            item {
                EmptyItemsContent()
            }
        } else {
            items(bom.items!!, key = { it.id }) { item ->
                BomItemCard(item = item)
            }
        }

        item {
            AppButtonPrimary(
                onClick = { onCreateProductionOrder(bom.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.xs))
                Text(stringResource(R.string.create_production_order))
            }
        }
    }
}

@Composable
private fun BomHeaderSection(bom: ManufacturingBom) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = bom.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.appColors.onSurface
            )
            
            bom.description?.let { description ->
                if (description.isNotBlank()) {
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
}

@Composable
private fun BomProductSection(bom: ManufacturingBom) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.produced_product),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = bom.product?.name ?: stringResource(R.string.unknown_product),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.appColors.onSurface
            )
            bom.product?.sku?.let { sku ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sku_format, sku),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BomItemCard(item: BomItem) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product?.name ?: stringResource(R.string.unknown_product),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.appColors.onSurface
                )
                item.product?.sku?.let { sku ->
                    Text(
                        text = stringResource(R.string.sku_format, sku),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.quantity_format, item.quantity),
                    style = MaterialTheme.appTypography.amountM,
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
}

@Composable
private fun EmptyItemsContent() {
    AppEmptyState(
        title = stringResource(R.string.no_bom_items),
        message = stringResource(R.string.no_bom_items),
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Default.Inventory
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
        androidx.compose.material3.TextButton(onClick = onRetry) {
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
            text = stringResource(R.string.bom_not_found),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appColors.onSurfaceVariant
        )
    }
}
