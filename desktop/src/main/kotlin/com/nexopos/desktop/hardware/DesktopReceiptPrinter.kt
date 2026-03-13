package com.nexopos.desktop.hardware

import com.nexopos.shared.hardware.PrinterStatus
import com.nexopos.shared.hardware.Receipt
import com.nexopos.shared.hardware.ReceiptPrinter
import com.sun.jna.ptr.IntByReference

/**
 * Desktop implementation of ESC/POS receipt printer using libusb.
 * 
 * Uses libusb-1.0 to communicate with USB thermal printer.
 * Default: H313 POS printer (VID: 0x0483, PID: 0x5840, Endpoint: 0x04)
 */
class DesktopReceiptPrinter(
    private val vendorId: Short = 0x0483.toShort(),
    private val productId: Short = 0x5840.toShort(),
    private val endpoint: Byte = 0x04.toByte()
) : ReceiptPrinter {
    
    override suspend fun printReceipt(receipt: Receipt): Result<Unit> {
        return try {
            println("[DesktopReceiptPrinter] Printing via libusb (VID: ${String.format("0x%04X", vendorId)}, PID: ${String.format("0x%04X", productId)})")
            
            val libusb = LibUSB.INSTANCE
            
            // 1. Initialize libusb
            val initResult = libusb.libusb_init(null)
            if (initResult < 0) {
                return Result.failure(Exception("Failed to initialize libusb: ${libusb.libusb_error_name(initResult)}"))
            }
            
            try {
                // 2. Open device with VID/PID
                val handle = libusb.libusb_open_device_with_vid_pid(null, vendorId, productId)
                if (handle == null) {
                    return Result.failure(Exception("Printer device not found (VID: ${String.format("0x%04X", vendorId)}, PID: ${String.format("0x%04X", productId)})"))
                }
                
                try {
                    // 3. Set auto detach kernel driver and claim interface
                    libusb.libusb_set_auto_detach_kernel_driver(handle, 1)
                    val claimResult = libusb.libusb_claim_interface(handle, 0)
                    if (claimResult < 0) {
                        return Result.failure(Exception("Failed to claim interface: ${libusb.libusb_error_name(claimResult)}"))
                    }
                    
                    try {
                        // 4. Generate ESC/POS commands
                        val escPosCommands = generateEscPosCommands(receipt)
                        
                        // 5. Send bulk transfer
                        println("[DesktopReceiptPrinter] Sending ${escPosCommands.size} bytes to endpoint ${String.format("0x%02X", endpoint)}")
                        val transferred = IntByReference()
                        val transferResult = libusb.libusb_bulk_transfer(
                            handle,
                            endpoint,
                            escPosCommands,
                            escPosCommands.size,
                            transferred,
                            5000 // 5 second timeout
                        )
                        
                        if (transferResult < 0) {
                            return Result.failure(Exception("Bulk transfer error: ${libusb.libusb_error_name(transferResult)}"))
                        }
                        
                        println("[DesktopReceiptPrinter] Success! Sent ${transferred.value} bytes")
                        Result.success(Unit)
                    } finally {
                        // Release interface
                        libusb.libusb_release_interface(handle, 0)
                    }
                } finally {
                    // Close device
                    libusb.libusb_close(handle)
                }
            } finally {
                // Exit libusb
                libusb.libusb_exit(null)
            }
        } catch (e: Exception) {
            println("[DesktopReceiptPrinter] Print failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun isReady(): Boolean {
        return try {
            val libusb = LibUSB.INSTANCE
            libusb.libusb_init(null)
            try {
                val handle = libusb.libusb_open_device_with_vid_pid(null, vendorId, productId)
                val result = handle != null
                if (handle != null) {
                    libusb.libusb_close(handle)
                }
                result
            } finally {
                libusb.libusb_exit(null)
            }
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getStatus(): PrinterStatus {
        return if (isReady()) {
            PrinterStatus.READY
        } else {
            PrinterStatus.DISCONNECTED
        }
    }
    
    /**
     * Generate ESC/POS byte commands for the receipt.
     * Matches Android app receipt format from PrintUtil.kt
     * 
     * ESC/POS reference: https://reference.epson-biz.com/modules/ref_escpos/
     */
    private fun generateEscPosCommands(receipt: Receipt): ByteArray {
        val builder = EscPosBuilder()
        
        // Initialize and center
        builder.init()
            .alignCenter()
        
        // Store name (bold, centered)
        builder.bold(true)
            .text(receipt.storeName.take(32))
            .lf()
            .bold(false)
        
        // Order code if present
        receipt.orderCode?.let {
            builder.text("Commande: ${it.take(32)}")
                .lf()
        }
        
        // Timestamp
        builder.text(receipt.timestamp)
            .lf()
        
        // Customer name if present
        receipt.customerName?.let {
            builder.text("Client: ${it.take(32)}")
                .lf()
        }
        
        // Horizontal rule
        builder.text(horizontalRule())
            .lf()
        
        // Items (left aligned)
        builder.alignLeft()
        receipt.items.forEach { item ->
            // Product name with unit
            val nameWithUnit = item.unitName?.let { "${item.name} ($it)" } ?: item.name
            builder.text(nameWithUnit.take(32))
                .lf()
            // Quantity x unit price = total
            val qty = formatQuantity(item.quantity)
            val unit = item.price
            val total = item.total
            builder.text(formatLine("$qty x $unit", total))
                .lf()
        }
        
        builder.lf()
        
        // Subtotal
        builder.text(formatLine("Sous-total", receipt.subtotal))
            .lf()
        
        // Horizontal rule
        builder.text(horizontalRule())
            .lf()
        
        // Discount if present
        receipt.discount?.let {
            builder.text(formatLine("Remise", "-$it"))
                .lf()
        }
        
        // Tax
        builder.text(formatLine("TVA (19%)", receipt.tax))
            .lf()
        
        // Total (bold)
        builder.bold(true)
            .text(formatLine("TOTAL", receipt.total))
            .lf()
            .bold(false)
        
        // Footer
        builder.lf(2)
            .alignCenter()
            .text(receipt.footer.take(32))
            .lf(4)
        
        // Cut paper
        builder.cut()
        
        return builder.bytes()
    }
    
    private fun formatLine(label: String, value: String): String {
        val cleanLabel = label.take(16)
        val cleanValue = value.take(16)
        return String.format("%-16s%16s", cleanLabel, cleanValue)
    }
    
    private fun horizontalRule(): String = "-".repeat(32)
    
    private fun formatQuantity(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.3f", value).trimEnd('0').trimEnd('.')
        }
    }
}

/**
 * ESC/POS command builder (matching Android app's EscPosBuilder)
 */
class EscPosBuilder {
    private val buffer = mutableListOf<Byte>()
    
    fun init(): EscPosBuilder {
        buffer.addAll(listOf(0x1B, 0x40))
        return this
    }
    
    fun alignLeft(): EscPosBuilder {
        buffer.addAll(listOf(0x1B, 0x61, 0x00))
        return this
    }
    
    fun alignCenter(): EscPosBuilder {
        buffer.addAll(listOf(0x1B, 0x61, 0x01))
        return this
    }
    
    fun alignRight(): EscPosBuilder {
        buffer.addAll(listOf(0x1B, 0x61, 0x02))
        return this
    }
    
    fun bold(on: Boolean): EscPosBuilder {
        buffer.addAll(listOf(0x1B, 0x45, if (on) 0x01 else 0x00))
        return this
    }
    
    fun text(s: String): EscPosBuilder {
        buffer.addAll(s.toByteArray(Charsets.UTF_8).map { it })
        return this
    }
    
    fun lf(lines: Int = 1): EscPosBuilder {
        repeat(lines) { buffer.add(0x0A) }
        return this
    }
    
    fun cut(): EscPosBuilder {
        buffer.addAll(listOf(0x1D, 0x56, 0x00))
        return this
    }
    
    fun bytes(): ByteArray = buffer.map { it.toByte() }.toByteArray()
    
    private fun List<Int>.addAll(items: List<Int>) = items.forEach { buffer.add(it.toByte()) }
}
