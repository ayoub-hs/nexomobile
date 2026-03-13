package com.nexopos.erp.core.network

import com.squareup.moshi.Json
 
typealias Product = com.nexopos.shared.models.Product
typealias UnitQuantity = com.nexopos.shared.models.UnitQuantity
typealias UnitDetail = com.nexopos.shared.models.UnitDetail
typealias ContainerLink = com.nexopos.shared.models.ContainerLink

data class BarcodeSearchResult(
    val type: String?,
    val product: Product?
)

data class SearchRequest(
    val search: String,
    val arguments: Map<String, Any?>? = null
)

/**
 * Category from GET /api/categories
 */
data class Category(
    val id: Long,
    val name: String,
    @Json(name = "parent_id") val parentId: Long?,
    @Json(name = "media_id") val mediaId: Long?,
    @Json(name = "preview_url") val previewUrl: String?,
    @Json(name = "displays_on_pos") val displaysOnPos: Int,
    @Json(name = "total_items") val totalItems: Int,
    val description: String?,
    val author: Long?,
    val uuid: String?,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)
