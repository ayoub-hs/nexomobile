package com.nexopos.erp.feature.pricelookup

object PriceLookupRoutes {
    const val HOME = "price-lookup"
    const val DETAIL = "price-lookup/product/{id}"

    fun detail(id: Long) = "price-lookup/product/$id"
}
