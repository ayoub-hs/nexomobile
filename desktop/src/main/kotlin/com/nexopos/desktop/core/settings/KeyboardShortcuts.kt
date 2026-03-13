package com.nexopos.desktop.core.settings

import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

/**
 * Keyboard shortcuts configuration with persistence
 * ROBUST: Type-safe enum for shortcut actions, file-based persistence
 */
data class KeyboardShortcut(
    val action: ShortcutAction,
    val key: Key,
    val label: String,
    val description: String
)

enum class ShortcutAction {
    OPEN_CASH_DRAWER,
    TOGGLE_PRINT,
    SUBMIT_ORDER,
    TOGGLE_WHOLESALE,
    OPEN_DISCOUNT,
    REFRESH_DATA,
    OPEN_QUICK_PRODUCT,
    CLEAR_CART,
    NAVIGATE_ORDERS
}

class KeyboardShortcutsManager {
    private val configFile = File(System.getProperty("user.home"), ".nexopos/keyboard_shortcuts.properties")
    
    private val defaultShortcuts = mapOf(
        ShortcutAction.OPEN_CASH_DRAWER to KeyboardShortcut(
            action = ShortcutAction.OPEN_CASH_DRAWER,
            key = Key.F8,
            label = "F8",
            description = "Open cash drawer"
        ),
        ShortcutAction.TOGGLE_PRINT to KeyboardShortcut(
            action = ShortcutAction.TOGGLE_PRINT,
            key = Key.F9,
            label = "F9",
            description = "Toggle print receipt"
        ),
        ShortcutAction.SUBMIT_ORDER to KeyboardShortcut(
            action = ShortcutAction.SUBMIT_ORDER,
            key = Key.F12,
            label = "F12",
            description = "Submit order"
        ),
        ShortcutAction.TOGGLE_WHOLESALE to KeyboardShortcut(
            action = ShortcutAction.TOGGLE_WHOLESALE,
            key = Key.G,
            label = "G",
            description = "Toggle wholesale price"
        ),
        ShortcutAction.OPEN_DISCOUNT to KeyboardShortcut(
            action = ShortcutAction.OPEN_DISCOUNT,
            key = Key.D,
            label = "D",
            description = "Open discount dialog"
        ),
        ShortcutAction.REFRESH_DATA to KeyboardShortcut(
            action = ShortcutAction.REFRESH_DATA,
            key = Key.F5,
            label = "F5",
            description = "Refresh data from server"
        ),
        ShortcutAction.OPEN_QUICK_PRODUCT to KeyboardShortcut(
            action = ShortcutAction.OPEN_QUICK_PRODUCT,
            key = Key.Q,
            label = "Q",
            description = "Quick add product"
        ),
        ShortcutAction.CLEAR_CART to KeyboardShortcut(
            action = ShortcutAction.CLEAR_CART,
            key = Key.Escape,
            label = "ESC",
            description = "Clear cart"
        ),
        ShortcutAction.NAVIGATE_ORDERS to KeyboardShortcut(
            action = ShortcutAction.NAVIGATE_ORDERS,
            key = Key.O,
            label = "O",
            description = "Navigate to orders"
        )
    )
    
    private val _shortcuts = MutableStateFlow(defaultShortcuts)
    val shortcuts: StateFlow<Map<ShortcutAction, KeyboardShortcut>> = _shortcuts.asStateFlow()
    
    init {
        loadShortcuts()
    }
    
    /**
     * Get shortcut for an action
     */
    fun getShortcut(action: ShortcutAction): KeyboardShortcut? {
        return _shortcuts.value[action]
    }
    
    /**
     * Get action for a key
     */
    fun getAction(key: Key): ShortcutAction? {
        return _shortcuts.value.entries.find { it.value.key == key }?.key
    }
    
    /**
     * Update a shortcut
     * ROBUST: Validates no conflicts before saving
     */
    fun updateShortcut(action: ShortcutAction, newKey: Key): Result<Unit> {
        // Check for conflicts
        val existingAction = getAction(newKey)
        if (existingAction != null && existingAction != action) {
            return Result.failure(IllegalArgumentException("Key $newKey is already assigned to ${existingAction.name}"))
        }
        
        val currentShortcut = _shortcuts.value[action] ?: return Result.failure(IllegalArgumentException("Unknown action"))
        val updatedShortcut = currentShortcut.copy(key = newKey, label = keyToLabel(newKey))
        
        _shortcuts.value = _shortcuts.value.toMutableMap().apply {
            put(action, updatedShortcut)
        }
        
        saveShortcuts()
        return Result.success(Unit)
    }
    
