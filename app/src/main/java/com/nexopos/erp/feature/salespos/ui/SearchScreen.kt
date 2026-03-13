package com.nexopos.erp.feature.salespos.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.core.repo.CategoryRepository
import com.nexopos.erp.core.repo.MobileSyncRepository
import com.nexopos.erp.core.repo.ProductRepository
import com.nexopos.erp.feature.salespos.ui.CartViewModel
import com.nexopos.erp.feature.salespos.ui.QuantityDialog
import com.nexopos.erp.feature.salespos.ui.QuantityDialogData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nexopos.erp.R
import com.nexopos.erp.ui.formatAppCurrency
import com.nexopos.erp.ui.formatAppQuantity
import com.nexopos.erp.ui.theme.posColors
import java.util.Locale
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexopos.erp.feature.catalog.CatalogRoutes
import com.nexopos.erp.feature.salespos.PosRoutes
import com.nexopos.erp.ui.components.AppChipFilter
import com.nexopos.erp.ui.components.AppButtonPrimary
import com.nexopos.erp.ui.components.AppButtonSecondary
import com.nexopos.erp.ui.components.AppButtonTertiary
import com.nexopos.erp.ui.components.AppCard
import com.nexopos.erp.ui.components.AppDialog
import com.nexopos.erp.ui.components.AppTextField
import com.nexopos.erp.ui.theme.appColors
import com.nexopos.erp.ui.theme.appRadii
import com.nexopos.erp.ui.theme.appSpacing
import com.nexopos.shared.models.Register

data class CategorySection(
    val id: Long,
    val title: String,
    val products: List<Product>
)

/**
 * Category configuration with slug for API-based categories
 */
data class CategoryConfig(val id: Long, val title: String, val slug: String = "")

private data class CategoryLoadPayload(
    val configs: List<CategoryConfig>,
    val sections: List<CategorySection>,
    val errorMessage: String?
)

/**
 * ViewModel for the search screen with cache-first loading strategy.
 * 
 * Key behaviors:
 * - On init: loads from cache immediately (no network call)
 * - On pull-to-refresh: fetches from network and updates cache
 * - Search always tries network first, falls back to cache
 */
