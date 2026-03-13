package com.nexopos.desktop.ui.pos

import com.nexopos.desktop.core.repo.getDefaultUnitQuantity
import com.nexopos.desktop.core.repo.getPrice
import com.nexopos.desktop.core.repo.getUnitQuantities
import com.nexopos.desktop.core.repo.getDisplayName
import com.nexopos.desktop.core.repo.getUnitQuantity
import com.nexopos.shared.models.Customer
import com.nexopos.shared.models.PaymentMethod
import com.nexopos.shared.models.Product
import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexopos.desktop.core.repo.ProductEntity
import com.nexopos.desktop.hardware.DesktopCashDrawer
import com.nexopos.desktop.hardware.DesktopReceiptPrinter
import com.nexopos.shared.hardware.Receipt
import com.nexopos.shared.hardware.ReceiptItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

// Unified theme colors for consistent POS UI
object PosTheme {
    val Success = Color(0xFF0F766E)
    val Warning = Color(0xFFB7791F)
    val Info = Color(0xFF2563EB)
    val Highlight = Color(0xFFE6F4F1)
    val Border = Color(0xFFD9DEE7)
    val SurfaceSoft = Color(0xFFF0F2F5)
    val DangerSoft = Color(0xFFFDECEC)
    val WarningSoft = Color(0xFFFFF7E6)
    val StatusOpen = Success
    val StatusClosed = Color(0xFFC62828)
    val StatusDisabled = Color(0xFF98A2B3)
    val PriceRetail = Color(0xFF667085)
    val PriceWholesale = Color(0xFF0F766E)
    val PriceCustom = Color(0xFFB7791F)
    val SpacingTight = 4.dp
    val SpacingCompact = 8.dp
    val SpacingNormal = 16.dp
    val SpacingWide = 24.dp
    val ElevationLow = 0.dp
    val ElevationMedium = 1.dp
    val ElevationHigh = 2.dp
}

// POS Modes for input authority management
enum class POSMode {
    SALE,      // Scanner + shortcuts, NO focus
    PAYMENT,   // Numeric only, scanner LOCKED
    MODAL      // Tab navigation, focus trapped
}

// Cashier skill levels for UI adaptation
enum class CashierSkillLevel {
    BEGINNER,  // Larger UI, more guidance, images enabled
    EXPERT     // Dense UI, minimal guidance, text-only
}

// Price states for cart items
enum class PriceState {
    RETAIL,
    WHOLESALE,
    CUSTOM
}

// ROBUST: Pending product state (before adding to cart)
data class PendingProductState(
    val product: Product,
    val unitQuantity: com.nexopos.shared.models.UnitQuantity,
    val needsPrice: Boolean
)

// ROBUST: Unified add-product dialog state for proper focus management
data class AddProductDialogState(
    val product: Product,
    val selectedUnitQuantityId: Long,
    val initialQuantity: Double = 1.0
)

private fun resolveInitialUnitQuantity(
    product: Product,
    preferredUnitQuantity: com.nexopos.shared.models.UnitQuantity? = null
): com.nexopos.shared.models.UnitQuantity? {
    return preferredUnitQuantity
        ?: product.getDefaultUnitQuantity()
        ?: product.getUnitQuantities().firstOrNull()
}

@Composable
private fun PosAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconContentColor = MaterialTheme.colorScheme.primary,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

// Mode-specific keyboard handlers
private fun handleSaleModeKeys(
    event: KeyEvent,
    shortcutsManager: com.nexopos.desktop.core.settings.KeyboardShortcutsManager,
    scope: kotlinx.coroutines.CoroutineScope,
    cashDrawer: com.nexopos.desktop.hardware.DesktopCashDrawer,
    printReceipt: Boolean,
    showMessage: (String?) -> Unit,
    doSubmitOrder: () -> Unit,
    viewModel: POSViewModel,
    cart: List<CartItem>,
    setShowDiscountInput: (Boolean) -> Unit,
    setShowQuickProductDialog: (Boolean) -> Unit,
    showAddProductDialog: AddProductDialogState?,
    onNavigateToOrders: () -> Unit,
    setCurrentMode: (POSMode) -> Unit,
    selectedCustomer: Customer?,
    selectedPaymentMethod: PaymentMethod?,
    hiddenFieldFocusRequester: FocusRequester,
    setPrintReceipt: (Boolean) -> Unit
): Boolean {
    // Only handle when no dialogs are open
    if (showAddProductDialog != null) {
        return false
    }

    val action = shortcutsManager.getAction(event.key)
    return when (action) {
        com.nexopos.desktop.core.settings.ShortcutAction.OPEN_CASH_DRAWER -> {
            scope.launch {
                cashDrawer.open()
                showMessage("Tiroir-caisse ouvert")
            }
            true
        }
      com.nexopos.desktop.core.settings.ShortcutAction.TOGGLE_PRINT -> {
    val newValue = !printReceipt  // Now printReceipt is just a Boolean
    setPrintReceipt(newValue)  // Call the setter to update the state
    showMessage(if (newValue) "Impression : activée" else "Impression : désactivée")
    true
}
        com.nexopos.desktop.core.settings.ShortcutAction.SUBMIT_ORDER -> {
            if (cart.isNotEmpty() && selectedCustomer != null && selectedPaymentMethod != null) {
                // Directly switch to payment without extra message
                setCurrentMode(POSMode.PAYMENT)
                true
            } else {
                val missing = mutableListOf<String>()
                if (cart.isEmpty()) missing.add("panier")
                if (selectedCustomer == null) missing.add("client")
                if (selectedPaymentMethod == null) missing.add("mode de paiement")
                showMessage("Manquant : ${missing.joinToString(", ")}")
                true
            }
        }
        com.nexopos.desktop.core.settings.ShortcutAction.TOGGLE_WHOLESALE -> {
            if (!event.isCtrlPressed) {
                cart.lastOrNull()?.let { lastItem ->
                    if (lastItem.wholesalePrice != null && lastItem.wholesalePrice > 0) {
                        viewModel.toggleItemWholesale(lastItem)
                        showMessage(if (!lastItem.isWholesale) "Prix Gros" else "Prix Détail")
                        // Refocus scanner after action
                        scope.launch {
                            kotlinx.coroutines.delay(100)
                            hiddenFieldFocusRequester.requestFocus()
                        }
                    }
                }
                true
            } else false
        }
        com.nexopos.desktop.core.settings.ShortcutAction.OPEN_DISCOUNT -> {
            if (!event.isCtrlPressed) {
                setShowDiscountInput(true)
                setCurrentMode(POSMode.MODAL)
                true
            } else false
        }
        com.nexopos.desktop.core.settings.ShortcutAction.OPEN_QUICK_PRODUCT -> {
            if (!event.isCtrlPressed) {
                setShowQuickProductDialog(true)
                setCurrentMode(POSMode.MODAL)
                true
            } else false
        }
        com.nexopos.desktop.core.settings.ShortcutAction.REFRESH_DATA -> {
            scope.launch { viewModel.refreshDataIfNeeded() }
            true
        }
        com.nexopos.desktop.core.settings.ShortcutAction.NAVIGATE_ORDERS -> {
            onNavigateToOrders()
            true
        }
        com.nexopos.desktop.core.settings.ShortcutAction.CLEAR_CART -> {
            viewModel.clearCart()
            showMessage("Panier vidé")
            // Refocus scanner after action
            scope.launch {
                kotlinx.coroutines.delay(100)
                hiddenFieldFocusRequester.requestFocus()
            }
            true
        }
        // Non-configurable shortcuts
        null -> when (event.key) {
            Key.NumPadAdd -> {
                cart.lastOrNull()?.let { 
                    viewModel.increaseQuantity(it)
                    // Refocus scanner after action
                    scope.launch {
                        kotlinx.coroutines.delay(100)
                        hiddenFieldFocusRequester.requestFocus()
                    }
                }
                true
            }
            Key.NumPadSubtract -> {
                cart.lastOrNull()?.let { 
                    viewModel.decreaseQuantity(it)
                    // Refocus scanner after action
                    scope.launch {
                        kotlinx.coroutines.delay(100)
                        hiddenFieldFocusRequester.requestFocus()
                    }
                }
                true
            }
            Key.Delete -> {
                cart.lastOrNull()?.let { 
                    viewModel.removeFromCart(it.key)
                    // Refocus scanner after action
                    scope.launch {
                        kotlinx.coroutines.delay(100)
                        hiddenFieldFocusRequester.requestFocus()
                    }
                }
                true
            }
            Key.Escape -> {
                // Refocus scanner field on Escape key
                scope.launch {
                    kotlinx.coroutines.delay(50)
                    try {
                        hiddenFieldFocusRequester.requestFocus()
                        println("[Focus] Scanner field refocused via Escape")
                    } catch (e: Exception) {
                        println("[Focus] Failed to refocus: ${e.message}")
                    }
                }
                false // Let other handlers process ESC too
            }
            else -> false
        }
    }
}

private fun handlePaymentModeKeys(event: KeyEvent): Boolean {
    // PAYMENT mode: Numeric only, scanner LOCKED
    // Only allow numeric input, decimal point, and control keys
    return when (event.key) {
        // Allow numeric input (both numpad and top row)
        Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
        Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
        Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
        Key.Period -> false // Allow numeric input

        // Allow payment control keys
        Key.Enter, Key.NumPadEnter, Key.Escape, Key.Backspace, Key.Delete, Key.Tab -> false // Allow payment controls

        // Block everything else (including scanner input and shortcuts)
        else -> {
            println("[InputAuthority] Key blocked in PAYMENT mode: ${event.key}")
            true
        }
    }
}

private fun handleModalModeKeys(
    event: KeyEvent,
    setShowDiscountInput: (Boolean) -> Unit,
    setShowAddProductDialog: (AddProductDialogState?) -> Unit,
    setShowPriceInput: (CartItem?) -> Unit,
    setPendingProduct: (PendingProductState?) -> Unit,
    setCurrentMode: (POSMode) -> Unit,
    showDiscountInput: Boolean,
    showAddProductDialog: AddProductDialogState?,
    showPriceInput: CartItem?,
    hiddenFieldFocusRequester: FocusRequester,
    scope: kotlinx.coroutines.CoroutineScope
): Boolean {
    return when (event.key) {
        Key.Escape -> {
            // Always return to SALE mode on Escape
            setShowDiscountInput(false)
            setShowAddProductDialog(null)
            setShowPriceInput(null)
            setPendingProduct(null)
            setCurrentMode(POSMode.SALE)
            scope.launch {
                delay(50)
                hiddenFieldFocusRequester.requestFocus()
            }
            true
        }
        else -> false // Let modal handle its own keys
    }
}

/**
 * POS Screen using real data from NexoPOS server via ViewModel
 * REFACTORED: Complete implementation with proper focus management and keyboard shortcuts
 * UPDATED: Added cash register management
 */
