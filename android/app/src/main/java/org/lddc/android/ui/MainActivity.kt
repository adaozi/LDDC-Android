// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.lddc.android.data.settings.SettingsRepository
import org.lddc.android.ui.navigation.AppScaffold
import org.lddc.android.ui.permission.PermissionRequestScreen
import org.lddc.android.ui.permission.rememberAllFilesAccess
import org.lddc.android.ui.permission.requestAllFilesAccess
import org.lddc.android.ui.theme.LddcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LddcTheme {
                val context = LocalContext.current
                val settingsRepository = remember { SettingsRepository(context) }
                val granted = rememberAllFilesAccess()

                if (granted.value) {
                    AppScaffold(settingsRepository = settingsRepository)
                } else {
                    PermissionRequestScreen(onRequest = { requestAllFilesAccess(context) })
                }
            }
        }
    }
}
