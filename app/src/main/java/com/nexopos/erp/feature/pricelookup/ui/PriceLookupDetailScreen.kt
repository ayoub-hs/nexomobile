package com.nexopos.erp.feature.pricelookup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.AdminUnitQuantityResponse
import com.nexopos.erp.feature.pricelookup.vm.PriceLookupDetailViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import java.util.Locale

@Composable
fun PriceLookupDetailScreen(
    productId: Long,
    viewModel: PriceLookupDetailViewModel,
    onBack: () -> Unit
) {
    val state = viewModel.state

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.appSpacing.screen, vertical = MaterialTheme.appSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.price_lookup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.appColors.text
                )
            }
        }
    ) { paddingValues ->
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(MaterialTheme.appSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
                ) {
                    Text(text = state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            state.product != null -> {
                val product = state.product
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = MaterialTheme.appSpacing.screen, vertical = MaterialTheme.appSpacing.l),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.appColors.text
                            )
                            if (!state.editMode) {
                                AppButtonSecondary(onClick = viewModel::toggleEditMode) {
                                    Text(text = androidx.compose.ui.res.stringResource(R.string.price_lookup_edit_prices))
                                }
                            }
                        }
                        product.barcode?.takeIf { it.isNotBlank() }?.let { code ->
                            Text(
                                text = code,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.muted
                            )
                        }
                    }

                    if (state.editMode) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppButtonPrimary(
                                    onClick = viewModel::saveChanges,
                                    modifier = Modifier.weight(1f),
                                    enabled = !state.isSaving
                                ) {
                                    Text(text = androidx.compose.ui.res.stringResource(R.string.price_lookup_save_prices))
                                }
                                AppButtonSecondary(
                                    onClick = viewModel::cancelEdit,
                                    modifier = Modifier.weight(1f),
                                    enabled = !state.isSaving
                                ) {
                                    Text(text = androidx.compose.ui.res.stringResource(R.string.common_cancel))
                                }
                            }
                            if (state.isSaving) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = MaterialTheme.appSpacing.sm),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }

                    items(product.unitQuantities, key = { it.id }) { unit ->
                        PriceLookupUnitRow(
                            unit = unit,
                            editMode = state.editMode,
                            saleValue = state.drafts[unit.id]?.sale.orEmpty(),
                            wholesaleValue = state.drafts[unit.id]?.wholesale.orEmpty(),
                            cogsValue = state.drafts[unit.id]?.cogs.orEmpty(),
                            onSaleChange = { viewModel.updateDraft(unit.id, sale = it) },
                            onWholesaleChange = { viewModel.updateDraft(unit.id, wholesale = it) },
                            onCogsChange = { viewModel.updateDraft(unit.id, cogs = it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceLookupUnitRow(
    unit: AdminUnitQuantityResponse,
    editMode: Boolean,
    saleValue: String,
    wholesaleValue: String,
    cogsValue: String,
    onSaleChange: (String) -> Unit,
    onWholesaleChange: (String) -> Unit,
    onCogsChange: (String) -> Unit
) {
    val pricePattern = remember { Regex("^\\d*(?:[\\.,]\\d{0,3})?$") }
    val unitLabel = unit.unit?.name ?: ""

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
            Text(
                text = unitLabel.ifBlank { androidx.compose.ui.res.stringResource(R.string.scanner_unit_fallback_title) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.appColors.text
            )

            if (!editMode) {
                PriceLookupMetricRow(
                    label = androidx.compose.ui.res.stringResource(R.string.scanner_label_sale_price),
                    value = formatAppCurrency(unit.salePriceEdit ?: unit.salePrice ?: 0.0)
                )
                PriceLookupMetricRow(
                    label = androidx.compose.ui.res.stringResource(R.string.scanner_label_wholesale_price),
                    value = formatAppCurrency(unit.wholesalePriceEdit ?: unit.wholesalePrice ?: 0.0)
                )
                PriceLookupMetricRow(
                    label = androidx.compose.ui.res.stringResource(R.string.scanner_label_cogs),
                    value = formatAppCurrency(unit.cogs ?: 0.0)
                )
            } else {
                AppTextField(
                    value = saleValue,
                    onValueChange = { if (it.isBlank() || pricePattern.matches(it)) onSaleChange(it) },
                    label = androidx.compose.ui.res.stringResource(R.string.scanner_label_sale_price),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                AppTextField(
                    value = wholesaleValue,
                    onValueChange = { if (it.isBlank() || pricePattern.matches(it)) onWholesaleChange(it) },
                    label = androidx.compose.ui.res.stringResource(R.string.scanner_label_wholesale_price),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                AppTextField(
                    value = cogsValue,
                    onValueChange = { if (it.isBlank() || pricePattern.matches(it)) onCogsChange(it) },
                    label = androidx.compose.ui.res.stringResource(R.string.scanner_label_cogs),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    }
}

@Composable
private fun PriceLookupMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.muted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appColors.text
        )
    }
}
