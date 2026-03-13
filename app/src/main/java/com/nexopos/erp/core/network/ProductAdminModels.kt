package com.nexopos.erp.core.network

import com.squareup.moshi.Json

data class AdminProductResponse(
    val id: Long,
    val name: String,
    val barcode: String?,
    @Json(name = "barcode_type") val barcodeType: String?,
    val sku: String?,
    val status: String?,
    @Json(name = "category_id") val categoryId: Long?,
    val description: String?,
    @Json(name = "stock_management") val stockManagement: String?,
    @Json(name = "tax_group_id") val taxGroupId: Long?,
    @Json(name = "tax_type") val taxType: String?,
    @Json(name = "unit_group") val unitGroup: Long?,
    @Json(name = "accurate_tracking") val accurateTracking: Boolean = false,
    @Json(name = "auto_cogs") val autoCogs: Boolean = false,
    @Json(name = "on_expiration") val onExpiration: String?,
    @Json(name = "is_manufactured") val isManufactured: Boolean = false,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean = false,
    @Json(name = "unit_quantities") val unitQuantities: List<AdminUnitQuantityResponse> = emptyList(),
    @Json(name = "low_stock_threshold") val lowStockThreshold: Int? = null,
    @Json(name = "stock_quantity") val stockQuantity: Double? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "deleted_at") val deletedAt: String? = null
)

data class AdminUnitQuantityResponse(
    val id: Long,
    @Json(name = "unit_id") val unitId: Long,
    val barcode: String? = null,
    @Json(name = "sale_price") val salePrice: Double? = null,
    @Json(name = "sale_price_edit") val salePriceEdit: Double? = null,
    @Json(name = "sale_price_with_tax") val salePriceWithTax: Double? = null,
    @Json(name = "wholesale_price") val wholesalePrice: Double? = null,
    @Json(name = "wholesale_price_edit") val wholesalePriceEdit: Double? = null,
    val cogs: Double? = null,
    @Json(name = "low_quantity") val lowQuantity: Double? = null,
    @Json(name = "stock_alert_enabled") val stockAlertEnabled: Boolean = false,
    val visible: Boolean = true,
    val quantity: Double = 0.0,
    @Json(name = "convert_unit_id") val convertUnitId: Long? = null,
    @Json(name = "preview_url") val previewUrl: String? = null,
    @Json(name = "is_manufactured") val isManufactured: Boolean = false,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean = false,
    val unit: UnitDetail? = null,
    @Json(name = "container_link") val containerLink: ContainerLink? = null
)

data class UnitQuantityUpdateResponse(
    @Json(name = "product_id") val productId: Long,
    @Json(name = "unit_quantity") val unitQuantity: AdminUnitQuantityResponse
)

data class UnitGroupResponse(
    val id: Long,
    val name: String,
    val description: String?
)

data class TaxGroupResponse(
    val id: Long,
    val name: String,
    val rate: Double,
    val description: String? = null
)

data class CreateProductRequest(
    val name: String,
    val barcode: String?,
    @Json(name = "barcode_type") val barcodeType: String,
    val sku: String?,
    val status: String,
    @Json(name = "category_id") val categoryId: Long,
    val description: String,
    @Json(name = "stock_management") val stockManagement: String,
    @Json(name = "tax_group_id") val taxGroupId: Long?,
    @Json(name = "tax_type") val taxType: String,
    @Json(name = "unit_group") val unitGroup: Long,
    @Json(name = "accurate_tracking") val accurateTracking: Boolean,
    @Json(name = "auto_cogs") val autoCogs: Boolean,
    @Json(name = "on_expiration") val onExpiration: String,
    @Json(name = "is_manufactured") val isManufactured: Boolean,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean,
    val variations: List<ProductAdminVariationRequest>
)

