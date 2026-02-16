package com.example.lddc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户偏好设置存储
 *
 * 使用 DataStore 存储用户的偏好设置，如上次选择的文件夹等
 */
class UserPreferences(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

        // 上次选择的文件夹路径
        val LAST_SELECTED_FOLDER = stringPreferencesKey("last_selected_folder")

        // 上次选择的视图模式
        val LAST_VIEW_MODE = stringPreferencesKey("last_view_mode")
    }

    /**
     * 获取上次选择的文件夹路径
     */
    val lastSelectedFolder: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_SELECTED_FOLDER]
        }

    /**
     * 保存上次选择的文件夹路径
     */
    suspend fun saveLastSelectedFolder(folderPath: String?) {
        context.dataStore.edit { preferences ->
            if (folderPath != null) {
                preferences[LAST_SELECTED_FOLDER] = folderPath
            } else {
                preferences.remove(LAST_SELECTED_FOLDER)
            }
        }
    }

    /**
     * 获取上次选择的视图模式
     */
    val lastViewMode: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_VIEW_MODE]
        }

    /**
     * 保存上次选择的视图模式
     */
    suspend fun saveLastViewMode(viewMode: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_VIEW_MODE] = viewMode
        }
    }

    /**
     * 清除所有偏好设置
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
