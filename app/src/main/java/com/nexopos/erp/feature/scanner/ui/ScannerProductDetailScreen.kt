package com.nexopos.erp.feature.scanner.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.AdminUnitQuantityResponse
import com.nexopos.erp.feature.scanner.vm.ScannerProductDetailViewModel
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.formatAppQuantity
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppChipStatus
import com.nexopos.erp.ui.components.AppStatusTone
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing

@Composable
fun ScannerProductDetailScreen(
    productId: Long,
    viewModel: ScannerProductDetailViewModel,
    onEdit: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    when {
        state.isLoading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        state.error != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.appSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
            ) {
                Text(state.error!!)
            }
        }

        else -> {
            val product = state.product ?: return
            var selectedUnitIndex by rememberSaveable(product.id) { mutableStateOf(0) }
            val units = product.unitQuantities
            if (selectedUnitIndex > units.lastIndex) {
                selectedUnitIndex = 0
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MaterialTheme.appSpacing.screen, vertical = MaterialTheme.appSpacing.l),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
            ) {
                item {
                    AppButtonPrimary(
                        onClick = { onEdit(product.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.scanner_edit_product_action))
                    }
                }
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        ProductMetaRow(
                            label = stringResource(R.string.scanner_label_barcode),
                            value = product.barcode.orEmpty()
                        )
                        ProductMetaRow(
                            label = stringResource(R.string.scanner_label_category_id),
                            value = state.categoryName.orEmpty()
                        )
                        StockSummaryCard(stock = product.stockQuantity ?: 0.0)
                        FlagChips(
                            manufactured = product.isManufactured,
                            rawMaterial = product.isRawMaterial
                        )
                    }
                }
                if (units.isNotEmpty()) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedUnitIndex,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                units.forEachIndexed { index, unit ->
                                    val label = unit.unit?.name ?: stringResource(R.string.scanner_unit_fallback_title)
                                    Tab(
                                        selected = selectedUnitIndex == index,
                                        onClick = { selectedUnitIndex = index },
                                        text = { Text(label) }
                                    )
                                }
                            }
                            UnitDetailCard(unit = units[selectedUnitIndex])
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitDetailCard(unit: AdminUnitQuantityResponse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.appSpacing.m),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
    ) {
        MetricRow(
            primaryLabel = stringResource(R.string.scanner_label_sale_price),
            primaryValue = formatAppCurrency(unit.salePriceEdit ?: unit.salePrice ?: 0.0),
            secondaryLabel = stringResource(R.string.scanner_label_wholesale_price),
            secondaryValue = formatAppCurrency(unit.wholesalePriceEdit ?: unit.wholesalePrice ?: 0.0)
        )
        MetricRow(
            primaryLabel = stringResource(R.string.scanner_label_cogs),
            primaryValue = formatAppCurrency(unit.cogs ?: 0.0),
            secondaryLabel = stringResource(R.string.scanner_label_quantity),
            secondaryValue = formatAppQuantity(unit.quantity)
        )
        unit.containerLink?.let { container ->
            ProductMetaRow(
                label = stringResource(R.string.scanner_label_container),
                value = container.containerTypeName
            )
        }
        FlagChips(
            manufactured = unit.isManufactured,
            rawMaterial = unit.isRawMaterial
        )
    }
}

@Composable
private fun ProductMetaRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.muted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun StockSummaryCard(stock: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.appColors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.padding(start = MaterialTheme.appSpacing.s),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
        ) {
            Text(
                text = stringResource(R.string.scanner_label_stock),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.muted
            )
            Text(
                text = formatAppQuantity(stock),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlagChips(
    manufactured: Boolean,
    rawMaterial: Boolean
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
    ) {
        AppChipStatus(
            label = if (manufactured) {
                stringResource(R.string.scanner_flag_manufactured_on)
            } else {
                stringResource(R.string.scanner_flag_manufactured_off)
            },
            tone = if (manufactured) AppStatusTone.Success else AppStatusTone.Info
        )
        AppChipStatus(
            label = if (rawMaterial) {
                stringResource(R.string.scanner_flag_raw_material_on)
            } else {
                stringResource(R.string.scanner_flag_raw_material_off)
            },
            tone = if (rawMaterial) AppStatusTone.Warning else AppStatusTone.Info
        )
    }
}

@Composable
private fun MetricRow(
    primaryLabel: String,
    primaryValue: String,
    secondaryLabel: String,
    secondaryValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        MetricBlock(
            label = primaryLabel,
            value = primaryValue,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.l))
        MetricBlock(
            label = secondaryLabel,
            value = secondaryValue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.muted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
