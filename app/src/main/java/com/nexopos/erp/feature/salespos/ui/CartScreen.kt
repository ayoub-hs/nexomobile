package com.nexopos.erp.feature.salespos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PrintDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import java.util.Locale
import com.nexopos.erp.core.network.Customer
import com.nexopos.erp.core.network.PaymentMethod
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.nexopos.erp.R
import com.nexopos.erp.feature.catalog.CatalogRoutes
import com.nexopos.erp.feature.salespos.PosRoutes
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppChipFilter
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppEmptyState
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.components.SummaryCard
import com.nexopos.erp.ui.components.SummaryRowData
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.formatAppQuantity
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.erp.ui.theme.posColors
import com.nexopos.shared.models.Register
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun CartItemsScreen(
    cartViewModel: CartViewModel,
    widthSizeClass: WindowWidthSizeClass,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToCheckout: () -> Unit = {}
) {
    val state by cartViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isWide = widthSizeClass != WindowWidthSizeClass.Compact
    val horizontalPadding = if (isWide) 24.dp else 12.dp
    val verticalSpacing = if (isWide) 12.dp else 6.dp
    var containerDialogItem by remember { mutableStateOf<CartItem?>(null) }

    LaunchedEffect(state.error) {
        val error = state.error
        if (!error.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            cartViewModel.clearError()
        }
    }

    LaunchedEffect(state.pendingOrderCount) {
        if (state.pendingOrderCount > 0) {
            val message = context.getString(R.string.checkout_pending_orders, state.pendingOrderCount)
            snackbarHostState.showSnackbar(message = message, withDismissAction = true)
        }
    }

    CartItemsContent(
        state = state,
        widthSizeClass = widthSizeClass,
        snackbarHostState = snackbarHostState,
        horizontalPadding = horizontalPadding,
        verticalSpacing = verticalSpacing,
        onUpdateQuantity = cartViewModel::updateQuantity,
        onUpdatePrice = cartViewModel::updateItemPrice,
        onToggleWholesale = cartViewModel::toggleWholesale,
        onEditContainer = { containerDialogItem = it },
        onRemoveItem = cartViewModel::removeItem,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToCheckout = onNavigateToCheckout
    )

    containerDialogItem?.let { item ->
        ContainerTrackingDialog(
            item = item,
            onDismiss = { containerDialogItem = null },
            onApply = { enabled, quantityOverride ->
                cartViewModel.updateContainerTracking(item.key, enabled, quantityOverride)
                containerDialogItem = null
            }
        )
    }
}

@Composable
private fun CartItemsCard(
    items: List<CartItem>,
    isWide: Boolean,
    modifier: Modifier = Modifier,
    onUpdateQuantity: (String, Double) -> Unit,
    onUpdatePrice: (String, Double) -> Unit,
    onToggleWholesale: (String, Boolean) -> Unit,
    onEditContainer: (CartItem) -> Unit,
    onRemoveItem: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(color = MaterialTheme.appColors.surfaceVariant.copy(alpha = 0.3f)),
        contentPadding = PaddingValues(
            start = if (isWide) 16.dp else 12.dp,
            top = 12.dp,
            end = if (isWide) 16.dp else 12.dp,
            bottom = if (isWide) 24.dp else 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { _, item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                CartItemRow(
                    item = item,
                    onQuantityChange = { quantity -> onUpdateQuantity(item.key, quantity) },
                    onPriceChange = { price -> onUpdatePrice(item.key, price) },
                    onWholesaleToggle = { useWholesale -> onToggleWholesale(item.key, useWholesale) },
                    onEditContainer = { onEditContainer(item) },
                    onRemove = { onRemoveItem(item.key) }
                )
            }
        }
    }
}

