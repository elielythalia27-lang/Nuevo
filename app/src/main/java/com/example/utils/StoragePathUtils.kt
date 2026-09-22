package com.example.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.net.URLDecoder

object StoragePathUtils {

    data class FolderResult(
        val name: String,
        val path: String
    )

    fun getDefaultDownloadFolder(): FolderResult {
        val publicDir = try {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DownloadFree")
        } catch (_: Exception) {
            File("/storage/emulated/0/Download/DownloadFree")
        }
        return FolderResult(
            name = "Download Free",
            path = publicDir.absolutePath
        )
    }

    /**
     * Resolves human-readable storage path and folder name from a SAF Tree Uri on Android 11+ / Android 16.
     */
    fun parseTreeUri(context: Context, uri: Uri): FolderResult {
        try {
            val docId = try {
                if (DocumentsContract.isTreeUri(uri)) {
                    DocumentsContract.getTreeDocumentId(uri)
                } else {
                    DocumentsContract.getDocumentId(uri)
                }
            } catch (_: Exception) {
                uri.path ?: ""
            }

            val decodedDocId = try {
                URLDecoder.decode(docId, "UTF-8")
            } catch (_: Exception) {
                docId
            }

            if (decodedDocId.startsWith("raw:", ignoreCase = true)) {
                val rawPath = decodedDocId.substring(4)
                val folderName = rawPath.trimEnd('/').substringAfterLast('/')
                return FolderResult(
                    name = folderName.ifBlank { "Descargas" },
                    path = rawPath
                )
            }

            val split = decodedDocId.split(":")
            val type = split.getOrNull(0) ?: "primary"
            val relativePath = if (split.size > 1) split[1] else ""

            val realPath = if (type.equals("primary", ignoreCase = true)) {
                if (relativePath.isBlank()) {
                    "/storage/emulated/0"
                } else {
                    "/storage/emulated/0/${relativePath.trimStart('/')}"
                }
            } else if (type.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))) {
                // Secondary external SD card
                if (relativePath.isBlank()) {
                    "/storage/$type"
                } else {
                    "/storage/$type/${relativePath.trimStart('/')}"
                }
            } else {
                if (relativePath.isNotBlank()) {
                    "/storage/emulated/0/${relativePath.trimStart('/')}"
                } else {
                    val fallbackSegment = uri.lastPathSegment?.substringAfterLast(":") ?: ""
                    if (fallbackSegment.isNotBlank()) "/storage/emulated/0/$fallbackSegment" else "/storage/emulated/0/Download"
                }
            }

            val folderName = if (relativePath.isNotBlank()) {
                relativePath.trimEnd('/').substringAfterLast('/')
            } else if (type.equals("primary", ignoreCase = true)) {
                "Almacenamiento interno"
            } else {
                "Tarjeta SD"
            }

            return FolderResult(
                name = folderName.ifBlank { "Carpeta" },
                path = realPath
            )
        } catch (e: Exception) {
            val defaultRes = getDefaultDownloadFolder()
            return FolderResult(
                name = "Descargas",
                path = defaultRes.path
            )
        }
    }
}
