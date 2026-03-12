package com.example.lddc.data.repository

import com.example.lddc.common.models.enums.LyricsFormat
import com.example.lddc.common.models.enums.Source
import com.example.lddc.data.local.datastore.AppSettings
import com.example.lddc.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val settingsDataStore: SettingsDataStore
) {

    val settings: Flow<AppSettings> = settingsDataStore.settings

    suspend fun updateSettings(settings: AppSettings) {
        settingsDataStore.updateSettings(settings)
    }

    suspend fun updateDefaultSavePath(path: String) {
        settingsDataStore.updateDefaultSavePath(path)
    }

    suspend fun updateLyricsFormat(format: LyricsFormat) {
        settingsDataStore.updateLyricsFormat(format)
    }

    suspend fun updateSearchSource(source: Source) {
        settingsDataStore.updateSearchSource(source)
    }

}
