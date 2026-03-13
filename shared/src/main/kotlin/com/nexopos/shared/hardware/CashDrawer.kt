package com.nexopos.shared.hardware

/**
 * Platform-agnostic cash drawer interface.
 * 
 * Android: Usually triggered via printer ESC/POS commands
 * Desktop: Direct serial write to /dev/ttyUSB0 (e.g., echo '1' > /dev/ttyUSB0)
 */
interface CashDrawer {
    /**
     * Open the cash drawer.
     * 
     * @return true if command was sent successfully, false otherwise
     */
    suspend fun open(): Result<Unit>
    
    /**
     * Check if cash drawer is connected.
     */
    suspend fun isConnected(): Boolean
}
