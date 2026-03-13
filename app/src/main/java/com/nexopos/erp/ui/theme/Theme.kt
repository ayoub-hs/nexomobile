package com.nexopos.erp.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class PosColors(
    val success: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDanger: Color,
    val priceText: Color,
    val totalHighlight: Color,
    val totalBackground: Color,
    val discountText: Color,
    val changeText: Color,
    val stockInStock: Color,
    val stockLow: Color,
    val stockOut: Color,
    val selectedItem: Color,
    val cartItemBorder: Color,
    val quantityBadge: Color,
    val quickAdd: Color,
    val quickAddContainer: Color,
    val categoryAccent1: Color,
    val categoryAccent2: Color,
    val categoryAccent3: Color,
    val categoryAccent4: Color,
    val categoryAccent5: Color
)

private val LocalPosColors = staticCompositionLocalOf { buildPosColors(AppColors.nexoDark()) }

private fun buildColorScheme(colors: AppColors, dark: Boolean): ColorScheme {
    val builder: ColorScheme = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryDim,
            onPrimaryContainer = colors.onSurface,
            secondary = colors.info,
            onSecondary = colors.onPrimary,
            secondaryContainer = colors.surfaceOverlay,
            onSecondaryContainer = colors.onSurface,
            tertiary = colors.primaryHover,
            onTertiary = colors.onPrimary,
            tertiaryContainer = colors.surfaceRaised,
            onTertiaryContainer = colors.onSurface,
            error = colors.error,
            onError = colors.onError,
            errorContainer = colors.errorDim,
            onErrorContainer = colors.onSurface,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceVariant,
            outline = colors.outline,
            outlineVariant = colors.divider,
            scrim = Color(0xCC000000),
            inverseSurface = colors.inverseSurface,
            inverseOnSurface = colors.inverseOnSurface,
            inversePrimary = colors.primaryHover,
            surfaceDim = colors.surfaceVariant,
            surfaceBright = colors.surfaceRaised,
            surfaceContainerLowest = colors.background,
            surfaceContainerLow = colors.surfaceVariant,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceOverlay
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryDim,
            onPrimaryContainer = colors.onSurface,
            secondary = colors.info,
            onSecondary = colors.onPrimary,
            secondaryContainer = colors.surfaceVariant,
            onSecondaryContainer = colors.onSurface,
            tertiary = colors.primaryHover,
            onTertiary = colors.onPrimary,
            tertiaryContainer = colors.surfaceRaised,
            onTertiaryContainer = colors.onSurface,
            error = colors.error,
            onError = colors.onError,
            errorContainer = colors.errorDim,
            onErrorContainer = colors.onSurface,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.onSurfaceVariant,
            outline = colors.outline,
            outlineVariant = colors.divider,
            scrim = Color(0x66000000),
            inverseSurface = colors.inverseSurface,
            inverseOnSurface = colors.inverseOnSurface,
            inversePrimary = colors.primaryPressed,
            surfaceDim = colors.surfaceRaised,
            surfaceBright = colors.surface,
            surfaceContainerLowest = colors.surface,
            surfaceContainerLow = colors.surfaceRaised,
            surfaceContainer = colors.surfaceVariant,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceOverlay
        )
    }

    return builder
}

private fun buildPosColors(colors: AppColors) = PosColors(
    success = colors.success,
    successContainer = colors.successDim,
    onSuccess = colors.onSuccess,
    warning = colors.warning,
    warningContainer = colors.warningDim,
    onWarning = colors.onWarning,
    danger = colors.error,
    dangerContainer = colors.errorDim,
    onDanger = colors.onError,
    priceText = colors.info,
    totalHighlight = colors.primaryHover,
    totalBackground = colors.primaryDim,
    discountText = colors.warning,
    changeText = colors.success,
    stockInStock = colors.success,
    stockLow = colors.warning,
    stockOut = colors.error,
    selectedItem = colors.surfaceOverlay,
    cartItemBorder = colors.outline,
    quantityBadge = colors.primary,
    quickAdd = colors.primary,
    quickAddContainer = colors.surfaceRaised,
    categoryAccent1 = colors.primary,
    categoryAccent2 = colors.info,
    categoryAccent3 = colors.success,
    categoryAccent4 = colors.warning,
    categoryAccent5 = colors.primaryHover
)

@Composable
fun AppTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) AppColors.nexoDark() else AppColors.nexoLight()
    val colorScheme = buildColorScheme(colors, darkTheme)
    val typography = AppTypography.build()
    val posColors = buildPosColors(colors)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalAppRadii provides AppRadii(),
        LocalAppElevations provides AppElevations(),
        LocalAppColors provides colors,
        LocalAppTypography provides typography,
        LocalPosColors provides posColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography.material,
            content = content
        )
    }
}

val MaterialTheme.posColors: PosColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPosColors.current
