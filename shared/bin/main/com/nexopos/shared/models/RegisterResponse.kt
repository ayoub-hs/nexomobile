// RegisterResponse.kt
package com.nexopos.shared.models

import com.squareup.moshi.Json

data class RegisterResponse(
    val status: String,
    val message: String,
    val data: RegisterData
)

data class RegisterData(
    @Json(name = "register") val register: Register
)

data class RegistersListResponse(
    val status: String? = null,
    val message: String? = null,
    val data: List<Register>? = null
)