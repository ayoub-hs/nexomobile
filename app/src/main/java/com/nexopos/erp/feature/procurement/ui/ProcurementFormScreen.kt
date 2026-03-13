package com.nexopos.erp.feature.procurement.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexopos.erp.R
import com.nexopos.erp.core.network.ProcurementProductRequestDto
import com.nexopos.erp.core.network.ProviderSummary
import com.nexopos.erp.core.network.MobileProduct
import com.nexopos.erp.feature.procurement.vm.ProcurementViewModel
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.appTypography
import com.nexopos.shared.models.UnitQuantity
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class ProcurementFormLineItem(
    val productId: Long = 0L,
    val productName: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val taxType: String = "inclusive",
    val unitId: Long? = null,
    val unitName: String? = null,
    val unitOptions: List<UnitQuantity> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcurementFormScreen(
    procurementId: Long? = null,
    viewModel: ProcurementViewModel,
    onSaveSuccess: (Long) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedProvider by remember { mutableStateOf<ProviderSummary?>(null) }
    var providerExpanded by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var invoiceReference by rememberSaveable { mutableStateOf("") }
    var invoiceDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var deliveryDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var deliveryStatus by rememberSaveable { mutableStateOf("pending") }
    var paymentStatus by rememberSaveable { mutableStateOf("unpaid") }
    var infoTabExpanded by remember { mutableStateOf(false) }
    var paymentTabExpanded by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showInvoiceDatePicker by remember { mutableStateOf(false) }
    var showDeliveryDatePicker by remember { mutableStateOf(false) }
    val invoiceDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = parseDateToMillis(invoiceDate)
    )
    val deliveryDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = parseDateToMillis(deliveryDate)
    )
    val lineItems = remember { mutableStateListOf(ProcurementFormLineItem()) }

    val isEditing = procurementId != null
    val totalAmount = lineItems.sumOf { it.quantity * it.unitPrice }
    val isLoading = state.isLoadingFormOptions || (state.isLoading && isEditing)
    val hasInvalidLineItems = lineItems.any {
        it.productId <= 0L || it.quantity <= 0.0 || it.unitPrice < 0.0
    }
    val canSubmit = selectedProvider != null && lineItems.isNotEmpty() && !hasInvalidLineItems && !state.isCreating

    LaunchedEffect(Unit) {
        viewModel.loadFormOptions()
    }

    LaunchedEffect(state.providers) {
        if (selectedProvider == null && state.providers.size == 1) {
            selectedProvider = state.providers.first()
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    LaunchedEffect(state.createSuccess, state.createdProcurementId) {
        val createdProcurementId = state.createdProcurementId ?: return@LaunchedEffect
        if (state.createSuccess) {
            viewModel.clearCreateSuccess()
            onSaveSuccess(createdProcurementId)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (isEditing) {
                    stringResource(R.string.edit_procurement)
                } else {
                    stringResource(R.string.create_procurement)
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.appSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.section)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.procurement_info_tab)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.procurement_products_tab)) }
                )
            }

            if (selectedTab == 0) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.select_provider),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.appColors.text
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AppTextField(
                            value = selectedProvider?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(R.string.provider),
                            placeholder = stringResource(R.string.select_provider),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = state.providers.isNotEmpty()) {
                                    providerExpanded = true
                                }
                        )
                        DropdownMenu(
                            expanded = providerExpanded,
                            onDismissRequest = { providerExpanded = false }
                        ) {
                            if (state.providers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.procurement_form_no_providers)) },
                                    onClick = { providerExpanded = false }
                                )
                            } else {
                                state.providers.forEach { provider ->
                                    DropdownMenuItem(
                                        text = { Text(provider.name) },
                                        onClick = {
                                            selectedProvider = provider
                                            providerExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (isLoading && state.providers.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(MaterialTheme.appSpacing.l),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.procurement_form_loading_options),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.appColors.muted
                            )
                        }
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.procurement_invoice_section),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.appColors.text
                    )
                    AppTextField(
                        value = invoiceReference,
                        onValueChange = { invoiceReference = it },
                        label = stringResource(R.string.invoice_number),
                        placeholder = stringResource(R.string.invoice_number_placeholder),
                        singleLine = true
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AppTextField(
                            value = invoiceDate,
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(R.string.invoice_date),
                            placeholder = stringResource(R.string.date_placeholder),
                            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            singleLine = true
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showInvoiceDatePicker = true }
                        )
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.procurement_delivery_section),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.appColors.text
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AppTextField(
                            value = deliveryDate,
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(R.string.delivery_date),
                            placeholder = stringResource(R.string.date_placeholder),
                            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            singleLine = true
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showDeliveryDatePicker = true }
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AppTextField(
                            value = if (deliveryStatus == "delivered") {
                                stringResource(R.string.status_delivered)
                            } else {
                                stringResource(R.string.status_pending)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(R.string.delivery_status),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { infoTabExpanded = true }
                        )
                        DropdownMenu(
                            expanded = infoTabExpanded,
                            onDismissRequest = { infoTabExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_pending)) },
                                onClick = {
                                    deliveryStatus = "pending"
                                    infoTabExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_delivered)) },
                                onClick = {
                                    deliveryStatus = "delivered"
                                    infoTabExpanded = false
                                }
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AppTextField(
                            value = if (paymentStatus == "paid") {
                                stringResource(R.string.status_paid)
                            } else {
                                stringResource(R.string.status_unpaid)
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = stringResource(R.string.payment_status),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { paymentTabExpanded = true }
                        )
                        DropdownMenu(
                            expanded = paymentTabExpanded,
                            onDismissRequest = { paymentTabExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_unpaid)) },
                                onClick = {
                                    paymentStatus = "unpaid"
                                    paymentTabExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_paid)) },
                                onClick = {
                                    paymentStatus = "paid"
                                    paymentTabExpanded = false
                                }
                            )
                        }
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.notes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.appColors.text
                    )
                    AppTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = stringResource(R.string.notes),
                        placeholder = stringResource(R.string.notes_placeholder),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            } else {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.line_items),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.appColors.text
                        )
                        IconButton(onClick = { lineItems.add(ProcurementFormLineItem()) }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_item),
                                tint = MaterialTheme.appColors.primary
                            )
                        }
                    }

                    if (lineItems.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_items_added),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.appColors.muted
                        )
                    } else {
                        lineItems.forEachIndexed { index, item ->
                            ProcurementFormLineItemRow(
                                item = item,
                                searchProducts = viewModel::searchProducts,
                                onProductChange = { product ->
                                    val defaultUnit = product.unitQuantities.firstOrNull()
                                    lineItems[index] = item.copy(
                                        productId = product.id,
                                        productName = product.name,
                                        unitId = defaultUnit?.unitId,
                                        unitName = defaultUnit?.unit?.name,
                                        unitOptions = product.unitQuantities
                                    )
                                },
                                onProductClear = {
                                    lineItems[index] = item.copy(
                                        productId = 0L,
                                        productName = "",
                                        unitId = null,
                                        unitName = null,
                                        unitOptions = emptyList()
                                    )
                                },
                                onUnitChange = { unitQuantity ->
                                    lineItems[index] = item.copy(
                                        unitId = unitQuantity.unitId,
                                        unitName = unitQuantity.unit?.name
                                    )
                                },
                                onTaxTypeChange = { taxType ->
                                    lineItems[index] = item.copy(taxType = taxType)
                                },
                                onQuantityChange = { quantity ->
                                    lineItems[index] = item.copy(quantity = quantity)
                                },
                                onPriceChange = { price ->
                                    lineItems[index] = item.copy(unitPrice = price)
                                },
                                onDelete = { lineItems.removeAt(index) }
                            )
                        }
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.appColors.text
                        )
                        Text(
                            text = formatAppCurrency(totalAmount),
                            style = MaterialTheme.appTypography.amountL,
                            color = MaterialTheme.appColors.primary
                        )
                    }
                }
            }

            AppButtonPrimary(
                onClick = {
                    selectedProvider?.id?.let { providerId ->
                        val providerName = selectedProvider?.name?.trim().orEmpty()
                        val providerFirstName = providerName.split(" ").firstOrNull().orEmpty()
                        val trimmedInvoice = invoiceReference.trim()
                        val computedName = listOfNotNull(
                            providerFirstName.takeIf { it.isNotBlank() },
                            trimmedInvoice.takeIf { it.isNotBlank() }
                        ).joinToString(" - ")

                        viewModel.createProcurement(
                            providerId = providerId,
                            notes = notes,
                            expectedDelivery = deliveryDate,
                            name = computedName,
                            invoiceReference = trimmedInvoice,
                            invoiceDate = invoiceDate,
                            deliveryStatus = deliveryStatus,
                            paymentStatus = paymentStatus,
                            products = lineItems.map { lineItem ->
                                ProcurementProductRequestDto(
                                    productId = lineItem.productId,
                                    quantity = lineItem.quantity,
                                    unitPrice = lineItem.unitPrice,
                                    unitId = lineItem.unitId,
                                    taxType = lineItem.taxType
                                )
                            }
                        )
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = MaterialTheme.appSpacing.s)
                            .height(MaterialTheme.appSpacing.l),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.appColors.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.s))
                }
                Text(
                    text = if (isEditing) {
                        stringResource(R.string.update_procurement)
                    } else {
                        stringResource(R.string.create_procurement)
                    }
                )
            }

            AppButtonSecondary(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    }

    if (showInvoiceDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showInvoiceDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        invoiceDatePickerState.selectedDateMillis?.let { millis ->
                            invoiceDate = millisToDate(millis)
                        }
                        showInvoiceDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInvoiceDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = invoiceDatePickerState)
        }
    }

    if (showDeliveryDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDeliveryDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        deliveryDatePickerState.selectedDateMillis?.let { millis ->
                            deliveryDate = millisToDate(millis)
                        }
                        showDeliveryDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeliveryDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = deliveryDatePickerState)
        }
    }
}

