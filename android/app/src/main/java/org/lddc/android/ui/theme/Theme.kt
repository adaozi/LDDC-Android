// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Blue = Color(0xFF8AB4F8)
private val BlueDark = Color(0xFF3B6EA5)
private val Amber = Color(0xFFF2B84B)

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color(0xFF0B1B2B),
    primaryContainer = BlueDark,
    onPrimaryContainer = Color(0xFFE6F0FF),
    secondary = Amber,
    onSecondary = Color(0xFF2A1D00),
    background = Color(0xFF101317),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF161A20),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF232932),
    onSurfaceVariant = Color(0xFFB6BEC9),
    outline = Color(0xFF3A424D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5E9E),
    secondary = Color(0xFF8A5A00),
)

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
    MaterialTheme(colorScheme = colorScheme, content = content)
}
