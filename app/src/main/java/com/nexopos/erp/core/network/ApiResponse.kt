package com.nexopos.erp.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Generic API response wrapper for consistent API response handling.
 */
@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "status")
    val status: String,
    @Json(name = "data")
    val data: T?,
    @Json(name = "message")
    val message: String? = null
)
