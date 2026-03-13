package com.nexopos.desktop.ui.pos

import com.nexopos.desktop.core.repo.hasVariations
import com.nexopos.desktop.core.repo.getPrice
import com.nexopos.desktop.core.repo.getUnitQuantities
import com.nexopos.desktop.core.repo.getDisplayName
import com.nexopos.desktop.core.repo.getUnitQuantity
import com.nexopos.shared.models.Customer
import com.nexopos.shared.models.PaymentMethod
import com.nexopos.shared.models.Product
import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.compose.ui.graphics.Color
import com.nexopos.desktop.core.repo.ProductEntity
import com.nexopos.desktop.hardware.DesktopBarcodeScanner
import com.nexopos.desktop.hardware.DesktopCashDrawer
import com.nexopos.desktop.hardware.DesktopReceiptPrinter
import com.nexopos.shared.hardware.Receipt
import com.nexopos.shared.hardware.ReceiptItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

// Unified theme colors for consistent POS UI
object PosTheme {
    // Semantic colors
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    val Info = Color(0xFF2196F3)
    val Highlight = Color(0xFFFFD700)
    
    // Status colors
    val StatusOpen = Color(0xFF4CAF50)
    val StatusClosed = Color(0xFFF44336)
    val StatusDisabled = Color(0xFF9E9E9E)
    
    // Price state colors
    val PriceRetail = Color(0xFF1976D2) // Material Blue
    val PriceWholesale = Color(0xFF9C27B0)
    val PriceCustom = Color(0xFFFF5722)
    
    // Spacing constants
    val SpacingTight = 4.dp
    val SpacingCompact = 8.dp
    val SpacingNormal = 16.dp
    val SpacingWide = 24.dp
    