@Composable
private fun CartItemsContent(
    state: CartState,
    widthSizeClass: WindowWidthSizeClass,
    snackbarHostState: SnackbarHostState,
    horizontalPadding: Dp,
    verticalSpacing: Dp,
    onUpdateQuantity: (String, Double) -> Unit,
    onUpdatePrice: (String, Double) -> Unit,
    onToggleWholesale: (String, Boolean) -> Unit,
    onEditContainer: (CartItem) -> Unit,
    onRemoveItem: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToCheckout: () -> Unit = {}
) {
    val isWide = widthSizeClass != WindowWidthSizeClass.Compact
    val useSplitPane = widthSizeClass == WindowWidthSizeClass.Expanded

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(start = horizontalPadding, top = 0.dp, end = horizontalPadding, bottom = verticalSpacing),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Show editing indicator when editing an existing order
            if (state.editingOrderId != null) {
                val isServerOrder = state.editingServerOrderId != null
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServerOrder)
                            MaterialTheme.appColors.tertiaryContainer
                        else
                            MaterialTheme.appColors.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = if (isServerOrder)
                                MaterialTheme.appColors.onTertiaryContainer
                            else
                                MaterialTheme.appColors.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(
                                if (isServerOrder) R.string.cart_editing_server_order
                                else R.string.cart_editing_order
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isServerOrder)
                                MaterialTheme.appColors.onTertiaryContainer
                            else
                                MaterialTheme.appColors.onPrimaryContainer
                        )
                    }
                }
            }

            if (state.items.isEmpty() && !state.isLoading) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.appColors.surfaceVariant
                ) {
                    AppEmptyState(
                        title = stringResource(R.string.cart_empty_title),
                        message = stringResource(R.string.cart_empty_message),
                        actionLabel = stringResource(R.string.nav_search),
                        onAction = onNavigateToSearch,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (state.items.isNotEmpty()) {
                if (useSplitPane) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        CartItemsCard(
                            items = state.items,
                            isWide = true,
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight(),
                            onUpdateQuantity = onUpdateQuantity,
                            onUpdatePrice = onUpdatePrice,
                            onToggleWholesale = onToggleWholesale,
                            onEditContainer = onEditContainer,
                            onRemoveItem = onRemoveItem
                        )

                        CartSummarySidebar(
                            state = state,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    CartItemsCard(
                        items = state.items,
                        isWide = isWide,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        onUpdateQuantity = onUpdateQuantity,
                        onUpdatePrice = onUpdatePrice,
                        onToggleWholesale = onToggleWholesale,
                        onEditContainer = onEditContainer,
                        onRemoveItem = onRemoveItem
                    )
                }
                
                // Checkout button at bottom of cart
                AppButtonPrimary(
                    onClick = onNavigateToCheckout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.checkout_button_proceed),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun CartSummarySidebar(state: CartState, modifier: Modifier = Modifier) {
    val totalQuantity = state.items.sumOf { it.quantity }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.cart_summary_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.cart_summary_items, formatQuantity(totalQuantity)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.cart_summary_line_count, state.items.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                Divider()
                SummarySection(state = state)
                Divider()
                Text(
                    text = stringResource(R.string.checkout_total, formatCurrency(state.total)),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Preview(name = "Cart – Compact", widthDp = 360, showBackground = true)
@Composable
private fun CartItemsCompactPreview() {
    val widthSizeClass = WindowWidthSizeClass.Compact
    val horizontalPadding = 12.dp
    val verticalSpacing = 6.dp
    val snackbarHostState = remember { SnackbarHostState() }
    CartItemsContent(
        state = previewCartState(),
        widthSizeClass = widthSizeClass,
        snackbarHostState = snackbarHostState,
        horizontalPadding = horizontalPadding,
        verticalSpacing = verticalSpacing,
        onUpdateQuantity = { _: String, _: Double -> },
        onUpdatePrice = { _: String, _: Double -> },
        onToggleWholesale = { _: String, _: Boolean -> },
        onEditContainer = { _: CartItem -> },
        onRemoveItem = { _: String -> },
        onNavigateToSearch = {},
        onNavigateToCheckout = {}
    )
}

@Preview(name = "Cart – Expanded", widthDp = 1024, showBackground = true)
@Composable
private fun CartItemsExpandedPreview() {
    val widthSizeClass = WindowWidthSizeClass.Expanded
    val horizontalPadding = 24.dp
    val verticalSpacing = 12.dp
    val snackbarHostState = remember { SnackbarHostState() }
    CartItemsContent(
        state = previewCartState(),
        widthSizeClass = widthSizeClass,
        snackbarHostState = snackbarHostState,
        horizontalPadding = horizontalPadding,
        verticalSpacing = verticalSpacing,
        onUpdateQuantity = { _: String, _: Double -> },
        onUpdatePrice = { _: String, _: Double -> },
        onToggleWholesale = { _: String, _: Boolean -> },
        onEditContainer = { _: CartItem -> },
        onRemoveItem = { _: String -> },
        onNavigateToSearch = {},
        onNavigateToCheckout = {}
    )
}

@Preview(name = "Cart – Empty", widthDp = 360, showBackground = true)
@Composable
private fun CartItemsEmptyPreview() {
    val widthSizeClass = WindowWidthSizeClass.Compact
    val horizontalPadding = 12.dp
    val verticalSpacing = 6.dp
    val snackbarHostState = remember { SnackbarHostState() }
    CartItemsContent(
        state = previewCartState(hasItems = false),
        widthSizeClass = widthSizeClass,
        snackbarHostState = snackbarHostState,
        horizontalPadding = horizontalPadding,
        verticalSpacing = verticalSpacing,
        onUpdateQuantity = { _: String, _: Double -> },
        onUpdatePrice = { _: String, _: Double -> },
        onToggleWholesale = { _: String, _: Boolean -> },
        onEditContainer = { _: CartItem -> },
        onRemoveItem = { _: String -> },
        onNavigateToSearch = {},
        onNavigateToCheckout = {}
    )
}

private fun previewCartState(hasItems: Boolean = true): CartState {
    if (!hasItems) {
        val payment = PaymentMethod(identifier = "cash", label = "Cash")
        return CartState(
            items = emptyList(),
            paymentMethods = listOf(payment),
            selectedPayment = payment
        )
    }

    val items = listOf(
        CartItem(
            key = "1",
            productId = 1L,
            name = "Espresso Beans",
            quantity = 2.0,
            unitPrice = 7.5,
            wholesalePriceWithTax = 6.5,
            useWholesale = false
        ),
        CartItem(
            key = "2",
            productId = 2L,
            name = "Milk Bottle",
            quantity = 1.0,
            unitPrice = 3.25,
            useWholesale = false
        ),
        CartItem(
            key = "3",
            productId = 3L,
            name = "Chocolate Bar",
            quantity = 3.0,
            unitPrice = 1.5,
            useWholesale = false
        )
    )
    val payment = PaymentMethod(identifier = "cash", label = "Cash")
    return CartState(
        items = items,
        discountType = DiscountType.Amount,
        discountValue = 0.0,
        paymentMethods = listOf(payment),
        selectedPayment = payment
    )
}

private fun formatQuantity(value: Double): String {
    return formatAppQuantity(value)
}

private fun formatPrice(value: Double): String {
    return formatAppQuantity(value, maxDecimals = 3)
}

private fun formatCurrency(value: Double): String {
    return formatAppCurrency(value)
}

@Composable
private fun checkoutFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.appColors.primary,
    unfocusedBorderColor = MaterialTheme.appColors.border,
    focusedContainerColor = MaterialTheme.appColors.surface,
    unfocusedContainerColor = MaterialTheme.appColors.surface
)

@Composable
private fun CustomerSelector(
    customers: List<Customer>,
    selected: Customer?,
    onSelect: (Customer) -> Unit
) {
    val displayName = selected?.let { "${it.firstName.orEmpty()} ${it.lastName.orEmpty()}".trim() } ?: ""
    DropdownSelector(
        label = stringResource(R.string.cart_customer_label),
        placeholder = stringResource(R.string.cart_customer_placeholder),
        displayValue = displayName,
        options = customers,
        optionLabel = { "${it.firstName.orEmpty()} ${it.lastName.orEmpty()}".trim() },
        onOptionSelected = onSelect
    )
}

@Composable
private fun PaymentSelector(
    state: CartState,
    onSelect: (String) -> Unit
) {
    DropdownSelector(
        label = stringResource(R.string.cart_payment_label),
        placeholder = stringResource(R.string.cart_payment_placeholder),
        displayValue = state.selectedPayment?.label ?: state.selectedPayment?.identifier.orEmpty(),
        options = state.paymentMethods,
        optionLabel = { method: PaymentMethod -> method.label ?: method.identifier },
        onOptionSelected = { option: PaymentMethod -> onSelect(option.identifier) }
    )
}

@Composable
private fun TenderedField(
    value: Double,
    onValueChange: (Double) -> Unit,
    total: Double
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var inputError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(value) {
        val formatted = if (value == 0.0) "" else value.toString().let { str ->
            if (str.contains('.')) str.trimEnd('0').trimEnd('.') else str
        }
        if (formatted != fieldValue.text) {
            fieldValue = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
        }
        inputError = null
    }

    AppTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            val cleaned = newValue.text.replace(',', '.')
            val numericPattern = Regex("^\\d*(?:\\.\\d{0,3})?$")
            if (cleaned.isEmpty() || numericPattern.matches(cleaned)) {
                val selection = TextRange(
                    start = newValue.selection.start.coerceIn(0, cleaned.length),
                    end = newValue.selection.end.coerceIn(0, cleaned.length)
                )
                fieldValue = TextFieldValue(
                    text = cleaned,
                    selection = selection,
                    composition = newValue.composition
                )
                val parsed = cleaned.toDoubleOrNull()
                if (parsed == null) {
                    onValueChange(0.0)
                    inputError = null
                } else if (parsed <= 0.0) {
                    inputError = context.getString(R.string.tendered_error_amount_gt_zero)
                } else if (parsed + 0.0001 < total) {
                    inputError = context.getString(R.string.tendered_error_cover_total, formatCurrency(total))
                } else {
                    inputError = null
                    onValueChange(parsed)
                }
            }
        },
        label = stringResource(R.string.tendered_label),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        isError = inputError != null,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                }
            }
    )
    if (inputError != null) {
        Text(
            text = inputError!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.error
        )
    }
}

