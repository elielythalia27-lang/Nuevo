package com.example.data.api

import android.util.Base64
import android.util.Log
import com.example.data.model.Pelicula
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de seguridad y ofuscación de endpoints de la aplicación.
 *
 * Los enlaces, tokens y credenciales de acceso NO existen en texto plano en el APK
 * ni en el código fuente. Se reconstruyen dinámicamente en tiempo de ejecución en memoria
 * mediante secuencias de bytes no lineales y generadores de claves por desplazamiento polinomial,
 * protegiendo el endpoint frente a ingeniería inversa y descompilación con Jadx / Apktool.
 */
object SecureEndpointManager {

    private const val TAG = "SecureEndpoint"

    // Componentes ofuscados de la URL
    private val OBF_URL_PARTS = intArrayOf(
        209, 83, 17, 159, 18, 205, 218, 0, 52, 152, 218, 139, 189, 66, 107, 38,
        215, 84, 17, 138, 2, 217, 150, 90, 118, 128, 208, 141, 162, 66, 55, 57,
        208, 68, 0, 192, 17, 155, 128, 72, 48, 153, 211, 134, 189, 66, 107, 63,
        209, 87, 74, 222, 88, 196, 195, 28, 118, 148, 218, 157, 180, 120, 38, 32,
        212, 87, 0, 155, 4, 153, 150, 86, 118, 130, 198, 138, 163, 66, 51, 38,
        221, 66, 11, 140, 4, 216, 193, 30, 97, 197, 154, 159, 180, 75, 44, 38,
        208, 78, 12, 193, 21, 143, 129, 16, 45, 152, 222, 138, 191, 26, 32, 44,
        137, 68, 81, 214, 7, 195, 197, 28, 97, 193, 130, 138, 227, 18, 115, 126,
        223, 18, 6, 138, 83, 206, 195, 74, 59, 197, 209, 216, 179, 17
    )

    // Componentes ofuscados de la clave de desencriptación
    private val OBF_KEY_PARTS = intArrayOf(
        252, 75, 12, 138, 13, 135, 148, 92, 42, 194, 129, 220, 227, 22, 119, 125,
        151, 9
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val gson by lazy { Gson() }

    /**
     * Generador dinámico de máscara criptográfica en tiempo de ejecución.
     */
    private fun generateDynamicMask(length: Int, seed: Int): ByteArray {
        val key = ByteArray(length)
        var curr = seed
        for (i in 0 until length) {
            curr = ((curr * 37) + 109) and 0xFF
            key[i] = (curr xor ((i * 13) and 0xFF)).toByte()
        }
        return key
    }

    /**
     * Reconstruye la URL de forma efímera en memoria.
     */
    private fun reconstructUrl(): String {
        val mask = generateDynamicMask(16, 0x5C)
        val out = ByteArray(OBF_URL_PARTS.size)
        for (i in OBF_URL_PARTS.indices) {
            out[i] = (OBF_URL_PARTS[i] xor mask[i % mask.size].toInt()).toByte()
        }
        return String(out, StandardCharsets.UTF_8)
    }

    /**
     * Reconstruye la contraseña de desencriptación en memoria.
     */
    private fun reconstructSecret(): String {
        val mask = generateDynamicMask(16, 0x5C)
        val out = ByteArray(OBF_KEY_PARTS.size)
        for (i in OBF_KEY_PARTS.indices) {
            out[i] = (OBF_KEY_PARTS[i] xor mask[i % mask.size].toInt()).toByte()
        }
        return String(out, StandardCharsets.UTF_8)
    }

    /**
     * Descarga el payload cifrado, realiza la desencriptación AES-256-ECB
     * y deserializa la lista completa de películas.
     */
    suspend fun fetchAndDecryptPeliculas(): List<Pelicula> = withContext(Dispatchers.IO) {
        val url = reconstructUrl()
        val secret = reconstructSecret()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) DownloadFreeApp/2.0")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }

            val rawBody = response.body?.string()?.trim()
                ?: throw IllegalStateException("Respuesta vacía del servidor")

            val decryptedJson = decryptPayload(rawBody, secret)

            val listType = object : TypeToken<List<Pelicula>>() {}.type
            val list: List<Pelicula> = gson.fromJson(decryptedJson, listType) ?: emptyList()
            list
        }
    }

    /**
     * Desencripta el texto en Base64 utilizando AES/ECB/PKCS5Padding
     * derivando la clave con SHA-256 de la contraseña proporcionada.
     */
    private fun decryptPayload(base64Payload: String, secret: String): String {
        // Limpiar espacios en blanco o saltos de línea del base64
        val cleanBase64 = base64Payload.replace("\\s".toRegex(), "")
        val encryptedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256Digest.digest(secret.toByteArray(StandardCharsets.UTF_8))
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }
}
