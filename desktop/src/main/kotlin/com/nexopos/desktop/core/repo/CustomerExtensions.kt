package com.nexopos.desktop.core.repo

import com.nexopos.shared.models.Customer

/**
 * Desktop-specific extension functions for the shared Customer model.
 */

/**
 * Get display name for a customer.
 * Tries name first, then firstName + lastName, then falls back to "Customer #id".
 */
fun Customer.getDisplayName(): String {
    return name ?: "${firstName ?: ""} ${lastName ?: ""}".trim().ifEmpty { "Customer #$id" }
}
