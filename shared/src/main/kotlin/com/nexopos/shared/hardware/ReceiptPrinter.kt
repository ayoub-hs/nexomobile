package com.nexopos.shared.hardware

/**
 * Platform-agnostic receipt printer interface.
 * 
 * Android: Uses androidx.print or Bluetooth printer APIs
 * Desktop: Direct ESC/POS commands to /dev/usb/lp* or /dev/ttyUSB*
 */
interface ReceiptPrinter {
    /**
     * Print a receipt with the given content.
     * 
     * @param receipt Receipt data to print
     * @return true if print was successful, false otherwise
     */
    suspend fun printReceipt(receipt: Receipt): Result<Unit>
    
    /**
     * Check if printer is connected and ready.
     */
    suspend fun isReady(): Boolean
    
    /**
     * Get printer status information.
     */
    suspend fun getStatus(): PrinterStatus
}

/**
 * Receipt data model (matching Android app format).
 */
data class Receipt(
    val storeName: String,
    val orderCode: String? = null,
    val timestamp: String,
    val customerName: String? = null,
    val items: List<ReceiptItem>,
    val subtotal: String,
    val discount: String? = null,
    val tax: String,
    val total: String,
    val paymentMethod: String,
    val footer: String,
    // Legacy fields for backwards compatibility
    val header: String = storeName
)

data class ReceiptItem(
    val name: String,
    val quantity: Double,
    val price: String,
    val total: String,
    val unitName: String? = null
)

enum class PrinterStatus {
    READY,
    BUSY,
    OUT_OF_PAPER,
    ERROR,
    DISCONNECTED
}
