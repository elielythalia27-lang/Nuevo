package com.example

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.data.model.ThemeMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MovieApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Seamless cold start: Apply the saved theme immediately at Application creation
        // before any Activity Window is created so the OS splash and window match the theme.
        val prefs = getSharedPreferences("theme_sync_prefs", Context.MODE_PRIVATE)
        val savedMode = prefs.getString("theme_mode", null)
        val savedColor = prefs.getString("theme_color", null)
        val nightMode = when (savedMode) {
            ThemeMode.DARK.name -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.LIGHT.name -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.SYSTEM.name -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(false)
            .crossfade(false)
            .build()
    }
}