    /**
     * Reset to defaults
     */
    fun resetToDefaults() {
        _shortcuts.value = defaultShortcuts
        saveShortcuts()
    }
    
    /**
     * Load shortcuts from file
     */
    private fun loadShortcuts() {
        if (!configFile.exists()) {
            println("[KeyboardShortcuts] Config file not found, using defaults")
            return
        }
        
        try {
            val props = Properties()
            configFile.inputStream().use { props.load(it) }
            
            val loaded = mutableMapOf<ShortcutAction, KeyboardShortcut>()
            
            for (action in ShortcutAction.values()) {
                val keyName = props.getProperty(action.name)
                if (keyName != null) {
                    val key = labelToKey(keyName)
                    if (key != null) {
                        val default = defaultShortcuts[action]!!
                        loaded[action] = default.copy(key = key, label = keyName)
                    } else {
                        // Invalid key, use default
                        loaded[action] = defaultShortcuts[action]!!
                    }
                } else {
                    // Not in file, use default
                    loaded[action] = defaultShortcuts[action]!!
                }
            }
            
            _shortcuts.value = loaded
            println("[KeyboardShortcuts] Loaded ${loaded.size} shortcuts from config")
        } catch (e: Exception) {
            println("[KeyboardShortcuts] Error loading shortcuts: ${e.message}")
            _shortcuts.value = defaultShortcuts
        }
    }
    
    /**
     * Save shortcuts to file
     */
    private fun saveShortcuts() {
        try {
            configFile.parentFile?.mkdirs()
            
            val props = Properties()
            for ((action, shortcut) in _shortcuts.value) {
                props.setProperty(action.name, shortcut.label)
            }
            
            configFile.outputStream().use { props.store(it, "NexoPOS Keyboard Shortcuts") }
            println("[KeyboardShortcuts] Saved shortcuts to ${configFile.absolutePath}")
        } catch (e: Exception) {
            println("[KeyboardShortcuts] Error saving shortcuts: ${e.message}")
        }
    }
    
    /**
     * Convert key to display label
     */
    private fun keyToLabel(key: Key): String {
        return when (key) {
            Key.F1 -> "F1"
            Key.F2 -> "F2"
            Key.F3 -> "F3"
            Key.F4 -> "F4"
            Key.F5 -> "F5"
            Key.F6 -> "F6"
            Key.F7 -> "F7"
            Key.F8 -> "F8"
            Key.F9 -> "F9"
            Key.F10 -> "F10"
            Key.F11 -> "F11"
            Key.F12 -> "F12"
            Key.Escape -> "ESC"
            Key.Enter -> "ENTER"
            Key.Tab -> "TAB"
            Key.Spacebar -> "SPACE"
            else -> key.toString().uppercase()
        }
    }
    
    /**
     * Convert label to key
     */
    private fun labelToKey(label: String): Key? {
        return when (label.uppercase()) {
            "F1" -> Key.F1
            "F2" -> Key.F2
            "F3" -> Key.F3
            "F4" -> Key.F4
            "F5" -> Key.F5
            "F6" -> Key.F6
            "F7" -> Key.F7
            "F8" -> Key.F8
            "F9" -> Key.F9
            "F10" -> Key.F10
            "F11" -> Key.F11
            "F12" -> Key.F12
            "ESC", "ESCAPE" -> Key.Escape
            "ENTER" -> Key.Enter
            "TAB" -> Key.Tab
            "SPACE" -> Key.Spacebar
            else -> {
                // Try single character keys
                if (label.length == 1) {
                    when (label.uppercase().first()) {
                        'A' -> Key.A
                        'B' -> Key.B
                        'C' -> Key.C
                        'D' -> Key.D
                        'E' -> Key.E
                        'F' -> Key.F
                        'G' -> Key.G
                        'H' -> Key.H
                        'I' -> Key.I
                        'J' -> Key.J
                        'K' -> Key.K
                        'L' -> Key.L
                        'M' -> Key.M
                        'N' -> Key.N
                        'O' -> Key.O
                        'P' -> Key.P
                        'Q' -> Key.Q
                        'R' -> Key.R
                        'S' -> Key.S
                        'T' -> Key.T
                        'U' -> Key.U
                        'V' -> Key.V
                        'W' -> Key.W
                        'X' -> Key.X
                        'Y' -> Key.Y
                        'Z' -> Key.Z
                        else -> null
                    }
                } else null
            }
        }
    }
}
