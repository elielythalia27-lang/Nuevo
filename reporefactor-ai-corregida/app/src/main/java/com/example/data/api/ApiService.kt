package com.example.data.api

import com.example.data.model.Pelicula

interface ApiService {
    suspend fun getPeliculas(): List<Pelicula>

    companion object {
        fun create(): ApiService {
            return object : ApiService {
                override suspend fun getPeliculas(): List<Pelicula> {
                    return SecureEndpointManager.fetchAndDecryptPeliculas()
                }
            }
        }
    }
}

