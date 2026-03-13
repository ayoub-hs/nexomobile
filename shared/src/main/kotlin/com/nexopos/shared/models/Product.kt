package com.nexopos.shared.models

import com.squareup.moshi.Json

/**
 * Shared network models for products and units used by both Android and Desktop.
 */

data class UnitDetail(
    val id: Long,
    val name: String?,
    val identifier: String?
)

data class ContainerLink(
    @Json(name = "container_type_id") val containerTypeId: Long,
    @Json(name = "container_type_name") val containerTypeName: String,
    val capacity: Double,
    @Json(name = "capacity_unit") val capacityUnit: String,
    @Json(name = "deposit_fee") val depositFee: Double
)

/**
 * Unit quantity information for a product variation.
 * Combines fields used by both Android and Desktop.
 */
data class UnitQuantity(
    val id: Long,
    @Json(name = "unit_id") val unitId: Long,
    val barcode: String? = null,
    @Json(name = "is_manufactured") val isManufactured: Boolean? = null,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean? = null,
    @Json(name = "sale_price") val salePrice: Double? = null,
    @Json(name = "sale_price_with_tax") val salePriceWithTax: Double? = null,
    @Json(name = "wholesale_price") val wholesalePrice: Double? = null,
    @Json(name = "wholesale_price_edit") val wholesalePriceWithTax: Double? = null,
    val quantity: Double? = null,
    val unit: UnitDetail? = null,
    @Json(name = "container_link") val containerLink: ContainerLink? = null
) {
    val unitName: String? get() = unit?.name
    val effectivePrice: Double get() = salePriceWithTax ?: salePrice ?: 0.0
}

/**
 * Base product model shared across platforms.
 */
data class Product(
    val id: Long,
    val name: String,
    val barcode: String?,
    @Json(name = "barcode_type") val barcodeType: String?,
    val sku: String?,
    @Json(name = "status") val status: String? = null,
    @Json(name = "category_id") val categoryId: Long? = null,
    @Json(name = "is_manufactured") val isManufactured: Boolean? = null,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean? = null,
    @Json(name = "unit_quantities") val unitQuantities: List<UnitQuantity>? = null,
    @Json(name = "low_stock_threshold") val lowStockThreshold: Int? = null,
    @Json(name = "stock_quantity") val stockQuantity: Double? = null
)