@Composable
private fun DiscountField(
    type: DiscountType,
    value: Double,
    grossTotal: Double,
    onTypeChange: (DiscountType) -> Unit,
    onValueChange: (Double) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val maxValue = if (type == DiscountType.Amount) grossTotal.coerceAtLeast(0.0) else 100.0
    val label = stringResource(
        if (type == DiscountType.Amount) R.string.discount_label_amount else R.string.discount_label_percent
    )
    var inputError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(type, value) {
        val formatted = if (value == 0.0) "" else String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
        if (formatted != fieldValue.text) {
            fieldValue = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
        }
        inputError = null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
    ) {
        AppTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                val cleaned = newValue.text.replace(',', '.')
                val numericPattern = Regex("^\\d*(?:\\.\\d{0,2})?$")
                if (cleaned.isEmpty()) {
                    fieldValue = TextFieldValue("", TextRange.Zero)
                    onValueChange(0.0)
                    inputError = null
                } else if (numericPattern.matches(cleaned)) {
                    val selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, cleaned.length),
                        end = newValue.selection.end.coerceIn(0, cleaned.length)
                    )
                    fieldValue = TextFieldValue(cleaned, selection, newValue.composition)
                    val parsed = cleaned.toDoubleOrNull() ?: 0.0
                    if (parsed > maxValue) {
                        inputError = if (type == DiscountType.Amount) {
                            context.getString(R.string.discount_error_amount, formatCurrency(maxValue))
                        } else {
                            context.getString(R.string.discount_error_percent)
                        }
                    } else {
                        inputError = null
                        onValueChange(parsed)
                    }
                }
            },
            label = label,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            isError = inputError != null,
            modifier = Modifier.weight(1f)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DiscountType.values().forEach { option ->
                AppChipFilter(
                    selected = option == type,
                    onClick = { onTypeChange(option) },
                    label = {
                        Text(
                            text = stringResource(
                                if (option == DiscountType.Amount) R.string.discount_chip_amount else R.string.discount_chip_percent
                            )
                        )
                    }
                )
            }
        }
    }
    if (inputError != null) {
        Text(
            text = inputError!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.error
        )
    }
}

