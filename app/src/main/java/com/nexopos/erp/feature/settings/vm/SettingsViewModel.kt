package com.nexopos.erp.feature.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.print.PrinterConfig
import com.nexopos.erp.feature.auth.AuthViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State for the Settings screen.
 */
data class SettingsState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val baseUrl: String = "",
    val token: String = "",
    val storeName: String = "",
    val printerConfig: PrinterConfig = PrinterConfig(type = com.nexopos.erp.core.print.PrinterType.Bluetooth),
    val themeMode: String = "system",
    val encryptionAvailable: Boolean = true,
    val encryptionError: String? = null
)

/**
 * Available settings items for filtering
 */
sealed class SettingsItem {
    abstract val title: String
    abstract val subtitle: String

    data class ApiSettings(override val title: String, override val subtitle: String) : SettingsItem()
    data class PrinterSettings(override val title: String, override val subtitle: String) : SettingsItem()
    data class GeneralSettings(override val title: String, override val subtitle: String) : SettingsItem()
}

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val authViewModel: AuthViewModel
) : ViewModel() {
    
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    val baseUrlFlow = repo.baseUrlFlow
    val tokenFlow = repo.tokenFlow
    val storeNameFlow = repo.storeNameFlow
    val printerConfigFlow = repo.printerConfigFlow
    val themeModeFlow = repo.themeModeFlow
    
    init {
        // Collect settings changes to update state
        viewModelScope.launch {
            repo.baseUrlFlow.collect { baseUrl ->
                _state.value = _state.value.copy(baseUrl = baseUrl)
            }
        }
        viewModelScope.launch {
            repo.tokenFlow.collect { token ->
                _state.value = _state.value.copy(token = token)
            }
        }
        viewModelScope.launch {
            repo.storeNameFlow.collect { storeName ->
                _state.value = _state.value.copy(storeName = storeName)
            }
        }
        viewModelScope.launch {
            repo.printerConfigFlow.collect { printerConfig ->
                _state.value = _state.value.copy(printerConfig = printerConfig)
            }
        }
        viewModelScope.launch {
            repo.themeModeFlow.collect { themeMode ->
                _state.value = _state.value.copy(themeMode = themeMode)
            }
        }
        viewModelScope.launch {
            repo.encryptionStatusFlow.collect { status ->
                _state.value = _state.value.copy(
                    encryptionAvailable = status.isAvailable,
                    encryptionError = status.error
                )
            }
        }
    }
    
    /**
     * Get all available settings items
     */
    fun getSettingsItems(): List<SettingsItem> {
        return listOf(
            SettingsItem.ApiSettings(
                title = "API Settings",
                subtitle = "Configure server URL and authentication"
            ),
            SettingsItem.PrinterSettings(
                title = "Printer Settings",
                subtitle = "Configure receipt printer"
            ),
            SettingsItem.GeneralSettings(
                title = "General",
                subtitle = "General app settings"
            )
        )
    }
    
    /**
     * Filter settings items based on search query
     */
    fun filterSettingsItems(query: String): List<SettingsItem> {
        if (query.isBlank()) {
            return getSettingsItems()
        }
        
        val lowerQuery = query.lowercase()
        return getSettingsItems().filter { item ->
            item.title.lowercase().contains(lowerQuery) ||
            item.subtitle.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * Update search query
     */
    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(
            searchQuery = query,
            isSearching = query.isNotBlank()
        )
    }
    
    /**
     * Clear search
     */
    fun clearSearch() {
        _state.value = _state.value.copy(
            searchQuery = "",
            isSearching = false
        )
    }
    
    fun save(baseUrl: String, storeName: String) {
        viewModelScope.launch {
            repo.setBaseUrl(baseUrl)
            repo.setStoreName(storeName)
        }
    }

    fun savePrinter(config: PrinterConfig) {
        viewModelScope.launch { repo.setPrinterConfig(config) }
    }
    
    /**
     * Set theme mode preference.
     * @param mode One of "system", "light", "dark"
     */
    fun setThemeMode(mode: String) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun logout() {
        authViewModel.logout()
    }
}
