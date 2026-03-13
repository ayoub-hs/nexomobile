package com.nexopos.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nexopos.desktop.core.prefs.AppSettings
import com.nexopos.desktop.di.appModules
import com.nexopos.desktop.platform.DesktopPlatform
import com.nexopos.desktop.ui.screens.HardwareTestScreen
import com.nexopos.desktop.ui.theme.NexoPOSDesktopTheme
import com.nexopos.shared.SharedModule
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
        title = "NexoPOS Desktop - Linux ARM",
        state = windowState
    ) {
        NexoPOSDesktopTheme {
            App()
        }
    }
}

@Composable
fun App() {
    val platform = remember { DesktopPlatform() }
    val platformInfo = remember { SharedModule.getPlatformInfo(platform) }
    val settings = org.koin.compose.koinInject<AppSettings>()
    
    // Check if configured, go directly to POS if configured, otherwise show settings
    var currentScreen by remember { 
        mutableStateOf(if (settings.isConfigured()) "pos" else "settings") 
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            "home" -> HomeScreen(
                platformInfo = platformInfo,
                onNavigateToPOS = { currentScreen = "pos" },
                onNavigateToSettings = { currentScreen = "settings" },
                onNavigateToHardwareTest = { currentScreen = "hardware" }
            )
            "settings" -> com.nexopos.desktop.ui.settings.SettingsScreen(
                onBack = { currentScreen = "home" },
                onConfigured = { currentScreen = "pos" },
                onNavigateToKeyboardShortcuts = { currentScreen = "keyboard_shortcuts" }
            )
            "keyboard_shortcuts" -> com.nexopos.desktop.ui.settings.KeyboardShortcutsSettings(
                onBack = { currentScreen = "settings" }
            )
            "hardware" -> com.nexopos.desktop.ui.screens.HardwareTestScreen(
                onBack = { currentScreen = "home" }
            )
            "pos" -> com.nexopos.desktop.ui.pos.POSScreenWithViewModel(
                onNavigateToOrders = { currentScreen = "orders" },
                onBack = { currentScreen = "home" }
            )
            "orders" -> {
                val ordersViewModel = org.koin.compose.koinInject<com.nexopos.desktop.ui.pos.OrdersViewModel>()
                val printer = remember { com.nexopos.desktop.hardware.DesktopReceiptPrinter() }
                val scope = rememberCoroutineScope()
                val settings = org.koin.compose.koinInject<com.nexopos.desktop.core.prefs.AppSettings>()
                
                com.nexopos.desktop.ui.pos.OrdersScreen(
                    viewModel = ordersViewModel,
                    onBack = { currentScreen = "pos" },
                    onEditOrder = { /* TODO: Implement edit functionality */ },
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

@Composable
fun HomeScreen(
    platformInfo: String,
    onNavigateToPOS: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHardwareTest: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingCart,
                contentDescription = "NexoPOS",
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "NexoPOS Desktop",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Point of Sale System for Linux ARM",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = platformInfo,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "✓ Compose Desktop Running",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Ready for H313 2GB Armbian Testing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNavigateToPOS,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Start POS")
                }
                
                Button(
                    onClick = onNavigateToHardwareTest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Hardware Test")
                }
                
                OutlinedButton(
                    onClick = onNavigateToSettings
                ) {
                    Text("Settings")
                }
            }
        }
    }
}
