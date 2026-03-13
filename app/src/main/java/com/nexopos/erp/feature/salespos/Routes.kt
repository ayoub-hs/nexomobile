package com.nexopos.erp.feature.salespos

/**
 * POS Feature Routes
 * Navigation routes for the Point of Sale feature flow.
 * 
 * Flow: Search (main entry) → Scan (optional) → Cart → Checkout
 */
object PosRoutes {
    const val SEARCH = "pos/search"      // Main entry - product search
    const val SCAN = "pos/scan"          // Optional barcode scanning
    const val CART = "pos/cart"          // Cart review
    const val CHECKOUT = "pos/checkout"  // Payment processing
}
