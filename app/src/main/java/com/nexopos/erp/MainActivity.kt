@file:OptIn(ExperimentalMaterial3WindowSizeClassApi::class)

package com.nexopos.erp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.repo.AuthState
import com.nexopos.erp.core.sync.OfflineOrderSyncWorker
import com.nexopos.erp.feature.auth.AuthViewModel
import com.nexopos.erp.feature.auth.LoginScreen
import com.nexopos.erp.feature.catalog.CatalogRoutes
import com.nexopos.erp.feature.containermanagement.ContainerRoutes
import com.nexopos.erp.feature.home.HomeRoutes
import com.nexopos.erp.feature.home.ui.HomeScreen
import com.nexopos.erp.feature.inventory.InventoryRoutes
import com.nexopos.erp.feature.manufacturing.ManufacturingRoutes
import com.nexopos.erp.feature.more.MoreRoutes
import com.nexopos.erp.feature.more.ui.MoreScreen
import com.nexopos.erp.feature.orders.OrdersRoutes
import com.nexopos.erp.feature.orders.ui.OrderDetailScreen
import com.nexopos.erp.feature.orders.ui.OrdersScreen
import com.nexopos.erp.feature.orders.vm.OrdersListItem
import com.nexopos.erp.feature.orders.vm.OrdersViewModel
import com.nexopos.erp.feature.procurement.ProcurementRoutes
import com.nexopos.erp.feature.pricelookup.PriceLookupRoutes
import com.nexopos.erp.feature.pricelookup.ui.PriceLookupDetailScreen
import com.nexopos.erp.feature.pricelookup.ui.PriceLookupScanScreen
import com.nexopos.erp.feature.pricelookup.vm.PriceLookupDetailViewModel
import com.nexopos.erp.feature.salespos.PosRoutes
import com.nexopos.erp.feature.salespos.ui.CartItemsScreen
import com.nexopos.erp.feature.salespos.ui.CartViewModel
import com.nexopos.erp.feature.salespos.ui.CheckoutScreen
import com.nexopos.erp.feature.salespos.ui.RegisterHost
import com.nexopos.erp.feature.salespos.ui.RegisterViewModel
import com.nexopos.erp.feature.salespos.ui.ScanScreen
import com.nexopos.erp.feature.salespos.ui.SearchScreen
import com.nexopos.erp.feature.salespos.ui.SearchViewModel
import com.nexopos.erp.feature.scanner.ScannerFeature
import com.nexopos.erp.feature.scanner.ScannerRoutes
import com.nexopos.erp.feature.scanner.ui.BarcodeScannerView
import com.nexopos.erp.feature.scanner.ui.ScannerHomeScreen
import com.nexopos.erp.feature.scanner.ui.ScannerProductDetailScreen
import com.nexopos.erp.feature.scanner.ui.ScannerProductEditorScreen
import com.nexopos.erp.feature.scanner.vm.ScannerLookupViewModel
import com.nexopos.erp.feature.scanner.vm.ScannerProductDetailViewModel
import com.nexopos.erp.feature.scanner.vm.ScannerProductEditorViewModel
import com.nexopos.erp.feature.settings.SettingsRoutes
import com.nexopos.erp.feature.settings.ui.SettingsScreen
import com.nexopos.erp.feature.specialcustomer.CustomerRoutes
import com.nexopos.erp.ui.components.AppSwitcherSheet
import com.nexopos.erp.ui.components.HubActionItem
import com.nexopos.erp.ui.components.AppTopBar
import com.nexopos.erp.ui.components.ShellDestination
import com.nexopos.erp.ui.theme.AppTheme
import com.nexopos.erp.ui.theme.appColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val SettingsRoute = SettingsRoutes.SETTINGS

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        OfflineOrderSyncWorker.schedule(applicationContext)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val settingsRepository: SettingsRepository = koinInject()
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = "system")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            AppTheme(darkTheme = darkTheme) {
                MainAppContent(windowSizeClass = windowSizeClass)
            }
        }
    }
}

