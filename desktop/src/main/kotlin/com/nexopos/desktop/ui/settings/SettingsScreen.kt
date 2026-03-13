package com.nexopos.desktop.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nexopos.desktop.core.repo.CategoryRepository
import com.nexopos.desktop.core.repo.CustomerRepository
import com.nexopos.desktop.core.repo.PaymentMethodRepository
import com.nexopos.desktop.core.repo.ProductRepository
import com.nexopos.desktop.ui.pos.PosTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.net.URL

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
    val categoryRepo = koinInject<CategoryRepository>()
    val scope = rememberCoroutineScope()
    
    var baseUrl by remember { mutableStateOf(settings.baseUrl) }
    var token by remember { mutableStateOf(settings.token) }
    var storeName by remember { mutableStateOf(settings.storeName) }
    var quickAccessCategoryId by remember { mutableStateOf(settings.quickAccessCategoryId) }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showQuickAccessDropdown by remember { mutableStateOf(false) }
    val categories by categoryRepo.getAllCategories().collectAsState(emptyList())
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                categoryRepo.refreshCategories()
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
                    enabled = !isRefreshing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PosTheme.Success,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRefreshing) "Refreshing..." else "Refresh Data")
                }
            }
        }
        
        // API Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                
                message?.let { bannerMessage ->
                    val isSuccess = bannerMessage.contains("success", ignoreCase = true)
                    val background = if (isSuccess) PosTheme.Highlight else PosTheme.DangerSoft
                    val textColor = if (isSuccess) PosTheme.Success else MaterialTheme.colorScheme.error
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = background),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = bannerMessage,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
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

                                val urlError = validateBaseUrl(baseUrl)
                                if (urlError != null) {
                                    message = urlError
                                    return@launch
                                }
                                
                                // Save settings
                                settings.baseUrl = baseUrl.trim().trimEnd('/')
                                settings.token = token.trim()
                                settings.storeName = storeName.trim()
                                settings.quickAccessCategoryId = quickAccessCategoryId
                                
                                message = "Settings saved successfully"
                                
                                // Auto-refresh data (repositories will use updated settings)
                                if (settings.isConfigured()) {
                                    try {
                                        categoryRepo.refreshCategories()
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
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PosTheme.Success,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(if (isSaving) "Saving..." else "Save & Sync")
                }
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "POS Quick Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                val selectedCategoryLabel = categories
                    .firstOrNull { it.id == quickAccessCategoryId }
                    ?.name
                    ?: "None"

                ExposedDropdownMenuBox(
                    expanded = showQuickAccessDropdown,
                    onExpandedChange = { showQuickAccessDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategoryLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Quick-Access Category") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showQuickAccessDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = showQuickAccessDropdown,
                        onDismissRequest = { showQuickAccessDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                quickAccessCategoryId = 0L
                                showQuickAccessDropdown = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    quickAccessCategoryId = category.id
                                    showQuickAccessDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keyboard Shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Customize keyboard shortcuts for POS actions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onNavigateToKeyboardShortcuts,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PosTheme.Success,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) { Text("Configure") }
            }
        }
        
        // Help text
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = PosTheme.SurfaceSoft
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

private fun validateBaseUrl(input: String): String? {
    val trimmed = input.trim()
    val url = try {
        URL(trimmed)
    } catch (e: Exception) {
        return "Invalid URL format. Use http:// or https://"
    }

    val scheme = url.protocol.lowercase()
    if (scheme != "http" && scheme != "https") {
        return "URL must start with http:// or https://"
    }

    if (scheme == "http" && !isPrivateOrLocalHost(url.host)) {
        return "HTTP is only allowed for local/LAN addresses. Please use HTTPS."
    }

    return null
}

private fun isPrivateOrLocalHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val normalized = host.lowercase()

    if (normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1") return true
    if (normalized.startsWith("10.")) return true
    if (normalized.startsWith("172.")) {
        val parts = normalized.split(".")
        val second = parts.getOrNull(1)?.toIntOrNull()
        if (second != null && second in 16..31) return true
    }
    if (normalized.startsWith("192.168.")) return true
    if (normalized.startsWith("169.254.")) return true
    if (normalized.endsWith(".local")) return true
    if (normalized.endsWith(".internal")) return true

    return false
}