class SearchViewModel(
    private val repo: ProductRepository,
    private val syncRepo: MobileSyncRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<Product>>(emptyList())
        private set

    var categoriesLoading by mutableStateOf(true)
        private set
    var categorySections by mutableStateOf<List<CategorySection>>(emptyList())
        private set
    var categoriesError by mutableStateOf<String?>(null)
        private set
    var categoriesRefreshing by mutableStateOf(false)
        private set
    var fullSyncInProgress by mutableStateOf(false)
        private set

    private var searchJob: Job? = null
    private var categoriesLoadJob: Job? = null
    private var initialCatalogLoadStarted = false

    private var categoryConfigs: List<CategoryConfig> = emptyList()

    private val _refreshEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val refreshEvents: SharedFlow<Boolean> = _refreshEvents.asSharedFlow()

    init {
        if (!initialCatalogLoadStarted) {
            initialCatalogLoadStarted = true
            // Defer category loading to avoid blocking first frame
            // P1 Fix: Increased delay to 1500ms to stagger bootstrap sync after login
            viewModelScope.launch {
                kotlinx.coroutines.delay(1500) // Stagger after login to reduce initialization storm
                // Check if cache is empty - if so, trigger bootstrap sync first
                val cachedProducts = withContext(Dispatchers.IO) {
                    repo.getCachedProductCount()
                }
                if (cachedProducts == 0) {
                    Log.d("SearchViewModel", "Cache empty on init - triggering bootstrap sync")
                    fullSyncInProgress = true
                    categoriesRefreshing = true
                    val result = syncRepo.bootstrapSync()
                    result.onSuccess {
                        Log.d("SearchViewModel", "Bootstrap sync completed - loading categories from cache")
                    }.onFailure { e ->
                        Log.e("SearchViewModel", "Bootstrap sync failed: ${e.message}")
                        categoriesError = e.message
                    }
                    fullSyncInProgress = false
                    categoriesRefreshing = false
                }
                loadCategories()
            }
        }
    }

    fun search(term: String) {
        searchJob?.cancel()
        if (term.length < 2) {
            results = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            isLoading = true
            try {
                results = repo.searchByTerm(term)
            } catch (e: Exception) {
                results = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    private var lastRefreshRequestAt: Long = 0L
    private val refreshDebounceMillis = 1_000L

    /**
     * Full catalog refresh - fetches all products via bootstrap sync API.
     * Single request, no background worker or notifications.
     */
    fun refreshCatalog() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshRequestAt < refreshDebounceMillis || fullSyncInProgress) {
            return
        }
        lastRefreshRequestAt = now
        
        viewModelScope.launch {
            fullSyncInProgress = true
            categoriesRefreshing = true
            categoriesError = null
            
            val result = syncRepo.bootstrapSync()
            
            result.onSuccess {
                // Reload categories from fresh cache
                loadCategoriesFromCache()
                _refreshEvents.tryEmit(true)
            }.onFailure { e ->
                categoriesError = e.message
                _refreshEvents.tryEmit(false)
            }
            
            fullSyncInProgress = false
            categoriesRefreshing = false
        }
    }

    /**
     * Refresh categories via pull-to-refresh.
     * This is the ONLY time we make network calls for category products.
     */
    fun refreshCategories() {
        loadCategories()
    }

    /**
     * Load categories from the synced category source.
     * 
     * Performance: Uses parallel processing and skips unit enrichment on initial load
     * to prevent main thread blocking. Unit enrichment happens on-demand when adding to cart.
     */
    private fun loadCategories() {
        categoriesLoadJob?.cancel()
        categoriesLoadJob = viewModelScope.launch {
            categoriesLoading = true
            categoriesError = null
            try {
                val payload = buildCategorySections()
                categoryConfigs = payload.configs
                categorySections = payload.sections
                categoriesError = payload.errorMessage
            } catch (e: Exception) {
                categoriesError = e.message
                categorySections = emptyList()
            } finally {
                categoriesLoading = false
            }
        }
    }

    suspend fun ensureProductDetails(product: Product): Product? {
        if (!product.unitQuantities.isNullOrEmpty()) {
            return product
        }
        return runCatching { repo.ensureProductUnits(product) }.getOrNull()
    }

    /**
     * Reload categories from the synced cache after bootstrap completes.
     */
    private fun loadCategoriesFromCache() {
        categoriesLoadJob?.cancel()
        categoriesLoadJob = viewModelScope.launch {
            try {
                val payload = buildCategorySections()
                categoryConfigs = payload.configs
                categorySections = payload.sections
                categoriesError = payload.errorMessage
            } catch (_: Exception) {
                // Preserve the last rendered category state on cache reload failure.
            }
        }
    }

    private suspend fun buildCategorySections(): CategoryLoadPayload = withContext(Dispatchers.IO) {
        val configs = runCatching {
            categoryRepo.getCategories().map { cat ->
                CategoryConfig(id = cat.id, title = cat.name, slug = cat.slug)
            }
        }.getOrElse { error ->
            if (categoryConfigs.isNotEmpty()) {
                categoryConfigs
            } else {
                throw error
            }
        }

        val sections = ArrayList<CategorySection>(configs.size)
        var firstErrorMessage: String? = null

        configs.forEach { config ->
            runCatching {
                repo.getCategoryProducts(config.id, forceRefresh = false)
                    .sortedBy { it.name.lowercase() }
            }.onSuccess { products ->
                sections += CategorySection(
                    id = config.id,
                    title = config.title,
                    products = products
                )
            }.onFailure { error ->
                if (firstErrorMessage == null) {
                    firstErrorMessage = error.message
                }
            }
        }

        CategoryLoadPayload(
            configs = configs,
            sections = sections,
            errorMessage = firstErrorMessage
        )
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    cartViewModel: CartViewModel,
    searchViewModel: SearchViewModel,
    widthSizeClass: WindowWidthSizeClass,
    navController: NavController,
    currentRegister: Register?,
    onManageRegister: () -> Unit,
    storeName: String = "",
    pendingActionId: String? = null,
    actionNonce: Int = 0,
    onActionHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val vm = searchViewModel
    val cartState by cartViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var dialogData by remember { mutableStateOf<QuantityDialogData?>(null) }
    var loadingProductId by remember { mutableStateOf<Long?>(null) }
    var scannerVisible by remember { mutableStateOf(false) }
    var customDialogVisible by rememberSaveable { mutableStateOf(false) }
    var showCartSheet by rememberSaveable { mutableStateOf(false) }
    var containerDialogItem by remember { mutableStateOf<CartItem?>(null) }
    var customName by rememberSaveable { mutableStateOf("") }
    var customQuantity by rememberSaveable { mutableStateOf("1") }
    var customPrice by rememberSaveable { mutableStateOf("") }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }

    fun resetCustomFields() {
        customName = ""
        customQuantity = "1"
        customPrice = ""
    }

    fun clearCart() {
        cartState.items.forEach { item ->
            cartViewModel.removeItem(item.key)
        }
    }

    val onProductSelected: (Product) -> Unit = { product ->
        val needsFetch = product.unitQuantities.isNullOrEmpty()
        if (!needsFetch) {
            val units = product.unitQuantities.orEmpty()
            if (units.isEmpty()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_no_units_for, product.name),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val mergedDialog = buildQuantityDialogData(context, product)
                if (mergedDialog == null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_no_price_for, product.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    dialogData = mergedDialog
                }
            }
            if (query.length >= 2) {
                query = ""
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        } else if (loadingProductId == null) {
            scope.launch {
                loadingProductId = product.id
                val detailed = vm.ensureProductDetails(product) ?: product
                loadingProductId = null
                if (detailed.unitQuantities.isNullOrEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_no_units_for, detailed.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val mergedDialog = buildQuantityDialogData(context, detailed)
                    if (mergedDialog == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_no_price_for, detailed.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        dialogData = mergedDialog
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.refreshEvents.collectLatest { success ->
            val message = if (success) {
                context.getString(R.string.refresh_catalog_success)
            } else {
                val error = vm.categoriesError ?: context.getString(R.string.message_no_category_products)
                context.getString(R.string.refresh_catalog_failed, error)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { query.trim() }
            .distinctUntilChanged()
            .debounce(400)
            .collect { term -> vm.search(term) }
    }

    LaunchedEffect(actionNonce, pendingActionId) {
        when (pendingActionId) {
            "custom_item" -> {
                resetCustomFields()
                customDialogVisible = true
                showCartSheet = false
                onActionHandled()
            }

            "new_order" -> {
                query = ""
                showCartSheet = false
                keyboardController?.hide()
                focusManager.clearFocus()
                onActionHandled()
            }
        }
    }

    val showResults = query.trim().length >= 2
    val cartItemCount by remember(cartState.items) {
        derivedStateOf { cartState.items.size }
    }
    val horizontalPadding = if (widthSizeClass == WindowWidthSizeClass.Compact) 16.dp else 24.dp
    val pullRefreshState = rememberPullRefreshState(
        refreshing = vm.categoriesRefreshing,
        onRefresh = { vm.refreshCategories() }
    )

    LaunchedEffect(vm.categorySections) {
        if (vm.categorySections.isNotEmpty() && selectedCategoryIndex > vm.categorySections.lastIndex) {
            selectedCategoryIndex = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalPadding,
                    top = MaterialTheme.appSpacing.sm,
                    end = horizontalPadding,
                    bottom = if (cartItemCount > 0) 88.dp else MaterialTheme.appSpacing.sm
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
        ) {
            com.nexopos.erp.ui.components.SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.label_search_products),
                leadingIcon = Icons.Filled.Search,
                trailingIcon = Icons.Filled.QrCodeScanner,
                trailingDescription = stringResource(R.string.content_desc_scan_barcode),
                onTrailingClick = { scannerVisible = true },
                secondaryTrailingIcon = Icons.Filled.Add,
                secondaryTrailingDescription = stringResource(R.string.quick_action_custom_item),
                onSecondaryTrailingClick = {
                    resetCustomFields()
                    customDialogVisible = true
                },
                clearDescription = stringResource(R.string.clear_search_description),
                onClear = { query = "" }
            )
            if (currentRegister == null) {
                MissingRegisterCard(
                    title = stringResource(R.string.register_search_warning_title),
                    message = stringResource(R.string.register_search_warning_message),
                    onManageRegister = onManageRegister
                )
            }
            CategoryContent(
                sections = vm.categorySections,
                isLoading = vm.categoriesLoading && !vm.categoriesRefreshing,
                error = vm.categoriesError,
                selectedIndex = selectedCategoryIndex,
                onSelect = { selectedCategoryIndex = it },
                onProductSelected = onProductSelected,
                widthSizeClass = widthSizeClass,
                showResults = showResults,
                results = vm.results,
                resultsLoading = vm.isLoading,
                modifier = Modifier
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
            )
        }

        PullRefreshIndicator(
            refreshing = vm.categoriesRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        if (loadingProductId != null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.appColors.surface.copy(alpha = 0.55f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        if (scannerVisible) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.appColors.surface.copy(alpha = 0.97f)
            ) {
                Box(Modifier.fillMaxSize()) {
                    ScanScreen(
                        cartViewModel = cartViewModel,
                        onClose = { scannerVisible = false }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(MaterialTheme.appSpacing.md)
                            .size(48.dp)
                            .background(
                                MaterialTheme.appColors.elevated.copy(alpha = 0.94f),
                                CircleShape
                            )
                            .clickable { scannerVisible = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.content_desc_close_scanner),
                            tint = MaterialTheme.appColors.text
                        )
                    }
                }
            }
        }

        if (cartItemCount > 0) {
            MiniCartBar(
                itemCount = cartItemCount,
                total = cartState.total,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = horizontalPadding, vertical = MaterialTheme.appSpacing.md),
                onOpen = { showCartSheet = true }
            )
        }
    }

    if (showCartSheet && cartState.items.isNotEmpty()) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = {
                showCartSheet = false
            },
            shape = RoundedCornerShape(
                topStart = MaterialTheme.appRadii.xl,
                topEnd = MaterialTheme.appRadii.xl
            ),
            containerColor = MaterialTheme.appColors.surfaceOverlay
        ) {
            SellCartSheet(
                state = cartState,
                onClear = ::clearCart,
                onQuantityChange = { item, quantity -> cartViewModel.updateQuantity(item.key, quantity) },
                onPriceChange = { item, price -> cartViewModel.updateItemPrice(item.key, price) },
                onToggleWholesale = { item ->
                    cartViewModel.toggleWholesale(item.key, !item.useWholesale)
                },
                onEditContainer = { item ->
                    containerDialogItem = item
                },
                onRemove = { item -> cartViewModel.removeItem(item.key) },
                onPay = {
                    showCartSheet = false
                    navController.navigate(PosRoutes.CHECKOUT) { launchSingleTop = true }
                },
                onCancel = {
                    showCartSheet = false
                }
            )
        }
    }

    if (customDialogVisible) {
        AppDialog(
            onDismissRequest = {
                customDialogVisible = false
                resetCustomFields()
            },
            modifier = Modifier.widthIn(max = 450.dp),
            title = {
                Text(
                    text = stringResource(R.string.custom_item_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.text
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)) {
                    AppTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = stringResource(R.string.custom_item_name_label),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
                    ) {
                        AppTextField(
                            value = customQuantity,
                            onValueChange = { value ->
                                val cleaned = value.replace(',', '.')
                                if (cleaned.isEmpty() || Regex("^\\d*(?:\\.\\d{0,3})?$").matches(cleaned)) {
                                    customQuantity = cleaned
                                }
                            },
                            label = stringResource(R.string.custom_item_quantity_label),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true
                        )
                        AppTextField(
                            value = customPrice,
                            onValueChange = { value ->
                                val cleaned = value.replace(',', '.')
                                if (cleaned.isEmpty() || Regex("^\\d*(?:\\.\\d{0,3})?$").matches(cleaned)) {
                                    customPrice = cleaned
                                }
                            },
                            label = stringResource(R.string.custom_item_price_label),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                AppButtonPrimary(
                    onClick = {
                        val name = customName.trim()
                        val quantity = customQuantity.toDoubleOrNull() ?: 0.0
                        val price = customPrice.toDoubleOrNull() ?: 0.0
                        if (name.isEmpty() || quantity <= 0.0 || price < 0.0) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.custom_item_validation_error),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            cartViewModel.addProduct(
                                productId = 0L,
                                name = name,
                                unitQuantityId = null,
                                unitId = null,
                                unitName = null,
                                unitPrice = price,
                                quantity = quantity,
                                isCustomPrice = true
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_added_product, name),
                                Toast.LENGTH_SHORT
                            ).show()
                            customDialogVisible = false
                            resetCustomFields()
                        }
                    }
                ) {
                    Text(stringResource(R.string.custom_item_dialog_confirm))
                }
            },
            dismissButton = {
                AppButtonSecondary(
                    onClick = {
                        customDialogVisible = false
                        resetCustomFields()
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    dialogData?.let { data ->
        QuantityDialog(
            data = data,
            onConfirm = { result ->
                if (result.unitPrice <= 0.0) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_no_price_for, data.name),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    cartViewModel.addProduct(
                        productId = data.productId,
                        name = data.name,
                        unitQuantityId = result.option.unitQuantityId,
                        unitId = result.option.unitId,
                        unitName = result.option.unitName,
                        unitPrice = result.unitPrice,
                        quantity = result.quantity,
                        salePrice = result.option.salePrice?.takeIf { it > 0.0 },
                        wholesalePriceWithTax = result.option.wholesalePriceWithTax?.takeIf { it > 0.0 },
                        useWholesale = result.useWholesale,
                        isCustomPrice = false,
                        containerLink = result.option.containerLink,
                        hasContainerMetadata = result.option.containerLink != null
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_added_product, data.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    dialogData = null
                }
            },
            onDismiss = { dialogData = null }
        )
    }

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
private fun MiniCartBar(
    itemCount: Int,
    total: Double,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaterialTheme.appRadii.xl),
        color = MaterialTheme.appColors.elevated,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        onClick = onOpen
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.appSpacing.md, vertical = MaterialTheme.appSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xxs)) {
                Text(
                    text = stringResource(R.string.sell_cart_items_total, itemCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.appColors.text
                )
                Text(
                    text = formatMoney(total),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.posColors.priceText
                )
            }
            AppButtonPrimary(
                onClick = onOpen,
                modifier = Modifier.height(48.dp)
            ) {
                Text(stringResource(R.string.sell_cart_open))
            }
        }
    }
}

@Composable
private fun SellCartSheet(
    state: CartState,
    onClear: () -> Unit,
    onQuantityChange: (CartItem, Double) -> Unit,
    onPriceChange: (CartItem, Double) -> Unit,
    onToggleWholesale: (CartItem) -> Unit,
    onEditContainer: (CartItem) -> Unit,
    onRemove: (CartItem) -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.appSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sell_cart_sheet_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.appColors.text
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)) {
                AppButtonTertiary(onClick = onClear) {
                    Text(stringResource(R.string.sell_action_clear))
                }
                AppButtonTertiary(onClick = onCancel) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
        ) {
            items(state.items, key = { it.key }) { item ->
                SellCartLine(
                    item = item,
                    onQuantityChange = { quantity -> onQuantityChange(item, quantity) },
                    onPriceChange = { price -> onPriceChange(item, price) },
                    onToggleWholesale = { onToggleWholesale(item) },
                    onEditContainer = { onEditContainer(item) },
                    onRemove = { onRemove(item) }
                )
            }
        }
        AppButtonPrimary(
            onClick = onPay,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(stringResource(R.string.sell_pay_cta, formatMoney(state.total)))
        }
        Spacer(modifier = Modifier.height(MaterialTheme.appSpacing.xl))
    }
}

