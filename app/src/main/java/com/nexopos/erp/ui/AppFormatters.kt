package com.nexopos.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val APP_DECIMAL_SYMBOLS = DecimalFormatSymbols(Locale.US)
private val APP_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
private val APP_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

private fun decimalFormatter(pattern: String): DecimalFormat =
    DecimalFormat(pattern, APP_DECIMAL_SYMBOLS)

@Composable
fun rememberAppCurrencyFormatter(): NumberFormat = remember {
    decimalFormatter("#,##0.000 'DT'")
}

fun formatAppCurrency(value: Double): String = decimalFormatter("#,##0.000 'DT'").format(value)

fun formatAppQuantity(value: Double, maxDecimals: Int = 4): String {
    if (value == value.toLong().toDouble()) {
        return value.toLong().toString()
    }
    val pattern = buildString {
        append("#,##0")
        if (maxDecimals > 0) {
            append('.')
            repeat(maxDecimals) { append('#') }
        }
    }
    return decimalFormatter(pattern).format(value)
}

fun formatAppUnit(unitName: String?): String? {
    val trimmed = unitName?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    return when (trimmed.lowercase(Locale.ROOT)) {
        "pcs", "pc" -> "PCS"
        "l", "lt", "ltr", "liter", "litre" -> "L"
        "ml" -> "ML"
        "kg" -> "KG"
        "g" -> "G"
        else -> if (trimmed.length <= 4) trimmed.uppercase(Locale.ROOT) else trimmed
    }
}

fun formatAppQuantityWithUnit(value: Double, unitName: String?): String {
    val unit = formatAppUnit(unitName)
    return if (unit.isNullOrBlank()) {
        formatAppQuantity(value)
    } else {
        "${formatAppQuantity(value)} $unit"
    }
}

fun formatAppCountWithUnit(value: Int, unitName: String?): String {
    val unit = formatAppUnit(unitName)
    return if (unit.isNullOrBlank()) {
        value.toString()
    } else {
        "$value $unit"
    }
}

fun formatAppDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(APP_DATE_FORMATTER)

fun formatAppDateTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(APP_DATE_TIME_FORMATTER)

fun formatRemoteDateTime(value: String?): String {
    val instant = parseRemoteInstant(value) ?: return value.orEmpty()
    return instant.atZone(ZoneId.systemDefault()).format(APP_DATE_TIME_FORMATTER)
}

private fun parseRemoteInstant(value: String?): Instant? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) {
        return null
    }
    return runCatching { Instant.parse(normalized) }.getOrElse {
        runCatching { OffsetDateTime.parse(normalized).toInstant() }.getOrElse {
            normalized.toLongOrNull()?.let(Instant::ofEpochMilli)
        }
    }
}
