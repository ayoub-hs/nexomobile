package com.nexopos.desktop.hardware

import com.nexopos.shared.hardware.CashDrawer
import java.io.File
import java.io.FileOutputStream

/**
 * Desktop implementation of cash drawer control.
 * 
 * Opens cash drawer by writing to serial device (e.g., /dev/ttyUSB0).
 * Equivalent to: echo '1' > /dev/ttyUSB0
 * 
 * TODO: Configure the actual command byte(s) your drawer expects.
 */
class DesktopCashDrawer(
    private val devicePath: String = "/dev/ttyUSB0",
    private val openCommand: ByteArray = byteArrayOf('1'.code.toByte(), '\n'.code.toByte())
) : CashDrawer {
    
    override suspend fun open(): Result<Unit> {
        return try {
            println("[DesktopCashDrawer] Opening drawer via $devicePath")
            
            val device = File(devicePath)
            if (!device.exists()) {
                return Result.failure(Exception("Cash drawer device not found: $devicePath"))
            }
            
            // Write open command to serial device
            FileOutputStream(device).use { output ->
                output.write(openCommand)
                output.flush()
            }
            
            println("[DesktopCashDrawer] Drawer opened successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            println("[DesktopCashDrawer] Failed to open drawer: ${e.message}")
            Result.failure(e)
        }
    }
    
    override suspend fun isConnected(): Boolean {
        val device = File(devicePath)
        return device.exists() && device.canWrite()
    }
}