@Composable
private fun MainAppContent(windowSizeClass: WindowSizeClass) {
    val authViewModel: AuthViewModel = koinViewModel()
    val authState by authViewModel.authState.collectAsState()

    when (authState) {
        is AuthState.Authenticated -> AppScaffold(windowSizeClass = windowSizeClass)
        is AuthState.Unauthenticated,
        is AuthState.SessionExpired,
        is AuthState.Error -> LoginScreen(
            onLoginSuccess = {},
            viewModel = authViewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val settingsRepository: SettingsRepository = koinInject()
    val cartViewModel: CartViewModel = koinViewModel()
    val searchViewModel: SearchViewModel = koinViewModel()
    val registerViewModel: RegisterViewModel = koinViewModel()
    val registerUiState by registerViewModel.uiState.collectAsState()
    val ordersViewModel: OrdersViewModel = koinViewModel()
    val registerName by settingsRepository.storeNameFlow.collectAsState(initial = SettingsRepository.DEFAULT_STORE_NAME)
    val isOnline by rememberShellConnectivityState()
    var switcherVisible by remember { mutableStateOf(false) }
    var registerHostVisible by remember { mutableStateOf(false) }

    val destinations = remember {
        listOf(
            ShellDestination.sell(PosRoutes.SEARCH),
            ShellDestination.orders(OrdersRoutes.ORDERS),
            ShellDestination.products(InventoryRoutes.PRODUCTS),
            ShellDestination.customers(CustomerRoutes.CUSTOMERS),
            ShellDestination.more(MoreRoutes.MORE)
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSellRoute = currentRoute in sellRoutes
    val showQuickSwitch = currentRoute in quickSwitchRoutes
    val showHomeIcon = currentRoute in primaryModuleRoutes
    val showShellTopBar = currentRoute !in routesWithDedicatedTopBar
    val sellSubtitle = if (currentRoute == PosRoutes.SEARCH || currentRoute == CatalogRoutes.SEARCH) {
        registerName.takeIf { it.isNotBlank() }
    } else {
        null
    }

    LaunchedEffect(Unit) {
        registerViewModel.loadIfNeeded()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showShellTopBar) {
                AppTopBar(
                    title = stringResource(routeTitle(currentRoute)),
                    subtitle = sellSubtitle,
                    navigationIcon = when {
                        showHomeIcon -> {
                            {
                                IconButton(
                                    onClick = {
                                        val popped = navController.popBackStack(HomeRoutes.HOME, false)
                                        if (!popped) {
                                            navController.navigate(HomeRoutes.HOME) {
                                                popUpTo(navController.graph.findStartDestination().id)
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Home,
                                        contentDescription = stringResource(R.string.nav_home)
                                    )
                                }
                            }
                        }
                        currentRoute != HomeRoutes.HOME -> {
                            {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.back)
                                    )
                                }
                            }
                        }
                        else -> null
                    },
                    actions = {
                        if (isSellRoute) {
                            IconButton(onClick = { registerHostVisible = true }) {
                                Icon(
                                    imageVector = Icons.Filled.PointOfSale,
                                    contentDescription = stringResource(R.string.register_manage_action),
                                    tint = if (registerUiState.currentRegister != null) {
                                        MaterialTheme.appColors.success
                                    } else {
                                        MaterialTheme.appColors.warning
                                    }
                                )
                            }
                        }

                        if (currentRoute == OrdersRoutes.ORDERS) {
                            IconButton(onClick = { ordersViewModel.refreshFromServer() }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.button_refresh)
                                )
                            }
                        }

                        if (showQuickSwitch) {
                            IconButton(onClick = { switcherVisible = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.quick_actions_title)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = HomeRoutes.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(HomeRoutes.HOME) {
                    val featureFlags: com.nexopos.erp.core.prefs.FeatureFlags = koinInject()
                    HomeScreen(
                        onNavigateToPos = { navController.navigate(PosRoutes.SEARCH) { launchSingleTop = true } },
                        onNavigateToOrders = { navController.navigate(OrdersRoutes.ORDERS) { launchSingleTop = true } },
                        onNavigateToInventory = { navController.navigate(InventoryRoutes.PRODUCTS) { launchSingleTop = true } },
                        onNavigateToScanner = { navController.navigate(ScannerRoutes.HOME) { launchSingleTop = true }},
                        onNavigateToSpecialCustomer = { navController.navigate(CustomerRoutes.CUSTOMERS) { launchSingleTop = true } },
                        onNavigateToContainers = { navController.navigate(ContainerRoutes.INVENTORY) { launchSingleTop = true } },
                        onNavigateToManufacturing = { navController.navigate(ManufacturingRoutes.PRODUCTION) { launchSingleTop = true } },
                        onNavigateToProcurements = { navController.navigate(ProcurementRoutes.PROCUREMENTS) { launchSingleTop = true } },
                        onNavigateToSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                        onNavigateToPriceLookup = { navController.navigate(PriceLookupRoutes.HOME) { launchSingleTop = true } }
                    )
                }

                composable(SettingsRoute) {
                    SettingsScreen()
                }

                composable(PosRoutes.SEARCH) {
                    SearchScreen(
                        cartViewModel = cartViewModel,
                        searchViewModel = searchViewModel,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        navController = navController,
                        currentRegister = registerUiState.currentRegister,
                        isOnline = isOnline,
                        onManageRegister = { registerHostVisible = true },
                        storeName = registerName,
                        pendingActionId = null,
                        actionNonce = 0,
                        onActionHandled = {}
                    )
                }

                composable(PosRoutes.SCAN) {
                    ScanScreen(cartViewModel) { navController.popBackStack() }
                }

                composable(PosRoutes.CART) {
                    CartItemsScreen(
                        cartViewModel = cartViewModel,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        onNavigateToSearch = {
                            navController.popBackStack(PosRoutes.SEARCH, inclusive = false)
                        },
                        onNavigateToCheckout = {
                            navController.navigate(PosRoutes.CHECKOUT) { launchSingleTop = true }
                        }
                    )
                }

                composable(PosRoutes.CHECKOUT) {
                    CheckoutScreen(
                        navController = navController,
                        cartViewModel = cartViewModel,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        currentRegister = registerUiState.currentRegister,
                        isOnline = isOnline,
                        onManageRegister = { registerHostVisible = true },
                        registerViewModel = registerViewModel
                    )
                }

                composable(CatalogRoutes.SEARCH) {
                    SearchScreen(
                        cartViewModel = cartViewModel,
                        searchViewModel = searchViewModel,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        navController = navController,
                        currentRegister = registerUiState.currentRegister,
                        isOnline = isOnline,
                        onManageRegister = { registerHostVisible = true },
                        storeName = registerName,
                        pendingActionId = null,
                        actionNonce = 0,
                        onActionHandled = {}
                    )
                }

                composable(CatalogRoutes.SCAN) {
                    ScanScreen(cartViewModel) { navController.popBackStack() }
                }

                composable(ScannerRoutes.HOME) {
                    val lookupViewModel: ScannerLookupViewModel = koinViewModel()
                    LaunchedEffect(Unit) {
                        lookupViewModel.events.collect { event ->
                            when (event) {
                                is com.nexopos.erp.feature.scanner.vm.ScannerLookupEvent.ProductFound -> {
                                    navController.navigate(ScannerRoutes.detail(event.productId)) {
                                        launchSingleTop = true
                                    }
                                }
                                is com.nexopos.erp.feature.scanner.vm.ScannerLookupEvent.ProductMissing -> {
                                    navController.navigate(ScannerRoutes.create(event.barcode)) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }
                    ScannerHomeScreen(
                        onScan = { navController.navigate(ScannerRoutes.SCAN) { launchSingleTop = true } },
                        onCreateProduct = { navController.navigate(ScannerRoutes.create()) { launchSingleTop = true } },
                        onManualEntry = lookupViewModel::lookupBarcode
                    )
                }

                composable(ScannerRoutes.SCAN) {
                    val lookupViewModel: ScannerLookupViewModel = koinViewModel()
                    val lookupState by lookupViewModel.state.collectAsState()
                    var showManualDialog by remember { mutableStateOf(false) }
                    var manualBarcode by remember { mutableStateOf("") }
                    LaunchedEffect(Unit) {
                        lookupViewModel.events.collect { event ->
                            when (event) {
                                is com.nexopos.erp.feature.scanner.vm.ScannerLookupEvent.ProductFound -> {
                                    navController.navigate(ScannerRoutes.detail(event.productId)) {
                                        popUpTo(ScannerRoutes.SCAN) { inclusive = true }
                                    }
                                }
                                is com.nexopos.erp.feature.scanner.vm.ScannerLookupEvent.ProductMissing -> {
                                    navController.navigate(ScannerRoutes.create(event.barcode)) {
                                        popUpTo(ScannerRoutes.SCAN) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                    BarcodeScannerView(
                        onBarcode = lookupViewModel::lookupBarcode,
                        onManualEntry = { showManualDialog = true }
                    )
                    if (showManualDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showManualDialog = false },
                            title = { androidx.compose.material3.Text(stringResource(R.string.scanner_manual_entry_title)) },
                            text = {
                                com.nexopos.erp.ui.components.AppTextField(
                                    value = manualBarcode,
                                    onValueChange = { manualBarcode = it },
                                    label = stringResource(R.string.scanner_label_barcode),
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                com.nexopos.erp.ui.components.AppButtonPrimary(onClick = {
                                    lookupViewModel.lookupBarcode(manualBarcode)
                                    showManualDialog = false
                                    manualBarcode = ""
                                }) {
                                    androidx.compose.material3.Text(stringResource(R.string.scanner_lookup_button))
                                }
                            },
                            dismissButton = {
                                com.nexopos.erp.ui.components.AppButtonSecondary(onClick = { showManualDialog = false }) {
                                    androidx.compose.material3.Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                    lookupState.error?.let { error ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = androidx.compose.ui.Alignment.BottomCenter
                        ) {
                            androidx.compose.material3.Surface(
                                color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ) {
                                androidx.compose.material3.Text(
                                    text = error,
                                    modifier = Modifier.padding(16.dp),
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                composable(PriceLookupRoutes.HOME) {
                    PriceLookupScanScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProduct = { productId ->
                            navController.navigate(PriceLookupRoutes.detail(productId)) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(
                    route = PriceLookupRoutes.DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val productId = backStackEntry.arguments?.getLong("id") ?: 0L
                    val viewModel: PriceLookupDetailViewModel = koinViewModel()
                    PriceLookupDetailScreen(
                        productId = productId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = ScannerRoutes.PRODUCT_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val productId = backStackEntry.arguments?.getLong("id") ?: 0L
                    val detailViewModel: ScannerProductDetailViewModel = koinViewModel()
                    ScannerProductDetailScreen(
                        productId = productId,
                        viewModel = detailViewModel,
                        onEdit = { navController.navigate(ScannerRoutes.edit(it)) }
                    )
                }

                composable(
                    route = ScannerRoutes.PRODUCT_CREATE,
                    arguments = listOf(navArgument("barcode") {
                        type = NavType.StringType
                        defaultValue = ""
                    })
                ) { backStackEntry ->
                    val barcode = backStackEntry.arguments?.getString("barcode")
                    val editorViewModel: ScannerProductEditorViewModel = koinViewModel()
                    ScannerProductEditorScreen(
                        productId = null,
                        barcode = barcode,
                        viewModel = editorViewModel,
                        onSaved = { productId ->
                            navController.navigate(ScannerRoutes.detail(productId)) {
                                popUpTo(ScannerRoutes.PRODUCT_CREATE) { inclusive = true }
                            }
                            editorViewModel.clearSavedProduct()
                        }
                    )
                }

                composable(
                    route = ScannerRoutes.PRODUCT_EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val productId = backStackEntry.arguments?.getLong("id")
                    val editorViewModel: ScannerProductEditorViewModel = koinViewModel()
                    ScannerProductEditorScreen(
                        productId = productId,
                        barcode = null,
                        viewModel = editorViewModel,
                        onSaved = { savedId ->
                            navController.navigate(ScannerRoutes.detail(savedId)) {
                                popUpTo(ScannerRoutes.PRODUCT_EDIT) { inclusive = true }
                            }
                            editorViewModel.clearSavedProduct()
                        }
                    )
                }

                composable(OrdersRoutes.ORDERS) {
                    OrdersScreen(
                        viewModel = ordersViewModel,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        onOrderClick = { item ->
                            navController.navigate(OrdersRoutes.detail(item.id))
                        },
                        onEditOrder = { orderItem ->
                            loadOrderIntoCart(cartViewModel = cartViewModel, orderItem = orderItem)
                            navController.navigate(PosRoutes.CART) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    )
                }

                composable(
                    route = OrdersRoutes.DETAIL,
                    arguments = listOf(navArgument("orderId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getLong("orderId") ?: 0L
                    OrderDetailScreen(
                        orderId = orderId,
                        viewModel = ordersViewModel,
                        onBack = { navController.popBackStack() },
                        onEdit = { orderItem ->
                            loadOrderIntoCart(cartViewModel = cartViewModel, orderItem = orderItem)
                            navController.navigate(PosRoutes.CART) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(CustomerRoutes.CUSTOMERS) {
                    val customerListViewModel: com.nexopos.erp.feature.specialcustomer.vm.CustomerListViewModel = koinViewModel()
                    val customerListState by customerListViewModel.state.collectAsState()
                    com.nexopos.erp.feature.specialcustomer.ui.CustomerListScreen(
                        customers = customerListState.customers,
                        isLoading = customerListState.isLoading,
                        isRefreshing = customerListState.isRefreshing,
                        error = customerListState.error,
                        onRefresh = { customerListViewModel.refresh() },
                        onCustomerClick = { customerId ->
                            navController.navigate(CustomerRoutes.dashboard(customerId))
                        }
                    )
                }

                composable(
                    route = CustomerRoutes.DASHBOARD,
                    arguments = listOf(navArgument("customerId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getLong("customerId") ?: 0L
                    val dashboardViewModel: com.nexopos.erp.feature.specialcustomer.vm.CustomerDashboardViewModel = koinViewModel()
                    com.nexopos.erp.feature.specialcustomer.ui.CustomerDashboardScreen(
                        customerId = customerId,
                        viewModel = dashboardViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(MoreRoutes.MORE) {
                    MoreScreen(
                        onNavigateToSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                        onNavigateToManufacturing = { navController.navigate(ManufacturingRoutes.PRODUCTION) { launchSingleTop = true } },
                        onNavigateToProcurement = { navController.navigate(ProcurementRoutes.PROCUREMENTS) { launchSingleTop = true } },
                        onNavigateToInventory = { navController.navigate(InventoryRoutes.PRODUCTS) { launchSingleTop = true } },
                        onNavigateToContainers = { navController.navigate(ContainerRoutes.INVENTORY) { launchSingleTop = true } },
                        onSyncCatalog = { searchViewModel.refreshCatalog() }
                    )
                }

                composable(ManufacturingRoutes.PRODUCTION) {
                    val manufacturingViewModel: com.nexopos.erp.feature.manufacturing.vm.ManufacturingViewModel = koinViewModel()
                    val featureFlags: com.nexopos.erp.core.prefs.FeatureFlags = koinInject()
                    com.nexopos.erp.feature.manufacturing.ui.ProductionListScreen(
                        viewModel = manufacturingViewModel,
                        featureFlags = featureFlags,
                        canCreate = true,
                        onBack = { navController.popBackStack() },
                        onCreateClick = {},
                        onOrderClick = { orderId -> navController.navigate(ManufacturingRoutes.productionEdit(orderId)) },
                        onBomsClick = { navController.navigate(ManufacturingRoutes.BOMS) }
                    )
                }

                composable(
                    route = ManufacturingRoutes.PRODUCTION_EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getLong("id") ?: 0L
                    val productionOrderEditViewModel: com.nexopos.erp.feature.manufacturing.vm.ProductionOrderEditViewModel = koinViewModel()
                    com.nexopos.erp.feature.manufacturing.ui.ProductionOrderEditScreen(
                        orderId = orderId,
                        viewModel = productionOrderEditViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ManufacturingRoutes.BOMS) {
                    val manufacturingViewModel: com.nexopos.erp.feature.manufacturing.vm.ManufacturingViewModel = koinViewModel()
                    com.nexopos.erp.feature.manufacturing.ui.BomListScreen(
                        viewModel = manufacturingViewModel,
                        canCreate = true,
                        onBack = { navController.popBackStack() },
                        onCreateClick = {},
                        onBomClick = { bomId -> navController.navigate(ManufacturingRoutes.bomEdit(bomId)) }
                    )
                }

                composable(
                    route = ManufacturingRoutes.BOM_EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val bomId = backStackEntry.arguments?.getLong("id") ?: 0L
                    val bomItemsEditViewModel: com.nexopos.erp.feature.manufacturing.vm.BomItemsEditViewModel = koinViewModel()
                    com.nexopos.erp.feature.manufacturing.ui.BomItemsEditScreen(
                        bomId = bomId,
                        viewModel = bomItemsEditViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ProcurementRoutes.PROCUREMENTS) {
                    val procurementViewModel: com.nexopos.erp.feature.procurement.vm.ProcurementViewModel = koinViewModel()
                    val featureFlags: com.nexopos.erp.core.prefs.FeatureFlags = koinInject()
                    com.nexopos.erp.feature.procurement.ui.ProcurementListScreen(
                        viewModel = procurementViewModel,
                        featureFlags = featureFlags,
                        canCreate = true,
                        onBack = { navController.popBackStack() },
                        onCreateClick = { navController.navigate(ProcurementRoutes.NEW) },
                        onProcurementClick = { procurementId -> navController.navigate(ProcurementRoutes.detail(procurementId)) }
                    )
                }

                composable(ProcurementRoutes.NEW) {
                    val procurementViewModel: com.nexopos.erp.feature.procurement.vm.ProcurementViewModel = koinViewModel()
                    com.nexopos.erp.feature.procurement.ui.ProcurementFormScreen(
                        viewModel = procurementViewModel,
                        onSaveSuccess = { procurementId ->
                            navController.navigate(ProcurementRoutes.detail(procurementId)) {
                                popUpTo(ProcurementRoutes.NEW) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }

                composable(
                    route = ProcurementRoutes.DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { backStackEntry ->
                    val procurementId = backStackEntry.arguments?.getLong("id") ?: 0L
                    val procurementViewModel: com.nexopos.erp.feature.procurement.vm.ProcurementViewModel = koinViewModel()
                    com.nexopos.erp.feature.procurement.ui.ProcurementDetailScreen(
                        procurementId = procurementId,
                        viewModel = procurementViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ContainerRoutes.INVENTORY) {
                    val containerInventoryViewModel: com.nexopos.erp.feature.containermanagement.vm.ContainerInventoryViewModel = koinViewModel()
                    com.nexopos.erp.feature.containermanagement.ui.ContainerInventoryScreen(
                        viewModel = containerInventoryViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToMovements = { typeId -> navController.navigate(ContainerRoutes.movements(typeId)) }
                    )
                }

                composable(
                    route = ContainerRoutes.MOVEMENTS,
                    arguments = listOf(navArgument("typeId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val typeId = backStackEntry.arguments?.getLong("typeId") ?: 0L
                    val containerMovementsViewModel: com.nexopos.erp.feature.containermanagement.vm.ContainerMovementsViewModel = koinViewModel()
                    com.nexopos.erp.feature.containermanagement.ui.ContainerMovementsScreen(
                        typeId = typeId,
                        viewModel = containerMovementsViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(ContainerRoutes.CONTAINERS) {
                    val containerInventoryViewModel: com.nexopos.erp.feature.containermanagement.vm.ContainerInventoryViewModel = koinViewModel()
                    com.nexopos.erp.feature.containermanagement.ui.ContainerInventoryScreen(
                        viewModel = containerInventoryViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToMovements = { typeId -> navController.navigate(ContainerRoutes.movements(typeId)) }
                    )
                }

                composable(InventoryRoutes.PRODUCTS) {
                    val inventoryViewModel: com.nexopos.erp.feature.inventory.vm.InventoryViewModel = koinViewModel()
                    val featureFlags: com.nexopos.erp.core.prefs.FeatureFlags = koinInject()
                    com.nexopos.erp.feature.inventory.ui.InventoryListScreen(
                        viewModel = inventoryViewModel,
                        featureFlags = featureFlags,
                        canAdjust = com.nexopos.erp.feature.inventory.InventoryFeature.FeatureFlags.canAdjustStock(featureFlags),
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(InventoryRoutes.INVENTORY) {
                    val inventoryViewModel: com.nexopos.erp.feature.inventory.vm.InventoryViewModel = koinViewModel()
                    val featureFlags: com.nexopos.erp.core.prefs.FeatureFlags = koinInject()
                    com.nexopos.erp.feature.inventory.ui.InventoryListScreen(
                        viewModel = inventoryViewModel,
                        featureFlags = featureFlags,
                        canAdjust = com.nexopos.erp.feature.inventory.InventoryFeature.FeatureFlags.canAdjustStock(featureFlags),
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            RegisterHost(
                registerViewModel = registerViewModel,
                isOnline = isOnline,
                visible = registerHostVisible,
                onDismissRequest = { registerHostVisible = false }
            )
        }
    }

    if (switcherVisible) {
        val switcherDestinations = emptyList<ShellDestination>()
        val hubPrimary = listOf(
            HubActionItem(
                id = "sync",
                labelRes = R.string.more_sync_title,
                icon = Icons.Filled.CloudSync,
                subtitleRes = R.string.more_sync_subtitle
            ) {
                switcherVisible = false
                searchViewModel.refreshCatalog()
                registerViewModel.refreshAll()
            },
            HubActionItem(
                id = "orders",
                labelRes = R.string.shell_tab_orders,
                icon = Icons.AutoMirrored.Filled.ReceiptLong
            ) {
                switcherVisible = false
                navController.navigate(OrdersRoutes.ORDERS) { launchSingleTop = true }
            },
            HubActionItem(
                id = "products",
                labelRes = R.string.shell_tab_products,
                icon = Icons.Filled.Inventory
            ) {
                switcherVisible = false
                navController.navigate(InventoryRoutes.PRODUCTS) { launchSingleTop = true }
            },
            HubActionItem(
                id = "customers",
                labelRes = R.string.shell_tab_customers,
                icon = Icons.Filled.People
            ) {
                switcherVisible = false
                navController.navigate(CustomerRoutes.CUSTOMERS) { launchSingleTop = true }
            },
            HubActionItem(
                id = "containers",
                labelRes = R.string.containers,
                icon = Icons.Filled.Folder,
                subtitleRes = R.string.more_containers_subtitle
            ) {
                switcherVisible = false
                navController.navigate(ContainerRoutes.INVENTORY) { launchSingleTop = true }
            },
            HubActionItem(
                id = "production",
                labelRes = R.string.nav_manufacturing,
                icon = Icons.Filled.Build,
                subtitleRes = R.string.more_manufacturing_subtitle
            ) {
                switcherVisible = false
                navController.navigate(ManufacturingRoutes.PRODUCTION) { launchSingleTop = true }
            },
            HubActionItem(
                id = "procurements",
                labelRes = R.string.nav_procurement,
                icon = Icons.Filled.LocalShipping,
                subtitleRes = R.string.more_procurement_subtitle
            ) {
                switcherVisible = false
                navController.navigate(ProcurementRoutes.PROCUREMENTS) { launchSingleTop = true }
            },
            HubActionItem(
                id = "settings",
                labelRes = R.string.nav_settings,
                icon = Icons.Filled.Settings,
                subtitleRes = R.string.more_settings_subtitle
            ) {
                switcherVisible = false
                navController.navigate(SettingsRoute) { launchSingleTop = true }
            }
        )
        AppSwitcherSheet(
            destinations = switcherDestinations,
            onDismiss = { switcherVisible = false },
            onDestinationSelected = { destination ->
                switcherVisible = false
                navController.navigate(destination.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            hubPrimary = hubPrimary,
            hubModules = emptyList()
        )
    }
}

private fun loadOrderIntoCart(
    cartViewModel: CartViewModel,
    orderItem: OrdersListItem
) {
    cartViewModel.loadOrderForEdit(
        orderId = orderItem.id,
        request = orderItem.request,
        serverOrderId = orderItem.serverId
    )
}

private val primaryModuleRoutes = setOf(
    ScannerRoutes.HOME,
    PosRoutes.SEARCH,
    OrdersRoutes.ORDERS,
    InventoryRoutes.PRODUCTS,
    CustomerRoutes.CUSTOMERS,
    MoreRoutes.MORE
)

private val quickSwitchRoutes = primaryModuleRoutes + HomeRoutes.HOME

private val sellRoutes = setOf(
    PosRoutes.SEARCH,
    PosRoutes.SCAN,
    PosRoutes.CART,
    PosRoutes.CHECKOUT,
    CatalogRoutes.SEARCH,
    CatalogRoutes.SCAN
)

private val routesWithDedicatedTopBar = setOf(
    OrdersRoutes.DETAIL,
    CustomerRoutes.DASHBOARD,
    ManufacturingRoutes.PRODUCTION,
    ManufacturingRoutes.PRODUCTION_EDIT,
    ManufacturingRoutes.BOMS,
    ManufacturingRoutes.BOM_EDIT,
    ProcurementRoutes.PROCUREMENTS,
    ProcurementRoutes.NEW,
    ProcurementRoutes.DETAIL,
    PriceLookupRoutes.HOME,
    PriceLookupRoutes.DETAIL
)

private fun routeTitle(route: String?): Int = when (route) {
    HomeRoutes.HOME -> R.string.nav_home
    ScannerRoutes.HOME -> R.string.nav_scanner
    ScannerRoutes.SCAN -> R.string.nav_scan
    ScannerRoutes.PRODUCT_DETAIL -> R.string.scanner_product_detail_title
    ScannerRoutes.PRODUCT_CREATE -> R.string.scanner_create_product_title
    ScannerRoutes.PRODUCT_EDIT -> R.string.scanner_edit_product_title
    PosRoutes.SEARCH, CatalogRoutes.SEARCH -> R.string.shell_tab_sell
    PosRoutes.SCAN, CatalogRoutes.SCAN -> R.string.quick_action_scan
    PosRoutes.CART -> R.string.nav_cart
    PosRoutes.CHECKOUT -> R.string.nav_checkout
    OrdersRoutes.ORDERS -> R.string.shell_tab_orders
    OrdersRoutes.DETAIL -> R.string.orders_details_title
    InventoryRoutes.PRODUCTS, InventoryRoutes.INVENTORY -> R.string.shell_tab_products
    CustomerRoutes.CUSTOMERS -> R.string.shell_tab_customers
    CustomerRoutes.DASHBOARD -> R.string.shell_tab_customers
    MoreRoutes.MORE -> R.string.shell_tab_more
    SettingsRoute -> R.string.nav_settings
    ManufacturingRoutes.PRODUCTION -> R.string.nav_manufacturing
    ManufacturingRoutes.BOMS -> R.string.create_bom
    ProcurementRoutes.PROCUREMENTS -> R.string.nav_procurement
    ProcurementRoutes.NEW -> R.string.create_procurement
    ProcurementRoutes.DETAIL -> R.string.nav_procurement
    PriceLookupRoutes.HOME -> R.string.nav_price_lookup
    PriceLookupRoutes.DETAIL -> R.string.price_lookup_title
    ContainerRoutes.INVENTORY, ContainerRoutes.CONTAINERS -> R.string.containers
    ContainerRoutes.MOVEMENTS -> R.string.containers
    else -> R.string.app_title
}

@Composable
private fun rememberShellConnectivityState(): androidx.compose.runtime.State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isShellOnline(context)) }
    androidx.compose.runtime.DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                state.value = true
            }

            override fun onLost(network: android.net.Network) {
                state.value = isShellOnline(context)
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

private fun isShellOnline(context: android.content.Context): Boolean {
    val connectivityManager =
        context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
