package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ContinueWatchingItem
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.Pelicula
import com.example.data.model.SortOption
import com.example.data.model.ThemeMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "peliculas_prefs")

class PeliculaPreferences(private val context: Context) {
    private val gson = Gson()

    companion object {
        val FAVORITES_KEY = stringSetPreferencesKey("favorite_ids")
        val CONTINUE_WATCHING_KEY = stringPreferencesKey("continue_watching_json")
        val DOWNLOADS_KEY = stringPreferencesKey("downloads_json")
        val DARK_THEME_KEY = booleanPreferencesKey("is_dark_theme")
        val SORT_OPTION_KEY = stringPreferencesKey("sort_option")
        val THEME_COLOR_KEY = stringPreferencesKey("theme_color_key")
        val DEFAULT_FILTER_TYPE_KEY = stringPreferencesKey("default_filter_type")
        val CACHED_PELICULAS_KEY = stringPreferencesKey("cached_peliculas_json")
        val MAX_CONCURRENT_DOWNLOADS_KEY = intPreferencesKey("max_concurrent_downloads")
        val CATALOG_LAYOUT_MODE_KEY = stringPreferencesKey("catalog_layout_mode")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode_setting")
        val DOWNLOAD_FOLDER_NAME_KEY = stringPreferencesKey("download_folder_name")
        val DOWNLOAD_FOLDER_PATH_KEY = stringPreferencesKey("download_folder_path")
    }