@Composable
fun CheckoutScreen(
    navController: NavHostController,
    cartViewModel: CartViewModel,
    widthSizeClass: WindowWidthSizeClass,
    currentRegister: Register?,
    onManageRegister: () -> Unit,
    registerViewModel: RegisterViewModel
) {
    val state by cartViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var holdDialogVisible by remember { mutableStateOf(false) }
    var holdTitle by remember { mutableStateOf(TextFieldValue("")) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isWideLayout = widthSizeClass != WindowWidthSizeClass.Compact
    val requiresCustomerForContainers = state.hasTrackedContainersWithoutCustomer
    var pendingRegisterRefreshId by remember { mutableStateOf<Int?>(null) }
    val outerHorizontalPadding = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 16.dp
        WindowWidthSizeClass.Medium -> 28.dp
        WindowWidthSizeClass.Expanded -> 40.dp
        else -> 16.dp
    }
    val verticalSpacing = if (isWideLayout) MaterialTheme.appSpacing.md else MaterialTheme.appSpacing.sm

    LaunchedEffect(Unit) {
        cartViewModel.events.collectLatest { event ->
            when (event) {
                CartEvent.OrderCompleted,
                CartEvent.OrderPlacedOnHold -> {
                    if (pendingRegisterRefreshId != null) {
                        registerViewModel.refreshAfterOrder()
                        pendingRegisterRefreshId = null
                    }
                    val returnedToSell = navController.popBackStack(PosRoutes.SEARCH, inclusive = false)
                    if (!returnedToSell) {
                        navController.navigate(PosRoutes.SEARCH) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }

                is CartEvent.OfflineSyncFailed -> {
                    val message = context.getString(R.string.checkout_failed_orders_snackbar, event.count)
                    val actionLabel = context.getString(R.string.checkout_retry_sync)
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = actionLabel,
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        cartViewModel.retryFailedOrders()
                    }
                }

                CartEvent.OrderQueuedOffline -> {
                    pendingRegisterRefreshId = null
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.checkout_offline_order_queued),
                        withDismissAction = true
                    )
                }

                is CartEvent.Error -> {
                    pendingRegisterRefreshId = null
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = true
                    )
                }
            }
            cartViewModel.clearEvents()
        }
    }

    LaunchedEffect(state.paymentMethods) {
        if (state.paymentMethods.isNotEmpty()) {
            val cashMethod = state.paymentMethods.firstOrNull {
                it.identifier.equals("cash", ignoreCase = true) ||
                    (it.label?.contains("cash", ignoreCase = true) == true)
            }
            if (cashMethod != null && state.selectedPayment?.identifier != cashMethod.identifier) {
                cartViewModel.setPayment(cashMethod.identifier)
            }
        }
    }

    val summaryContent: @Composable ColumnScope.() -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MaterialTheme.appRadii.xl),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            val printEnabled = state.printReceipt
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.appSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
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
                            text = stringResource(R.string.checkout_total_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.appColors.text
                        )
                        Text(
                            text = formatCurrency(state.total),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.posColors.totalHighlight
                        )
                        if (state.change > 0) {
                            Text(
                                text = stringResource(R.string.checkout_change, formatCurrency(state.change)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.posColors.changeText
                            )
                        }
                        Text(
                            text = stringResource(R.string.summary_tax_included, formatCurrency(state.taxTotal)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.muted
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)
                        ) {
                            val printerIcon = if (printEnabled) Icons.Filled.Print else Icons.Filled.PrintDisabled
                            val printerTint = if (printEnabled) MaterialTheme.posColors.success else MaterialTheme.appColors.muted
                            IconButton(onClick = { cartViewModel.setPrintReceipt(!printEnabled) }) {
                                Icon(
                                    imageVector = printerIcon,
                                    contentDescription = stringResource(R.string.checkout_print_receipt),
                                    tint = printerTint
                                )
                            }
                            Switch(
                                checked = printEnabled,
                                onCheckedChange = cartViewModel::setPrintReceipt
                            )
                        }
                        Text(
                            text = stringResource(R.string.summary_subtotal) + ": " + formatCurrency(state.subtotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.muted
                        )
                        if (state.discountAmount > 0.0) {
                            Text(
                                text = stringResource(R.string.summary_discount_amount) + ": " + formatCurrency(state.discountAmount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.posColors.discountText
                            )
                        }
                    }
                }
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.checkout_section_adjustments),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.appColors.text
            )
            DiscountField(
                type = state.discountType,
                value = state.discountValue,
                grossTotal = state.subtotal,
                onTypeChange = cartViewModel::setDiscountType,
                onValueChange = cartViewModel::setDiscountValue
            )
            Divider()
            TenderedField(
                value = state.tendered,
                onValueChange = { value -> cartViewModel.setTendered(value) },
                total = state.total
            )
        }

        if (currentRegister != null) {
            CurrentRegisterCard(
                register = currentRegister,
                onManageRegister = onManageRegister,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            MissingRegisterCard(
                title = stringResource(R.string.register_checkout_warning_title),
                message = stringResource(R.string.register_checkout_warning_message),
                onManageRegister = onManageRegister,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.checkout_section_details),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.appColors.text
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CustomerSelector(
                        customers = state.customers,
                        selected = state.selectedCustomer,
                        onSelect = cartViewModel::setCustomer
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    PaymentSelector(
                        state = state,
                        onSelect = { identifier -> cartViewModel.setPayment(identifier) }
                    )
                }
            }
        }
    }

    val paymentsContent: @Composable ColumnScope.() -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
            if (requiresCustomerForContainers) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.appColors.warning.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.container_tracking_select_customer_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.text
                    )
                }
            }
            AppButtonPrimary(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = {
                    scope.launch {
                        pendingRegisterRefreshId = currentRegister?.id
                        val result = cartViewModel.submitOrder(registerId = currentRegister?.id)
                        if (result.isFailure) {
                            pendingRegisterRefreshId = null
                        }
                    }
                },
                enabled = !state.isSubmitting && state.items.isNotEmpty() && !requiresCustomerForContainers
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.posColors.onSuccess
                    )
                } else {
                    Text(
                        stringResource(R.string.sell_pay_cta, formatCurrency(state.total)),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    onClick = { holdDialogVisible = true },
                    enabled = !state.isSubmitting && state.items.isNotEmpty() && !requiresCustomerForContainers,
                    shape = RoundedCornerShape(MaterialTheme.appRadii.lg)
                ) {
                    Text(
                        stringResource(R.string.checkout_button_hold),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                    .height(52.dp),
                    onClick = {
                        scope.launch {
                            pendingRegisterRefreshId = currentRegister?.id
                            val result = cartViewModel.submitOrderUnpaid(registerId = currentRegister?.id)
                            if (result.isFailure) {
                                pendingRegisterRefreshId = null
                            }
                        }
                    },
                    enabled = !state.isSubmitting && state.items.isNotEmpty() && !requiresCustomerForContainers,
                    shape = RoundedCornerShape(MaterialTheme.appRadii.lg)
                ) {
                    Text(
                        stringResource(R.string.checkout_button_unpaid),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        state.lastOrder?.let { summary ->
            val identifier = summary.code ?: summary.id.toString()
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.checkout_last_order, identifier),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.muted
                )
            }
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error
        if (!error.isNullOrBlank()) {
            val result = snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.Dismissed) {
                cartViewModel.clearError()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = outerHorizontalPadding, vertical = verticalSpacing)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            if (state.failedOrderCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MaterialTheme.appRadii.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaterialTheme.appSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.checkout_failed_orders_message, state.failedOrderCount),
                            color = MaterialTheme.appColors.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = cartViewModel::retryFailedOrders) {
                            Text(
                                text = stringResource(R.string.checkout_retry_sync),
                                color = MaterialTheme.appColors.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (state.items.isEmpty()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.checkout_empty_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.appColors.muted
                    )
                }
                return@Column
            }

            if (isWideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
                    ) {
                        summaryContent()
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
                    ) {
                        paymentsContent()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)) {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
                        summaryContent()
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
                        paymentsContent()
                    }
                }
            }

            Spacer(modifier = Modifier.height(MaterialTheme.appSpacing.lg))
        }
    }

    if (holdDialogVisible) {
        AppDialog(
            onDismissRequest = {
                if (!state.isSubmitting) {
                    holdDialogVisible = false
                    holdTitle = TextFieldValue("")
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.hold_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.text
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.hold_dialog_message),
                        color = MaterialTheme.appColors.muted
                    )
                    AppTextField(
                        value = holdTitle,
                        onValueChange = { holdTitle = it },
                        label = stringResource(R.string.hold_dialog_title_label),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                AppButtonPrimary(
                    onClick = {
                        val title = holdTitle.text.trim()
                        if (title.isNotEmpty()) {
                            scope.launch {
                                holdDialogVisible = false
                                holdTitle = TextFieldValue("")
                                pendingRegisterRefreshId = currentRegister?.id
                                val result = cartViewModel.submitOrderHold(
                                    title = title,
                                    registerId = currentRegister?.id
                                )
                                if (result.isFailure) {
                                    pendingRegisterRefreshId = null
                                }
                            }
                        }
                    },
                    enabled = titleIsValid(holdTitle.text) && !state.isSubmitting && !requiresCustomerForContainers
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.height(16.dp))
                    } else {
                        Text(stringResource(R.string.hold_dialog_confirm))
                    }
                }
            },
            dismissButton = {
                AppButtonSecondary(
                    onClick = {
                        holdDialogVisible = false
                        holdTitle = TextFieldValue("")
                    }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private fun titleIsValid(text: String): Boolean = text.trim().isNotEmpty()

@Composable
private fun <T> DropdownSelector(
    label: String,
    placeholder: String,
    displayValue: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appColors.text
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = !expanded },
                enabled = options.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(MaterialTheme.appRadii.lg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayValue.takeIf { it.isNotBlank() } ?: placeholder
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(MaterialTheme.appRadii.lg),
                containerColor = MaterialTheme.appColors.elevated
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CartItemRow(
    item: CartItem,
    onQuantityChange: (Double) -> Unit,
    onPriceChange: (Double) -> Unit,
    onWholesaleToggle: (Boolean) -> Unit,
    onEditContainer: () -> Unit,
    onRemove: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val numberPattern = remember { Regex("^\\d*(?:\\.\\d{0,3})?$") }
    val quantityPattern = remember { Regex("^\\d*(?:\\.\\d{0,4})?$") }

    var quantityField by rememberSaveable(item.key + "_qty", stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(formatQuantity(item.quantity)))
    }
    var priceField by rememberSaveable(item.key + "_price", stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(formatPrice(item.unitPrice)))
    }
    var priceFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(item.quantity) {
        val formatted = formatQuantity(item.quantity)
        if (quantityField.text != formatted) {
            quantityField = TextFieldValue(formatted)
        }
    }

    LaunchedEffect(item.unitPrice) {
        val formatted = formatPrice(item.unitPrice)
        if (priceField.text != formatted) {
            priceField = TextFieldValue(formatted)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Row: Product Name on left, Wholesale toggle on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product Name
            val displayName = if (item.unitName != null) {
                "${item.name} (${item.unitName})"
            } else {
                item.name
            }
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )

            // Wholesale Toggle on right
            if (item.wholesalePriceWithTax != null && item.wholesalePriceWithTax > 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.wholesale_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSurfaceVariant
                    )
                    Switch(
                        checked = item.useWholesale,
                        onCheckedChange = onWholesaleToggle,
                        enabled = !item.isCustomPrice,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        ContainerTrackingSummary(
            item = item,
            onClick = onEditContainer
        )

        // Middle Row: Quantity Controls on left, Price Input on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quantity Controls
            Row(
                modifier = Modifier.weight(1.4f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Minus Button - removes item if quantity is 1
                Button(
                    onClick = {
                        if (item.quantity <= 1.0) {
                            onRemove()
                        } else {
                            val newQty = item.quantity - 1.0
                            onQuantityChange(newQty)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.posColors.quickAdd
                    )
                ) {
                    Text("−", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }

                // Quantity Field
                QuantityField(
                    value = quantityField,
                    onValueChange = { quantityField = it },
                    pattern = quantityPattern,
                    focusManager = focusManager,
                    label = "",
                    onValidChange = onQuantityChange,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 72.dp, max = 96.dp)
                )

                // Plus Button
                Button(
                    onClick = {
                        val newQty = item.quantity + 1.0
                        onQuantityChange(newQty)
                    },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.appColors.outlineVariant
                    )
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.appColors.onSurface)
                }
            }

            // Price Input Field
            PriceField(
                value = priceField,
                onValueChange = { priceField = it },
                pattern = numberPattern,
                focusManager = focusManager,
                label = "",
                onValidChange = onPriceChange,
                onFocusChanged = { focused -> priceFieldFocused = focused },
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 104.dp, max = 144.dp)
            )
        }

        // Bottom Row: Line Total on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Line Total
            Text(
                text = formatCurrency(item.lineTotal),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.posColors.priceText
            )
        }
    }
}

