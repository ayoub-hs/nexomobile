package com.nexopos.erp.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.nexopos.erp.R
import com.nexopos.erp.core.network.CreateOrderRequest
import com.nexopos.erp.core.network.OrderSummary
import com.nexopos.erp.core.prefs.SettingsRepository
import com.nexopos.erp.core.print.EscPosBuilder
import com.nexopos.erp.core.print.PrinterConfig
import com.nexopos.erp.core.print.PrinterType
import com.nexopos.erp.core.print.TransportFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PrintUtil {
    suspend fun printSampleReceipt(context: Context, config: PrinterConfig) {
        val builder = EscPosBuilder()
            .init()
            .alignCenter()
            .bold(true)
            .text(context.getString(R.string.app_name))
            .lf()
            .bold(false)
            .text(context.getString(R.string.print_sample_title))
            .lf(2)
        val logo = loadLogo(context, config.logoUri)
        if (logo != null) {
            builder.image(logo, config.paperWidthDots).lf(2)
        }
        val bytes = builder
            .alignLeft()
            .text(context.getString(R.string.print_sample_item_a_line))
            .lf()
            .text(context.getString(R.string.print_sample_item_b_line))
            .lf()
            .alignRight()
            .bold(true)
            .text(context.getString(R.string.print_sample_total_line))
            .lf(2)
            .alignCenter()
            .bold(false)
            .text(context.getString(R.string.print_receipt_thank_you))
            .lf(4)
            .apply { if (config.cut) cut() }
            .bytes()
        send(context, config, bytes)
    }

    suspend fun printOrderReceipt(
        context: Context,
        config: PrinterConfig,
        request: CreateOrderRequest,
        summary: OrderSummary?,
        settingsRepository: SettingsRepository? = null
    ) {
        val storeName = withContext(Dispatchers.IO) {
            settingsRepository?.storeNameFlow?.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        val logo = loadLogo(context, config.logoUri)

        val builder = EscPosBuilder()
            .init()
            .alignCenter()

        if (logo != null) {
            builder.image(logo, config.paperWidthDots)
                .lf()
        } else {
            builder.bold(true)
                .text((storeName ?: context.getString(R.string.app_name)).take(32))
                .lf()
                .bold(false)
        }

        val orderCode = summary?.code?.trim()?.takeIf { it.isNotEmpty() }

        orderCode?.let {
            builder.text(context.getString(R.string.print_receipt_order, it.take(32)))
                .lf()
        }

        builder.text(currentTimestamp())
            .lf()

        val customer = summary?.customer ?: request.customer
        customer?.let { customer ->
            val nameCandidates = listOfNotNull(customer.firstName, customer.lastName)
                .filter { it.isNotBlank() }
            val combinedName = nameCandidates.joinToString(" ")
            val fallback = customer.name?.takeIf { it.isNotBlank() }
                ?: customer.username?.takeIf { it.isNotBlank() }
                ?: customer.id.takeIf { it > 0 }?.let { id ->
                    context.getString(R.string.customer_fallback_name, id)
                }
            val displayName = (if (combinedName.isNotBlank()) combinedName else fallback)?.take(32)
            if (!displayName.isNullOrBlank()) {
                builder.text(context.getString(R.string.print_receipt_customer, displayName))
                    .lf()
            }
        }

        builder.text(horizontalRule(config.paperWidthDots))
            .lf()

        builder.alignLeft()
        request.products.forEach { product ->
            val nameWithUnit = product.unitName?.takeIf { it.isNotBlank() }?.let { unit ->
                "${product.name} ($unit)"
            } ?: product.name
            builder.text(nameWithUnit.take(32))
                .lf()
            val qty = formatQuantity(product.quantity)
            val unit = formatMoney(product.unitPrice)
            val total = formatMoney(product.totalPrice)
            builder.text(formatLine("$qty x $unit", total))
                .lf()
        }

        builder.lf()
            .text(formatLine(context.getString(R.string.print_receipt_subtotal_label), formatMoney(request.subtotal)))
            .lf()
            .text(horizontalRule(config.paperWidthDots))
            .lf()

        val discountAmount = request.discountAmount ?: 0.0
        if (discountAmount != 0.0) {
            builder.text(formatLine(context.getString(R.string.summary_discount_amount), formatMoney(discountAmount)))
                .lf()
        }

        if (request.taxValue != 0.0) {
            builder.text(formatLine(context.getString(R.string.print_receipt_tax_label), formatMoney(request.taxValue)))
                .lf()
        }

        builder.bold(true)
            .text(formatLine(context.getString(R.string.print_receipt_total_label), formatMoney(request.total)))
            .lf()
            .bold(false)

        val bytes = builder
            .lf(2)
            .alignCenter()
            .text(context.getString(R.string.print_receipt_thank_you))
            .lf(4)
            .apply { if (config.cut) cut() }
            .bytes()
        send(context, config, bytes)
    }

    private suspend fun send(context: Context, config: PrinterConfig, data: ByteArray) {
        when (config.type) {
            PrinterType.Bluetooth -> {
                val transport = TransportFactory.bluetooth(context, config.macAddress)
                requireNotNull(transport) { context.getString(R.string.print_error_bluetooth_unconfigured) }
                transport.send(data)
            }
            PrinterType.Tcp -> {
                val transport = TransportFactory.tcp(config.host, config.port)
                requireNotNull(transport) { context.getString(R.string.print_error_tcp_unconfigured) }
                transport.send(data)
            }
        }
    }

    private fun loadLogo(context: Context, logoUri: String?): Bitmap? {
        return logoUri?.takeIf { it.isNotBlank() }?.let { loadBitmapFromUri(context, Uri.parse(it)) }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                val stream = context.contentResolver.openInputStream(uri)
                stream?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }
            bitmap?.let {
                if (it.config == Bitmap.Config.HARDWARE) {
                    it.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    it
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun currentTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date())
    }

    private fun formatMoney(value: Double): String = String.format(Locale.US, "%.3f DT", value)

    private fun formatQuantity(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }

    private fun formatLine(label: String, value: String): String {
        val cleanLabel = label.take(16)
        val cleanValue = value.take(16)
        return String.format(Locale.US, "%-16s%16s", cleanLabel, cleanValue)
    }

    private fun horizontalRule(widthDots: Int): String {
        val chars = (widthDots / 8).coerceAtLeast(1)
        return "-".repeat(chars)
    }

}
