package com.nexopos.erp.core.network

data class RegisterActionRequest(
    val amount: Double,
    val description: String = ""
)