@Composable
private fun QuantityField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    pattern: Regex,
    focusManager: FocusManager,
    label: String,
    onValidChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    AppTextField(
        value = value,
        onValueChange = { newValue ->
            val cleaned = newValue.text.replace(',', '.')
            if (cleaned.isEmpty() || pattern.matches(cleaned)) {
                val selection = TextRange(
                    start = newValue.selection.start.coerceIn(0, cleaned.length),
                    end = newValue.selection.end.coerceIn(0, cleaned.length)
                )
                val updated = TextFieldValue(cleaned, selection, newValue.composition)
                onValueChange(updated)
                cleaned.toDoubleOrNull()?.let(onValidChange)
            }
        },
        label = label.takeIf { it.isNotBlank() },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                onValueChange(value.copy(selection = TextRange(0, value.text.length)))
            }
        }
    )
}

@Composable
private fun PriceField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    pattern: Regex,
    focusManager: FocusManager,
    label: String,
    onValidChange: (Double) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AppTextField(
        value = value,
        onValueChange = { newValue ->
            val cleaned = newValue.text.replace(',', '.')
            if (cleaned.isEmpty() || pattern.matches(cleaned)) {
                val selection = TextRange(
                    start = newValue.selection.start.coerceIn(0, cleaned.length),
                    end = newValue.selection.end.coerceIn(0, cleaned.length)
                )
                val updated = TextFieldValue(cleaned, selection, newValue.composition)
                onValueChange(updated)
                cleaned.toDoubleOrNull()?.let(onValidChange)
            }
        },
        label = label.takeIf { it.isNotBlank() },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        modifier = modifier.onFocusChanged { focusState ->
            onFocusChanged(focusState.isFocused)
            if (focusState.isFocused) {
                onValueChange(value.copy(selection = TextRange(0, value.text.length)))
            }
        }
    )
}

@Composable
internal fun SummarySection(state: CartState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Subtotal Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.summary_subtotal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.onSurfaceVariant
            )
            Text(
                formatCurrency(state.subtotal),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.onSurface
            )
        }

        // Discount Row (if present)
        if (state.discountAmount > 0.0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val suffix = if (state.discountType == DiscountType.Percent) {
                    String.format(Locale.US, "%.3f", state.discountValue)
                } else {
                    null
                }
                val discountValue = formatCurrency(state.discountAmount)
                val discountLabel = if (suffix != null) {
                    stringResource(R.string.summary_discount_percent, suffix)
                } else {
                    stringResource(R.string.summary_discount_amount)
                }

                Text(
                    discountLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.onSurfaceVariant
                )
                Text(
                    discountValue,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.posColors.discountText
                )
            }
        }

        // Tax Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.summary_tax),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.onSurfaceVariant
            )
            Text(
                formatCurrency(state.taxTotal),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.appColors.onSurface
            )
        }
    }
}
