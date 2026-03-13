package com.nexopos.erp.feature.pricelookup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.feature.pricelookup.vm.PriceLookupSearchViewModel
import com.nexopos.erp.feature.scanner.ui.BarcodeScannerView
import com.nexopos.erp.feature.scanner.vm.ScannerLookupEvent
import com.nexopos.erp.feature.scanner.vm.ScannerLookupViewModel
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private enum class PriceLookupMode {
    SCAN,
    SEARCH
}

@Composable
fun PriceLookupScanScreen(
    onBack: () -> Unit,
    onOpenProduct: (Long) -> Unit
) {
    val lookupViewModel: ScannerLookupViewModel = koinViewModel()
    val searchViewModel: PriceLookupSearchViewModel = koinViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(PriceLookupMode.SCAN) }

    LaunchedEffect(Unit) {
        lookupViewModel.events.collect { event ->
            when (event) {
                is ScannerLookupEvent.ProductFound -> onOpenProduct(event.productId)
                is ScannerLookupEvent.ProductMissing -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.price_lookup_not_found, event.barcode)
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            when (mode) {
                PriceLookupMode.SCAN -> {
                    PriceLookupTopBar(
                        title = stringResource(R.string.price_lookup_title),
                        onBack = onBack
                    )
                }
                PriceLookupMode.SEARCH -> {
                    PriceLookupSearchBar(
                        query = searchViewModel.query,
                        onQueryChange = searchViewModel::updateQuery,
                        onBack = {
                            mode = PriceLookupMode.SCAN
                            searchViewModel.clearSearch()
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (mode) {
                PriceLookupMode.SCAN -> {
                    BarcodeScannerView(
                        onBarcode = lookupViewModel::lookupBarcode,
                        onManualEntry = { mode = PriceLookupMode.SEARCH }
                    )
                }
                PriceLookupMode.SEARCH -> {
                    PriceLookupSearchContent(
                        viewModel = searchViewModel,
                        onSelect = { product -> onOpenProduct(product.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceLookupTopBar(
    title: String,
    onBack: () -> Unit
) {
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
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.appColors.text
        )
    }
}

@Composable
private fun PriceLookupSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit
) {
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
        AppTextField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.price_lookup_search_title),
            placeholder = stringResource(R.string.price_lookup_search_hint),
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
    }
}

@Composable
private fun PriceLookupSearchContent(
    viewModel: PriceLookupSearchViewModel,
    onSelect: (Product) -> Unit
) {
    val query = viewModel.query
    val results = viewModel.results
    val isLoading = viewModel.isLoading
    val error = viewModel.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.appSpacing.screen, vertical = MaterialTheme.appSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
    ) {
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (query.length >= 2 && results.isEmpty() && !isLoading) {
            Text(
                text = stringResource(R.string.price_lookup_no_results),
                color = MaterialTheme.appColors.muted
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = MaterialTheme.appSpacing.l),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
        ) {
            items(results, key = { it.id }) { product ->
                PriceLookupResultRow(product = product, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun PriceLookupResultRow(
    product: Product,
    onSelect: (Product) -> Unit
) {
    val unit = product.unitQuantities?.firstOrNull()
    val priceHint = unit?.salePriceWithTax ?: unit?.salePrice

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onSelect(product) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.text
                )
                val code = product.barcode?.takeIf { it.isNotBlank() } ?: product.sku.orEmpty()
                if (code.isNotBlank()) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.muted
                    )
                }
            }
            if (priceHint != null) {
                Text(
                    text = formatAppCurrency(priceHint),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.primary
                )
            }
        }
    }
}
