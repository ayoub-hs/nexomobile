package com.nexopos.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nexopos.desktop.core.prefs.AppSettings
import com.nexopos.desktop.di.appModules
import com.nexopos.desktop.ui.theme.NexoPOSDesktopTheme
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlinx.coroutines.launch

fun main() = application {
    // Initialize Koin DI with eager creation
    startKoin {
        modules(appModules)
        createEagerInstances()
    }
    
    val windowState = rememberWindowState(width = 1024.dp, height = 768.dp)
    
    Window(
        onCloseRequest = {
            stopKoin()
            exitApplication()
        },
        title = "NexoPOS Desktop - Linux",
        state = windowState
    ) {
        NexoPOSDesktopTheme {
            App()
        }
    }
}

@Composable
fun App() {
    val settings = org.koin.compose.koinInject<AppSettings>()
    
    // Check if configured, go directly to POS if configured, otherwise show settings
    var currentScreen by remember { 
        mutableStateOf(if (settings.isConfigured()) "pos" else "settings") 
    }
    var orderToEdit by remember { mutableStateOf<com.nexopos.desktop.ui.pos.OrdersListItem?>(null) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            "settings" -> com.nexopos.desktop.ui.settings.SettingsScreen(
                onBack = {
                    if (settings.isConfigured()) {
                        currentScreen = "pos"
                    }
                },
                onConfigured = { currentScreen = "pos" },
                onNavigateToKeyboardShortcuts = { currentScreen = "keyboard_shortcuts" }
            )
            "keyboard_shortcuts" -> com.nexopos.desktop.ui.settings.KeyboardShortcutsSettings(
                onBack = { currentScreen = "settings" }
            )
            "pos" -> com.nexopos.desktop.ui.pos.POSScreenWithViewModel(
                onNavigateToOrders = { currentScreen = "orders" },
                onNavigateToReceiveContainers = { currentScreen = "receive_containers" },
                onNavigateToSettings = { currentScreen = "settings" },
                onBack = { currentScreen = "settings" },
                orderToEdit = orderToEdit,
                onOrderEditHandled = { orderToEdit = null }
            )
            "receive_containers" -> {
                val receiveViewModel = org.koin.compose.koinInject<com.nexopos.desktop.ui.pos.ReceiveContainersViewModel>()
                com.nexopos.desktop.ui.pos.ReceiveContainersScreen(
                    viewModel = receiveViewModel,
                    onBack = { currentScreen = "pos" }
                )
            }
            "orders" -> {
                val ordersViewModel = org.koin.compose.koinInject<com.nexopos.desktop.ui.pos.OrdersViewModel>()
                val printer = remember { com.nexopos.desktop.hardware.DesktopReceiptPrinter() }
                val scope = rememberCoroutineScope()
                val settings = org.koin.compose.koinInject<com.nexopos.desktop.core.prefs.AppSettings>()
                
                com.nexopos.desktop.ui.pos.OrdersScreen(
                    viewModel = ordersViewModel,
                    onBack = { currentScreen = "pos" },
                    onEditOrder = { selectedOrder ->
                        orderToEdit = selectedOrder
                        currentScreen = "pos"
                    },
                    onPrintOrder = { printOrder ->
                        // Use the existing DesktopReceiptPrinter with libusb + ESC/POS
                        println("[Orders] Printing order: ${printOrder.displayId}")
                        
                        // Build Receipt object for ESC/POS printer
                        val receiptItems = printOrder.request?.products?.map { product ->
                            com.nexopos.shared.hardware.ReceiptItem(
                                name = product.name,
                                quantity = product.quantity,
                                price = String.format("%.3f DT", product.unitPrice),
                                total = String.format("%.3f DT", product.totalPrice),
                                unitName = product.unitName
                            )
                        } ?: emptyList()
                        
                        // Get store name from settings (same as POS screen)
                        val storeName = settings.storeName.takeIf { it.isNotBlank() } ?: "NexoPOS"
                        
                        val receipt = com.nexopos.shared.hardware.Receipt(
                            storeName = storeName,
                            orderCode = printOrder.displayId,
                            timestamp = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(printOrder.createdAt)),
                            customerName = printOrder.customerName ?: "Walk-in",
                            items = receiptItems,
                            subtotal = String.format("%.3f DT", printOrder.request?.subtotal ?: printOrder.total),
                            discount = printOrder.request?.discountAmount?.let { 
                                if (it > 0) String.format("%.3f DT", it) else null 
                            },
                            tax = String.format("%.3f DT", printOrder.request?.taxValue ?: 0.0),
                            total = String.format("%.3f DT", printOrder.request?.total ?: printOrder.total),
                            paymentMethod = printOrder.request?.payments?.firstOrNull()?.identifier ?: "cash",
                            footer = "Merci! / Thank you!"
                        )
                        
                        // Print using libusb ESC/POS printer
                        scope.launch {
                            val result = printer.printReceipt(receipt)
                            if (result.isSuccess) {
                                println("[Orders] ✓ Receipt printed successfully via ESC/POS")
                            } else {
                                println("[Orders] ✗ Print failed: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    }
                )
            }
        }
    }
}
