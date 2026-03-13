package com.nexopos.desktop.core.repo

import com.nexopos.shared.models.Product
import com.nexopos.shared.models.UnitQuantity

/**
 * Desktop-specific extension functions for the shared Product model.
 * These provide helper methods needed by the Desktop UI and business logic.
 */

/**
 * Get a specific unit quantity by ID.
 */
fun Product.getUnitQuantity(id: Long): UnitQuantity? {
    return unitQuantities?.find { it.id == id }
}

/**
 * Get the default/first unit quantity.
 * This is typically the base selling unit.
 */
fun Product.getDefaultUnitQuantity(): UnitQuantity? {
    return unitQuantities?.firstOrNull()
}

/**
 * Get the effective price from the default unit quantity.
 * Returns 0.0 if no unit quantity is available.
 */
fun Product.getPrice(): Double {
    return getDefaultUnitQuantity()?.effectivePrice ?: 0.0
}

/**
 * Check if this product has multiple unit variations/quantities.
 */
fun Product.hasVariations(): Boolean {
    return (unitQuantities?.size ?: 0) > 1
}

/**
 * Get all unit quantities as a list.
 * Returns empty list if none available.
 */
fun Product.getUnitQuantities(): List<UnitQuantity> {
    return unitQuantities ?: emptyList()
}

// Note: effectivePrice and unitName are already defined in the shared UnitQuantity model
// No additional extensions needed for UnitQuantity
