package com.nexopos.erp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nexopos.erp.core.prefs.FeatureFlags
import org.koin.androidx.compose.koinViewModel

/**
 * A composable that conditionally displays content based on user permissions.
 * 
 * Usage:
 * ```kotlin
 * PermissionGate(permission = "products.read") {
 *     // This content only shows if user has products.read permission
 *     ProductList()
 * }
 * 
 * // With fallback content
 * PermissionGate(
 *     permission = "admin.feature",
 *     fallback = { Text("Upgrade to access this feature") }
 * ) {
 *     AdminPanel()
 * }
 * ```
 */
@Composable
fun PermissionGate(
    permission: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    fallback: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val featureFlags: FeatureFlags = koinViewModel()
    val permissions by featureFlags.permissionsFlow.collectAsState()
    
    val hasPermission = permissions.contains(permission)
    
    AnimatedVisibility(
        visible = visible && hasPermission,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
    
    if (fallback != null && (!visible || !hasPermission)) {
        fallback()
    }
}

/**
 * A composable that displays content only when the user has ALL specified permissions.
 */
@Composable
fun AllPermissionsGate(
    permissions: List<String>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    fallback: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val featureFlags: FeatureFlags = koinViewModel()
    val currentPermissions by featureFlags.permissionsFlow.collectAsState()
    
    val hasAllPermissions = permissions.all { currentPermissions.contains(it) }
    
    AnimatedVisibility(
        visible = visible && hasAllPermissions,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
    
    if (fallback != null && (!visible || !hasAllPermissions)) {
        fallback()
    }
}

/**
 * A composable that displays content only when the user has ANY of the specified permissions.
 */
@Composable
fun AnyPermissionGate(
    permissions: List<String>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    fallback: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val featureFlags: FeatureFlags = koinViewModel()
    val currentPermissions by featureFlags.permissionsFlow.collectAsState()
    
    val hasAnyPermission = permissions.any { currentPermissions.contains(it) }
    
    AnimatedVisibility(
        visible = visible && hasAnyPermission,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
    
    if (fallback != null && (!visible || !hasAnyPermission)) {
        fallback()
    }
}

/**
 * A composable that conditionally displays content based on feature toggle.
 */
@Composable
fun FeatureToggleGate(
    featureKey: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    fallback: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val featureFlags: FeatureFlags = koinViewModel()
    val featureToggles by featureFlags.featureTogglesFlow.collectAsState()
    
    val isEnabled = featureToggles[featureKey] ?: false
    
    AnimatedVisibility(
        visible = visible && isEnabled,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
    
    if (fallback != null && (!visible || !isEnabled)) {
        fallback()
    }
}

/**
 * A composable that displays content only when the user has the specified role.
 */
@Composable
fun RoleGate(
    role: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    fallback: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val featureFlags: FeatureFlags = koinViewModel()
    val roles by featureFlags.rolesFlow.collectAsState()
    
    val hasRole = roles.contains(role)
    
    AnimatedVisibility(
        visible = visible && hasRole,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
    
    if (fallback != null && (!visible || !hasRole)) {
        fallback()
    }
}
