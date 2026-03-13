package com.nexopos.erp.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Authentication request model for login.
 */
@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "email")
    val email: String,
    @Json(name = "password")
    val password: String,
    @Json(name = "device_name")
    val deviceName: String = "NexoPos Mobile"
)

/**
 * Authentication response model.
 */
@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "success")
    val success: Boolean,
    @Json(name = "data")
    val data: LoginData?
)

@JsonClass(generateAdapter = true)
data class LoginData(
    @Json(name = "token")
    val token: String,
    @Json(name = "user")
    val user: UserInfo
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    @Json(name = "id")
    val id: Long,
    @Json(name = "name")
    val name: String,
    @Json(name = "email")
    val email: String
)

/**
 * Logout response model.
 */
@JsonClass(generateAdapter = true)
data class LogoutResponse(
    @Json(name = "success")
    val success: Boolean,
    @Json(name = "message")
    val message: String?
)

/**
 * Current user response model.
 */
@JsonClass(generateAdapter = true)
data class UserResponse(
    @Json(name = "success")
    val success: Boolean,
    @Json(name = "data")
    val data: UserData?
)

@JsonClass(generateAdapter = true)
data class UserData(
    @Json(name = "id")
    val id: Long,
    @Json(name = "name")
    val name: String,
    @Json(name = "email")
    val email: String,
    @Json(name = "roles")
    val roles: List<String>
)

/**
 * Permissions response model.
 */
@JsonClass(generateAdapter = true)
data class PermissionsResponse(
    @Json(name = "success")
    val success: Boolean,
    @Json(name = "data")
    val data: PermissionsData?
)

@JsonClass(generateAdapter = true)
data class PermissionsData(
    @Json(name = "permissions")
    val permissions: List<String>,
    @Json(name = "roles")
    val roles: List<String>
)