    // Elevation constants
    val ElevationLow = 2.dp
    val ElevationMedium = 4.dp
    val ElevationHigh = 8.dp
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

// ROBUST: Quantity dialog state for proper focus management
data class QuantityDialogState(
    val product: Product,
    val unitQuantity: com.nexopos.shared.models.UnitQuantity,
    val initialQuantity: Double = 1.0
)

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
    showVariationPicker: Product?,
    onNavigateToOrders: () -> Unit,
    setCurrentMode: (POSMode) -> Unit,
    selectedCustomer: Customer?,
    selectedPaymentMethod: PaymentMethod?,
    barcodeScanner: DesktopBarcodeScanner,
    hiddenFieldFocusRequester: FocusRequester,
    setPrintReceipt: (Boolean) -> Unit
): Boolean {
    // Only handle when no dialogs are open
    if (showVariationPicker != null) {
        return false
    }

    // CRITICAL: Feed keyboard input to barcode scanner first
    // Scanner will consume Enter key if it has accumulated a barcode
    val scannerConsumed = barcodeScanner.processKeyEvent(event)
    if (scannerConsumed) {
        return true // Event consumed by scanner, don't process shortcuts
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
    showMessage(if (newValue) "Impression: ON" else "Impression: OFF")
    true
}
        com.nexopos.desktop.core.settings.ShortcutAction.SUBMIT_ORDER -> {
            if (cart.isNotEmpty() && selectedCustomer != null && selectedPaymentMethod != null) {
                // Directly switch to payment without extra message
                setCurrentMode(POSMode.PAYMENT)
                true
            } else {
                val missing = mutableListOf<String>()
                if (cart.isEmpty()) missing.add("cart")
                if (selectedCustomer == null) missing.add("customer")
                if (selectedPaymentMethod == null) missing.add("payment method")
                showMessage("Missing: ${missing.joinToString(", ")}")
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
    setShowQuantityDialog: (QuantityDialogState?) -> Unit,
    setShowVariationPicker: (Product?) -> Unit,
    setShowPriceInput: (CartItem?) -> Unit,
    setPendingProduct: (PendingProductState?) -> Unit,
    setCurrentMode: (POSMode) -> Unit,
    showDiscountInput: Boolean,
    showQuantityDialog: QuantityDialogState?,
    showVariationPicker: Product?,
    showPriceInput: CartItem?,
    hiddenFieldFocusRequester: FocusRequester,
    scope: kotlinx.coroutines.CoroutineScope
): Boolean {
    return when (event.key) {
        Key.Escape -> {
            // Always return to SALE mode on Escape
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
    onBack: () -> Unit
) {
    // Inject ViewModel via Koin
    val viewModel = koinInject<POSViewModel>()
    val shortcutsManager = koinInject<com.nexopos.desktop.core.settings.KeyboardShortcutsManager>()
    val shortcuts by shortcutsManager.shortcuts.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // POS Mode state management - CRITICAL for input authority
    var currentMode by remember { mutableStateOf(POSMode.SALE) }

    // Cashier skill level for UI adaptation
    var skillLevel by remember { mutableStateOf(CashierSkillLevel.EXPERT) }

    // Last scanned item for visual highlighting
    var lastScannedProductId by remember { mutableStateOf<String?>(null) }

    // Hidden text field for barcode scanner input (more reliable than key events)
    var hiddenBarcodeInput by remember { mutableStateOf(TextFieldValue("")) }
    val hiddenFieldFocusRequester = remember { FocusRequester() }
    var hiddenFieldHasFocus by remember { mutableStateOf(false) }

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
    val barcodeScanner = remember { DesktopBarcodeScanner() }
    val cashDrawer = remember { DesktopCashDrawer() }
    val printer = remember { DesktopReceiptPrinter() }

    // Collect state from ViewModel with performance optimizations
    // SINGLE UI STATE COLLECTOR for maximum performance
    val uiState by viewModel.uiState.collectAsState()
    
    // Destructure UI state for easier access
    val categories = uiState.categories
    val selectedCategory = uiState.selectedCategory
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
    
    // Performance optimization: derivedStateOf for expensive computations
    val visibleProducts by remember(products) {
        derivedStateOf {
            products.take(100) // Limit visible products for performance
        }
    }

    // Dialog states - using proper var declarations
    var showVariationPicker by remember { mutableStateOf<Product?>(null) }
    var showCustomerDropdown by remember { mutableStateOf(false) }
    var showPaymentDropdown by remember { mutableStateOf(false) }
    var showMessage by remember { mutableStateOf<String?>(null) }
    var printReceipt by remember { mutableStateOf(false) }
    var showDiscountInput by remember { mutableStateOf(false) }
    var showQuickProductDialog by remember { mutableStateOf(false) }
    var pendingProduct by remember { mutableStateOf<PendingProductState?>(null) }
    var showQuantityDialog by remember { mutableStateOf<QuantityDialogState?>(null) }
    var showPriceInput by remember { mutableStateOf<CartItem?>(null) }

    // Register dialog states
    var showRegisterDialog by remember { mutableStateOf(false) }
    var showOpenRegisterDialog by remember { mutableStateOf<Register?>(null) }
    var showCloseRegisterDialog by remember { mutableStateOf(false) }
    var showCashInDialog by remember { mutableStateOf(false) }
    var showCashOutDialog by remember { mutableStateOf(false) }
    var showRegisterHistoryDialog by remember { mutableStateOf(false) }
    var isRegisterDialogShown by remember { mutableStateOf(false) }

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
                            showVariationPicker = product
                            currentMode = POSMode.MODAL
                        }
                        is POSViewModel.ProductValidation.NeedsPrice -> {
                            // Can't add without price
                            showMessage = "Product ${product.name} has no price"
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
                showMessage = "Please open a cash register first"
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
                                paymentMethod = paymentSnapshot?.label ?: "Cash",
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

    // Main container - MODE-AWARE keyboard handling
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // MODE-AWARE keyboard handling - CRITICAL for input authority
                if (event.type == KeyEventType.KeyDown) {
                    println("[Keyboard] Mode: $currentMode, Key: ${event.key}")
                    when (currentMode) {
                        POSMode.SALE -> handleSaleModeKeys(
                            event = event,
                            shortcutsManager = shortcutsManager,
                            scope = scope,
                            cashDrawer = cashDrawer,
                            printReceipt = printReceipt,                            showMessage = updateMessage,
                            doSubmitOrder = doSubmitOrder,
                            viewModel = viewModel,
                            cart = cart,
                            setShowDiscountInput = { showDiscountInput = it },
                            setShowQuickProductDialog = { showQuickProductDialog = it },
                            showVariationPicker = showVariationPicker,
                            onNavigateToOrders = onNavigateToOrders,
                            setCurrentMode = { currentMode = it },
                            selectedCustomer = selectedCustomer,
                            selectedPaymentMethod = selectedPaymentMethod,
                            barcodeScanner = barcodeScanner,
                            hiddenFieldFocusRequester = hiddenFieldFocusRequester,
                            setPrintReceipt = { printReceipt = it }
                        )
                        POSMode.PAYMENT -> handlePaymentModeKeys(event)
                        POSMode.MODAL -> handleModalModeKeys(
                            event = event,
                            setShowDiscountInput = { showDiscountInput = it },
                            setShowQuantityDialog = { showQuantityDialog = it },
                            setShowVariationPicker = { showVariationPicker = it },
                            setShowPriceInput = { showPriceInput = it },
                            setPendingProduct = { pendingProduct = it },
                            setCurrentMode = { currentMode = it },
                            showDiscountInput = showDiscountInput,
                            showQuantityDialog = showQuantityDialog,
                            showVariationPicker = showVariationPicker,
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
                    // When user clicks on empty space, refocus scanner
                    if (currentMode == POSMode.SALE) {
                        refocusScanner()
                    }
                }
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left side - Product grid (50% - balanced layout)
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                // Header - simplified (customer/payment moved to cart)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.focusable(false)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Mode indicator
                    Badge(
                        containerColor = when (currentMode) {
                            POSMode.SALE -> MaterialTheme.colorScheme.primary
                            POSMode.PAYMENT -> MaterialTheme.colorScheme.error
                            POSMode.MODAL -> MaterialTheme.colorScheme.secondary
                        }
                    ) {
                        Text(
                            when (currentMode) {
                                POSMode.SALE -> "SALE"
                                POSMode.PAYMENT -> "PAYMENT"
                                POSMode.MODAL -> "MODAL"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Skill level toggle
                    IconButton(
                        onClick = {
                            skillLevel = if (skillLevel == CashierSkillLevel.EXPERT) CashierSkillLevel.BEGINNER else CashierSkillLevel.EXPERT
                            updateMessage("Mode: ${if (skillLevel == CashierSkillLevel.EXPERT) "Expert" else "Beginner"}")
                            refocusScanner()
                        },
                        modifier = Modifier.focusable(false)
                    ) {
                        Icon(
                            if (skillLevel == CashierSkillLevel.EXPERT) Icons.Default.Person else Icons.Default.Face,
                            contentDescription = "Skill Level",
                            tint = if (skillLevel == CashierSkillLevel.EXPERT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Action buttons
                    IconButton(
                        onClick = {
                            showQuickProductDialog = true
                            currentMode = POSMode.MODAL
                        },
                        modifier = Modifier.focusable(false)
                    ) {
                        Icon(Icons.Default.Add, "Quick Product")
                    }
                    IconButton(
                        onClick = { 
                            viewModel.refreshData()
                            refocusScanner()
                        },
                        modifier = Modifier.focusable(false)
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(
                        onClick = onNavigateToOrders,
                        modifier = Modifier.focusable(false)
                    ) {
                        Icon(Icons.Default.List, "Orders")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                cashDrawer.open()
                                refocusScanner()
                            }
                        },
                        modifier = Modifier.focusable(false)
                    ) {
                        Icon(Icons.Default.Inbox, "Open Drawer")
                    }
                    
                    // Register button - always visible
                    IconButton(
                        onClick = {
                            showRegisterDialog = true
                            currentMode = POSMode.MODAL
                        },
                        modifier = Modifier.focusable(false)
                    ) {
                        Icon(
                            if (currentRegister == null) Icons.Default.PointOfSale else Icons.Default.CheckCircle,
                            "Cash Register",
                            tint = if (currentRegister == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Hidden TextField for barcode scanner input - SIMPLIFIED
                Box(modifier = Modifier.height(0.dp).fillMaxWidth()) {
                    BasicTextField(
                        value = hiddenBarcodeInput,
                        enabled = currentMode == POSMode.SALE,
                        onValueChange = { newValue ->
                            if (currentMode != POSMode.SALE) return@BasicTextField
                            
                            val newText = newValue.text
                            // Simple detection: Enter key or barcode length >= 8
                            if (newText.contains('\n') || newText.contains('\r')) {
                                val barcode = newText.replace("\n", "").replace("\r", "").trim()
                                if (barcode.length >= 3) {
                                    processBarcodeInput(barcode)
                                    hiddenBarcodeInput = TextFieldValue("")
                                }
                            } else if (newText.length >= 12) { // Most barcodes are 12-13 digits
                                // Auto-process after reaching typical barcode length
                                processBarcodeInput(newText.trim())
                                hiddenBarcodeInput = TextFieldValue("")
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
                                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                    val barcode = hiddenBarcodeInput.text.trim()
                                    if (barcode.length >= 3) {
                                        processBarcodeInput(barcode)
                                        hiddenBarcodeInput = TextFieldValue("")
                                        true
                                    } else false
                                } else false
                            },
                        singleLine = true
                    )
                }

                // Search input - for manual product search only
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = { input ->
                        viewModel.setSearchTerm(input)
                    },
                    label = { Text("Search products (manual)") },
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
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category tabs - auto-select first category if none selected
                if (categories.isNotEmpty()) {
                    // Auto-select first category if none selected
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
                        // Category tabs only (no "All" tab)
                        categories.forEachIndexed { index, category ->
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

                // Loading or product grid
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Loading data from server...")
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
                            Text(
                                "No products loaded",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Check Settings → Refresh Data",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // ENTERPRISE: High-density grid for desktop (6-10 columns)
                    // Adapt grid density based on skill level
                    val gridSize = if (skillLevel == CashierSkillLevel.BEGINNER) 150.dp else 120.dp
                    val gridSpacing = if (skillLevel == CashierSkillLevel.BEGINNER) 8.dp else 4.dp

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridSize),
                        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                        verticalArrangement = Arrangement.spacedBy(gridSpacing),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            items = products.take(150), // Limit to 150 items for performance
                            key = { it.id.toString() } // Add key for stable identity
                        ) { product ->
                            ProductCard(
                                product = product,
                                onClick = {
                                    // Simple flow: if product has variations, show picker, else add directly
                                    if (product.hasVariations()) {
                                        showVariationPicker = product
                                        currentMode = POSMode.MODAL
                                    } else {
                                        // Get first unit quantity
                                        val unitQuantities = product.getUnitQuantities()
                                        if (unitQuantities.isNotEmpty()) {
                                            val uq = unitQuantities.first()
                                            if (uq.effectivePrice > 0) {
                                                // Direct add for products with price
                                                viewModel.confirmAddToCart(product, uq, quantity = 1.0)
                                                // Highlight and refocus
                                                lastScannedProductId = product.id.toString()
                                                scope.launch {
                                                    delay(2000)
                                                    lastScannedProductId = null
                                                }
                                                refocusScanner()
                                            } else {
                                                // No price - show edit dialog
                                                val tempItem = CartItem(
                                                    key = "temp_${product.id}",
                                                    product = product,
                                                    quantity = 1.0,
                                                    unitPrice = 0.0,
                                                    wholesalePrice = uq.wholesalePriceWithTax,
                                                    isWholesale = false,
                                                    unitQuantityId = uq.id,
                                                    unitId = uq.unitId,
                                                    unitName = uq.unitName,
                                                    isCustomPrice = false
                                                )
                                                showPriceInput = tempItem
                                                currentMode = POSMode.MODAL
                                            }
                                        }
                                    }
                                },
                                skillLevel = skillLevel,
                                isLastScanned = (product.id.toString() == lastScannedProductId),
                                modifier = Modifier // Smooth animations disabled for stability
                            )
                        }
                    }
                }
            }

            // Right side - Cart (50% - balanced layout)
            CartPanel(
                cart = cart,
                discountType = discountType,
                discountValue = discountValue,
                printReceipt = printReceipt,
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
                onClearCart = { 
                    viewModel.clearCart()
                    refocusScanner()
                },
                onSubmitOrder = {
                    if (currentRegister == null) {
                        showMessage = "Please open a cash register first"
                        showRegisterDialog = true
                        currentMode = POSMode.MODAL
                    } else if (cart.isNotEmpty() && selectedCustomer != null && selectedPaymentMethod != null) {
                        currentMode = POSMode.PAYMENT
                    } else {
                        showMessage = "Cart, customer, or payment method missing"
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

        // Keyboard shortcuts help - bottom left
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Raccourcis: F8: Tiroir  F9: Impr.  F12: Valider  G: Gros  D: Remise  NumPad+/-: Qté  Del: Suppr.", style = MaterialTheme.typography.labelSmall)
            }
        }

        // DIALOGS - All set POSMode.MODAL when open

        // Variation picker dialog
        if (showVariationPicker != null) {
            VariationPickerDialog(
                product = showVariationPicker!!,
                onSelect = { unitQuantity ->
                    val product = showVariationPicker!!
                    showVariationPicker = null
                    // Use merged edit dialog for variations too
                    // Use ONLY API-provided wholesale price with tax (server already includes tax)
                    // If not available, set to null (no wholesale pricing)
                    val wholesalePriceWithTax = unitQuantity.wholesalePriceWithTax

                    val tempItem = CartItem(
                        key = "temp_${product.id}_${unitQuantity.id}",
                        product = product,
                        quantity = 1.0,
                        unitPrice = unitQuantity.effectivePrice,
                        wholesalePrice = wholesalePriceWithTax,
                        isWholesale = false,
                        unitQuantityId = unitQuantity.id,
                        unitId = unitQuantity.unitId,
                        unitName = unitQuantity.unitName,
                        isCustomPrice = false
                    )

                    pendingProduct = PendingProductState(
                        product = product,
                        unitQuantity = unitQuantity,
                        needsPrice = unitQuantity.effectivePrice <= 0
                    )
                    showPriceInput = tempItem
                    // Stay in MODAL mode
                },
                onDismiss = {
                    showVariationPicker = null
                    currentMode = POSMode.SALE // Return to SALE mode
                    refocusScanner()
                }
            )
        }

        // Discount input dialog
        if (showDiscountInput) {
            var tempDiscount by remember { mutableStateOf("") }
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

        // Quantity input dialog
        if (showQuantityDialog != null) {
            val dialogState = showQuantityDialog!!
            var tempQuantity by remember {
                mutableStateOf(TextFieldValue(
                    dialogState.initialQuantity.toString(),
                    TextRange(0, dialogState.initialQuantity.toString().length)
                ))
            }
            val quantityFocusRequester = remember { FocusRequester() }

            val confirmQuantity: () -> Unit = {
                val newQty = tempQuantity.text.toDoubleOrNull() ?: 1.0
                if (newQty > 0) {
                    // Check if pending product needs price
                    val pending = pendingProduct
                    if (pending != null && pending.needsPrice) {
                        // Close quantity dialog, show price dialog next
                        showQuantityDialog = null
                        // Create temporary cart item for price input
                        showPriceInput = CartItem(
                            key = "temp_${System.currentTimeMillis()}",
                            product = dialogState.product,
                            quantity = newQty,
                            unitPrice = 0.0,
                            wholesalePrice = null,
                            isWholesale = false,
                            unitQuantityId = dialogState.unitQuantity.id,
                            unitId = dialogState.unitQuantity.unitId,
                            unitName = dialogState.unitQuantity.unitName
                        )
                        // Stay in MODAL mode
                    } else {
                        // Add to cart now (user confirmed)
                        viewModel.confirmAddToCart(
                            product = dialogState.product,
                            unitQuantity = dialogState.unitQuantity,
                            quantity = newQty
                        )
                        showQuantityDialog = null
                        pendingProduct = null
                        currentMode = POSMode.SALE
                        refocusScanner()
                    }
                } else {
                    // Invalid quantity - just close
                    showQuantityDialog = null
                    pendingProduct = null
                    currentMode = POSMode.SALE
                    refocusScanner()
                }
            }

            LaunchedEffect(Unit) {
                quantityFocusRequester.requestFocus()
            }

            AlertDialog(
                onDismissRequest = {
                    showQuantityDialog = null
                    pendingProduct = null
                    currentMode = POSMode.SALE
                    refocusScanner()
                },
                title = { Text(dialogState.product.name, maxLines = 1) },
                text = {
                    OutlinedTextField(
                        value = tempQuantity.text,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { c -> c.isDigit() || c == '.' }
                            val parts = filtered.split('.')
                            val sanitized = if (parts.size > 2) {
                                parts[0] + "." + parts.drop(1).joinToString("")
                            } else filtered
                            tempQuantity = TextFieldValue(
                                text = sanitized,
                                selection = tempQuantity.selection
                            )
                        },
                        label = { Text("Quantité") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(quantityFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                    confirmQuantity()
                                    true
                                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                    showQuantityDialog = null
                                    pendingProduct = null
                                    currentMode = POSMode.SALE
                                    refocusScanner()
                                    true
                                } else false
                            },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { confirmQuantity() },
                        modifier = Modifier.focusable(false)
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showQuantityDialog = null
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

    AlertDialog(
        onDismissRequest = {
            showPriceInput = null
            pendingProduct = null
            currentMode = POSMode.SALE
            refocusScanner()
        },
        title = { Text("Edit - ${item.product.name}", maxLines = 1) },
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
                    label = { Text("Quantity") },
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
                    label = { Text("Unit Price") },
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
                            Text("Wholesale Price", style = MaterialTheme.typography.bodyMedium)
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
                Text("Update")
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
                Text("Cancel")
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

    AlertDialog(
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

        // PAYMENT MODAL - Hard lock for payment processing
        if (currentMode == POSMode.PAYMENT) {
            PaymentModal(
                cart = cart,
                discountType = discountType,
                discountValue = discountValue,
                selectedCustomer = selectedCustomer,
                selectedPaymentMethod = selectedPaymentMethod,
                printReceipt = printReceipt,
                onDismiss = {
                    currentMode = POSMode.SALE
                    updateMessage("Payment cancelled")
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
                        showMessage = "You must select a cash register to continue"
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
            var amount by remember { mutableStateOf("") }
            var description by remember { mutableStateOf("") }
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            AlertDialog(
                onDismissRequest = {
                    // Can't dismiss - must open a register
                },
                title = { Text("Open Register: ${register.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            label = { Text("Opening Amount") },
                            suffix = { Text("DT") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                        val amountValue = amount.toDoubleOrNull() ?: 0.0
                                        if (amountValue > 0) {
                                            viewModel.openRegister(register.id, amountValue, description)
                                            showOpenRegisterDialog = null
                                            currentMode = POSMode.SALE
                                            refocusScanner()
                                        }
                                        true
                                    } else false
                                },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amountValue = amount.toDoubleOrNull() ?: 0.0
                            if (amountValue > 0) {
                                viewModel.openRegister(register.id, amountValue, description)
                                showOpenRegisterDialog = null
                                currentMode = POSMode.SALE
                                refocusScanner()
                            }
                        },
                        enabled = amount.isNotEmpty() && amount.toDoubleOrNull() ?: 0.0 > 0
                    ) {
                        Text("Open Register")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            // Go back to register selection
                            showOpenRegisterDialog = null
                            showRegisterDialog = true
                        }
                    ) {
                        Text("Back")
                    }
                }
            )
        }

        // 3. Close Register Dialog
        if (showCloseRegisterDialog) {
            var amount by remember { mutableStateOf("") }
            var description by remember { mutableStateOf("") }
            val currentReg = currentRegister
             if (currentReg != null) 
             {
                    Text("Available Balance: ${String.format("%.3f DT", currentReg.balance)}")

            AlertDialog(
                onDismissRequest = {
                    showCloseRegisterDialog = false
                    currentMode = POSMode.SALE
                    refocusScanner()
                },
                title = { Text("Close Register: ${currentReg.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Current Balance: ${String.format("%.3f DT", currentReg.balance)}")
                        
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
                            label = { Text("Closing Amount") },
                            suffix = { Text("DT") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amountValue = amount.toDoubleOrNull() ?: 0.0
                            viewModel.closeRegister(amountValue, description)
                            showCloseRegisterDialog = false
                            currentMode = POSMode.SALE
                            refocusScanner()
                        },
                        enabled = amount.isNotEmpty()
                    ) {
                        Text("Close Register")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCloseRegisterDialog = false
                            currentMode = POSMode.SALE
                            refocusScanner()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
        }

        // 4. Cash In Dialog
        if (showCashInDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCashInDialog = false
                    currentMode = POSMode.SALE
                    cashAmount = ""
                    cashDescription = ""
                    refocusScanner()
                },
                title = { Text("Cash In") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            label = { Text("Amount") },
                            suffix = { Text("DT") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = cashDescription,
                            onValueChange = { cashDescription = it },
                            label = { Text("Description (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amountValue = cashAmount.toDoubleOrNull() ?: 0.0
                            if (amountValue > 0) {
                                viewModel.cashIn(amountValue, cashDescription)
                                showCashInDialog = false
                                cashAmount = ""
                                cashDescription = ""
                                currentMode = POSMode.SALE
                                showMessage = "Cash in successful"
                                refocusScanner()
                            }
                        },
                        enabled = cashAmount.isNotEmpty() && cashAmount.toDoubleOrNull() ?: 0.0 > 0
                    ) {
                        Text("Cash In")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCashInDialog = false
                            currentMode = POSMode.SALE
                            cashAmount = ""
                            cashDescription = ""
                            refocusScanner()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 5. Cash Out Dialog
        if (showCashOutDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCashOutDialog = false
                    currentMode = POSMode.SALE
                    cashAmount = ""
                    cashDescription = ""
                    refocusScanner()
                },
                title = { Text("Cash Out") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val currentReg = currentRegister
                        if (currentReg != null) {
                    
                        Text("Available Balance: ${String.format("%.3f DT", currentReg.balance)}")
                        
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
                            label = { Text("Amount") },
                            suffix = { Text("DT") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = cashDescription,
                            onValueChange = { cashDescription = it },
                            label = { Text("Description (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amountValue = cashAmount.toDoubleOrNull() ?: 0.0
                            val currentReg = currentRegister
                            if (currentReg != null && amountValue > 0 && amountValue <= currentReg.balance) {
                                viewModel.cashOut(amountValue, cashDescription)
                                showCashOutDialog = false
                                cashAmount = ""
                                cashDescription = ""
                                currentMode = POSMode.SALE
                                showMessage = "Cash out successful"
                                refocusScanner()
                            } else if (currentReg != null && amountValue > currentReg.balance) {
                                showMessage = "Insufficient balance"
                            }
                        },
                        enabled = cashAmount.isNotEmpty() && cashAmount.toDoubleOrNull() ?: 0.0 > 0
                    ) {
                        Text("Cash Out")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCashOutDialog = false
                            currentMode = POSMode.SALE
                            cashAmount = ""
                            cashDescription = ""
                            refocusScanner()
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 6. Register History Dialog
        if (showRegisterHistoryDialog) {
            AlertDialog(
                onDismissRequest = {
                    showRegisterHistoryDialog = false
                    currentMode = POSMode.SALE
                    refocusScanner()
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Register History")
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (registerHistory.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No history available")
                            }
                        } else {
                            LazyColumn {
                                items(registerHistory) { history ->
                                    RegisterHistoryItem(history = history)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRegisterHistoryDialog = false
                            currentMode = POSMode.SALE
                            refocusScanner()
                        }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
        
        // Register FAB - only show when register is available
        currentRegister?.let { register ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                RegisterFAB(
                    register = register,
                    onClose = {
                        showCloseRegisterDialog = true
                        currentMode = POSMode.MODAL
                    },
                    onCashIn = {
                        showCashInDialog = true
                        currentMode = POSMode.MODAL
                    },
                    onCashOut = {
                        showCashOutDialog = true
                        currentMode = POSMode.MODAL
                    },
                    onHistory = {
                        showRegisterHistoryDialog = true
                        currentMode = POSMode.MODAL
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
    // Pre-compute values to avoid recomputation
    val price by remember(product) { 
        derivedStateOf { product.getPrice() }
    }
    val hasVariations by remember(product) {
        derivedStateOf { product.hasVariations() }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .focusable(false),
        colors = CardDefaults.cardColors(
            containerColor = if (isLastScanned)
                PosTheme.Highlight.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isLastScanned)
            BorderStroke(2.dp, PosTheme.Highlight)
        else
            null,
        elevation = CardDefaults.cardElevation(defaultElevation = PosTheme.ElevationLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PosTheme.SpacingCompact),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Variations indicator - simplified
            if (hasVariations) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Has variations",
                    modifier = Modifier.size(14.dp),
                    tint = PosTheme.PriceWholesale
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Product name - optimized for quick reading
            Text(
                product.name,
                style = if (skillLevel == CashierSkillLevel.BEGINNER) 
                    MaterialTheme.typography.titleMedium 
                else 
                    MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                minLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(PosTheme.SpacingTight))

            // Price - prominent and clear
            Text(
                if (price > 0) String.format("%.3f DT", price) else "—",
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
fun RowScope.CartPanel(
    cart: List<CartItem>,
    discountType: DiscountType,
    discountValue: Double,
    printReceipt: Boolean,
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
    onClearCart: () -> Unit,
    onSubmitOrder: () -> Unit,
    onEditItem: (CartItem) -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
    ) {
        // COMPACT Customer & Payment Section
        var showCustomerDropdown by remember { mutableStateOf(false) }
        var showPaymentDropdown by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Customer info - compact
            ExposedDropdownMenuBox(
                expanded = showCustomerDropdown,
                onExpandedChange = { showCustomerDropdown = it },
                modifier = Modifier.weight(1f)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .focusable(false), // Prevent focus stealing
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
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
                                selectedCustomer?.getDisplayName() ?: "Walk-in",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "Customer",
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

            // Payment method - compact
            ExposedDropdownMenuBox(
                expanded = showPaymentDropdown,
                onExpandedChange = { showPaymentDropdown = it },
                modifier = Modifier.weight(1f)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .focusable(false), // Prevent focus stealing
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
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
                                selectedPaymentMethod?.label ?: selectedPaymentMethod?.identifier ?: "Cash",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                "Payment",
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

        // Cart items with moderate spacing
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(cart) { item ->
                CartItemRow(
                    item = item,
                    onIncrease = { onIncreaseQuantity(item) },
                    onDecrease = { onDecreaseQuantity(item) },
                    onRemove = { onRemove(item) },
                    onToggleWholesale = { onToggleWholesale(item) },
                    onEdit = { onEditItem(item) }
                )
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

        // Order Summary Card - Enhanced visibility
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons with print toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onClearCart,
                enabled = cart.isNotEmpty(),
                modifier = Modifier.focusable(false)
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
                    contentDescription = "Print receipt",
                    tint = if (printReceipt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onSubmitOrder,
                modifier = Modifier.weight(1f),
                enabled = cart.isNotEmpty() && selectedCustomer != null && selectedPaymentMethod != null && currentRegister != null
            ) {
                Icon(Icons.Default.ShoppingCart, null)
                Spacer(Modifier.width(4.dp))
                Text("Valider")
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
    onEdit: () -> Unit
) {
    val hasWholesale = item.wholesalePrice != null && item.wholesalePrice > 0
    
    // Determine current price state - SIMPLIFIED LOGIC
    val currentState = remember(item, hasWholesale) {
        when {
            // Wholesale state: item is marked as wholesale AND has wholesale price
            item.isWholesale && hasWholesale -> PriceState.WHOLESALE
            // Custom state: explicitly marked as custom price
            item.isCustomPrice -> PriceState.CUSTOM
            // Default: retail state
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
    
    // What happens when chip is clicked - SIMPLIFIED
    val onChipClick = {
        when (currentState) {
            PriceState.RETAIL -> {
                // From Retail -> Wholesale (if available)
                if (hasWholesale) {
                    onToggleWholesale()
                }
            }
            PriceState.WHOLESALE -> {
                // From Wholesale -> Retail
                onToggleWholesale()
            }
            PriceState.CUSTOM -> {
                // From Custom -> Retail (always go back to retail from custom)
                // The ViewModel will handle if price matches retail or wholesale
                onToggleWholesale()
            }
        }
    }
    
    // Only enable chip click if item has wholesale price available
    val chipEnabled = hasWholesale || currentState == PriceState.CUSTOM
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .focusable(false), // Prevent focus stealing
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Product name and quantity controls
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Quantity controls next to unit × price
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onDecrease,
                        modifier = Modifier
                            .size(28.dp)
                            .focusable(false)
                    ) {
                        Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp))
                    }

                    Text(
                        String.format("%.1f", item.quantity),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 30.dp),
                        textAlign = TextAlign.Center
                    )

                    FilledTonalIconButton(
                        onClick = onIncrease,
                        modifier = Modifier
                            .size(28.dp)
                            .focusable(false)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    }

                    Text(
                        "${item.unitName ?: "PCS"} × ${String.format("%.2f", item.effectivePrice)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right: Price type chip (wholesale toggle) and actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total price
                Text(
                    String.format("%.2f", item.total),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Price type chip as wholesale toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = priceTypeColor.copy(alpha = 0.15f),
                    modifier = Modifier
                        .clickable(
                            enabled = chipEnabled,
                            onClick = onChipClick,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // No ripple effect
                        )
                        .focusable(false)
                ) {
                    Text(
                        priceType,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = priceTypeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Delete button
                FilledIconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(32.dp)
                        .focusable(false),
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
        }
    }
}

@Composable
fun VariationPickerDialog(
    product: Product,
    onSelect: (com.nexopos.shared.models.UnitQuantity) -> Unit,
    onDismiss: () -> Unit
) {
    val variations = product.getUnitQuantities()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select unit/variation:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                // Grid layout for variations
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(variations.size) { index ->
                        val uq = variations[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(uq) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    uq.unitName ?: "Unit ${uq.unitId}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    String.format("%.3f DT", uq.effectivePrice),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusable(false)
            ) {
                Text("Cancel")
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
                Text("Payment Processing")
                Spacer(modifier = Modifier.weight(1f))
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text("SCANNER LOCKED", style = MaterialTheme.typography.labelSmall)
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
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Order Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal:")
                            Text(String.format("%.3f DT", subtotal))
                        }

                        if (discountAmount > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Discount:")
                                Text(String.format("-%.3f DT", discountAmount), color = MaterialTheme.colorScheme.error)
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Total:",
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
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(customer.getDisplayName(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("Customer", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    selectedPaymentMethod?.let { method ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(method.label ?: method.identifier, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("Payment", style = MaterialTheme.typography.labelSmall)
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
                    label = { Text("Payment Amount") },
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
                                    Text("Change:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
                                    "Insufficient payment amount",
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
                enabled = paymentAmount.isNotEmpty() && paymentAmount.toDoubleOrNull() ?: 0.0 >= total,
                modifier = Modifier.focusable(false)
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Complete Payment")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.focusable(false)
            ) {
                Icon(Icons.Default.Cancel, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel")
            }
        }
    )
}

// --- REGISTER UI COMPONENTS ---

@Composable
fun RegisterSelectionDialog(
    registers: List<Register>,
    currentRegister: Register?,
    onRegisterSelected: (Register) -> Unit,
    onDismiss: () -> Unit
) {
    val filteredRegisters = registers.filter { it.status != "disabled" }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PointOfSale, null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Select Cash Register", style = MaterialTheme.typography.headlineSmall)
                    Text("Required before processing sales", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentRegister != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Currently Using: ${currentRegister.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Balance: ${String.format("%.3f DT", currentRegister.balance)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (filteredRegisters.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No registers available", style = MaterialTheme.typography.titleMedium)
                            Text("Contact administrator to create registers", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    LazyColumn {
                        items(filteredRegisters) { register ->
                            RegisterCard(
                                register = register,
                                isCurrent = register.id == currentRegister?.id,
                                onClick = { onRegisterSelected(register) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RegisterCard(
    register: Register,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isCurrent) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (register.status == "opened") Icons.Default.LockOpen 
                        else Icons.Default.Lock,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = when (register.status) {
                            "opened" -> MaterialTheme.colorScheme.primary
                            "closed" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        register.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = when (register.status) {
                            "opened" -> MaterialTheme.colorScheme.primary
                            "closed" -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        }
                    ) {
                        Text(register.status.uppercase(), style = MaterialTheme.typography.labelSmall)
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
val registerDescription = register.description
if (registerDescription != null && registerDescription.isNotBlank()) {                 Text(
                        registerDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                
                Text(
                    "Balance: ${String.format("%.3f DT", register.balance)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (isCurrent) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Current",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else if (register.status == "closed") {
                Icon(
                    Icons.Default.LockOpen,
                    "Open",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
fun RegisterHistoryItem(history: RegisterHistory) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                history.value > 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        getActionLabel(history.action),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
val historyDescription = history.description
if (historyDescription != null && historyDescription.isNotBlank()) {                       Text(
                            historyDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Determine display value and color based on action type
                val (displayValue, color) = when (history.action) {
                    "register-cash-out", "register-order-change" -> 
                        Pair(-history.value, MaterialTheme.colorScheme.error)
                    "register-cash-in" -> 
                        Pair(history.value, PosTheme.Success) // Use theme green
                    "register-opening" -> 
                        Pair(history.value, PosTheme.Info) // Use theme blue
                    else -> // register-order-payment
                        Pair(history.value, PosTheme.Success) // Use theme green
                }
                
                // Determine sign based on action type
                val sign = when (history.action) {
                    "register-cash-out", "register-order-change" -> "-" // show negative sign
                    "register-opening" -> "" // no sign for opening
                    else -> "+" // cash in and order payments get +
                }
                
                Text(
                    "$sign${String.format("%.3f DT", abs(displayValue))}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
history.createdAt ?: "",                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getActionLabel(action: String): String {
    return when (action) {
        "register-opening" -> "Opening"
        "register-closing" -> "Closing"
        "register-cash-in" -> "Cash In"
        "register-cash-out" -> "Cash Out"
        "register-order-payment" -> "Order Payment"
        "register-order-change" -> "Order Change"
        "register-refund" -> "Refund"
        else -> action.replace("register-", "").replace("-", " ").titlecase()
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
                            Text(register.status.uppercase(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    
                    Text("Balance: ${String.format("%.3f DT", register.balance)}")
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onHistory,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.History, "History")
                        }
                        IconButton(
                            onClick = onCashIn,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.CallReceived, "Cash In")
                        }
                        IconButton(
                            onClick = onCashOut,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.CallMade, "Cash Out")
                        }
                        FilledTonalButton(
                            onClick = onClose,
                            modifier = Modifier.height(36.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text("Close")
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
                "Register",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}