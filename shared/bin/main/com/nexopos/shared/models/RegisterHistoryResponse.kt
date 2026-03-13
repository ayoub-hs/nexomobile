// RegisterHistoryResponse.kt
package com.nexopos.shared.models

import com.squareup.moshi.Json

data class RegisterHistoryResponse(
    val status: String,
    val message: String,
    val data: RegisterHistoryData
)

data class RegisterHistoryData(
    @Json(name = "history") val history: RegisterHistory
)

data class RegisterHistoryListResponse(
    val status: String? = null,
    val message: String? = null,
    val data: List<RegisterHistory>? = null
)