data class UpdateProductRequest(
    val name: String,
    val barcode: String?,
    @Json(name = "barcode_type") val barcodeType: String,
    val sku: String?,
    val status: String,
    @Json(name = "category_id") val categoryId: Long,
    val description: String,
    @Json(name = "stock_management") val stockManagement: String,
    @Json(name = "tax_group_id") val taxGroupId: Long?,
    @Json(name = "tax_type") val taxType: String,
    @Json(name = "unit_group") val unitGroup: Long,
    @Json(name = "accurate_tracking") val accurateTracking: Boolean,
    @Json(name = "auto_cogs") val autoCogs: Boolean,
    @Json(name = "on_expiration") val onExpiration: String,
    @Json(name = "is_manufactured") val isManufactured: Boolean,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean,
    val variations: List<ProductAdminVariationRequest>
)

data class UpdateUnitQuantityRequest(
    @Json(name = "sale_price_edit") val salePriceEdit: Double? = null,
    @Json(name = "wholesale_price_edit") val wholesalePriceEdit: Double? = null,
    val cogs: Double? = null,
    @Json(name = "low_quantity") val lowQuantity: Double? = null,
    @Json(name = "stock_alert_enabled") val stockAlertEnabled: Boolean? = null,
    val visible: Boolean? = null,
    @Json(name = "convert_unit_id") val convertUnitId: Long? = null,
    @Json(name = "preview_url") val previewUrl: String? = null,
    @Json(name = "is_manufactured") val isManufactured: Boolean? = null,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean? = null,
    @Json(name = "container_type_id") val containerTypeId: Long? = null
)

data class ProductAdminVariationRequest(
    @Json(name = "\$primary") val primary: Boolean = true,
    val identification: ProductAdminIdentificationRequest = ProductAdminIdentificationRequest(),
    val expiry: ProductAdminExpiryRequest = ProductAdminExpiryRequest(),
    val taxes: ProductAdminTaxesRequest = ProductAdminTaxesRequest(),
    val units: ProductAdminUnitsRequest,
    val images: List<ProductAdminImageRequest> = emptyList(),
    val groups: List<ProductAdminGroupRequest> = emptyList()
)

data class ProductAdminIdentificationRequest(
    @Json(name = "category_id") val categoryId: Long? = null,
    val barcode: String? = null,
    val sku: String? = null,
    @Json(name = "barcode_type") val barcodeType: String? = null,
    val status: String? = null,
    val description: String? = null,
    @Json(name = "stock_management") val stockManagement: String? = null,
    @Json(name = "is_manufactured") val isManufactured: Boolean? = null,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean? = null
)

data class ProductAdminExpiryRequest(
    val expires: Boolean = false,
    @Json(name = "on_expiration") val onExpiration: String? = null
)

data class ProductAdminTaxesRequest(
    @Json(name = "tax_group_id") val taxGroupId: Long? = null,
    @Json(name = "tax_type") val taxType: String? = null
)

data class ProductAdminUnitsRequest(
    @Json(name = "unit_group") val unitGroup: Long,
    @Json(name = "accurate_tracking") val accurateTracking: Boolean,
    @Json(name = "auto_cogs") val autoCogs: Boolean,
    @Json(name = "selling_group") val sellingGroup: List<ProductAdminSellingGroupRequest>
)

data class ProductAdminSellingGroupRequest(
    @Json(name = "unit_id") val unitId: Long,
    val barcode: String? = null,
    @Json(name = "sale_price") val salePrice: Double,
    @Json(name = "sale_price_edit") val salePriceEdit: Double,
    @Json(name = "wholesale_price") val wholesalePrice: Double,
    @Json(name = "wholesale_price_edit") val wholesalePriceEdit: Double,
    val cogs: Double,
    @Json(name = "stock_alert_enabled") val stockAlertEnabled: Boolean,
    @Json(name = "low_quantity") val lowQuantity: Double,
    val visible: Boolean,
    val quantity: Double,
    @Json(name = "convert_unit_id") val convertUnitId: Long? = null,
    @Json(name = "preview_url") val previewUrl: String? = null,
    @Json(name = "is_manufactured") val isManufactured: Boolean,
    @Json(name = "is_raw_material") val isRawMaterial: Boolean,
    @Json(name = "container_type_id") val containerTypeId: Long? = null
)

data class ProductAdminImageRequest(
    val url: String,
    val featured: Boolean = false
)

data class ProductAdminGroupRequest(
    @Json(name = "product_id") val productId: Long
)
