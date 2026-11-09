// SPDX-FileCopyrightText: Copyright (C) 2024-2026 LDDC Android Port
// SPDX-License-Identifier: GPL-3.0-only
package org.lddc.android.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.lddc.android.data.model.Id3Version
import org.lddc.android.data.model.LyricsFormat
import org.lddc.android.data.model.SaveDirMode
import org.lddc.android.data.model.SaveMode
import org.lddc.android.data.model.Source
import org.lddc.android.data.model.TagLyricsContent
import org.lddc.android.data.model.TranslateBackend

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lddc_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs -> prefs.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.SOURCES] = next.sourcePriority.joinToString(",") { it.name }
            prefs[Keys.ENABLED_SOURCES] = next.enabledSources.joinToString(",") { it.name }
            prefs[Keys.MIN_SCORE] = next.minScore
            prefs[Keys.SKIP_EXISTING] = next.skipExistingLyrics
            prefs[Keys.SKIP_INST] = next.skipInstrumental
            prefs[Keys.CONCURRENCY] = next.concurrency
            prefs[Keys.SAVE_MODE] = next.saveMode.name
            prefs[Keys.LYRICS_FORMAT] = next.lyricsFormat.name
            prefs[Keys.ALSO_SAVE_LRC] = next.alsoSaveLrcFile
            prefs[Keys.TAG_CONTENT] = next.tagLyricsContent.name
            prefs[Keys.ID3_VERSION] = next.id3Version.name
            prefs[Keys.SAVE_DIR_MODE] = next.saveDirMode.name
            prefs[Keys.SAVE_ROOT] = next.saveRootPath
            prefs[Keys.FILE_NAME_FORMAT] = next.fileNameFormat
            prefs[Keys.LANGS_ORDER] = next.langsOrder.joinToString(",")
            prefs[Keys.TRANSLATE_BACKEND] = next.translateBackend.name
            prefs[Keys.TRANSLATE_TARGET] = next.translateTargetLang
            prefs[Keys.OPENAI_BASE] = next.openAiBaseUrl
            prefs[Keys.OPENAI_KEY] = next.openAiApiKey
            prefs[Keys.OPENAI_MODEL] = next.openAiModel
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            sourcePriority = this[Keys.SOURCES]
                ?.split(',')
                ?.mapNotNull { name -> Source.entries.firstOrNull { it.name == name } }
                ?.takeIf { it.isNotEmpty() }
                ?: d.sourcePriority,
            enabledSources = this[Keys.ENABLED_SOURCES]
                ?.split(',')
                ?.mapNotNull { name -> Source.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: d.enabledSources,
            minScore = this[Keys.MIN_SCORE] ?: d.minScore,
            skipExistingLyrics = this[Keys.SKIP_EXISTING] ?: d.skipExistingLyrics,
            skipInstrumental = this[Keys.SKIP_INST] ?: d.skipInstrumental,
            concurrency = (this[Keys.CONCURRENCY] ?: d.concurrency).coerceIn(1, 16),
            saveMode = enumOrDefault(this[Keys.SAVE_MODE], d.saveMode),
            lyricsFormat = enumOrDefault(this[Keys.LYRICS_FORMAT], d.lyricsFormat),
            alsoSaveLrcFile = this[Keys.ALSO_SAVE_LRC] ?: d.alsoSaveLrcFile,
            tagLyricsContent = enumOrDefault(this[Keys.TAG_CONTENT], d.tagLyricsContent),
            id3Version = enumOrDefault(this[Keys.ID3_VERSION], d.id3Version),
            saveDirMode = enumOrDefault(this[Keys.SAVE_DIR_MODE], d.saveDirMode),
            saveRootPath = this[Keys.SAVE_ROOT] ?: d.saveRootPath,
            fileNameFormat = this[Keys.FILE_NAME_FORMAT] ?: d.fileNameFormat,
            langsOrder = this[Keys.LANGS_ORDER]?.split(',')?.filter { it.isNotBlank() } ?: d.langsOrder,
            translateBackend = enumOrDefault(this[Keys.TRANSLATE_BACKEND], d.translateBackend),
            translateTargetLang = this[Keys.TRANSLATE_TARGET] ?: d.translateTargetLang,
            openAiBaseUrl = this[Keys.OPENAI_BASE] ?: d.openAiBaseUrl,
            openAiApiKey = this[Keys.OPENAI_KEY] ?: d.openAiApiKey,
            openAiModel = this[Keys.OPENAI_MODEL] ?: d.openAiModel,
        )
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    private object Keys {
        val SOURCES = stringPreferencesKey("source_priority")
        val ENABLED_SOURCES = stringPreferencesKey("enabled_sources")
        val MIN_SCORE = intPreferencesKey("min_score")
        val SKIP_EXISTING = booleanPreferencesKey("skip_existing_lyrics")
        val SKIP_INST = booleanPreferencesKey("skip_instrumental")
        val CONCURRENCY = intPreferencesKey("concurrency")
        val SAVE_MODE = stringPreferencesKey("save_mode")
        val LYRICS_FORMAT = stringPreferencesKey("lyrics_format")
        val ALSO_SAVE_LRC = booleanPreferencesKey("also_save_lrc")
        val TAG_CONTENT = stringPreferencesKey("tag_lyrics_content")
        val ID3_VERSION = stringPreferencesKey("id3_version")
        val SAVE_DIR_MODE = stringPreferencesKey("save_dir_mode")
        val SAVE_ROOT = stringPreferencesKey("save_root_path")
        val FILE_NAME_FORMAT = stringPreferencesKey("file_name_format")
        val LANGS_ORDER = stringPreferencesKey("langs_order")
        val TRANSLATE_BACKEND = stringPreferencesKey("translate_backend")
        val TRANSLATE_TARGET = stringPreferencesKey("translate_target_lang")
        val OPENAI_BASE = stringPreferencesKey("openai_base_url")
        val OPENAI_KEY = stringPreferencesKey("openai_api_key")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
    }
}
