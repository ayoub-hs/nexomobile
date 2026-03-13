package com.nexopos.erp.feature.salespos.ui

import androidx.compose.runtime.Immutable
import com.nexopos.erp.core.network.ContainerLink
import com.nexopos.erp.core.network.Customer
import com.nexopos.erp.core.network.OrderSummary
import com.nexopos.erp.core.network.PaymentMethod
import kotlin.math.floor

@Immutable
data class CartItem(
    val key: String,
    val productId: Long,
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val unitQuantityId: Long? = null,
    val unitId: Long? = null,
    val unitName: String? = null,
    val salePrice: Double? = null,
    val wholesalePriceWithTax: Double? = null,
    val useWholesale: Boolean = false,
    val isCustomPrice: Boolean = false,
    val containerLink: ContainerLink? = null,
    val containerTrackingEnabled: Boolean = false,
    val containerQuantityOverride: Int? = null,
    val hasContainerMetadata: Boolean = false
) {
    val lineTotal: Double
        get() = unitPrice * quantity

    val subtotalExcludingTax: Double
        get() = lineTotal

    val taxAmount: Double
        get() = if (unitPrice <= 0.0) 0.0 else {
            val grossPrice = unitPrice * quantity
            val netPrice = grossPrice / (1 + CartState.DEFAULT_TAX_RATE)
            Math.round((grossPrice - netPrice) * 100.0) / 100.0
        }

    val requiredContainerQuantity: Int?
        get() {
            val link = containerLink ?: return null
            if (link.capacity <= 0.0) return 0
            return floor(quantity / link.capacity).toInt().coerceAtLeast(0)
        }

    val effectiveContainerQuantity: Int?
        get() = if (!containerTrackingEnabled) null else (containerQuantityOverride ?: requiredContainerQuantity)
}

enum class DiscountType {
    Amount,
    Percent
}

@Immutable
data class CartState(
    val items: List<CartItem> = emptyList(),
    val discountType: DiscountType = DiscountType.Amount,
    val discountValue: Double = 0.0,
    val tenderedOverride: Boolean = false,
    val tendered: Double = 0.0,
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedPayment: PaymentMethod? = null,
    val customers: List<Customer> = emptyList(),
    val selectedCustomer: Customer? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val lastOrder: OrderSummary? = null,
    val printReceipt: Boolean = true,
    val printerLabel: String? = null,
    val printerReady: Boolean = false,
    val isTestingPrinter: Boolean = false,
    val printerMessage: String? = null,
    val printerMessageError: Boolean = false,
    val pendingOrderCount: Int = 0,
    val failedOrderCount: Int = 0,
    val editingOrderId: Long? = null,
    val editingServerOrderId: Long? = null
) {
    companion object {
        const val DEFAULT_TAX_RATE = 0.19
    }

    val subtotal: Double
        get() = items.sumOf { it.lineTotal }

    val discountAmount: Double
        get() = when (discountType) {
            DiscountType.Amount -> discountValue.coerceIn(0.0, subtotal)
            DiscountType.Percent -> subtotal * (discountValue.coerceIn(0.0, 100.0) / 100.0)
        }

    val total: Double
        get() = (subtotal - discountAmount).coerceAtLeast(0.0)

    val taxTotal: Double
        get() = if (total <= 0.0) 0.0 else {
            val netPrice = total / (1 + DEFAULT_TAX_RATE)
            Math.round((total - netPrice) * 100.0) / 100.0
        }

    val change: Double
        get() = (tendered - total).coerceAtLeast(0.0)

    val effectiveTendered: Double
        get() = if (tenderedOverride) tendered else total

    val hasTrackedContainersWithoutCustomer: Boolean
        get() = selectedCustomer == null && items.any { it.containerTrackingEnabled }
}
