package com.nexopos.erp.core.print

enum class PrinterType { Bluetooth, Tcp }

data class PrinterConfig(
    val type: PrinterType,
    val macAddress: String? = null,
    val host: String? = null,
    val port: Int = 9100,
    val paperWidthDots: Int = 384,
    val cut: Boolean = true,
    val logoUri: String? = null
)