@Composable
fun POSScreenWithViewModel(
    onNavigateToOrders: () -> Unit,
    onNavigateToReceiveContainers: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onBack: () -> Unit,
    orderToEdit: OrdersListItem? = null,
    onOrderEditHandled: (() -> Unit)? = null
) {
    // Inject ViewModel via Koin
    val viewModel = koinInject<POSViewModel>()
    val appSettings = koinInject<com.nexopos.desktop.core.prefs.AppSettings>()
    val shortcutsManager = koinInject<com.nexopos.desktop.core.settings.KeyboardShortcutsManager>()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // POS Mode state management - CRITICAL for input authority
    var currentMode by remember { mutableStateOf(POSMode.SALE) }
    val utilityDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Cashier skill level for UI adaptation
    var skillLevel by remember { mutableStateOf(CashierSkillLevel.EXPERT) }

    // Last scanned item for visual highlighting
    var lastScannedProductId by remember { mutableStateOf<String?>(null) }

    // Hidden text field for barcode scanner input (more reliable than key events)
    var hiddenBarcodeInput by remember { mutableStateOf(TextFieldValue("")) }
    val hiddenFieldFocusRequester = remember { FocusRequester() }
    var hiddenFieldHasFocus by remember { mutableStateOf(false) }
    var lastProcessedBarcode by remember { mutableStateOf<String?>(null) }
    var lastProcessedAt by remember { mutableStateOf(0L) }

    // Dispose ViewModel when screen leaves to prevent memory leaks
    DisposableEffect(Unit) {
        onDispose {
            viewModel.close()
        }
    }

    // Trigger data reload when screen appears (only if needed)
    LaunchedEffect(Unit) {
        viewModel.refreshDataIfNeeded()
    }

    // Hardware
    val cashDrawer = remember { DesktopCashDrawer() }
    val printer = remember { DesktopReceiptPrinter() }

    // Collect state from ViewModel with performance optimizations
    // SINGLE UI STATE COLLECTOR for maximum performance
    val uiState by viewModel.uiState.collectAsState()
    
    // Destructure UI state for easier access
    val categories = uiState.categories
    val selectedCategory = uiState.selectedCategory
    val allProducts = uiState.allProducts
    val products = uiState.filteredProducts
    val searchTerm = uiState.searchTerm
    val customers = uiState.customers
    val paymentMethods = uiState.paymentMethods
    val selectedCustomer = uiState.selectedCustomer
    val selectedPaymentMethod = uiState.selectedPaymentMethod
    val cart = uiState.cart
    val discountType = uiState.discountType
    val discountValue = uiState.discountValue
    val isLoading = uiState.isLoading
    val error = uiState.error
    val currentRegister = uiState.currentRegister
    val registers = uiState.registers
    val registerHistory = uiState.registerHistory
    val editingOrderId = uiState.editingOrderId
    val editingOrderLabel = uiState.editingOrderLabel
    val editingPaymentStatus = uiState.editingPaymentStatus
    val isSubmitting = uiState.isSubmitting
    val requiresPaymentSelection = editingPaymentStatus?.lowercase() !in setOf("hold", "unpaid")
    val requiresCustomerForContainers = selectedCustomer == null && cart.any {
        it.containerTrackingEnabled || (it.hasContainerMetadata && it.containerLink != null)
    }

    LaunchedEffect(orderToEdit?.id) {
        val request = orderToEdit?.request ?: return@LaunchedEffect
        viewModel.loadOrderForEditing(
            orderId = orderToEdit.id,
            serverOrderId = orderToEdit.serverId,
            orderLabel = orderToEdit.displayId,
            request = request
        )
        currentMode = POSMode.SALE
        onOrderEditHandled?.invoke()
    }
    
    // Performance optimization: derivedStateOf for expensive computations
    val visibleProducts by remember(products) {
        derivedStateOf {
            products.take(100) // Limit visible products for performance
        }
    }

    // Dialog states - using proper var declarations
    var showAddProductDialog by remember { mutableStateOf<AddProductDialogState?>(null) }
    var showCustomerDropdown by remember { mutableStateOf(false) }
    var showPaymentDropdown by remember { mutableStateOf(false) }
    var showMessage by remember { mutableStateOf<String?>(null) }
    var printReceipt by remember { mutableStateOf(false) }
    var showDiscountInput by remember { mutableStateOf(false) }
    var showQuickProductDialog by remember { mutableStateOf(false) }
    var pendingProduct by remember { mutableStateOf<PendingProductState?>(null) }
    var showPriceInput by remember { mutableStateOf<CartItem?>(null) }
    var showContainerTrackingDialog by remember { mutableStateOf<CartItem?>(null) }
    var showHoldOrderDialog by remember { mutableStateOf(false) }
    var holdOrderTitle by remember { mutableStateOf("") }

    // Register dialog states
    var showRegisterDialog by remember { mutableStateOf(false) }
    var showOpenRegisterDialog by remember { mutableStateOf<Register?>(null) }
    var showCloseRegisterDialog by remember { mutableStateOf(false) }
    var showCashInDialog by remember { mutableStateOf(false) }
    var showCashOutDialog by remember { mutableStateOf(false) }
    var showRegisterHistoryDialog by remember { mutableStateOf(false) }
    var showRegisterQuickMenu by remember { mutableStateOf(false) }
    var isRegisterDialogShown by remember { mutableStateOf(false) }
    val popularProductClicks = remember {
        mutableStateMapOf<Long, Int>().apply {
            putAll(appSettings.getPopularProductClicks())
        }
    }

    // Cash operation states
    var cashAmount by remember { mutableStateOf("") }
    var cashDescription by remember { mutableStateOf("") }

    // Quick product dialog fields
    var quickProductName by remember { mutableStateOf("") }
    var quickProductQuantity by remember { mutableStateOf("1") }
    var quickProductPrice by remember { mutableStateOf("") }

    
    // Helper function to update message
    val updateMessage: (String?) -> Unit = { message ->
        showMessage = message
    }

    // Helper function to refocus scanner
    val refocusScanner: () -> Unit = {
        scope.launch {
            kotlinx.coroutines.delay(100)
            try {
                hiddenFieldFocusRequester.requestFocus()
                println("[Focus] Scanner field refocused")
            } catch (e: Exception) {
                println("[Focus] Failed to refocus: ${e.message}")
            }
        }
    }

    val registerQuickSelection: (Product) -> Unit = { product ->
        val nextCount = (popularProductClicks[product.id] ?: 0) + 1
        popularProductClicks[product.id] = nextCount
        appSettings.incrementPopularProductClick(product.id)
    }

    val openAddProductDialog: (Product, com.nexopos.shared.models.UnitQuantity?, Double) -> Unit =
        { product, preferredUnitQuantity, initialQuantity ->
            val selectedUnitQuantity = resolveInitialUnitQuantity(product, preferredUnitQuantity)
            if (selectedUnitQuantity == null) {
                showMessage = "Aucune unité de vente configurée pour ${product.name}"
            } else {
                pendingProduct = null
                showAddProductDialog = AddProductDialogState(
                    product = product,
                    selectedUnitQuantityId = selectedUnitQuantity.id,
                    initialQuantity = initialQuantity
                )
                currentMode = POSMode.MODAL
            }
        }

    val quickProducts by remember(allProducts, popularProductClicks.toMap()) {
        derivedStateOf {
            val rankedProducts = popularProductClicks.entries
                .sortedByDescending { it.value }
                .mapNotNull { entry ->
                    allProducts.firstOrNull { it.id == entry.key }
                }
            val fallbackProducts = allProducts
                .filter { it.getDefaultUnitQuantity() != null }
                .take(20)
            (rankedProducts + fallbackProducts)
                .distinctBy { it.id }
                .take(20)
        }
    }

    val quickAccessCategoryId = appSettings.quickAccessCategoryId
    val quickAccessCategory = categories.firstOrNull { it.id == quickAccessCategoryId }
    val quickAccessProducts by remember(allProducts, quickAccessCategoryId, popularProductClicks.toMap()) {
        derivedStateOf {
            if (quickAccessCategoryId != 0L) {
                val clickMap = popularProductClicks.toMap()
                allProducts
                    .asSequence()
                    .filter { it.categoryId == quickAccessCategoryId }
                    .sortedWith(
                        compareByDescending<Product> { clickMap[it.id] ?: 0 }
                            .thenBy { it.name.lowercase() }
                    )
                    .toList()
            } else {
                emptyList()
            }
        }
    }
    val quickAccessPageSize = 30
    var quickAccessPage by remember { mutableStateOf(0) }
    val quickAccessPageCount = remember(quickAccessProducts) {
        if (quickAccessProducts.isNotEmpty()) {
            (quickAccessProducts.size + quickAccessPageSize - 1) / quickAccessPageSize
        } else {
            0
        }
    }
    LaunchedEffect(quickAccessCategoryId, quickAccessProducts.size) {
        quickAccessPage = 0
    }
    if (quickAccessPageCount > 0 && quickAccessPage >= quickAccessPageCount) {
        quickAccessPage = quickAccessPageCount - 1
    }
    val isQuickAccessCategoryActive = quickAccessCategoryId != 0L && quickAccessProducts.isNotEmpty()
    val quickAccessRowProducts = if (isQuickAccessCategoryActive) {
        val start = quickAccessPage * quickAccessPageSize
        quickAccessProducts.drop(start).take(quickAccessPageSize)
    } else {
        quickProducts
    }
    val quickAccessRowTitle = if (isQuickAccessCategoryActive) {
        quickAccessCategory?.name ?: "Produits rapides"
    } else {
        "Produits rapides"
    }
    val quickAccessRowSubtitle = if (isQuickAccessCategoryActive) {
        "Accès rapide (catégorie)"
    } else {
        "Accès rapide caisse"
    }
    val showQuickAccessPagination = isQuickAccessCategoryActive && quickAccessPageCount > 1

    // Show register selection dialog on startup if no register is open
    LaunchedEffect(currentRegister, registers) {
        if (currentRegister == null && registers.isNotEmpty() && !isRegisterDialogShown) {
            // Small delay to let UI load
            kotlinx.coroutines.delay(500)
            showRegisterDialog = true
            isRegisterDialogShown = true
            currentMode = POSMode.MODAL
            println("[Register] Showing register selection dialog")
        }
    }

    // Process barcode input (extracted to function for reuse)
    val processBarcodeInput: (String) -> Unit = { barcode ->
        println("[Barcode] Processing: $barcode (Mode: $currentMode)")

        // Only process scanner input in SALE mode
        if (currentMode == POSMode.SALE) {
            viewModel.searchByBarcode(barcode) { product ->
                if (product != null) {
                    // ROBUST: Use validation pattern for barcode-scanned products
                    // For scanned products, skip quantity dialog and add directly
                    when (val validation = viewModel.validateProduct(product)) {
                        is POSViewModel.ProductValidation.NeedsVariation -> {
                            openAddProductDialog(product, null, 1.0)
                        }
                        is POSViewModel.ProductValidation.NeedsPrice -> {
                            // Can't add without price
                            showMessage = "Le produit ${product.name} n'a pas de prix"
                        }
                        is POSViewModel.ProductValidation.NeedsQuantityDialog,
                        is POSViewModel.ProductValidation.ReadyToAdd -> {
                            // Auto-add to cart with quantity 1.0 (scanned products skip quantity dialog)
                            val uq = when (validation) {
                                is POSViewModel.ProductValidation.NeedsQuantityDialog -> validation.unitQuantity
                                is POSViewModel.ProductValidation.ReadyToAdd -> validation.unitQuantity
                                else -> null
                            }
                            if (uq != null) {
                                viewModel.confirmAddToCart(product, uq, quantity = 1.0)
                                // Visual highlight: Set last scanned product
                                lastScannedProductId = product.id.toString()
                                // Clear highlight after 2 seconds
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    lastScannedProductId = null
                                }
                                println("[Barcode] ✓ Product added to cart: ${product.name}")
                                refocusScanner()
                            }
                        }
                    }
                } else {
                    showMessage = "Produit non trouvé: $barcode"
                    println("[Barcode] Product not found for barcode: $barcode")
                    refocusScanner()
                }
            }
        } else {
            println("[Barcode] Ignored - not in SALE mode (current: $currentMode)")
        }
    }

    // Guard against duplicate barcode triggers (Enter + text change, or partial scans)
    val normalizeBarcode: (String) -> String = { raw ->
        val trimmed = raw.trim()
        val stripped = trimmed.dropWhile { it == '+' || it == '-' }
        if (stripped.isNotBlank() && stripped.all(Char::isDigit)) stripped else trimmed
    }

    val processBarcodeOnce: (String) -> Unit = { raw ->
        val barcode = normalizeBarcode(raw)
        if (barcode.length >= 3) {
            val now = System.currentTimeMillis()
            if (barcode == lastProcessedBarcode && now - lastProcessedAt < 500L) {
                println("[Barcode] Duplicate ignored: $barcode")
            } else {
                lastProcessedBarcode = barcode
                lastProcessedAt = now
                processBarcodeInput(barcode)
            }
        }
    }

    // Auto-focus hidden field when no other input is focused (in SALE mode only)
    // Clear field when leaving SALE mode
    LaunchedEffect(currentMode) {
        if (currentMode == POSMode.SALE && !hiddenFieldHasFocus) {
            kotlinx.coroutines.delay(100) // Small delay to avoid focus conflicts
            try {
                hiddenFieldFocusRequester.requestFocus()
                println("[HiddenField] Auto-focused for SALE mode")
            } catch (e: Exception) {
                println("[Barcode] Could not focus hidden field: ${e.message}")
            }
        } else if (currentMode != POSMode.SALE) {
            // Clear hidden field when not in SALE mode
            hiddenBarcodeInput = TextFieldValue("")
            println("[HiddenField] Cleared - not in SALE mode (current: $currentMode)")
        }
    }

    // Error snackbar
    LaunchedEffect(error) {
        if (error != null) {
            showMessage = error
        }
    }

       // Submit order function for reuse
    val doSubmitOrder: () -> Unit = {
        run {
            if (currentRegister == null) {
                showMessage = "Veuillez d'abord ouvrir une caisse"
                showRegisterDialog = true
                currentMode = POSMode.MODAL
                return@run
            }
        
        
        if (cart.isNotEmpty() && selectedCustomer != null && selectedPaymentMethod != null) {
            // Capture cart data BEFORE submitting (cart gets cleared on success)
            val cartSnapshot = cart.toList()
            val customerSnapshot = selectedCustomer
            val paymentSnapshot = selectedPaymentMethod
            val discountTypeSnapshot = discountType
            val discountValueSnapshot = discountValue
            val shouldPrint = printReceipt

            viewModel.submitOrder(
                onSuccess = {
                    scope.launch {
                        cashDrawer.open()
                        if (shouldPrint) {
                            val subtotal = cartSnapshot.sumOf { it.total }
                            val discountAmt = if (discountTypeSnapshot == DiscountType.Percent) {
                                subtotal * (discountValueSnapshot / 100.0)
                            } else {
                                discountValueSnapshot
                            }.coerceAtMost(subtotal)
                            val total = subtotal - discountAmt
                            val taxIncluded = total - (total / 1.19)

                            // Format timestamp
                            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date())

                            // Get store name from settings
                            val storeName = com.nexopos.desktop.core.prefs.AppSettings.getInstance().storeName
                                .takeIf { it.isNotBlank() } ?: "NexoPOS"

                            val receipt = Receipt(
                                storeName = storeName,
                                timestamp = timestamp,
                                customerName = customerSnapshot?.getDisplayName(),
                                items = cartSnapshot.map {
                                    ReceiptItem(
                                        name = it.product.name,
                                        quantity = it.quantity,
                                        price = String.format("%.3f DT", it.effectivePrice),
                                        total = String.format("%.3f DT", it.total),
                                        unitName = it.unitName
                                    )
                                },
                                subtotal = String.format("%.3f DT", subtotal),
                                discount = if (discountAmt > 0) String.format("%.3f DT", discountAmt) else null,
                                tax = String.format("%.3f DT", taxIncluded),
                                total = String.format("%.3f DT", total),
                                paymentMethod = paymentSnapshot?.label ?: "Espèces",
                                footer = "Merci de votre visite!"
                            )
                            printer.printReceipt(receipt)
                        }
                        showMessage = "Commande validée!"
                        currentMode = POSMode.SALE // Return to SALE mode after success
                        refocusScanner()
                    }
                },
                onError = { errorMsg ->
                    showMessage = errorMsg
                    currentMode = POSMode.SALE // Return to SALE mode on error
                    refocusScanner()
                }
            )
        }}
    }

    val beginManualProductSelection: (Product, com.nexopos.shared.models.UnitQuantity?) -> Unit = { product, preferredUnitQuantity ->
        val unitQuantity = resolveInitialUnitQuantity(product, preferredUnitQuantity)
        if (unitQuantity == null) {
            showMessage = "Aucune unité de vente configurée pour ${product.name}"
        } else {
            registerQuickSelection(product)
            openAddProductDialog(product, unitQuantity, 1.0)
            lastScannedProductId = product.id.toString()
            scope.launch {
                delay(1500)
                if (lastScannedProductId == product.id.toString()) {
                    lastScannedProductId = null
                }
            }
        }
    }

    // Main container - MODE-AWARE keyboard handling
    ModalNavigationDrawer(
        drawerState = utilityDrawerState,
        drawerContent = {
            PosUtilityDrawer(
                currentRegister = currentRegister,
                onNavigateToOrders = {
                    scope.launch { utilityDrawerState.close() }
                    onNavigateToOrders()
                },
                onReceiveContainers = {
                    scope.launch { utilityDrawerState.close() }
                    onNavigateToReceiveContainers()
                },
                onRegister = {
                    scope.launch { utilityDrawerState.close() }
                    showRegisterDialog = true
                    currentMode = POSMode.MODAL
                },
                onRefreshData = {
                    scope.launch { utilityDrawerState.close() }
                    viewModel.refreshData()
                    refocusScanner()
                },
                onNavigateToSettings = {
                    scope.launch { utilityDrawerState.close() }
                    onNavigateToSettings()
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        println("[Keyboard] Mode: $currentMode, Key: ${event.key}")
                        when (currentMode) {
                            POSMode.SALE -> handleSaleModeKeys(
                                event = event,
                                shortcutsManager = shortcutsManager,
                                scope = scope,
                                cashDrawer = cashDrawer,
                                printReceipt = printReceipt,
                                showMessage = updateMessage,
                                doSubmitOrder = doSubmitOrder,
                                viewModel = viewModel,
                                cart = cart,
                                setShowDiscountInput = { showDiscountInput = it },
                                setShowQuickProductDialog = { showQuickProductDialog = it },
                                showAddProductDialog = showAddProductDialog,
                                onNavigateToOrders = onNavigateToOrders,
                                setCurrentMode = { currentMode = it },
                                selectedCustomer = selectedCustomer,
                                selectedPaymentMethod = selectedPaymentMethod,
                                hiddenFieldFocusRequester = hiddenFieldFocusRequester,
                                setPrintReceipt = { printReceipt = it }
                            )
                            POSMode.PAYMENT -> handlePaymentModeKeys(event)
                            POSMode.MODAL -> handleModalModeKeys(
                                event = event,
                                setShowDiscountInput = { showDiscountInput = it },
                                setShowAddProductDialog = { showAddProductDialog = it },
                                setShowPriceInput = { showPriceInput = it },
                                setPendingProduct = { pendingProduct = it },
                                setCurrentMode = { currentMode = it },
                                showDiscountInput = showDiscountInput,
                                showAddProductDialog = showAddProductDialog,
                                showPriceInput = showPriceInput,
                                hiddenFieldFocusRequester = hiddenFieldFocusRequester,
                                scope = scope
                            )
                        }
                    } else false
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (currentMode == POSMode.SALE) {
                            refocusScanner()
                        }
                    }
                )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val leftPaneWidth = maxWidth
                    val productColumns = when {
                        leftPaneWidth >= 700.dp -> 6
                        leftPaneWidth >= 470.dp -> 5
                        else -> 4
                    }
                    val gridSpacing = if (leftPaneWidth >= 560.dp) 6.dp else 5.dp
                    val panePadding = if (leftPaneWidth >= 560.dp) 16.dp else 12.dp

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(panePadding)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    scope.launch {
                                        if (utilityDrawerState.isClosed) {
                                            utilityDrawerState.open()
                                        } else {
                                            utilityDrawerState.close()
                                        }
                                    }
                                },
                                modifier = Modifier.focusable(false),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Default.Menu, "Ouvrir le menu")
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    scope.launch {
                                        cashDrawer.open()
                                        refocusScanner()
                                    }
                                },
                                modifier = Modifier.focusable(false),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(Icons.Default.Inbox, "Tiroir-caisse")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Box {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (currentRegister == null) {
                                            showRegisterDialog = true
                                        } else {
                                            showRegisterQuickMenu = true
                                        }
                                        currentMode = POSMode.MODAL
                                    },
                                    modifier = Modifier.focusable(false),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = if (currentRegister == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        if (currentRegister == null) Icons.Default.PointOfSale else Icons.Default.CheckCircle,
                                        "Caisse"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showRegisterQuickMenu && currentRegister != null,
                                    onDismissRequest = {
                                        showRegisterQuickMenu = false
                                        currentMode = POSMode.SALE
                                        refocusScanner()
                                    },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 2.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    currentRegister?.let { register ->
                                        DropdownMenuItem(
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(register.name, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        "Solde : ${String.format("%.3f DT", register.balance)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {},
                                            enabled = false,
                                            leadingIcon = {
                                                Icon(Icons.Default.PointOfSale, contentDescription = null)
                                            }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        DropdownMenuItem(
                                            text = { Text("Historique de caisse") },
                                            onClick = {
                                                showRegisterQuickMenu = false
                                                showRegisterHistoryDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.History, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Entrée de caisse") },
                                            onClick = {
                                                showRegisterQuickMenu = false
                                                showCashInDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.CallReceived, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Sortie de caisse") },
                                            onClick = {
                                                showRegisterQuickMenu = false
                                                showCashOutDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.CallMade, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Fermer la caisse") },
                                            onClick = {
                                                showRegisterQuickMenu = false
                                                showCloseRegisterDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Lock, contentDescription = null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Changer de caisse") },
                                            onClick = {
                                                showRegisterQuickMenu = false
                                                showRegisterDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                            }
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                            ) {
                                Text(
                                    "VENTE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(modifier = Modifier.height(0.dp).fillMaxWidth()) {
                            BasicTextField(
                                value = hiddenBarcodeInput,
                                enabled = currentMode == POSMode.SALE,
                                onValueChange = { newValue ->
                                    if (currentMode != POSMode.SALE) return@BasicTextField

                                    val newText = newValue.text
                                    if (newText.contains('\n') || newText.contains('\r')) {
                                        val barcode = newText.replace("\n", "").replace("\r", "").trim()
                                        if (barcode.length >= 3) {
                                            processBarcodeOnce(barcode)
                                            hiddenBarcodeInput = TextFieldValue("")
                                        }
                                    } else {
                                        hiddenBarcodeInput = newValue
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(hiddenFieldFocusRequester)
                                    .onFocusChanged { focusState ->
                                        hiddenFieldHasFocus = focusState.isFocused
                                    }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                        ) {
                                            val barcode = hiddenBarcodeInput.text.trim()
                                            if (barcode.length >= 3) {
                                                processBarcodeOnce(barcode)
                                                hiddenBarcodeInput = TextFieldValue("")
                                                true
                                            } else false
                                        } else false
                                    },
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = searchTerm,
                            onValueChange = { input -> viewModel.setSearchTerm(input) },
                            placeholder = { Text("Rechercher des produits (manuel)") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (searchTerm.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            viewModel.setSearchTerm("")
                                            refocusScanner()
                                        },
                                        modifier = Modifier.focusable(false)
                                    ) {
                                        Icon(Icons.Default.Clear, "Effacer")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (categories.isNotEmpty()) {
                            LaunchedEffect(categories) {
                                if (selectedCategory == null && categories.isNotEmpty()) {
                                    viewModel.selectCategory(categories.first())
                                }
                            }

                            val currentCategory = selectedCategory
                            val tabIndex = categories.indexOfFirst { it.id == currentCategory?.id }.coerceAtLeast(0)

                            ScrollableTabRow(
                                selectedTabIndex = tabIndex,
                                modifier = Modifier.fillMaxWidth(),
                                edgePadding = 0.dp
                            ) {
                                categories.forEach { category ->
                                    Tab(
                                        selected = selectedCategory?.id == category.id,
                                        onClick = {
                                            viewModel.selectCategory(category)
                                            refocusScanner()
                                        },
                                        text = { Text(category.name, maxLines = 1) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (quickAccessRowProducts.isNotEmpty()) {
                            QuickProductsRow(
                                title = quickAccessRowTitle,
                                subtitle = quickAccessRowSubtitle,
                                showAddButton = !isQuickAccessCategoryActive,
                                showPagination = showQuickAccessPagination,
                                pageIndex = quickAccessPage,
                                pageCount = quickAccessPageCount,
                                onPrevPage = {
                                    if (quickAccessPage > 0) {
                                        quickAccessPage -= 1
                                    }
                                },
                                onNextPage = {
                                    if (quickAccessPage + 1 < quickAccessPageCount) {
                                        quickAccessPage += 1
                                    }
                                },
                                products = quickAccessRowProducts,
                                onProductClick = { product ->
                                    beginManualProductSelection(product, null)
                                },
                                onOpenQuickProductDialog = {
                                    if (!isQuickAccessCategoryActive) {
                                        showQuickProductDialog = true
                                        currentMode = POSMode.MODAL
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(16.dp))
                                    Text("Chargement des données du serveur...")
                                }
                            }
                        } else if (products.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text("Aucun produit chargé", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Ouvrez les paramètres ou rafraîchissez les données depuis le menu.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(productColumns),
                                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                                verticalArrangement = Arrangement.spacedBy(gridSpacing),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(
                                    items = products.take(180),
                                    key = { it.id.toString() }
                                ) { product ->
                                    ProductCard(
                                        product = product,
                                        onClick = { beginManualProductSelection(product, null) },
                                        skillLevel = skillLevel,
                                        isLastScanned = (product.id.toString() == lastScannedProductId)
                                    )
                                }
                            }
                        }
                    }
                }

                CartPanel(
                cart = cart,
                discountType = discountType,
                discountValue = discountValue,
                printReceipt = printReceipt,
                editingOrderId = editingOrderId,
                editingOrderLabel = editingOrderLabel,
                isSubmitting = isSubmitting,
                requiresCustomerForContainers = requiresCustomerForContainers,
                requiresPaymentSelection = requiresPaymentSelection,
                selectedCustomer = selectedCustomer,
                selectedPaymentMethod = selectedPaymentMethod,
                customers = customers,
                paymentMethods = paymentMethods,
                currentRegister = currentRegister,
                onCustomerSelect = { 
                    viewModel.selectCustomer(it)
                    refocusScanner()
                },
                onPaymentMethodSelect = { 
                    viewModel.selectPaymentMethod(it)
                    refocusScanner()
                },
                onDiscountTypeChange = { 
                    viewModel.setDiscountType(it)
                    refocusScanner()
                },
                onDiscountValueChange = { 
                    viewModel.setDiscountValue(it)
                    refocusScanner()
                },
                onPrintToggle = { 
                    printReceipt = it
                    refocusScanner()
                },
                onToggleWholesale = { 
                    viewModel.toggleItemWholesale(it)
                    refocusScanner()
                },
                onIncreaseQuantity = { 
                    viewModel.increaseQuantity(it)
                    refocusScanner()
                },
                onDecreaseQuantity = { 
                    viewModel.decreaseQuantity(it)
                    refocusScanner()
                },
                onRemove = { 
                    viewModel.removeFromCart(it.key)
                    refocusScanner()
                },
                onEditContainer = { item ->
                    showContainerTrackingDialog = item
                    currentMode = POSMode.MODAL
                },
                onClearCart = { 
                    viewModel.clearCart()
                    refocusScanner()
                },
                onCancelEdit = {
                    viewModel.clearEditMode()
                    refocusScanner()
                },
                onSubmitHold = {
                    if (currentRegister == null) {
                        showMessage = "Veuillez d'abord ouvrir une caisse"
                        showRegisterDialog = true
                        currentMode = POSMode.MODAL
                    } else if (cart.isEmpty()) {
                        showMessage = "Le panier est vide"
                    } else if (requiresCustomerForContainers) {
                        showMessage = "Sélectionnez un client avant de valider les contenants suivis."
                    } else {
                        showHoldOrderDialog = true
                        currentMode = POSMode.MODAL
                    }
                },
                onSubmitUnpaid = {
                    if (currentRegister == null) {
                        showMessage = "Veuillez d'abord ouvrir une caisse"
                        showRegisterDialog = true
                        currentMode = POSMode.MODAL
                    } else if (cart.isEmpty()) {
                        showMessage = "Le panier est vide"
                    } else if (requiresCustomerForContainers) {
                        showMessage = "Sélectionnez un client avant de valider les contenants suivis."
                    } else {
                        viewModel.submitOrderUnpaid(
                            onSuccess = {
                                currentMode = POSMode.SALE
                                showMessage = "Commande impayée enregistrée"
                                refocusScanner()
                            },
                            onError = { errorMessage ->
                                currentMode = POSMode.SALE
                                showMessage = errorMessage
                                refocusScanner()
                            }
                        )
                    }
                },
                onSubmitOrder = {
                    if (currentRegister == null) {
                        showMessage = "Veuillez d'abord ouvrir une caisse"
                        showRegisterDialog = true
                        currentMode = POSMode.MODAL
                    } else if (cart.isNotEmpty() && selectedCustomer != null && (selectedPaymentMethod != null || !requiresPaymentSelection) && !requiresCustomerForContainers) {
                        currentMode = POSMode.PAYMENT
                    } else {
                        showMessage = if (requiresCustomerForContainers) {
                            "Sélectionnez un client avant de valider les contenants suivis."
                        } else {
                            "Panier, client ou mode de paiement manquant"
                        }
                    }
                },
                onEditItem = { item ->
                    showPriceInput = item
                    currentMode = POSMode.MODAL
                }
            )
            }

            // Snackbar for messages
        showMessage?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .widthIn(max = 400.dp),
                action = {
                    TextButton(
                        onClick = { 
                            showMessage = null
                            refocusScanner()
                        },
                        modifier = Modifier.focusable(false)
                    ) {
                        Text("OK")
                    }
                }
            ) {
                Text(message)
            }

            LaunchedEffect(showMessage) {
                kotlinx.coroutines.delay(3000)
                showMessage = null
                refocusScanner()
            }
        }

        // DIALOGS - All set POSMode.MODAL when open

        // Discount input dialog
        if (showDiscountInput) {
            var tempDiscount by remember(showDiscountInput, discountValue, discountType) {
                mutableStateOf(
                    if (discountValue == 0.0) "" else {
                        if (discountType == DiscountType.Percent) {
                            discountValue.toString()
                        } else {
                            String.format(java.util.Locale.US, "%.3f", discountValue)
                                .trimEnd('0')
                                .trimEnd('.')
                        }
                    }
                )
            }
            val discountFocusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                discountFocusRequester.requestFocus()
            }

            AlertDialog(
                onDismissRequest = {
                    showDiscountInput = false
                    currentMode = POSMode.SALE
                    refocusScanner()
                },
                title = { Text("Remise (${if (discountType == DiscountType.Percent) "%" else "DT"})") },
                text = {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tempDiscount,
                                onValueChange = { tempDiscount = it.filter { c -> c.isDigit() || c == '.' } },
                                placeholder = { Text("Entrez la remise") },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(discountFocusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                            viewModel.setDiscountValue(tempDiscount.toDoubleOrNull() ?: 0.0)
                                            showDiscountInput = false
                                            currentMode = POSMode.SALE
                                            refocusScanner()
                                            true
                                        } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                            showDiscountInput = false
                                            currentMode = POSMode.SALE
                                            refocusScanner()
                                            true
                                        } else false
                                    },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            TextButton(
                                onClick = {
                                    viewModel.setDiscountType(
                                        if (discountType == DiscountType.Percent) DiscountType.Amount else DiscountType.Percent
                                    )
                                },
                                modifier = Modifier.focusable(false)
                            ) {
                                Text(
                                    if (discountType == DiscountType.Percent) "%" else "DT",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.setDiscountValue(tempDiscount.toDoubleOrNull() ?: 0.0)
                            showDiscountInput = false
                            currentMode = POSMode.SALE
                            refocusScanner()
                        },
                        modifier = Modifier.focusable(false)
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDiscountInput = false
                            currentMode = POSMode.SALE
                            refocusScanner()
                        },
                        modifier = Modifier.focusable(false)
                    ) {
                        Text("Annuler")
                    }
                }
            )
        }

        // Unified add-product dialog
        if (showAddProductDialog != null) {
            val dialogState = showAddProductDialog!!
            AddProductDialog(
                state = dialogState,
                onDismiss = {
                    showAddProductDialog = null
                    pendingProduct = null
                    currentMode = POSMode.SALE
                    refocusScanner()
                },
                onConfirm = { unitQuantity, newQty, useWholesale ->
                    if (newQty > 0) {
                        if (unitQuantity.effectivePrice <= 0.0) {
                            pendingProduct = PendingProductState(
                                product = dialogState.product,
                                unitQuantity = unitQuantity,
                                needsPrice = true
                            )
                            showAddProductDialog = null
                            showPriceInput = CartItem(
                                key = "temp_${System.currentTimeMillis()}",
                                product = dialogState.product,
                                quantity = newQty,
                                unitPrice = 0.0,
                                wholesalePrice = unitQuantity.wholesalePriceWithTax,
                                isWholesale = useWholesale,
                                unitQuantityId = unitQuantity.id,
                                unitId = unitQuantity.unitId,
                                unitName = unitQuantity.unitName,
                                containerLink = unitQuantity.containerLink,
                                hasContainerMetadata = unitQuantity.containerLink != null
                            )
                        } else {
                            viewModel.confirmAddToCart(
                                product = dialogState.product,
                                unitQuantity = unitQuantity,
                                quantity = newQty
                            )
                            if (useWholesale && (unitQuantity.wholesalePriceWithTax ?: 0.0) > 0.0) {
                                viewModel.cart.value.lastOrNull {
                                    it.product.id == dialogState.product.id &&
                                        it.unitQuantityId == unitQuantity.id
                                }?.takeIf { !it.isWholesale }?.let(viewModel::toggleItemWholesale)
                            }
                            showAddProductDialog = null
                            pendingProduct = null
                            currentMode = POSMode.SALE
                            refocusScanner()
                        }
                    } else {
                        showAddProductDialog = null
                        pendingProduct = null
                        currentMode = POSMode.SALE
                        refocusScanner()
                    }
                }
            )
        }

        // Price input dialog - now merged with quantity dialog above
// This is only used for editing existing cart items
if (showPriceInput != null) {
    val item = showPriceInput!!
    // Store the original prices for reference
    val originalRetailPrice = remember { item.unitPrice }
    val originalWholesalePrice = remember { item.wholesalePrice }
    
    // Track if user manually edited the price (vs using default from toggle)
    var isPriceManuallyEdited by remember { mutableStateOf(false) }
    
    // Track original states
    val originalIsWholesale = remember { item.isWholesale }
    val originalIsCustomPrice = remember { item.isCustomPrice }
    
    // Initialize state with proper price based on wholesale status
    var useWholesale by remember { 
        mutableStateOf(
            // Check if item is wholesale AND has wholesale price
            item.isWholesale && (item.wholesalePrice ?: 0.0) > 0
        )
    }
    
    var tempPrice by remember { 
        mutableStateOf(
            if (item.isWholesale && item.wholesalePrice != null) {
                item.wholesalePrice.toString()
            } else {
                item.unitPrice.toString()
            }
        )
    }
    
    // FIX 1: Change to TextFieldValue for text selection
    var tempQuantity by remember { 
        mutableStateOf(
            TextFieldValue(
                text = item.quantity.toString(),
                selection = TextRange(0, item.quantity.toString().length) // Select all text
            )
        )
    }
    val quantityFocusRequester = remember { FocusRequester() }
    val hasWholesalePrice = (item.wholesalePrice ?: 0.0) > 0

    val confirmEdit: () -> Unit = {
        val newPrice = tempPrice.toDoubleOrNull() ?: 0.0
        // FIX 2: Use .text to get the string value
        val newQty = tempQuantity.text.toDoubleOrNull() ?: 1.0
        
        if (newPrice > 0 && newQty > 0) {
            // Check if this is a new item (from grid) or existing cart item
            if (item.key.startsWith("temp_")) {
                // New item from grid - add to cart
                val pending = pendingProduct
                if (pending != null) {
                    // Calculate the final price
                    var finalPrice = newPrice
                    var shouldUseWholesale = useWholesale && hasWholesalePrice
                    
                    // If user didn't manually edit price and we're using wholesale, 
                    // use the wholesale price
                    if (!isPriceManuallyEdited && shouldUseWholesale) {
                        finalPrice = originalWholesalePrice ?: newPrice
                    } else if (!isPriceManuallyEdited && !shouldUseWholesale) {
                        // If user didn't manually edit and we're using retail, use retail price
                        finalPrice = originalRetailPrice
                    }
                    
                    // Check if price is different from default
                    val isPriceDifferentFromDefault = Math.abs(finalPrice - pending.unitQuantity.effectivePrice) > 0.001
                    
                    // Determine if this should be wholesale
                    val isWholesalePrice = shouldUseWholesale && 
                                          hasWholesalePrice && 
                                          Math.abs(finalPrice - (originalWholesalePrice ?: 0.0)) < 0.001
                    
                    // Add to cart with the calculated price
                    viewModel.confirmAddToCart(
                        product = pending.product,
                        unitQuantity = pending.unitQuantity,
                        quantity = newQty,
                        customPrice = if (isPriceDifferentFromDefault && !isWholesalePrice) finalPrice else null
                    )
                    
                    // After adding to cart, if it's wholesale price, toggle wholesale status
                    if (isWholesalePrice) {
                        // Find the newly added item and toggle wholesale
                        val cartItems = viewModel.cart.value
                        val newlyAddedItem = cartItems.lastOrNull { 
                            it.product.id == pending.product.id && 
                            it.unitQuantityId == pending.unitQuantity.id 
                        }
                        newlyAddedItem?.let { cartItem ->
                            // Toggle to wholesale if not already wholesale
                            if (!cartItem.isWholesale) {
                                viewModel.toggleItemWholesale(cartItem)
                            }
                        }
                    }
                }
            } else {
                // Existing cart item - update
                // Check if wholesale status changed
                if (hasWholesalePrice && useWholesale != originalIsWholesale) {
                    viewModel.toggleItemWholesale(item)
                }
                
                // Update quantity
                viewModel.setQuantity(item, newQty)
                
                // Update price if manually edited OR if price changed
                if (isPriceManuallyEdited || Math.abs(newPrice - item.effectivePrice) > 0.001) {
                    viewModel.setItemPrice(item, newPrice)
                }
            }
        }
        showPriceInput = null
        pendingProduct = null
        currentMode = POSMode.SALE
        refocusScanner()
    }

    LaunchedEffect(Unit) {
        quantityFocusRequester.requestFocus()
        // Optional: Ensure text is selected after focus
        kotlinx.coroutines.delay(50)
        tempQuantity = tempQuantity.copy(selection = TextRange(0, tempQuantity.text.length))
    }

    PosAlertDialog(
        onDismissRequest = {
            showPriceInput = null
            pendingProduct = null
            currentMode = POSMode.SALE
            refocusScanner()
        },
        title = { Text("Modifier - ${item.product.name}", maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // FIX 3: Updated OutlinedTextField with proper onValueChange
                OutlinedTextField(
                    value = tempQuantity,
                    onValueChange = { newValue ->
                        // Filter input to only allow digits and decimal point
                        val filtered = newValue.text.filter { c -> c.isDigit() || c == '.' }
                        val parts = filtered.split('.')
                        val sanitized = if (parts.size > 2) {
                            // If more than one decimal point, keep only the first
                            parts[0] + "." + parts.drop(1).joinToString("")
                        } else filtered
                        // Update with new text but preserve cursor position
                        tempQuantity = TextFieldValue(
                            text = sanitized,
                            selection = newValue.selection
                        )
                    },
                    label = { Text("Quantité") },
                    suffix = { Text(item.unitName ?: "PCS") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(quantityFocusRequester)
                        // FIX 4: Select all text when field gets focus
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                tempQuantity = tempQuantity.copy(selection = TextRange(0, tempQuantity.text.length))
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                confirmEdit()
                                true
                            } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                showPriceInput = null
                                pendingProduct = null
                                currentMode = POSMode.SALE
                                refocusScanner()
                                true
                            } else false
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                OutlinedTextField(
                    value = tempPrice,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { c -> c.isDigit() || c == '.' }
                        val parts = filtered.split('.')
                        val sanitized = if (parts.size > 2) {
                            parts[0] + "." + parts.drop(1).joinToString("")
                        } else filtered
                        tempPrice = sanitized
                        // Mark that user manually edited the price
                        isPriceManuallyEdited = true
                    },
                    label = { Text("Prix unitaire") },
                    suffix = { Text("DT") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                if (hasWholesalePrice) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Prix de gros", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                String.format("%.3f DT", originalWholesalePrice ?: 0.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Switch(
                            checked = useWholesale,
                            onCheckedChange = {
                                useWholesale = it
                                if (it) {
                                    // Switching to wholesale: use wholesale price
                                    tempPrice = (originalWholesalePrice ?: 0.0).toString()
                                } else {
                                    // Switching to retail: use retail price
                                    tempPrice = originalRetailPrice.toString()
                                }
                                // Reset manual edit flag when toggling
                                isPriceManuallyEdited = false
                            },
                            enabled = hasWholesalePrice // Only enable if wholesale price exists
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { confirmEdit() },
                modifier = Modifier.focusable(false)
            ) {
                Text("Mettre à jour")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    showPriceInput = null
                    pendingProduct = null
                    currentMode = POSMode.SALE
                    refocusScanner()
                },
                modifier = Modifier.focusable(false)
            ) {
                Text("Annuler")
            }
        }
    )
}

        // Quick product dialog
if (showQuickProductDialog) {
    val nameFocusRequester = remember { FocusRequester() }
    // FIX: Change quantity to TextFieldValue for selection
    var quickProductQuantity by remember { 
        mutableStateOf(
            TextFieldValue(
                text = "1",
                selection = TextRange(0, 1) // Select all text (just "1")
            )
        )
    }

    val resetAndClose: () -> Unit = {
        quickProductName = ""
        quickProductQuantity = TextFieldValue(
            text = "1",
            selection = TextRange(0, 1)
        )
        quickProductPrice = ""
        showQuickProductDialog = false
        currentMode = POSMode.SALE
        refocusScanner()
    }

    val confirmQuickProduct: () -> Unit = {
        val name = quickProductName.trim()
        // FIX: Use .text to get string value
        val qty = quickProductQuantity.text.toDoubleOrNull() ?: 0.0
        val price = quickProductPrice.toDoubleOrNull() ?: 0.0
        if (name.isNotEmpty() && qty > 0 && price >= 0) {
            viewModel.addQuickProduct(name, qty, price)
            showMessage = "Produit ajouté: $name"
            resetAndClose()
        } else {
            showMessage = "Veuillez remplir tous les champs"
        }
    }

    LaunchedEffect(Unit) {
        // Focus quantity field instead of name field
        kotlinx.coroutines.delay(50)
        // No focus requester needed - just auto-select the quantity text
        // Re-select text after a short delay
        quickProductQuantity = quickProductQuantity.copy(selection = TextRange(0, quickProductQuantity.text.length))
    }

    PosAlertDialog(
        onDismissRequest = { resetAndClose() },
        title = { Text("Produit rapide") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quickProductName,
                    onValueChange = { quickProductName = it },
                    label = { Text("Nom du produit") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                    singleLine = true
                )
                
                // FIX: Updated Quantity field with TextFieldValue
                OutlinedTextField(
                    value = quickProductQuantity,
                    onValueChange = { newValue ->
                        val filtered = newValue.text.filter { c -> c.isDigit() || c == '.' }
                        val parts = filtered.split('.')
                        val sanitized = if (parts.size > 2) {
                            parts[0] + "." + parts.drop(1).joinToString("")
                        } else filtered
                        quickProductQuantity = TextFieldValue(
                            text = sanitized,
                            selection = newValue.selection
                        )
                    },
                    label = { Text("Quantité") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                // Select all text when field gets focus
                                quickProductQuantity = quickProductQuantity.copy(
                                    selection = TextRange(0, quickProductQuantity.text.length)
                                )
                            }
                        },
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = quickProductPrice,
                    onValueChange = { value ->
                        val filtered = value.filter { c -> c.isDigit() || c == '.' }
                        val parts = filtered.split('.')
                        quickProductPrice = if (parts.size > 2) {
                            parts[0] + "." + parts.drop(1).joinToString("")
                        } else filtered
                    },
                    label = { Text("Prix unitaire") },
                    suffix = { Text("DT") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                confirmQuickProduct()
                                true
                            } else false
                        },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirmQuickProduct() },
                modifier = Modifier.focusable(false)
            ) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { resetAndClose() },
                modifier = Modifier.focusable(false)
            ) {
                Text("Annuler")
            }
        }
    )
}

        showContainerTrackingDialog?.let { cartItem ->
            ContainerTrackingDialog(
                item = cartItem,
                onDismiss = {
                    showContainerTrackingDialog = null
                    currentMode = POSMode.SALE
                    refocusScanner()
                },
                onApply = { enabled, quantityOverride ->
                    viewModel.updateContainerTracking(cartItem.key, enabled, quantityOverride)
                    showContainerTrackingDialog = null
                    currentMode = POSMode.SALE
                    refocusScanner()
                }
            )
        }

        if (showHoldOrderDialog) {
            HoldOrderDialog(
                title = holdOrderTitle,
                onTitleChange = { holdOrderTitle = it },
                onDismiss = {
                    showHoldOrderDialog = false
                    holdOrderTitle = ""
                    currentMode = POSMode.SALE
                    refocusScanner()
                },
                onConfirm = {
                    val trimmedTitle = holdOrderTitle.trim()
                    if (trimmedTitle.isNotEmpty()) {
                        viewModel.submitOrderHold(
                            title = trimmedTitle,
                            onSuccess = {
                                showHoldOrderDialog = false
                                holdOrderTitle = ""
                                currentMode = POSMode.SALE
                                showMessage = "Commande mise en attente"
                                refocusScanner()
                            },
                            onError = { errorMessage ->
                                showHoldOrderDialog = false
                                holdOrderTitle = ""
                                currentMode = POSMode.SALE
                                showMessage = errorMessage
                                refocusScanner()
                            }
                        )
                    }
                },
                isSubmitting = isSubmitting
            )
        }

        // PAYMENT MODAL - Hard lock for payment processing
        if (currentMode == POSMode.PAYMENT) {
            PaymentModal(
                cart = cart,
                discountType = discountType,
                discountValue = discountValue,
                selectedCustomer = selectedCustomer,
                selectedPaymentMethod = selectedPaymentMethod,
                printReceipt = printReceipt,
                isSubmitting = isSubmitting,
                onDismiss = {
                    currentMode = POSMode.SALE
                    updateMessage("Paiement annulé")
                    refocusScanner()
                },
                onSubmitOrder = doSubmitOrder,
                scope = scope,
                cashDrawer = cashDrawer,
                printer = printer,
                showMessageCallback = updateMessage
            )
        }

        // --- REGISTER DIALOGS ---

        // 1. Register Selection Dialog
        if (showRegisterDialog) {
            RegisterSelectionDialog(
                registers = registers,
                currentRegister = currentRegister,
                onRegisterSelected = { register ->
                    showRegisterDialog = false
                    showOpenRegisterDialog = register
                    currentMode = POSMode.MODAL
                },
                onDismiss = {
                    // Can't dismiss - must select a register if none is open
                    if (currentRegister == null) {
                        showMessage = "Vous devez sélectionner une caisse pour continuer"
                        showRegisterDialog = true
                    } else {
                        showRegisterDialog = false
                        currentMode = POSMode.SALE
                        refocusScanner()
                    }
                }
            )
        }

        // 2. Open Register Dialog (amount input)
        if (showOpenRegisterDialog != null) {
            val register = showOpenRegisterDialog!!
            var amount by remember(register.id) { mutableStateOf("") }
            var description by remember(register.id) { mutableStateOf("") }
            var isOpeningRegister by remember(register.id) { mutableStateOf(false) }
            var openRegisterError by remember(register.id) { mutableStateOf<String?>(null) }
            val focusRequester = remember { FocusRequester() }

            val submitOpenRegister: () -> Unit = {
                val amountValue = amount.toDoubleOrNull() ?: 0.0
                if (amountValue <= 0 || isOpeningRegister) {
                    if (amountValue <= 0) {
                        openRegisterError = "Saisissez un montant d'ouverture valide."
                    }
                    Unit
                } else {
                    isOpeningRegister = true
                    openRegisterError = null
                    viewModel.openRegister(register.id, amountValue, description) { result ->
                        isOpeningRegister = false
                        result
                            .onSuccess {
                                showOpenRegisterDialog = null
                                currentMode = POSMode.SALE
                                showMessage = "Caisse ouverte"
                                refocusScanner()
                            }
                            .onFailure { throwable ->
                                openRegisterError = throwable.message ?: "Impossible d'ouvrir la caisse."
                                currentMode = POSMode.MODAL
                            }
                    }
                }
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            AlertDialog(
                onDismissRequest = {
                    // Can't dismiss - must open a register
                },
                modifier = Modifier.widthIn(min = 500.dp, max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                title = {
                    RegisterDialogHeader(
                        icon = Icons.Default.PointOfSale,
                        title = "Ouvrir la caisse",
                        subtitle = register.name
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        openRegisterError?.let { message ->
                            RegisterDialogError(message)
                        }

                        OutlinedTextField(
                            value = amount,
                            onValueChange = { input ->
                                val filtered = input.filter { c -> c.isDigit() || c == '.' }
                                val parts = filtered.split('.')
                                val sanitized = if (parts.size > 2) {
                                    parts[0] + "." + parts.drop(1).joinToString("")
                                } else filtered
                                amount = sanitized
                            },
                            label = { Text("Montant d'ouverture") },
                            suffix = { Text("DT") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                        submitOpenRegister()
                                        true
                                    } else false
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !isOpeningRegister
                        )
                        
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (optionnelle)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isOpeningRegister,
                            supportingText = {
                                Text("Seules les caisses fermées peuvent être ouvertes.")
                            }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = submitOpenRegister,
                        enabled = !isOpeningRegister && amount.isNotEmpty() && ((amount.toDoubleOrNull() ?: 0.0) > 0.0),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PosTheme.Success,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isOpeningRegister) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Ouvrir la caisse")
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            // Go back to register selection
                            showOpenRegisterDialog = null
                            showRegisterDialog = true
                        },
                        enabled = !isOpeningRegister
                    ) {
                        Text("Retour")
                    }
                }
            )
        }

        // 3. Close Register Dialog
        if (showCloseRegisterDialog) {
            var amount by remember(currentRegister?.id) { mutableStateOf("") }
            var description by remember(currentRegister?.id) { mutableStateOf("") }
            var isClosingRegister by remember(currentRegister?.id) { mutableStateOf(false) }
            var closeRegisterError by remember(currentRegister?.id) { mutableStateOf<String?>(null) }
            val currentReg = currentRegister
            if (currentReg != null) {
                val submitCloseRegister: () -> Unit = {
                    val amountValue = amount.toDoubleOrNull() ?: 0.0
                    if (amountValue <= 0 || isClosingRegister) {
                        if (amountValue <= 0) {
                            closeRegisterError = "Saisissez un montant de clôture valide."
                        }
                    } else {
                        isClosingRegister = true
                        closeRegisterError = null
                        viewModel.closeRegister(amountValue, description) { result ->
                            isClosingRegister = false
                            result
                                .onSuccess {
                                    showCloseRegisterDialog = false
                                    currentMode = POSMode.SALE
                                    showMessage = "Caisse fermée"
                                    refocusScanner()
                                }
                                .onFailure { throwable ->
                                    closeRegisterError = throwable.message ?: "Impossible de fermer la caisse."
                                    currentMode = POSMode.MODAL
                                }
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = {
                        if (!isClosingRegister) {
                            showCloseRegisterDialog = false
                            currentMode = POSMode.SALE
                            refocusScanner()
                        }
                    },
                    modifier = Modifier.widthIn(min = 500.dp, max = 560.dp),
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    title = {
                        RegisterDialogHeader(
                            icon = Icons.Default.Lock,
                            title = "Fermer la caisse",
                            subtitle = currentReg.name
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            closeRegisterError?.let { message ->
                                RegisterDialogError(message)
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Solde actuel",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        String.format("%.3f DT", currentReg.balance),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PosTheme.Success
                                    )
                                }
                            }
                        
                            OutlinedTextField(
                            value = amount,
                            onValueChange = { input ->
                                val filtered = input.filter { c -> c.isDigit() || c == '.' }
                                val parts = filtered.split('.')
                                val sanitized = if (parts.size > 2) {
                                    parts[0] + "." + parts.drop(1).joinToString("")
                                } else filtered
                                amount = sanitized
                            },
                            label = { Text("Montant de clôture") },
                            suffix = { Text("DT") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !isClosingRegister
                        )
                        
                            OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (optionnelle)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isClosingRegister
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = submitCloseRegister,
                        enabled = !isClosingRegister && amount.isNotEmpty() && ((amount.toDoubleOrNull() ?: 0.0) > 0.0),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        if (isClosingRegister) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Fermer la caisse")
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showCloseRegisterDialog = false
                            currentMode = POSMode.SALE
                            refocusScanner()
                        },
                        enabled = !isClosingRegister
                    ) {
                        Text("Annuler")
                    }
                }
            )
            }
        }

        // 4. Cash In Dialog
        if (showCashInDialog) {
            var isCashInSubmitting by remember { mutableStateOf(false) }
            var cashInError by remember { mutableStateOf<String?>(null) }
            val submitCashIn: () -> Unit = {
                val amountValue = cashAmount.toDoubleOrNull() ?: 0.0
                if (amountValue <= 0 || isCashInSubmitting) {
                    if (amountValue <= 0) {
                        cashInError = "Saisissez un montant valide."
                    }
                } else {
                    isCashInSubmitting = true
                    cashInError = null
                    viewModel.cashIn(amountValue, cashDescription) { result ->
                        isCashInSubmitting = false
                        result
                            .onSuccess {
                                showCashInDialog = false
                                cashAmount = ""
                                cashDescription = ""
                                currentMode = POSMode.SALE
                                showMessage = "Entrée de caisse effectuée"
                                refocusScanner()
                            }
                            .onFailure { throwable ->
                                cashInError = throwable.message ?: "Impossible d'enregistrer l'entrée de caisse."
                                currentMode = POSMode.MODAL
                            }
                    }
                }
            }
            AlertDialog(
                onDismissRequest = {
                    if (!isCashInSubmitting) {
                        showCashInDialog = false
                        currentMode = POSMode.SALE
                        cashAmount = ""
                        cashDescription = ""
                        refocusScanner()
                    }
                },
                modifier = Modifier.widthIn(min = 500.dp, max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                title = {
                    RegisterDialogHeader(
                        icon = Icons.Default.CallReceived,
                        title = "Entrée de caisse",
                        subtitle = "Ajouter des fonds à la caisse active"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        cashInError?.let { message ->
                            RegisterDialogError(message)
                        }

                        OutlinedTextField(
                            value = cashAmount,
                            onValueChange = { input ->
                                val filtered = input.filter { c -> c.isDigit() || c == '.' }
                                val parts = filtered.split('.')
                                val sanitized = if (parts.size > 2) {
                                    parts[0] + "." + parts.drop(1).joinToString("")
                                } else filtered
                                cashAmount = sanitized
                            },
                            label = { Text("Montant") },
                            suffix = { Text("DT") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !isCashInSubmitting
                        )
                        
                        OutlinedTextField(
                            value = cashDescription,
                            onValueChange = { cashDescription = it },
                            label = { Text("Description (optionnelle)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isCashInSubmitting
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = submitCashIn,
                        enabled = !isCashInSubmitting && cashAmount.isNotEmpty() && ((cashAmount.toDoubleOrNull() ?: 0.0) > 0.0),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PosTheme.Success,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isCashInSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Entrée de caisse")
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showCashInDialog = false
                            currentMode = POSMode.SALE
                            cashAmount = ""
                            cashDescription = ""
                            refocusScanner()
                        },
                        enabled = !isCashInSubmitting
                    ) {
                        Text("Annuler")
                    }
                }
            )
        }

        // 5. Cash Out Dialog
        if (showCashOutDialog) {
            var isCashOutSubmitting by remember { mutableStateOf(false) }
            var cashOutError by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = {
                    if (!isCashOutSubmitting) {
                        showCashOutDialog = false
                        currentMode = POSMode.SALE
                        cashAmount = ""
                        cashDescription = ""
                        refocusScanner()
                    }
                },
                modifier = Modifier.widthIn(min = 500.dp, max = 560.dp),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                title = {
                    RegisterDialogHeader(
                        icon = Icons.Default.CallMade,
                        title = "Sortie de caisse",
                        subtitle = "Retirer des fonds de la caisse active"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val currentReg = currentRegister
                        if (currentReg != null) {
                            cashOutError?.let { message ->
                                RegisterDialogError(message)
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Solde disponible",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        String.format("%.3f DT", currentReg.balance),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PosTheme.Success
                                    )
                                }
                            }
                            
                            OutlinedTextField(
                                value = cashAmount,
                                onValueChange = { input ->
                                    val filtered = input.filter { c -> c.isDigit() || c == '.' }
                                    val parts = filtered.split('.')
                                    val sanitized = if (parts.size > 2) {
                                        parts[0] + "." + parts.drop(1).joinToString("")
                                    } else filtered
                                    cashAmount = sanitized
                                },
                                label = { Text("Montant") },
                                suffix = { Text("DT") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                enabled = !isCashOutSubmitting
                            )
                            
                            OutlinedTextField(
                                value = cashDescription,
                                onValueChange = { cashDescription = it },
                                label = { Text("Description (optionnelle)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !isCashOutSubmitting
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amountValue = cashAmount.toDoubleOrNull() ?: 0.0
                            val currentReg = currentRegister
                            when {
                                isCashOutSubmitting -> Unit
                                amountValue <= 0 -> cashOutError = "Saisissez un montant valide."
                                currentReg == null -> cashOutError = "Aucune caisse ouverte."
                                amountValue > currentReg.balance -> cashOutError = "Solde insuffisant"
                                else -> {
                                    isCashOutSubmitting = true
                                    cashOutError = null
                                    viewModel.cashOut(amountValue, cashDescription) { result ->
                                        isCashOutSubmitting = false
                                        result
                                            .onSuccess {
                                                showCashOutDialog = false
                                                cashAmount = ""
                                                cashDescription = ""
                                                currentMode = POSMode.SALE
                                                showMessage = "Sortie de caisse effectuée"
                                                refocusScanner()
                                            }
                                            .onFailure { throwable ->
                                                cashOutError = throwable.message ?: "Impossible d'enregistrer la sortie de caisse."
                                                currentMode = POSMode.MODAL
                                            }
                                    }
                                }
                            }
                        },
                        enabled = !isCashOutSubmitting && cashAmount.isNotEmpty() && ((cashAmount.toDoubleOrNull() ?: 0.0) > 0.0),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        if (isCashOutSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Sortie de caisse")
                        }
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showCashOutDialog = false
                            currentMode = POSMode.SALE
                            cashAmount = ""
                            cashDescription = ""
                            refocusScanner()
                        },
                        enabled = !isCashOutSubmitting
                    ) {
                        Text("Annuler")
                    }
                }
            )
        }

        // 6. Register History Dialog
        if (showRegisterHistoryDialog) {
            RegisterHistoryDialog(
                history = registerHistory,
                onDismiss = {
                    showRegisterHistoryDialog = false
                    currentMode = POSMode.SALE
                    refocusScanner()
                }
            )
        }
    }
}
}


@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    skillLevel: CashierSkillLevel = CashierSkillLevel.EXPERT,
    isLastScanned: Boolean = false,
    modifier: Modifier = Modifier
) {
    val price by remember(product) {
        derivedStateOf { product.getPrice() }
    }
    val defaultUnit by remember(product) {
        derivedStateOf { product.getDefaultUnitQuantity() }
    }
    val unitLabel = defaultUnit?.unitName?.takeIf { !it.isNullOrBlank() }
    val priceLabel = remember(price, unitLabel) {
        when {
            price <= 0 -> "Prix a definir"
            unitLabel != null -> String.format("%.3f DT/%s", price, unitLabel)
            else -> String.format("%.3f DT", price)
        }
    }
    val titleText = remember(product) { product.name.trim() }
    val titleStyle = when {
        skillLevel == CashierSkillLevel.BEGINNER -> MaterialTheme.typography.titleMedium
        titleText.length > 18 -> MaterialTheme.typography.labelLarge
        else -> MaterialTheme.typography.titleSmall
    }
    val titleLineHeight = when {
        skillLevel == CashierSkillLevel.BEGINNER -> 20.sp
        titleText.length > 18 -> 16.sp
        else -> 17.sp
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (skillLevel == CashierSkillLevel.BEGINNER) 104.dp else 92.dp)
            .clickable(onClick = onClick)
            .focusable(false),
        colors = CardDefaults.cardColors(
            containerColor = if (isLastScanned)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isLastScanned)
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = PosTheme.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                titleText,
                style = titleStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = titleLineHeight,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp)
            )
            Text(
                priceLabel,
                style = if (skillLevel == CashierSkillLevel.BEGINNER)
                    MaterialTheme.typography.titleMedium
                else
                    MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun QuickProductsRow(
    title: String,
    subtitle: String,
    showAddButton: Boolean,
    showPagination: Boolean,
    pageIndex: Int,
    pageCount: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    onOpenQuickProductDialog: () -> Unit
) {
    val quickProducts = products
    val rowCount = 3
    val itemsPerRow = remember(quickProducts) {
        ((quickProducts.size + rowCount - 1) / rowCount).coerceAtLeast(1)
    }
    val rows = remember(quickProducts, itemsPerRow) {
        quickProducts.chunked(itemsPerRow).take(rowCount)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showPagination) {
                        Text(
                            "${pageIndex + 1}/$pageCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onPrevPage,
                            enabled = pageIndex > 0,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("<")
                        }
                        OutlinedButton(
                            onClick = onNextPage,
                            enabled = pageIndex + 1 < pageCount,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(">")
                        }
                    }
                    if (showAddButton) {
                        FilledIconButton(
                            onClick = onOpenQuickProductDialog,
                            modifier = Modifier.size(34.dp).focusable(false),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, "Ajouter un produit rapide", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            rows.filter { it.isNotEmpty() }.forEach { rowProducts ->
                val rowState = rememberLazyListState()
                val hasOverflowToRight by remember(rowProducts, rowState) {
                    derivedStateOf {
                        val lastVisibleIndex = rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        lastVisibleIndex in 0 until rowProducts.lastIndex
                    }
                }

                Box(modifier = Modifier.fillMaxWidth())
                {
                    LazyRow(
                        state = rowState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(end = 22.dp)
                    ) {
                        items(rowProducts, key = { it.id }) { product ->
                            AssistChip(
                                onClick = { onProductClick(product) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                label = {
                                    QuickProductNameLabel(product.name)
                                }
                            )
                        }
                    }

                    if (hasOverflowToRight) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(36.dp)
                                .height(40.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickProductNameLabel(name: String) {
    val displayName = if (name.length > 15) {
        name.take(12) + "..."
    } else {
        name
    }

    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(
            alignment = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 8.dp)
        )
    ) {
        Text(
            text = displayName,
            maxLines = 1
        )
    }
}

@Composable
fun PosUtilityDrawer(
    currentRegister: Register?,
    onNavigateToOrders: () -> Unit,
    onReceiveContainers: () -> Unit,
    onRegister: () -> Unit,
    onRefreshData: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Menu utilitaire", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    currentRegister?.let { "Caisse : ${it.name}" } ?: "Aucune caisse ouverte",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DrawerSection(
                title = "Ventes",
                items = listOf(
                    DrawerItemState("Commandes", Icons.Default.ListAlt, onNavigateToOrders),
                    DrawerItemState("Réception des contenants", Icons.Default.Inventory2, onReceiveContainers)
                )
            )
            DrawerSection(
                title = "Caisse",
                items = listOf(
                    DrawerItemState("Caisse", Icons.Default.PointOfSale, onRegister)
                )
            )
            DrawerSection(
                title = "Système",
                items = listOf(
                    DrawerItemState("Rafraîchir les données", Icons.Default.Refresh, onRefreshData),
                    DrawerItemState("Paramètres", Icons.Default.Settings, onNavigateToSettings)
                )
            )
        }
    }
}

private data class DrawerItemState(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun DrawerSection(
    title: String,
    items: List<DrawerItemState>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.label) },
                selected = false,
                onClick = item.onClick,
                icon = { Icon(item.icon, null) },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun RowScope.CartPanel(
    cart: List<CartItem>,
    discountType: DiscountType,
    discountValue: Double,
    printReceipt: Boolean,
    editingOrderId: Long?,
    editingOrderLabel: String?,
    isSubmitting: Boolean,
    requiresCustomerForContainers: Boolean,
    requiresPaymentSelection: Boolean,
    selectedCustomer: Customer?,
    selectedPaymentMethod: PaymentMethod?,
    customers: List<Customer>,
    paymentMethods: List<PaymentMethod>,
    currentRegister: Register?,
    onCustomerSelect: (Customer) -> Unit,
    onPaymentMethodSelect: (PaymentMethod) -> Unit,
    onDiscountTypeChange: (DiscountType) -> Unit,
    onDiscountValueChange: (Double) -> Unit,
    onPrintToggle: (Boolean) -> Unit,
    onToggleWholesale: (CartItem) -> Unit,
    onIncreaseQuantity: (CartItem) -> Unit,
    onDecreaseQuantity: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit,
    onEditContainer: (CartItem) -> Unit,
    onClearCart: () -> Unit,
    onCancelEdit: () -> Unit,
    onSubmitHold: () -> Unit,
    onSubmitUnpaid: () -> Unit,
    onSubmitOrder: () -> Unit,
    onEditItem: (CartItem) -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp)
    ) {
        // COMPACT Customer & Payment Section
        var showCustomerDropdown by remember { mutableStateOf(false) }
        var showPaymentDropdown by remember { mutableStateOf(false) }

        if (editingOrderId != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modification de commande",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = editingOrderLabel ?: "#$editingOrderId",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    TextButton(onClick = onCancelEdit) {
                        Text("Annuler la modification")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (requiresCustomerForContainers) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Les contenants suivis nécessitent un client sélectionné.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        val cartListState = rememberLazyListState()
        val hasHiddenCartItemsBelow by remember(cart, cartListState) {
            derivedStateOf {
                val lastVisibleIndex = cartListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                cart.isNotEmpty() && lastVisibleIndex in 0 until cart.lastIndex
            }
        }
        // Cart items with moderate spacing
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = cartListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                items(cart) { item ->
                    CartItemRow(
                        item = item,
                        onIncrease = { onIncreaseQuantity(item) },
                        onDecrease = { onDecreaseQuantity(item) },
                        onRemove = { onRemove(item) },
                        onToggleWholesale = { onToggleWholesale(item) },
                        onEdit = { onEditItem(item) },
                        onEditContainer = { onEditContainer(item) }
                    )
                }
            }

            if (cart.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Aucun article",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Ajoutez un produit pour commencer la vente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (hasHiddenCartItemsBelow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                                )
                            )
                        )
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Plus d'articles en dessous",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Totals - VAT is inclusive (prices already include VAT)
        val subtotal = cart.sumOf { it.total }
        val discountAmount = if (discountType == DiscountType.Percent) {
            subtotal * (discountValue / 100.0)
        } else {
            discountValue
        }.coerceAtMost(subtotal)
        val total = subtotal - discountAmount
        val taxIncluded = total - (total / 1.19)
        val trackedContainerCount = cart
            .mapNotNull { it.effectiveContainerQuantity }
            .sum()
        val payLabel = if (editingOrderId != null) {
            "Payer maj ${String.format("%.3f DT", total)}"
        } else {
            "Payer ${String.format("%.3f DT", total)}"
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Subtotal
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sous-total",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        String.format("%.3f DT", subtotal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Discount row with inline edit
                if (discountAmount > 0 || discountValue > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Remise",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                TextButton(
                                    onClick = { onDiscountTypeChange(if (discountType == DiscountType.Percent) DiscountType.Amount else DiscountType.Percent) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.focusable(false)
                                ) {
                                    Text(
                                        if (discountType == DiscountType.Percent) "%" else "DT",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Text(
                            String.format("-%.3f DT", discountAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // VAT
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TVA (19% incl.)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        String.format("%.3f DT", taxIncluded),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                )

                // Total - Extra large and prominent
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TOTAL",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        String.format("%.3f DT", total),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (trackedContainerCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Containers suivis",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                trackedContainerCount.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = showCustomerDropdown,
                onExpandedChange = { showCustomerDropdown = it },
                modifier = Modifier.weight(1f)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .focusable(false),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedCustomer?.getDisplayName() ?: "Client comptoir",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "Client",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                ExposedDropdownMenu(
                    expanded = showCustomerDropdown,
                    onDismissRequest = { showCustomerDropdown = false }
                ) {
                    customers.forEach { customer ->
                        DropdownMenuItem(
                            text = { Text(customer.getDisplayName()) },
                            onClick = {
                                onCustomerSelect(customer)
                                showCustomerDropdown = false
                            }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = showPaymentDropdown,
                onExpandedChange = { showPaymentDropdown = it },
                modifier = Modifier.weight(1f)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .focusable(false),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedPaymentMethod?.label ?: selectedPaymentMethod?.identifier ?: "Espèces",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "Paiement",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                ExposedDropdownMenu(
                    expanded = showPaymentDropdown,
                    onDismissRequest = { showPaymentDropdown = false }
                ) {
                    paymentMethods.forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method.label ?: method.identifier) },
                            onClick = {
                                onPaymentMethodSelect(method)
                                showPaymentDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onClearCart,
                enabled = cart.isNotEmpty(),
                modifier = Modifier.focusable(false),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
            }

            // Print toggle
            IconButton(
                onClick = { onPrintToggle(!printReceipt) },
                modifier = Modifier.focusable(false)
            ) {
                Icon(
                    if (printReceipt) Icons.Default.Print else Icons.Default.PrintDisabled,
                    contentDescription = "Imprimer le reçu",
                    tint = if (printReceipt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onSubmitOrder,
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                enabled = cart.isNotEmpty() &&
                    selectedCustomer != null &&
                    !isSubmitting &&
                    !requiresCustomerForContainers &&
                    (selectedPaymentMethod != null || !requiresPaymentSelection) &&
                    currentRegister != null
                ,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.Payments, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    payLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSubmitHold,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = cart.isNotEmpty() && currentRegister != null && !isSubmitting && !requiresCustomerForContainers,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("En attente")
            }
            OutlinedButton(
                onClick = onSubmitUnpaid,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = cart.isNotEmpty() && currentRegister != null && !isSubmitting && !requiresCustomerForContainers,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Impayé")
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onToggleWholesale: () -> Unit,
    onEdit: () -> Unit,
    onEditContainer: () -> Unit
) {
    val hasWholesale = item.wholesalePrice != null && item.wholesalePrice > 0
    val resolvedContainerLink = remember(item) {
        item.containerLink ?: item.unitQuantityId?.let(item.product::getUnitQuantity)?.containerLink
    }
    val hasContainerSupport = remember(item, resolvedContainerLink) {
        resolvedContainerLink != null || item.containerTrackingEnabled || item.hasContainerMetadata
    }
    val requiredContainerQuantity = remember(item, resolvedContainerLink) {
        val link = resolvedContainerLink
        if (link == null || link.capacity <= 0.0) {
            null
        } else {
            kotlin.math.floor(item.quantity / link.capacity).toInt().coerceAtLeast(0)
        }
    }
    val effectiveContainerQuantity = remember(item, requiredContainerQuantity) {
        if (!item.containerTrackingEnabled) {
            requiredContainerQuantity
        } else {
            item.containerQuantityOverride ?: requiredContainerQuantity
        }
    }
    val titleLabel = remember(item, resolvedContainerLink, effectiveContainerQuantity) {
        buildString {
            append(item.product.name)
            val titleBits = mutableListOf<String>()
            item.unitName?.takeIf { it.isNotBlank() }?.let(titleBits::add)
            if (item.containerTrackingEnabled) {
                resolvedContainerLink?.let { link ->
                    titleBits.add("${link.containerTypeName} x ${effectiveContainerQuantity ?: 0}")
                }
            }
            if (titleBits.isNotEmpty()) {
                append(" (")
                append(titleBits.joinToString(" • "))
                append(")")
            }
        }
    }

    val currentState = remember(item, hasWholesale) {
        when {
            item.isWholesale && hasWholesale -> PriceState.WHOLESALE
            item.isCustomPrice -> PriceState.CUSTOM
            else -> PriceState.RETAIL
        }
    }

    val priceType = when (currentState) {
        PriceState.WHOLESALE -> "Gros"
        PriceState.CUSTOM -> "Spécial"
        PriceState.RETAIL -> "Détail"
    }

    val priceTypeColor = when (currentState) {
        PriceState.WHOLESALE -> PosTheme.PriceWholesale
        PriceState.CUSTOM -> PosTheme.PriceCustom
        PriceState.RETAIL -> PosTheme.PriceRetail
    }

    val onChipClick = {
        when (currentState) {
            PriceState.RETAIL -> {
                if (hasWholesale) {
                    onToggleWholesale()
                }
            }
            PriceState.WHOLESALE -> {
                onToggleWholesale()
            }
            PriceState.CUSTOM -> {
                onToggleWholesale()
            }
        }
    }

    val chipEnabled = hasWholesale || currentState == PriceState.CUSTOM
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .focusable(false),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        titleLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onDecrease,
                        modifier = Modifier.size(36.dp).focusable(false),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        String.format("%.1f", item.quantity),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 42.dp),
                        textAlign = TextAlign.Center
                    )
                    FilledTonalIconButton(
                        onClick = onIncrease,
                        modifier = Modifier.size(36.dp).focusable(false),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        "× ${String.format("%.3f", item.effectivePrice)} DT",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = priceTypeColor.copy(alpha = 0.15f),
                        modifier = Modifier
                            .clickable(
                                enabled = chipEnabled,
                                onClick = onChipClick,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            )
                            .focusable(false)
                    ) {
                        Text(
                            priceType,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = priceTypeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                        )
                    }
                    if (hasContainerSupport) {
                        FilledTonalIconButton(
                            onClick = onEditContainer,
                            modifier = Modifier.size(36.dp).focusable(false),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    FilledIconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(36.dp).focusable(false),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    String.format("%.3f DT", item.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun AddProductDialog(
    state: AddProductDialogState,
    onDismiss: () -> Unit,
    onConfirm: (com.nexopos.shared.models.UnitQuantity, Double, Boolean) -> Unit
) {
    val variations = remember(state.product.id) { state.product.getUnitQuantities() }
    var selectedUnitQuantityId by remember(state.product.id, state.selectedUnitQuantityId) {
        mutableStateOf(state.selectedUnitQuantityId)
    }
    val selectedUnitQuantity = remember(variations, selectedUnitQuantityId) {
        variations.firstOrNull { it.id == selectedUnitQuantityId } ?: variations.firstOrNull()
    }
    val unitName = selectedUnitQuantity?.unitName ?: "PCS"
    val cardsPerRow = remember(variations.size) {
        when {
            variations.size >= 3 -> 3
            variations.size == 2 -> 2
            else -> 1
        }
    }
    val initialQuantityText = remember(state.product.id, state.selectedUnitQuantityId, state.initialQuantity) {
        String.format(java.util.Locale.US, "%.1f", state.initialQuantity)
            .trimEnd('0')
            .trimEnd('.')
            .ifBlank { "1" }
    }
    var quantityField by remember(state.product.id, state.selectedUnitQuantityId, state.initialQuantity) {
        mutableStateOf(
            TextFieldValue(
                text = initialQuantityText,
                selection = TextRange(0, initialQuantityText.length)
            )
        )
    }
    var replaceOnNextKeypadEntry by remember(state.product.id, state.selectedUnitQuantityId, state.initialQuantity) {
        mutableStateOf(true)
    }
    var useWholesale by remember(state.product.id, state.selectedUnitQuantityId) { mutableStateOf(false) }
    val hasWholesalePrice = (selectedUnitQuantity?.wholesalePriceWithTax ?: 0.0) > 0.0
    val focusRequester = remember { FocusRequester() }

    fun sanitizeQuantity(input: String): String {
        val filtered = input.filter { it.isDigit() || it == '.' }
        val parts = filtered.split('.')
        return if (parts.size > 2) {
            parts[0] + "." + parts.drop(1).joinToString("")
        } else {
            filtered
        }
    }

    fun appendKeypad(value: String) {
        val currentText = quantityField.text
        val nextText = when {
            value == "." && currentText.contains('.') -> currentText
            replaceOnNextKeypadEntry && value != "." -> value
            replaceOnNextKeypadEntry && value == "." -> "0."
            currentText == "0" && value != "." -> value
            else -> sanitizeQuantity(currentText + value)
        }
        quantityField = TextFieldValue(
            text = nextText,
            selection = TextRange(nextText.length)
        )
        replaceOnNextKeypadEntry = false
    }

    fun setShortcut(amount: Double) {
        val nextText = String.format(java.util.Locale.US, "%.1f", amount)
            .trimEnd('0')
            .trimEnd('.')
        quantityField = TextFieldValue(
            text = nextText,
            selection = TextRange(nextText.length)
        )
        replaceOnNextKeypadEntry = false
    }

    fun normalizeFirstHardwareEntry(newValue: TextFieldValue): TextFieldValue {
        val sanitized = sanitizeQuantity(newValue.text)
        if (!replaceOnNextKeypadEntry) {
            return newValue.copy(text = sanitized)
        }

        val replacement = when {
            sanitized.isBlank() -> sanitized
            sanitized == initialQuantityText -> sanitized
            sanitized.startsWith(initialQuantityText) && sanitized.length > initialQuantityText.length ->
                sanitized.removePrefix(initialQuantityText)
            else -> sanitized
        }.let { if (it == ".") "0." else it }

        return TextFieldValue(
            text = replacement,
            selection = TextRange(replacement.length)
        )
    }

    LaunchedEffect(state.product.id, state.selectedUnitQuantityId, state.initialQuantity) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(selectedUnitQuantity?.id) {
        if (!hasWholesalePrice) {
            useWholesale = false
        }
    }

    fun formatUnitPrice(
        unitQuantity: com.nexopos.shared.models.UnitQuantity,
        wholesale: Boolean
    ): String {
        val price = if (wholesale && (unitQuantity.wholesalePriceWithTax ?: 0.0) > 0.0) {
            unitQuantity.wholesalePriceWithTax ?: unitQuantity.effectivePrice
        } else {
            unitQuantity.effectivePrice
        }
        return String.format("%.3f DT", price)
    }

    fun submitDialog(): Boolean {
        val quantity = quantityField.text.toDoubleOrNull() ?: 0.0
        val unitQuantity = selectedUnitQuantity ?: return false
        onConfirm(unitQuantity, quantity, useWholesale)
        return true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 560.dp, max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> submitDialog()
                            Key.Escape -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            state.product.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                String.format("%.3f DT/%s", selectedUnitQuantity?.effectivePrice ?: 0.0, unitName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (useWholesale && hasWholesalePrice) {
                                Text(
                                    String.format(
                                        "Gros %.3f DT/%s",
                                        selectedUnitQuantity?.wholesalePriceWithTax ?: 0.0,
                                        unitName
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PosTheme.Success,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (useWholesale) PosTheme.Highlight else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (useWholesale) PosTheme.Success.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Gros",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (useWholesale) PosTheme.Success else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Switch(
                                checked = useWholesale,
                                onCheckedChange = { useWholesale = it },
                                enabled = hasWholesalePrice,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = PosTheme.Success,
                                    checkedBorderColor = PosTheme.Success,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                                    uncheckedTrackColor = PosTheme.Border,
                                    uncheckedBorderColor = PosTheme.Border,
                                    disabledCheckedTrackColor = PosTheme.Success.copy(alpha = 0.35f),
                                    disabledUncheckedTrackColor = PosTheme.Border.copy(alpha = 0.35f)
                                )
                            )
                        }
                    }
                }

                if (variations.size > 1) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Sélectionner une unité",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        variations.chunked(cardsPerRow).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { unitQuantity ->
                                    val isSelected = unitQuantity.id == selectedUnitQuantity?.id
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusable()
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown &&
                                                    event.key == Key.Spacebar
                                                ) {
                                                    selectedUnitQuantityId = unitQuantity.id
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .clickable { selectedUnitQuantityId = unitQuantity.id },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            }
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                unitQuantity.unitName ?: "Unit ${unitQuantity.unitId}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                formatUnitPrice(unitQuantity, useWholesale),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (useWholesale && (unitQuantity.wholesalePriceWithTax ?: 0.0) > 0.0) {
                                                    PosTheme.Success
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                textAlign = TextAlign.Center
                                            )
                                            if (useWholesale && (unitQuantity.wholesalePriceWithTax ?: 0.0) <= 0.0) {
                                                Text(
                                                    text = "Gros indisponible",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = quantityField,
                    onValueChange = {
                        quantityField = normalizeFirstHardwareEntry(it)
                        replaceOnNextKeypadEntry = quantityField.text == initialQuantityText
                    },
                    label = { Text("Quantité") },
                    suffix = { Text(unitName) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                quantityField = quantityField.copy(
                                    selection = TextRange(0, quantityField.text.length)
                                )
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            false
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1.0, 3.0, 5.0, 10.0, 20.0).forEach { amount ->
                        OutlinedButton(
                            onClick = { setShortcut(amount) },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(amount.toInt().toString())
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false,
                    modifier = Modifier.height(220.dp)
                ) {
                    items(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "<")) { key ->
                        FilledTonalButton(
                            onClick = {
                                when (key) {
                                    "<" -> {
                                        val nextText = quantityField.text.dropLast(1).ifBlank { "0" }
                                        quantityField = TextFieldValue(
                                            text = nextText,
                                            selection = TextRange(nextText.length)
                                        )
                                        replaceOnNextKeypadEntry = false
                                    }
                                    else -> appendKeypad(key)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(key, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Annuler")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { submitDialog() },
                        modifier = Modifier.height(46.dp),
                        enabled = selectedUnitQuantity != null
                    ) {
                        Text("Ajouter")
                    }
                }
            }
        }
    }
}

@Composable
fun ContainerTrackingDialog(
    item: CartItem,
    onDismiss: () -> Unit,
    onApply: (enabled: Boolean, quantityOverride: Int?) -> Unit
) {
    val resolvedContainerLink = remember(item) {
        item.containerLink ?: item.unitQuantityId?.let(item.product::getUnitQuantity)?.containerLink
    }
    val requiredContainerQuantity = remember(item, resolvedContainerLink) {
        val link = resolvedContainerLink
        if (link == null || link.capacity <= 0.0) {
            0
        } else {
            kotlin.math.floor(item.quantity / link.capacity).toInt().coerceAtLeast(0)
        }
    }
    var enabled by remember { mutableStateOf(item.containerTrackingEnabled) }
    var quantityText by remember {
        mutableStateOf(item.containerQuantityOverride?.toString().orEmpty())
    }

    PosAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suivi des contenants") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                resolvedContainerLink?.let { link ->
                    Text(
                        text = "Contenant : ${link.containerTypeName} • ${link.capacity} ${link.capacityUnit}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = String.format("Consigne : %.3f DT", link.depositFee),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activer le suivi")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { input ->
                        quantityText = input.filter { it.isDigit() }
                    },
                    enabled = enabled,
                    label = { Text("Quantité de contenants forcée") },
                    placeholder = {
                        Text(requiredContainerQuantity.toString())
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(enabled, quantityText.toIntOrNull())
                }
            ) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun HoldOrderDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSubmitting: Boolean
) {
    PosAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mettre la commande en attente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Les commandes en attente ignorent le paiement et restent modifiables depuis l'historique.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Titre de mise en attente") },
                    placeholder = { Text("Vente comptoir / Retrait client") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = title.trim().isNotEmpty() && !isSubmitting
            ) {
                Text("Mettre en attente")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun PaymentModal(
    cart: List<CartItem>,
    discountType: DiscountType,
    discountValue: Double,
    selectedCustomer: Customer?,
    selectedPaymentMethod: PaymentMethod?,
    printReceipt: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitOrder: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    cashDrawer: com.nexopos.desktop.hardware.DesktopCashDrawer,
    printer: com.nexopos.desktop.hardware.DesktopReceiptPrinter,
    showMessageCallback: (String) -> Unit
) {
    // Payment amount state
    var paymentAmount by remember { mutableStateOf("") }
    val paymentFocusRequester = remember { FocusRequester() }

    // Calculate totals
    val subtotal = cart.sumOf { it.total }
    val discountAmount = if (discountType == DiscountType.Percent) {
        subtotal * (discountValue / 100.0)
    } else {
        discountValue
    }.coerceAtMost(subtotal)
    val total = subtotal - discountAmount
    val change = paymentAmount.toDoubleOrNull()?.let { it - total } ?: 0.0

    // Auto-focus payment field when modal opens
    LaunchedEffect(Unit) {
        paymentFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Payment, null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Traitement du paiement")
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "SCANNER VERROUILLÉ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Order summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Résumé de la commande",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sous-total :")
                            Text(String.format("%.3f DT", subtotal))
                        }

                        if (discountAmount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Remise :")
                                Text(String.format("-%.3f DT", discountAmount), color = MaterialTheme.colorScheme.error)
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total :",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                String.format("%.3f DT", total),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Customer & payment method info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedCustomer?.let { customer ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(customer.getDisplayName(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("Client", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    selectedPaymentMethod?.let { method ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(method.label ?: method.identifier, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("Paiement", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Payment amount input
                OutlinedTextField(
                    value = paymentAmount,
                    onValueChange = { input ->
                        val filtered = input.filter { c -> c.isDigit() || c == '.' }
                        val parts = filtered.split('.')
                        val sanitized = if (parts.size > 2) {
                            parts[0] + "." + parts.drop(1).joinToString("")
                        } else filtered
                        paymentAmount = sanitized
                    },
                    label = { Text("Montant du paiement") },
                    placeholder = { Text(String.format("%.3f", total)) },
                    suffix = { Text("DT") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(paymentFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                // If payment amount is empty or 0, treat as exact payment
                                val paymentValue = paymentAmount.toDoubleOrNull() ?: total
                                if (paymentValue >= total) {
                                    onSubmitOrder()
                                }
                                true
                            } else false
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                // Change calculation
                if (paymentAmount.isNotEmpty()) {
                    val paymentValue = paymentAmount.toDoubleOrNull() ?: 0.0
                    if (paymentValue >= total) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Savings, null, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Monnaie :", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    String.format("%.3f DT", change),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    } else if (paymentValue > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Montant de paiement insuffisant",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(0.9f),
        confirmButton = {
            Button(
                onClick = onSubmitOrder,
                enabled = !isSubmitting && paymentAmount.isNotEmpty() && paymentAmount.toDoubleOrNull() ?: 0.0 >= total,
                modifier = Modifier.focusable(false)
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSubmitting) "Traitement..." else "Terminer le paiement")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.focusable(false)
            ) {
                Icon(Icons.Default.Cancel, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Annuler")
            }
        }
    )
}

// --- REGISTER UI COMPONENTS ---

@Composable
private fun RegisterDialogContainer(
    onDismissRequest: () -> Unit,
    width: Dp = 580.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = width, max = width),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun RegisterDialogHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = PosTheme.Highlight
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PosTheme.Success,
                modifier = Modifier.padding(10.dp).size(20.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RegisterDialogError(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun RegisterStatusPill(status: String) {
    val (containerColor, contentColor) = when (status.lowercase()) {
        "opened" -> PosTheme.Highlight to PosTheme.Success
        "closed" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.16f))
    ) {
        Text(
            formatRegisterStatus(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
fun RegisterSelectionDialog(
    registers: List<Register>,
    currentRegister: Register?,
    onRegisterSelected: (Register) -> Unit,
    onDismiss: () -> Unit
) {
    val filteredRegisters = registers.filter { it.status != "disabled" }
    
    RegisterDialogContainer(onDismissRequest = onDismiss, width = 620.dp) {
        RegisterDialogHeader(
            icon = Icons.Default.PointOfSale,
            title = "Sélectionner une caisse",
            subtitle = "Requis avant de traiter les ventes"
        )

        if (currentRegister != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = PosTheme.Highlight,
                border = BorderStroke(1.dp, PosTheme.Success.copy(alpha = 0.14f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Caisse actuelle",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            currentRegister.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        String.format("%.3f DT", currentRegister.balance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PosTheme.Success
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
        ) {
            if (filteredRegisters.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("Aucune caisse disponible", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Contactez l'administrateur pour créer des caisses.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredRegisters) { register ->
                        val canOpen = register.status == "closed"
                        RegisterCard(
                            register = register,
                            isCurrent = register.id == currentRegister?.id,
                            enabled = canOpen,
                            onClick = { onRegisterSelected(register) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    }
}

@Composable
fun RegisterCard(
    register: Register,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val registerDescription = register.description
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) 
                PosTheme.Highlight
            else if (!enabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isCurrent) 1.5.dp else 1.dp,
            if (isCurrent) PosTheme.Success.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (register.status == "opened") Icons.Default.LockOpen 
                        else Icons.Default.Lock,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = when (register.status) {
                            "opened" -> PosTheme.Success
                            "closed" -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        register.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled || isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RegisterStatusPill(register.status)
                }
                
                if (registerDescription != null && registerDescription.isNotBlank()) {
                    Text(
                        registerDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }

                Text(
                    "Solde : ${String.format("%.3f DT", register.balance)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled || isCurrent) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (!enabled && !isCurrent) {
                    Text(
                        "Cette caisse n'est pas disponible pour une nouvelle ouverture.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isCurrent) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Actuelle",
                    modifier = Modifier.size(24.dp),
                    tint = PosTheme.Success
                )
            } else if (register.status == "closed") {
                Icon(
                    Icons.Default.LockOpen,
                    "Ouvrir",
                    modifier = Modifier.size(24.dp),
                    tint = PosTheme.Success
                )
            } else {
                Icon(
                    Icons.Default.Block,
                    "Indisponible",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RegisterHistoryDialog(
    history: List<RegisterHistory>,
    onDismiss: () -> Unit
) {
    RegisterDialogContainer(onDismissRequest = onDismiss, width = 620.dp) {
        RegisterDialogHeader(
            icon = Icons.Default.History,
            title = "Historique de caisse",
            subtitle = "Mouvements récents de la session"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 430.dp)
        ) {
            if (history.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Aucun historique disponible",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(history) { item ->
                        RegisterHistoryItem(history = item)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PosTheme.Success,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Fermer")
            }
        }
    }
}

@Composable
fun RegisterHistoryItem(history: RegisterHistory) {
    val historyDescription = history.description
    val normalizedAction = normalizeRegisterAction(history.action)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            when (normalizedAction) {
                "register-opening" -> PosTheme.Info.copy(alpha = 0.18f)
                "register-cash-in" -> PosTheme.Success.copy(alpha = 0.18f)
                "register-cash-out" -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                "register-order-payment" -> PosTheme.Warning.copy(alpha = 0.18f)
                "register-order-change", "register-refund" -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                else -> PosTheme.Success.copy(alpha = 0.18f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        getActionLabel(normalizedAction),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (historyDescription != null && historyDescription.isNotBlank()) {
                        Text(
                            historyDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Determine display value and color based on action type
                val (displayValue, color) = when (normalizedAction) {
                    "register-cash-out", "register-order-change" ->
                        Pair(-history.value, MaterialTheme.colorScheme.error)
                    "register-cash-in" ->
                        Pair(history.value, PosTheme.Success) // Use theme green
                    "register-opening" ->
                        Pair(history.value, PosTheme.Info) // Use theme blue
                    "register-order-payment" ->
                        Pair(history.value, PosTheme.Warning) // Use theme amber
                    else ->
                        Pair(history.value, PosTheme.Success) // Use theme green
                }
                
                // Determine sign based on action type
                val sign = when (normalizedAction) {
                    "register-cash-out", "register-order-change" -> "-" // show negative sign
                    "register-opening" -> "" // no sign for opening
                    "register-cash-in", "register-order-payment" -> "+" // cash in and order payments get +
                    else -> "+" // default to positive
                }
                
                Text(
                    "$sign${String.format("%.3f DT", abs(displayValue))}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Text(
                history.createdAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getActionLabel(action: String): String {
    return when (normalizeRegisterAction(action)) {
        "register-opening" -> "Ouverture"
        "register-closing" -> "Fermeture"
        "register-cash-in" -> "Entrée de caisse"
        "register-cash-out" -> "Sortie de caisse"
        "register-order-payment" -> "Paiement de commande"
        "register-order-change" -> "Rendu de commande"
        "register-refund" -> "Remboursement"
        else -> action.replace("register-", "").replace("-", " ").titlecase()
    }
}

private fun normalizeRegisterAction(action: String): String {
    val normalized = action
        .lowercase()
        .trim()
        .replace("_", "-")
        .replace(" ", "-")
        .replace(Regex("-+"), "-")
    return when (normalized) {
        "initial", "opening", "open", "register-opening" -> "register-opening"
        "closing", "close", "register-closing" -> "register-closing"
        "cashin", "cash-in", "register-cash-in" -> "register-cash-in"
        "cashout", "cash-out", "register-cash-out" -> "register-cash-out"
        "orderpayment", "order-payment", "order-pay", "register-order-payment" -> "register-order-payment"
        "orderchange", "order-change", "register-order-change" -> "register-order-change"
        "refund", "register-refund" -> "register-refund"
        else -> normalized
    }
}

private fun formatRegisterStatus(status: String): String {
    return when (status.lowercase()) {
        "opened" -> "Ouverte"
        "closed" -> "Fermée"
        "disabled" -> "Désactivée"
        else -> status.replaceFirstChar { char -> char.uppercase() }
    }
}

private fun String.titlecase(): String {
    return this.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}

@Composable
fun RegisterFAB(
    register: Register,
    onClose: () -> Unit,
    onCashIn: () -> Unit,
    onCashOut: () -> Unit,
    onHistory: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .wrapContentSize()
    ) {
        if (expanded) {
            Card(
                modifier = Modifier.padding(top = 72.dp, end = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PointOfSale, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(register.name, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = when (register.status) {
                                "opened" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        ) {
                            Text(formatRegisterStatus(register.status), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    
                    Text("Solde : ${String.format("%.3f DT", register.balance)}")
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onHistory,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.History, "Historique")
                        }
                        IconButton(
                            onClick = onCashIn,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.CallReceived, "Entrée de caisse")
                        }
                        IconButton(
                            onClick = onCashOut,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.CallMade, "Sortie de caisse")
                        }
                        FilledTonalButton(
                            onClick = onClose,
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
        
        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(56.dp),
            containerColor = if (register.status == "opened") 
                MaterialTheme.colorScheme.primary 
                else MaterialTheme.colorScheme.secondary
        ) {
            Icon(
                if (expanded) Icons.Default.Close else Icons.Default.PointOfSale,
                "Caisse",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
