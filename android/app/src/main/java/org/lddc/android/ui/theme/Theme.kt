// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- 调色板：深夜蓝底 + 冷光蓝主色 + 薄荷绿点缀 ----------

private val Ink0 = Color(0xFF080C12)
private val Ink1 = Color(0xFF0F151E)
private val Ink2 = Color(0xFF151D28)
private val Ink3 = Color(0xFF1D2734)
private val Line = Color(0xFF26313F)

private val Blue = Color(0xFF7FA6FF)
private val BlueDeep = Color(0xFF213659)
private val Mint = Color(0xFF57D6B4)
private val MintDeep = Color(0xFF103129)
private val Peach = Color(0xFFFFBB78)
private val Rose = Color(0xFFFF7D7D)
private val Snow = Color(0xFFE9EFF8)
private val Mist = Color(0xFF9CACC2)

/** Material3 之外的语义色，随明暗主题切换 */
@Immutable
data class LddcExtraColors(
    val success: Color,
    val warning: Color,
    val surfaceSunken: Color,
    val hairline: Color,
    val glow: Color,
    val onGlow: Color,
)

val LocalLddcColors = staticCompositionLocalOf {
    LddcExtraColors(
        success = Mint,
        warning = Peach,
        surfaceSunken = Ink1,
        hairline = Line,
        glow = Blue,
        onGlow = Ink0,
    )
}

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF071426),
    primaryContainer = BlueDeep,
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Mint,
    onSecondary = Color(0xFF00251C),
    secondaryContainer = MintDeep,
    onSecondaryContainer = Color(0xFFB6F2E2),
    tertiary = Peach,
    onTertiary = Color(0xFF2A1700),
    background = Ink0,
    onBackground = Snow,
    surface = Ink1,
    onSurface = Snow,
    surfaceVariant = Ink3,
    onSurfaceVariant = Mist,
    surfaceTint = Blue,
    outline = Line,
    outlineVariant = Color(0xFF1E2733),
    error = Rose,
    onError = Color(0xFF2B0606),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFD9D9),
    scrim = Color(0xFF000000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5BDA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Color(0xFF10214F),
    secondary = Color(0xFF0C8A70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F4E9),
    onSecondaryContainer = Color(0xFF00332A),
    tertiary = Color(0xFFA85F10),
    onTertiary = Color.White,
    background = Color(0xFFF5F8FD),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFEAF0F9),
    onSurfaceVariant = Color(0xFF5A6B82),
    outline = Color(0xFFD5DFED),
    outlineVariant = Color(0xFFE6ECF6),
    error = Color(0xFFC43B3B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF3F0B0B),
)

private val DarkExtras = LddcExtraColors(
    success = Mint,
    warning = Peach,
    surfaceSunken = Ink0,
    hairline = Color(0xFF202A38),
    glow = Blue,
    onGlow = Color(0xFF071426),
)

private val LightExtras = LddcExtraColors(
    success = Color(0xFF0C8A70),
    warning = Color(0xFFA85F10),
    surfaceSunken = Color(0xFFF0F4FB),
    hairline = Color(0xFFE4EBF6),
    glow = Color(0xFF2E5BDA),
    onGlow = Color.White,
)

private val LddcTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

private val LddcShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** 统一间距刻度，避免各页面各写各的 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
fun LddcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    val extras = if (darkTheme) DarkExtras else LightExtras

    CompositionLocalProvider(LocalLddcColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LddcTypography,
            shapes = LddcShapes,
            content = content,
        )
    }
}
