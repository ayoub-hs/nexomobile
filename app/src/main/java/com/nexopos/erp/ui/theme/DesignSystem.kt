package com.nexopos.erp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AppSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp
) {
    val screen: Dp get() = l
    val card: Dp get() = l
    val section: Dp get() = xl

    // Backward-compatible aliases used by the current UI.
    val xxs: Dp get() = xs
    val sm: Dp get() = m
    val md: Dp get() = l
    val lg: Dp get() = xl
}

@Immutable
data class AppRadii(
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val fab: Dp = 22.dp
) {
    val md: Dp get() = medium
    val lg: Dp get() = large
    val xl: Dp get() = fab
}

@Immutable
data class AppElevations(
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp
)

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceOverlay: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val divider: Color,
    val primary: Color,
    val primaryHover: Color,
    val primaryPressed: Color,
    val primaryDim: Color,
    val onPrimary: Color,
    val success: Color,
    val onSuccess: Color,
    val successDim: Color,
    val warning: Color,
    val onWarning: Color,
    val warningDim: Color,
    val error: Color,
    val onError: Color,
    val errorDim: Color,
    val info: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color
) {
    val elevated: Color get() = surfaceRaised
    val border: Color get() = outline
    val text: Color get() = onSurface
    val muted: Color get() = onSurfaceVariant
    val danger: Color get() = error

    // Compatibility aliases for legacy colorScheme call sites.
    val primaryContainer: Color get() = primaryDim
    val onPrimaryContainer: Color get() = onSurface
    val secondary: Color get() = info
    val onSecondary: Color get() = onPrimary
    val secondaryContainer: Color get() = surfaceOverlay
    val onSecondaryContainer: Color get() = onSurface
    val tertiary: Color get() = primaryHover
    val tertiaryContainer: Color get() = surfaceRaised
    val onTertiaryContainer: Color get() = onSurface
    val errorContainer: Color get() = errorDim
    val onErrorContainer: Color get() = onSurface
    val outlineVariant: Color get() = divider

    companion object {
        fun nexoDark() = AppColors(
            background = Color(0xFF0B1419),
            surface = Color(0xFF111C22),
            surfaceRaised = Color(0xFF16242C),
            surfaceOverlay = Color(0xFF1C2F38),
            surfaceVariant = Color(0xFF0F1A20),
            outline = Color(0xFF2A3C45),
            divider = Color(0xFF22343D),
            primary = Color(0xFF5AA7D9),
            primaryHover = Color(0xFF6FB6E3),
            primaryPressed = Color(0xFF4B97C8),
            primaryDim = Color(0xFF2F6D8F),
            onPrimary = Color(0xFF001820),
            success = Color(0xFF39C28E),
            onSuccess = Color(0xFF002116),
            successDim = Color(0xFF1F6F4F),
            warning = Color(0xFFF0B65B),
            onWarning = Color(0xFF2A1A00),
            warningDim = Color(0xFF7A5A21),
            error = Color(0xFFF06A6A),
            onError = Color(0xFF2A0000),
            errorDim = Color(0xFF7A2F2F),
            info = Color(0xFF7FAFD1),
            onBackground = Color(0xFFE8F1F5),
            onSurface = Color(0xFFE8F1F5),
            onSurfaceVariant = Color(0xFFA4B7C1),
            inverseSurface = Color(0xFFE8F1F5),
            inverseOnSurface = Color(0xFF0B1419)
        )

        fun nexoLight() = AppColors(
            background = Color(0xFFF7FAFC),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFF1F5F9),
            surfaceOverlay = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEEF2F6),
            outline = Color(0xFFCBD5E1),
            divider = Color(0xFFE2E8F0),
            primary = Color(0xFF5AA7D9),
            primaryHover = Color(0xFF6FB6E3),
            primaryPressed = Color(0xFF4B97C8),
            primaryDim = Color(0xFFD7EAF5),
            onPrimary = Color(0xFF001820),
            success = Color(0xFF1E9E73),
            onSuccess = Color(0xFFFFFFFF),
            successDim = Color(0xFFD9F3EA),
            warning = Color(0xFFD9902F),
            onWarning = Color(0xFFFFFFFF),
            warningDim = Color(0xFFF8E8D2),
            error = Color(0xFFD94A4A),
            onError = Color(0xFFFFFFFF),
            errorDim = Color(0xFFF9D9D9),
            info = Color(0xFF2F7FB0),
            onBackground = Color(0xFF0B1220),
            onSurface = Color(0xFF0B1220),
            onSurfaceVariant = Color(0xFF475569),
            inverseSurface = Color(0xFF111C22),
            inverseOnSurface = Color(0xFFE8F1F5)
        )
    }
}

@Immutable
data class AppTypography(
    val material: Typography,
    val amountXL: TextStyle,
    val amountL: TextStyle,
    val amountM: TextStyle
) {
    companion object {
        fun build(): AppTypography {
            val body = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            val caption = TextStyle(
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            val subtitle = TextStyle(
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
            val title = TextStyle(
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
            val headline = TextStyle(
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold
            )

            return AppTypography(
                material = Typography(
                    headlineLarge = headline,
                    headlineMedium = title,
                    headlineSmall = title,
                    titleLarge = title,
                    titleMedium = subtitle,
                    titleSmall = subtitle,
                    bodyLarge = body.copy(fontWeight = FontWeight.Medium),
                    bodyMedium = body,
                    bodySmall = caption,
                    labelLarge = body.copy(fontWeight = FontWeight.Medium),
                    labelMedium = caption.copy(fontWeight = FontWeight.Medium),
                    labelSmall = caption
                ),
                amountXL = TextStyle(
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                amountL = TextStyle(
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold
                ),
                amountM = TextStyle(
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

internal val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
internal val LocalAppRadii = staticCompositionLocalOf { AppRadii() }
internal val LocalAppElevations = staticCompositionLocalOf { AppElevations() }
internal val LocalAppColors = staticCompositionLocalOf { AppColors.nexoDark() }
internal val LocalAppTypography = staticCompositionLocalOf { AppTypography.build() }

val MaterialTheme.appSpacing: AppSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalAppSpacing.current

val MaterialTheme.appRadii: AppRadii
    @Composable
    @ReadOnlyComposable
    get() = LocalAppRadii.current

val MaterialTheme.appElevations: AppElevations
    @Composable
    @ReadOnlyComposable
    get() = LocalAppElevations.current

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

val MaterialTheme.appTypography: AppTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalAppTypography.current