    val downloadFolderName: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[DOWNLOAD_FOLDER_NAME_KEY] ?: "Download Free"
        }

    val downloadFolderPath: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[DOWNLOAD_FOLDER_PATH_KEY] ?: ""
        }

    suspend fun setDownloadFolder(name: String, path: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_FOLDER_NAME_KEY] = name
            preferences[DOWNLOAD_FOLDER_PATH_KEY] = path
        }
    }

    val maxConcurrentDownloads: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            (preferences[MAX_CONCURRENT_DOWNLOADS_KEY] ?: 3).coerceIn(1, 5)
        }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        val safeCount = count.coerceIn(1, 5)
        context.dataStore.edit { preferences ->
            preferences[MAX_CONCURRENT_DOWNLOADS_KEY] = safeCount
        }
    }

    val catalogLayoutMode: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[CATALOG_LAYOUT_MODE_KEY] ?: "GRID_2"
        }

    suspend fun setCatalogLayoutMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[CATALOG_LAYOUT_MODE_KEY] = mode
        }
    }

    val themeColor: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[THEME_COLOR_KEY] ?: "teal"
        }

    suspend fun setThemeColor(colorId: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_COLOR_KEY] = colorId
        }
    }

    val defaultFilterType: Flow<String> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[DEFAULT_FILTER_TYPE_KEY] ?: "ALL"
        }

    suspend fun setDefaultFilterType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_FILTER_TYPE_KEY] = type
        }
    }

    val favoriteIds: Flow<Set<String>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[FAVORITES_KEY] ?: emptySet()
        }

    suspend fun toggleFavorite(id: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[FAVORITES_KEY]?.toMutableSet() ?: mutableSetOf()
            if (current.contains(id)) {
                current.remove(id)
            } else {
                current.add(id)
            }
            preferences[FAVORITES_KEY] = current
        }
    }

    val continueWatching: Flow<ContinueWatchingItem?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val json = preferences[CONTINUE_WATCHING_KEY]
            if (!json.isNullOrEmpty()) {
                try {
                    gson.fromJson(json, ContinueWatchingItem::class.java)
                } catch (e: Exception) {
                    null
                }
            } else null
        }

    suspend fun saveContinueWatching(item: ContinueWatchingItem) {
        context.dataStore.edit { preferences ->
            preferences[CONTINUE_WATCHING_KEY] = gson.toJson(item)
        }
    }

    suspend fun clearContinueWatching() {
        context.dataStore.edit { preferences ->
            preferences.remove(CONTINUE_WATCHING_KEY)
        }
    }

    val downloads: Flow<List<DownloadItem>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val json = preferences[DOWNLOADS_KEY]
            if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<DownloadItem>>() {}.type
                    gson.fromJson<List<DownloadItem>>(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()
        }

    suspend fun saveDownloads(list: List<DownloadItem>) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOADS_KEY] = gson.toJson(list)
        }
    }

    suspend fun addOrUpdateDownload(item: DownloadItem) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY]
            val currentList = if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<DownloadItem>>() {}.type
                    gson.fromJson<List<DownloadItem>>(json, type)?.toMutableList() ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else mutableListOf()

            val index = currentList.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                currentList[index] = item
            } else {
                currentList.add(item)
            }
            preferences[DOWNLOADS_KEY] = gson.toJson(currentList)
        }
    }

    suspend fun updateMultipleDownloads(items: List<DownloadItem>) {
        if (items.isEmpty()) return
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY]
            val currentList = if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<DownloadItem>>() {}.type
                    gson.fromJson<List<DownloadItem>>(json, type)?.toMutableList() ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else mutableListOf()

            items.forEach { updatedItem ->
                val index = currentList.indexOfFirst { it.id == updatedItem.id }
                if (index >= 0) {
                    // Safety check: do not overwrite a PAUSED item with a DOWNLOADING periodic update
                    if (currentList[index].status == DownloadStatus.PAUSED && updatedItem.status == DownloadStatus.DOWNLOADING) {
                        return@forEach
                    }
                    currentList[index] = updatedItem
                } else {
                    currentList.add(updatedItem)
                }
            }
            preferences[DOWNLOADS_KEY] = gson.toJson(currentList)
        }
    }

    suspend fun removeDownload(id: String) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY]
            if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<DownloadItem>>() {}.type
                    val currentList = gson.fromJson<List<DownloadItem>>(json, type)?.toMutableList() ?: mutableListOf()
                    currentList.removeAll { it.id == id }
                    preferences[DOWNLOADS_KEY] = gson.toJson(currentList)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    suspend fun removeDownloads(ids: Set<String>) {
        context.dataStore.edit { preferences ->
            val json = preferences[DOWNLOADS_KEY]
            if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<DownloadItem>>() {}.type
                    val currentList = gson.fromJson<List<DownloadItem>>(json, type)?.toMutableList() ?: mutableListOf()
                    currentList.removeAll { ids.contains(it.id) }
                    preferences[DOWNLOADS_KEY] = gson.toJson(currentList)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val raw = preferences[THEME_MODE_KEY]
            if (raw != null) {
                try {
                    ThemeMode.valueOf(raw)
                } catch (e: Exception) {
                    ThemeMode.SYSTEM
                }
            } else {
                ThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
            when (mode) {
                ThemeMode.DARK -> preferences[DARK_THEME_KEY] = true
                ThemeMode.LIGHT -> preferences[DARK_THEME_KEY] = false
                ThemeMode.SYSTEM -> preferences.remove(DARK_THEME_KEY)
            }
        }
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[DARK_THEME_KEY] ?: true
        }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_THEME_KEY] = enabled
        }
    }

    // Default sort is NAME_AZ as requested by user
    val sortOption: Flow<SortOption> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val name = preferences[SORT_OPTION_KEY] ?: SortOption.NAME_AZ.name
            try {
                SortOption.valueOf(name)
            } catch (e: Exception) {
                SortOption.NAME_AZ
            }
        }

    suspend fun setSortOption(option: SortOption) {
        context.dataStore.edit { preferences ->
            preferences[SORT_OPTION_KEY] = option.name
        }
    }

    val cachedPeliculas: Flow<List<Pelicula>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val json = preferences[CACHED_PELICULAS_KEY]
            if (!json.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<Pelicula>>() {}.type
                    gson.fromJson<List<Pelicula>>(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()
        }

    suspend fun saveCachedPeliculas(list: List<Pelicula>) {
        context.dataStore.edit { preferences ->
            preferences[CACHED_PELICULAS_KEY] = gson.toJson(list)
        }
    }

    suspend fun clearAllCache() {
        context.dataStore.edit { preferences ->
            preferences.remove(CACHED_PELICULAS_KEY)
        }
    }
}