@Composable
private fun SellCartLine(
    item: CartItem,
    onQuantityChange: (Double) -> Unit,
    onPriceChange: (Double) -> Unit,
    onToggleWholesale: () -> Unit,
    onEditContainer: () -> Unit,
    onRemove: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val quantityPattern = remember { Regex("^\\d*(?:\\.\\d{0,4})?$") }
    val pricePattern = remember { Regex("^\\d*(?:\\.\\d{0,3})?$") }
    var quantityField by rememberSaveable(item.key + "_sell_qty", stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(formatSellQuantity(item.quantity)))
    }
    var priceField by rememberSaveable(item.key + "_sell_price", stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(formatSellPrice(item.unitPrice)))
    }
    var priceFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(item.quantity) {
        val formatted = formatSellQuantity(item.quantity)
        if (quantityField.text != formatted) {
            quantityField = TextFieldValue(formatted)
        }
    }

    LaunchedEffect(item.unitPrice) {
        val formatted = formatSellPrice(item.unitPrice)
        if (!priceFieldFocused && priceField.text != formatted) {
            priceField = TextFieldValue(formatted)
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.appColors.danger.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(MaterialTheme.appRadii.lg)
                    )
                    .padding(horizontal = MaterialTheme.appSpacing.md),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cart_item_remove),
                    tint = MaterialTheme.appColors.danger
                )
            }
        }
    ) {
        AppCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.name + (item.unitName?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.appColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatMoney(item.lineTotal),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.posColors.priceText,
                        modifier = Modifier.padding(start = MaterialTheme.appSpacing.sm)
                    )
                }
                ContainerTrackingSummary(
                    item = item,
                    onClick = onEditContainer
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SellCartNumberField(
                        value = priceField,
                        onValueChange = { priceField = it },
                        pattern = pricePattern,
                        label = stringResource(R.string.custom_item_price_label),
                        onValidChange = onPriceChange,
                        focusManager = focusManager,
                        modifier = Modifier.weight(1f),
                        onFocusChanged = { focused ->
                            priceFieldFocused = focused
                            if (!focused) {
                                val formatted = formatSellPrice(item.unitPrice)
                                if (priceField.text != formatted) {
                                    priceField = TextFieldValue(formatted)
                                }
                            }
                        }
                    )
                    SellCartNumberField(
                        value = quantityField,
                        onValueChange = { quantityField = it },
                        pattern = quantityPattern,
                        label = stringResource(R.string.custom_item_quantity_label),
                        onValidChange = onQuantityChange,
                        focusManager = focusManager,
                        modifier = Modifier.weight(1f),
                        onFocusChanged = {}
                    )
                    if ((item.wholesalePriceWithTax ?: 0.0) > 0.0) {
                        OutlinedButton(
                            onClick = onToggleWholesale,
                            modifier = Modifier.widthIn(min = 104.dp),
                            shape = RoundedCornerShape(MaterialTheme.appRadii.lg),
                            contentPadding = PaddingValues(
                                horizontal = MaterialTheme.appSpacing.sm,
                                vertical = MaterialTheme.appSpacing.xs
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = if (item.useWholesale) MaterialTheme.appColors.primary else MaterialTheme.appColors.muted
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.appSpacing.xxs))
                            Text(
                                text = if (item.useWholesale) {
                                    stringResource(R.string.sell_price_wholesale)
                                } else {
                                    stringResource(R.string.sell_price_retail)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (item.useWholesale) MaterialTheme.appColors.primary else MaterialTheme.appColors.muted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SellCartNumberField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    pattern: Regex,
    label: String,
    onValidChange: (Double) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
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
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .heightIn(min = 56.dp)
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
                if (focusState.isFocused) {
                    onValueChange(value.copy(selection = TextRange(0, value.text.length)))
                }
            }
    )
}

@Composable
private fun CategoryContent(
    sections: List<CategorySection>,
    isLoading: Boolean,
    error: String?,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onProductSelected: (Product) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    showResults: Boolean,
    results: List<Product>,
    resultsLoading: Boolean,
    modifier: Modifier = Modifier
) {
    com.nexopos.erp.ui.components.AppCard(modifier = modifier.fillMaxWidth()) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.appSpacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            !error.isNullOrBlank() -> {
                Text(text = error, color = MaterialTheme.appColors.danger)
            }

            sections.isEmpty() -> {
                Text(text = stringResource(R.string.message_no_category_products))
            }

            else -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.s)
                ) {
                    items(sections.mapIndexed { index, section -> index to section.title }) { (index, title) ->
                        AppChipFilter(
                            selected = selectedIndex.coerceIn(0, sections.lastIndex) == index,
                            onClick = { onSelect(index) },
                            label = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                val gridProducts = if (showResults) results else sections[selectedIndex.coerceIn(0, sections.lastIndex)].products
                val isCompact = widthSizeClass == WindowWidthSizeClass.Compact
                val columns = if (isCompact) 2 else 3
                if (showResults && resultsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = MaterialTheme.appSpacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (gridProducts.isEmpty()) {
                    Text(
                        text = if (showResults) stringResource(R.string.message_no_results) else stringResource(R.string.message_no_category_products),
                        color = MaterialTheme.appColors.muted
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 120.dp, top = MaterialTheme.appSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.appSpacing.sm)
                    ) {
                        items(gridProducts, key = { it.id }) { product ->
                            ProductTile(product = product, onClick = { onProductSelected(product) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductTile(
    product: Product,
    onClick: () -> Unit
) {
    val salePrice = product.unitQuantities?.firstOrNull()?.salePrice?.takeIf { it > 0.0 }
    val wholesalePrice = product.unitQuantities?.firstOrNull()?.wholesalePriceWithTax?.takeIf { it > 0.0 }
    val primaryPrice = salePrice ?: wholesalePrice
    val stockText = stockLabel(product)
    com.nexopos.erp.ui.components.AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Text(
            text = product.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.appColors.text,
            maxLines = 2
        )
        stockText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.muted
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = primaryPrice?.let(::formatMoney) ?: stringResource(R.string.sell_no_price),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.posColors.priceText
            )
            androidx.compose.material3.FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.button_add)
                )
            }
        }
    }
}

@Composable
private fun rememberConnectivityState(): androidx.compose.runtime.State<Boolean> {
    val context = LocalContext.current
    val state = remember { androidx.compose.runtime.mutableStateOf(isOnline(context)) }
    androidx.compose.runtime.DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                state.value = true
            }

            override fun onLost(network: android.net.Network) {
                state.value = isOnline(context)
            }
        }
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
        onDispose {
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
    }
    return state
}

private fun isOnline(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun stockLabel(product: Product): String? {
    val stock = product.stockQuantity ?: return null
    val threshold = product.lowStockThreshold ?: 0
    return when {
        stock <= 0 -> "Out of stock"
        threshold > 0 && stock <= threshold -> "Low stock: ${formatSellQuantity(stock)}"
        else -> "Stock: ${formatSellQuantity(stock)}"
    }
}

private fun formatMoney(value: Double): String {
    return formatAppCurrency(value)
}

private fun formatSellQuantity(value: Double): String {
    return formatAppQuantity(value, maxDecimals = 3)
}

private fun formatSellPrice(value: Double): String {
    return String.format(Locale.getDefault(), "%.3f", value)
}
