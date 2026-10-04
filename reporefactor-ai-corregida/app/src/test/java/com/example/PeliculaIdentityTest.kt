package com.example

import com.example.data.model.Pelicula
import org.junit.Assert.assertEquals
import org.junit.Test

class PeliculaIdentityTest {

  @Test
  fun idPrefersVideoUrl() {
    val pelicula = Pelicula(peli = "https://media.example/video.mp4", url = "https://images.example/cover.jpg", nombre = "Título")
    assertEquals("https://media.example/video.mp4", pelicula.id)
  }

  @Test
  fun idFallsBackToCoverUrlWhenVideoUrlIsBlank() {
    val pelicula = Pelicula(peli = "  ", url = "https://images.example/cover.jpg", nombre = "Título")
    assertEquals("https://images.example/cover.jpg", pelicula.id)
  }

  @Test
  fun idFallsBackToTitleWhenUrlsAreMissing() {
    val pelicula = Pelicula(peli = null, url = null, nombre = "Título")
    assertEquals("Título", pelicula.id)
  }
}
