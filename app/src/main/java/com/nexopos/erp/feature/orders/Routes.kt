package com.nexopos.erp.feature.orders

/**
 * Orders Feature Routes
 * Navigation routes for the Orders feature.
 * 
 * Order details are shown as popup/dialog, not as separate screen.
 */
object OrdersRoutes {
    const val ORDERS = "orders/list"
    const val DETAIL = "orders/{orderId}"

    fun detail(orderId: Long): String = "orders/$orderId"
}
