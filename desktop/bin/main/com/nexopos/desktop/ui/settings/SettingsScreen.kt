package com.nexopos.desktop.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexopos.desktop.core.prefs.AppSettings
import com.nexopos.desktop.core.repo.CustomerRepository
import com.nexopos.desktop.core.repo.PaymentMethodRepository
import com.nexopos.desktop.core.repo.ProductRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onConfigured: () -> Unit,
    onNavigateToKeyboardShortcuts: () -> Unit = {}
) {
    val settings = koinInject<AppSettings>()
    val productRepo = koinInject<ProductRepository>()
    val customerRepo = koinInject<CustomerRepository>()
    val paymentRepo = koinInject<PaymentMethodRepository>()
    val scope = rememberCoroutineScope()
    
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var token by remember { mutableStateOf(settings.token) }
    var storeName by remember { mutableStateOf(settings.storeName) }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (settings.isConfigured()) {
                Button(
                    onClick = {
                        scope.launch {
                            isRefreshing = true
                            message = null
                            try {
                                productRepo.refreshProducts()
                                customerRepo.refreshCustomers()
                                paymentRepo.refreshPaymentMethods()
                                
                                message = "Data refreshed successfully"
                            } catch (e: Exception) {
                                message = "Failed to refresh: ${e.message}"
                            } finally {
                                isRefreshing = false
                            }
                        }
                    },
                    enabled = !isRefreshing
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRefreshing) "Refreshing..." else "Refresh Data")
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // API Settings Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "NexoPOS Server Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://your-server.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("API Token") },
                    placeholder = { Text("Your NexoPOS API token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store Name (optional)") },
                    placeholder = { Text("My Store") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (message != null) {
                    Text(
                        message!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message!!.contains("success", ignoreCase = true))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            message = null
                            
                            try {
                                // Validate
                                if (baseUrl.isBlank() || token.isBlank()) {
                                    message = "Please enter both server URL and token"
                                    return@launch
                                }
                                
                                // Save settings
                                settings.baseUrl = baseUrl.trim().trimEnd('/')
                                settings.token = token.trim()
                                settings.storeName = storeName.trim()
                                
                                message = "Settings saved successfully"
                                
                                // Auto-refresh data (repositories will use updated settings)
                                if (settings.isConfigured()) {
                                    try {
                                        productRepo.refreshProducts()
                                        customerRepo.refreshCustomers()
                                        paymentRepo.refreshPaymentMethods()
                                        
                                        message = "Settings saved and data synced!"
                                        
                                        // Navigate to POS
                                        kotlinx.coroutines.delay(1000)
                                        onConfigured()
                                    } catch (e: Exception) {
                                        message = "Settings saved but sync failed: ${e.message}"
                                    }
                                }
                            } catch (e: Exception) {
                                message = "Error: ${e.message}"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    Text(if (isSaving) "Saving..." else "Save & Sync")
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keyboard Shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Customize keyboard shortcuts for POS actions", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onNavigateToKeyboardShortcuts) { Text("Configure") }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Help text
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Setup Instructions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. Enter your NexoPOS server URL (e.g., https://demo.nexopos.com)\n" +
                    "2. Get your API token from NexoPOS dashboard → Settings → API\n" +
                    "3. Click 'Save & Sync' to connect and download your catalog\n" +
                    "4. The app will automatically sync products, customers, and payment methods",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
