package com.nexopos.erp.core.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Feature flags and permissions management.
 * 
 * Integrates with the permissions endpoint to provide:
 * - Permission-based feature gating
 * - Role-based access control
 * - A/B testing support through feature toggles
 * 
 * Usage:
 * ```kotlin
 * // Check single permission
 * if (featureFlags.hasPermission("products.read")) { ... }
 * 
 * // Check multiple permissions (AND logic)
 * if (featureFlags.hasAllPermissions(listOf("orders.read", "orders.write"))) { ... }
 * 
 * // Check any permission (OR logic)
 * if (featureFlags.hasAnyPermission(listOf("admin", "manager"))) { ... }
 * 
 * // Observe permission changes
 * featureFlags.permissionsFlow.collect { permissions ->
 *     // Update UI based on permissions
 * }
 * ```
 */
class FeatureFlags {
    
    private val _permissions = MutableStateFlow<Set<String>>(emptySet())
    val permissionsFlow: StateFlow<Set<String>> = _permissions.asStateFlow()
    
    private val _roles = MutableStateFlow<Set<String>>(emptySet())
    val rolesFlow: StateFlow<Set<String>> = _roles.asStateFlow()
    
    private val _featureToggles = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val featureTogglesFlow: StateFlow<Map<String, Boolean>> = _featureToggles.asStateFlow()
    
    /**
     * Update permissions from the server response.
     */
    fun updatePermissions(permissions: List<String>) {
        _permissions.value = permissions.toSet()
    }
    
    /**
     * Update roles from the server response.
     */
    fun updateRoles(roles: List<String>) {
        _roles.value = roles.toSet()
    }
    
    /**
     * Update permissions and roles together.
     */
    fun updatePermissionsAndRoles(permissions: List<String>, roles: List<String>) {
        _permissions.value = permissions.toSet()
        _roles.value = roles.toSet()
    }
    
    /**
     * Set a feature toggle value.
     */
    fun setFeatureToggle(feature: String, enabled: Boolean) {
        _featureToggles.value = _featureToggles.value + (feature to enabled)
    }
    
    /**
     * Enable a feature toggle.
     */
    fun enableFeature(feature: String) {
        setFeatureToggle(feature, true)
    }
    
    /**
     * Disable a feature toggle.
     */
    fun disableFeature(feature: String) {
        setFeatureToggle(feature, false)
    }
    
    /**
     * Check if user has a specific permission.
     */
    fun hasPermission(permission: String): Boolean {
        return _permissions.value.contains(permission)
    }
    
    /**
     * Check if user has all of the specified permissions.
     */
    fun hasAllPermissions(permissions: List<String>): Boolean {
        return permissions.all { _permissions.value.contains(it) }
    }
    
    /**
     * Check if user has any of the specified permissions.
     */
    fun hasAnyPermission(permissions: List<String>): Boolean {
        return permissions.any { _permissions.value.contains(it) }
    }
    
    /**
     * Check if user has a specific role.
     */
    fun hasRole(role: String): Boolean {
        return _roles.value.contains(role)
    }
    
    /**
     * Check if user has all of the specified roles.
     */
    fun hasAllRoles(roles: List<String>): Boolean {
        return roles.all { _roles.value.contains(it) }
    }
    
    /**
     * Check if user has any of the specified roles.
     */
    fun hasAnyRole(roles: List<String>): Boolean {
        return roles.any { _roles.value.contains(it) }
    }
    
    /**
     * Check if a feature toggle is enabled.
     */
    fun isFeatureEnabled(feature: String): Boolean {
        return _featureToggles.value[feature] ?: false
    }
    
    /**
     * Check if feature is enabled based on permission OR feature toggle.
     */
    fun isEnabledByPermissionOrToggle(permission: String, feature: String): Boolean {
        return hasPermission(permission) || isFeatureEnabled(feature)
    }
    
    /**
     * Get current permissions snapshot.
     */
    fun getPermissions(): Set<String> = _permissions.value
    
    /**
     * Get current roles snapshot.
     */
    fun getRoles(): Set<String> = _roles.value
    
    /**
     * Clear all permissions and roles (on logout).
     */
    fun clear() {
        _permissions.value = emptySet()
        _roles.value = emptySet()
        _featureToggles.value = emptyMap()
    }
    
    companion object {
        // ====================================================================
        // Feature Toggle Keys (for A/B testing)
        // ====================================================================
        
        const val FEATURE_NEW_CART_UI = "feature_new_cart_ui"
        const val FEATURE_ENHANCED_SEARCH = "feature_enhanced_search"
        const val FEATURE_BATCH_ORDERS = "feature_batch_orders"
        const val FEATURE_OFFLINE_MODE = "feature_offline_mode"
        const val FEATURE_MULTI_CURRENCY = "feature_multi_currency"
        const val FEATURE_BARCODE_SCANNER = "feature_barcode_scanner"
        const val FEATURE_CLOUD_SYNC = "feature_cloud_sync"
        const val FEATURE_ANALYTICS = "feature_analytics"
    }
}

/**
 * Sealed class for permission check results.
 */
sealed class PermissionResult {
    object Granted : PermissionResult()
    data class Denied(val reason: String) : PermissionResult()
}

/**
 * Extension function to check permission with result type.
 */
fun FeatureFlags.checkPermission(permission: String): PermissionResult {
    return if (hasPermission(permission)) {
        PermissionResult.Granted
    } else {
        PermissionResult.Denied("Permission '$permission' is required")
    }
}
