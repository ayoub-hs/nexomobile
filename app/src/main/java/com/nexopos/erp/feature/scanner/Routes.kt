package com.nexopos.erp.feature.scanner

import android.net.Uri

object ScannerRoutes {
    const val HOME = "scanner"
    const val SCAN = "scanner/scan"
    const val PRODUCT_DETAIL = "scanner/product/{id}"
    const val PRODUCT_CREATE = "scanner/product/new?barcode={barcode}"
    const val PRODUCT_EDIT = "scanner/product/{id}/edit"

    fun detail(id: Long) = "scanner/product/$id"
    fun create(barcode: String? = null): String = "scanner/product/new?barcode=${Uri.encode(barcode ?: "")}"
    fun edit(id: Long) = "scanner/product/$id/edit"
}

object ScannerFeature {
    const val PERMISSION_READ = "products.read"
    const val PERMISSION_WRITE = "products.write"
}