private fun parseDateToMillis(value: String): Long? {
    return runCatching {
        LocalDate.parse(value)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

private fun millisToDate(millis: Long): String {
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
}

@Composable
private fun ProcurementFormLineItemRow(
    item: ProcurementFormLineItem,
    searchProducts: suspend (String) -> List<MobileProduct>,
    onProductChange: (MobileProduct) -> Unit,
    onProductClear: () -> Unit,
    onUnitChange: (UnitQuantity) -> Unit,
    onTaxTypeChange: (String) -> Unit,
    onQuantityChange: (Double) -> Unit,
    onPriceChange: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var productExpanded by remember { mutableStateOf(false) }
    var productQuery by remember(item.productName) { mutableStateOf(item.productName) }
    var productResults by remember { mutableStateOf<List<MobileProduct>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var quantityText by remember(item.quantity) { mutableStateOf(item.quantity.toString()) }
    var priceText by remember(item.unitPrice) { mutableStateOf(item.unitPrice.toString()) }
    var unitExpanded by remember { mutableStateOf(false) }
    var taxExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(productQuery) {
        val query = productQuery.trim()
        if (query.length < 3) {
            isSearching = false
            productResults = emptyList()
            return@LaunchedEffect
        }
        val snapshot = query
        delay(250)
        if (snapshot != productQuery.trim()) return@LaunchedEffect
        isSearching = true
        productResults = searchProducts(snapshot).take(20)
        isSearching = false
        productExpanded = true
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        elevated = false,
        containerColor = MaterialTheme.appColors.surfaceVariant
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackValueFields = maxWidth < 360.dp

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = productQuery,
                        onValueChange = { newValue ->
                            val trimmed = newValue.trim()
                            productQuery = newValue
                            if (trimmed.isEmpty()) {
                                onProductClear()
                                productExpanded = false
                            } else if (trimmed != item.productName) {
                                onProductClear()
                                productExpanded = true
                            }
                        },
                        label = stringResource(R.string.product),
                        placeholder = stringResource(R.string.search_products),
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(MaterialTheme.appSpacing.m),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = productExpanded && productQuery.trim().length >= 3,
                        onDismissRequest = { productExpanded = false }
                    ) {
                        if (isSearching) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.procurement_form_loading_options)) },
                                onClick = { productExpanded = false }
                            )
                        } else if (productResults.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.procurement_form_no_products)) },
                                onClick = { productExpanded = false }
                            )
                        } else {
                            productResults.forEach { product ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = product.name)
                                            product.sku?.takeIf { it.isNotBlank() }?.let { sku ->
                                                Text(
                                                    text = sku,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.appColors.muted
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onProductChange(product)
                                        productQuery = product.name
                                        productExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    AppTextField(
                        value = item.unitName
                            ?: item.unitOptions.firstOrNull()?.unit?.name
                            ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = stringResource(R.string.unit),
                        placeholder = stringResource(R.string.select_unit),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        enabled = item.unitOptions.isNotEmpty()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(enabled = item.unitOptions.isNotEmpty()) {
                                unitExpanded = true
                            }
                    )
                    DropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        item.unitOptions.forEach { unitQuantity ->
                            val unitLabel = unitQuantity.unit?.name ?: stringResource(R.string.unit)
                            DropdownMenuItem(
                                text = { Text(unitLabel) },
                                onClick = {
                                    onUnitChange(unitQuantity)
                                    unitExpanded = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    val taxLabel = if (item.taxType == "inclusive") {
                        stringResource(R.string.tax_type_inclusive)
                    } else {
                        stringResource(R.string.tax_type_exclusive)
                    }
                    AppTextField(
                        value = taxLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = stringResource(R.string.tax_type_label),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { taxExpanded = true }
                    )
                    DropdownMenu(
                        expanded = taxExpanded,
                        onDismissRequest = { taxExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tax_type_inclusive)) },
                            onClick = {
                                onTaxTypeChange("inclusive")
                                taxExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tax_type_exclusive)) },
                            onClick = {
                                onTaxTypeChange("exclusive")
                                taxExpanded = false
                            }
                        )
                    }
                }

                if (stackValueFields) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.m)
                    ) {
                        ProcurementNumericField(
                            value = quantityText,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    quantityText = newValue
                                    newValue.toDoubleOrNull()?.let(onQuantityChange)
                                }
                            },
                            label = stringResource(R.string.qty),
                            modifier = Modifier.fillMaxWidth()
                        )
                        ProcurementNumericField(
                            value = priceText,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    priceText = newValue
                                    newValue.toDoubleOrNull()?.let(onPriceChange)
                                }
                            },
                            label = stringResource(R.string.price),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.remove_item),
                                    tint = MaterialTheme.appColors.error
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProcurementNumericField(
                            value = quantityText,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    quantityText = newValue
                                    newValue.toDoubleOrNull()?.let(onQuantityChange)
                                }
                            },
                            label = stringResource(R.string.qty),
                            modifier = Modifier.weight(1f)
                        )
                        ProcurementNumericField(
                            value = priceText,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    priceText = newValue
                                    newValue.toDoubleOrNull()?.let(onPriceChange)
                                }
                            },
                            label = stringResource(R.string.price),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.remove_item),
                                tint = MaterialTheme.appColors.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcurementNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}
