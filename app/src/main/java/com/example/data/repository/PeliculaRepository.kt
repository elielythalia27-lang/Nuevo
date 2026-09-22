package com.example.data.repository

import android.content.Context
import coil.Coil
import com.example.data.api.ApiService
import com.example.data.api.SecureEndpointManager
import com.example.data.local.PeliculaPreferences
import com.example.data.model.ContinueWatchingItem
import com.example.data.model.DownloadItem
import com.example.data.model.Pelicula
import com.example.data.model.SortOption
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.File

sealed class Resource<out T> {
    data class Success<out T>(val data: T, val isOffline: Boolean = false) : Resource<T>()
    data class Error(val message: String, val cachedData: List<Pelicula>? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

class PeliculaRepository(
    private val context: Context,
    private val apiService: ApiService,
    private val preferences: PeliculaPreferences
) {
    val favoriteIds: Flow<Set<String>> = preferences.favoriteIds
    val continueWatching: Flow<ContinueWatchingItem?> = preferences.continueWatching
    val downloads: Flow<List<DownloadItem>> = preferences.downloads
    val isDarkTheme: Flow<Boolean> = preferences.isDarkTheme
    val themeMode: Flow<ThemeMode> = preferences.themeMode
    val sortOption: Flow<SortOption> = preferences.sortOption
    val themeColor: Flow<String> = preferences.themeColor
    val defaultFilterType: Flow<String> = preferences.defaultFilterType
    val maxConcurrentDownloads: Flow<Int> = preferences.maxConcurrentDownloads
    val catalogLayoutMode: Flow<String> = preferences.catalogLayoutMode
    val downloadFolderName: Flow<String> = preferences.downloadFolderName
    val downloadFolderPath: Flow<String> = preferences.downloadFolderPath

    private val diskCacheFile: File by lazy {
        File(context.filesDir, "catalog_cache_v2.json")
    }

    private fun readDiskCache(): List<Pelicula> {
        return try {
            if (diskCacheFile.exists() && diskCacheFile.length() > 0) {
                val json = diskCacheFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<Pelicula>>() {}.type
                com.google.gson.Gson().fromJson<List<Pelicula>>(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeDiskCache(list: List<Pelicula>) {
        try {
            val json = com.google.gson.Gson().toJson(list)
            val temp = File(context.filesDir, "catalog_cache_v2.tmp")
            temp.writeText(json)
            temp.renameTo(diskCacheFile)
        } catch (_: Exception) {
            // ignore
        }
    }

    fun getPeliculasFlow(forceRefresh: Boolean = false): Flow<Resource<List<Pelicula>>> = flow {
        // Obtenemos primero la lista en caché (disco o DataStore)
        val diskList = readDiskCache()
        val cached = if (diskList.isNotEmpty()) {
            diskList
        } else {
            try {
                preferences.cachedPeliculas.first()
            } catch (_: Exception) {
                emptyList()
            }
        }

        // Si tenemos catálogo en caché y no se solicitó forzar refresco total,
        // lo emitimos inmediatamente para mostrarlo en el primer fotograma sin esperas
        if (cached.isNotEmpty() && !forceRefresh) {
            emit(Resource.Success(cached, isOffline = false))
        } else {
            emit(Resource.Loading)
        }

        try {
            // Cargar y desencriptar desde el endpoint seguro
            val remoteList = SecureEndpointManager.fetchAndDecryptPeliculas()

            if (remoteList.isNotEmpty()) {
                writeDiskCache(remoteList)
                preferences.saveCachedPeliculas(remoteList)
                emit(Resource.Success(remoteList, isOffline = false))
            } else if (cached.isNotEmpty()) {
                emit(Resource.Success(cached, isOffline = true))
            } else {
                val fallback = readDiskCache()
                if (fallback.isNotEmpty()) {
                    emit(Resource.Success(fallback, isOffline = true))
                } else {
                    emit(Resource.Error("No se pudo obtener el catálogo del servidor"))
                }
            }
        } catch (e: Exception) {
            val fallback = if (cached.isNotEmpty()) cached else readDiskCache()
            if (fallback.isNotEmpty()) {
                emit(Resource.Success(fallback, isOffline = true))
            } else {
                emit(Resource.Error("Sin conexión con el catálogo de películas: ${e.localizedMessage ?: "Comprueba tu conexión"}"))
            }
        }
    }

    suspend fun toggleFavorite(id: String) {
        preferences.toggleFavorite(id)
    }

    suspend fun saveContinueWatching(item: ContinueWatchingItem) {
        preferences.saveContinueWatching(item)
    }

    suspend fun clearContinueWatching() {
        preferences.clearContinueWatching()
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        preferences.setDarkTheme(enabled)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        preferences.setThemeMode(mode)
    }

    suspend fun setSortOption(option: SortOption) {
        preferences.setSortOption(option)
    }

    suspend fun setThemeColor(colorId: String) {
        preferences.setThemeColor(colorId)
    }

    suspend fun setDefaultFilterType(type: String) {
        preferences.setDefaultFilterType(type)
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        preferences.setMaxConcurrentDownloads(count)
    }

    suspend fun setCatalogLayoutMode(mode: String) {
        preferences.setCatalogLayoutMode(mode)
    }

    suspend fun setDownloadFolder(name: String, path: String) {
        preferences.setDownloadFolder(name, path)
    }

    suspend fun clearCache() {
        preferences.clearAllCache()
        // Clear Coil disk and memory cache
        try {
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        } catch (e: Exception) {
            // ignore
        }
    }
}
