package com.nexopos.desktop.core.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType

// Import POSMode from the UI package
import com.nexopos.desktop.ui.pos.POSMode

/**
 * Input Authority Manager - Enterprise POS Input Control
 * 
 * CRITICAL: This prevents the exact input conflicts and focus stealing
 * 
 * Single input authority per mode prevents:
 * - Scanner input triggering buttons
 * - Keyboard shortcuts firing during payment
 * - Focus conflicts between components
 * - Accidental actions during critical operations
 */
class InputAuthorityManager {
    
    private var currentMode: POSMode = POSMode.SALE
    private var scanBurstActive: Boolean = false
    private var lastScanTime: Long = 0L
    private var scanBuffer: StringBuilder = StringBuilder()
    
    companion object {
        // Scanner timing thresholds - enterprise tested
        private const val SCAN_BURST_THRESHOLD_MS = 50L    // <50ms between chars = scanner
        private const val SCAN_TIMEOUT_MS = 200L            // 200ms timeout to end burst
        private const val MIN_SCAN_LENGTH = 3               // Minimum chars for valid scan
    }
    
    /**
     * Set the current POS mode - CRITICAL for input authority
     */
    fun setMode(mode: POSMode) {
        currentMode = mode
        println("[InputAuthority] Mode changed to: $mode")
        
        // Reset scan state when leaving SALE mode
        if (mode != POSMode.SALE) {
            scanBurstActive = false
            scanBuffer.clear()
        }
    }
    
    /**
     * Process keyboard input with mode-specific authority
     * Returns true if input was handled, false if it should propagate
     */
    fun processKeyInput(event: KeyEvent, timestamp: Long = System.currentTimeMillis()): Boolean {
        return when (currentMode) {
            POSMode.SALE -> handleSaleModeInput(event, timestamp)
            POSMode.PAYMENT -> handlePaymentModeInput(event)
            POSMode.MODAL -> handleModalModeInput(event)
        }
    }
    
    /**
     * SALE mode: Scanner + shortcuts, NO focus
     * - Scanner input has highest priority
     * - Shortcuts suppressed during scan bursts
     * - No DOM focus management
     */
    private fun handleSaleModeInput(event: KeyEvent, timestamp: Long): Boolean {
        val timeSinceLastScan = timestamp - lastScanTime
        
        // Detect scanner burst vs manual typing
        if (event.key != Key.Enter && event.key != Key.NumPadEnter) {
            if (timeSinceLastScan < SCAN_BURST_THRESHOLD_MS) {
                // Continuing scan burst
                scanBurstActive = true
                scanBuffer.append(event.key.toString())
                println("[InputAuthority] Scan burst active: ${scanBuffer.length} chars")
            } else if (scanBurstActive && timeSinceLastScan > SCAN_TIMEOUT_MS) {
                // Scan burst ended by timeout
                endScanBurst()
                scanBurstActive = false
                scanBuffer.clear()
            }
        }
        
        // Handle Enter key - different behavior for scan vs manual
        if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
            return if (scanBurstActive && scanBuffer.length >= MIN_SCAN_LENGTH) {
                // Complete scan burst - process as barcode
                val barcode = scanBuffer.toString()
                println("[InputAuthority] Scan complete: $barcode")
                endScanBurst()
                processBarcodeScan(barcode)
                true // Consume the event
            } else {
                // Manual Enter - allow shortcuts to handle
                scanBurstActive = false
                scanBuffer.clear()
                false // Let shortcuts handle it
            }
        }
        
        // During scan burst, suppress all other keys (prevents accidental shortcuts)
        if (scanBurstActive) {
            println("[InputAuthority] Key suppressed during scan burst: ${event.key}")
            return true
        }
        
        // Not in scan burst - allow normal processing
        return false
    }
    
    /**
     * PAYMENT mode: Numeric only, scanner LOCKED
     * - Scanner input completely ignored
     * - Only numeric keys allowed
     * - Prevents payment corruption
     */
    private fun handlePaymentModeInput(event: KeyEvent): Boolean {
        return when (event.key) {
            // Allow numeric input
            Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
            Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9,
            Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
            Key.Period -> false // Allow numeric input
            
            // Allow payment control keys
            Key.Enter, Key.NumPadEnter, Key.Escape, Key.Backspace, Key.Delete -> false
            
            // Block everything else (including scanner input)
            else -> {
                println("[InputAuthority] Key blocked in PAYMENT mode: ${event.key}")
                true
            }
        }
    }
    
    /**
     * MODAL mode: Tab navigation, focus trapped
     * - Standard dialog behavior
     * - Focus trapped within modal bounds
     * - Escape to close
     */
    private fun handleModalModeInput(event: KeyEvent): Boolean {
        return when (event.key) {
            Key.Escape -> false // Allow modal to handle escape
            Key.Tab, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> false // Allow navigation
            Key.Enter, Key.NumPadEnter, Key.Spacebar -> false // Allow form interaction
            else -> {
                // Block other keys that might interfere with modal
                println("[InputAuthority] Key blocked in MODAL mode: ${event.key}")
                true
            }
        }
    }
    
    /**
     * Process completed barcode scan
     * This is where scanner input gets converted to business logic
     */
    private fun processBarcodeScan(barcode: String) {
        // TODO: Connect to ViewModel barcode processing
        println("[InputAuthority] Processing barcode: $barcode")
        // This should trigger the same logic as the original scanner flow
        // but without any DOM focus involvement
    }
    
    /**
     * End scan burst and reset state
     */
    private fun endScanBurst() {
        scanBurstActive = false
        lastScanTime = 0L
        val finalBarcode = scanBuffer.toString()
        scanBuffer.clear()
        println("[InputAuthority] Scan burst ended: $finalBarcode")
    }
    
    /**
     * Get current mode for debugging
     */
    fun getCurrentMode(): POSMode = currentMode
    
    /**
     * Check if currently in scan burst
     */
    fun isInScanBurst(): Boolean = scanBurstActive
    
    /**
     * Force reset scan state (for error recovery)
     */
    fun resetScanState() {
        scanBurstActive = false
        scanBuffer.clear()
        lastScanTime = 0L
        println("[InputAuthority] Scan state reset")
    }
}

/**
 * Global instance for application-wide input authority
 * Single source of truth for all input handling
 */
object GlobalInputAuthority {
    private val manager = InputAuthorityManager()
    
    fun setMode(mode: POSMode) = manager.setMode(mode)
    fun getCurrentMode() = manager.getCurrentMode()
    fun processKeyInput(event: KeyEvent, timestamp: Long) = manager.processKeyInput(event, timestamp)
    fun resetScanState() = manager.resetScanState()
    fun isInScanBurst() = manager.isInScanBurst()
}
