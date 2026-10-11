# Download Free — Rediseño UI/UX completo (v1.1, versionCode 2)

> Fase 2 sobre la versión ya corregida (ver `CORRECCIONES_APLICADAS.md` para los 4 fixes funcionales).
> Este documento describe las **6 mejoras** de UI/UX/animaciones implementadas a petición del usuario
> ("Implementa todo, quiero una aplicación 100% con una UI y animaciones de otro nivel, algo único").

---

## 1. Favoritos: interfaz completa ❤️

El backend de favoritos ya existía en `HomeViewModel` (`toggleFavorite`, `favoriteIds`, filtro
`onlyFavorites` en `applyFilter`); esta fase cableó toda la interfaz:

| Superficie | Qué se añadió |
|---|---|
| `PeliculaCard` (póster) | Corazón flotante arriba-izquierda: círculo de vidrio 30 dp, icono `Favorite`/`FavoriteBorder`, **pop de escala con spring** al alternar, doble tick háptico. |
| `FilterBar` (chips) | Nuevo chip **Favoritos** (rosa `#EC4899`) entre "YouTube" y el contador, con animación de color y tick háptico. |
| `HomeScreen` (estado vacío) | Estado vacío dedicado cuando no hay favoritos: corazón rosa, título "Aún no tienes favoritos" y botón "Explorar catálogo". |
| Ficha (bottom sheet) | Botón circular 42 dp a la derecha de la cabecera (junto al póster/metadatos), relleno rosa translúcido + borde rosa cuando está activo. |
| Botón masivo | El filtro de favoritos se activa/desactiva con `onToggleOnlyFavorites` del ViewModel (ya cableado en `MainActivity`). |

## 2. Sistema háptico completo 📳

Nuevo componente `ui/components/Haptics.kt` con `AppHaptics` + `rememberHaptics()`:

- `tick()` — toque sutil (chips, tabs, pausar/reanudar/long-press).
- `confirm()` — acción importante confirmada (Play, Descargar, Reintentar todo, activar bloqueo).
- `doubleTick()` — doble pulso de celebración (favorito, descarga completada, reanudar/reintentar tarjeta).
- `reject()` — pulso de advertencia (cancelar descarga, cancelar todo).
- `gestureTick()` — para gestos rápidos del reproductor.

Dónde se aplicó: chips del `FilterBar`, corazón de tarjeta y de ficha, botones Play/Descargar de la
ficha, navegación inferior (`AppBottomNav`), Pausar/Reanudar/Cancelar/Reintentar todo en Descargas,
botones de pausa/reanudar/reintentar/cancelar de cada tarjeta de descarga, long-press de selección en
"Descargadas", doble-tap ±10 s y seek horizontal del reproductor, bloqueo/desbloqueo de pantalla, y
selector de modo de tema en Ajustes.

## 3. Hero carrusel en Home 🎬

Nuevo composable `FeaturedHeroCarousel` (privado en `HomeScreen.kt`):

- Aparece solo en la vista completa (sin búsqueda, sin filtro de tipo, sin favoritos activos).
- `HorizontalPager` con hasta **5 títulos destacados** (prioriza películas), esquinas 24 dp, alto 216 dp.
- **Efecto Ken Burns**: zoom lento continuo (1.0→1.08, 14 s, reversa) sobre el póster vía `graphicsLayer`.
- Degradado cinematográfico vertical para legibilidad + chip **"DESTACADO"** con estrella ámbar.
- Acciones circulares: **Play** (relleno con color primario) y **Descargar** (vidrio oscuro con borde).
- **Auto-avance cada 5 s** (solo si el carrusel está quieto) + puntos indicadores animados.
- Indicador de corazón rosa si el título está en favoritos.
- Imágenes con Coil (`SubcomposeAsyncImage` + crossfade + shimmer de carga), mismas claves de caché que las tarjetas.

## 4. Cuadrícula escalonada (masonry) 🧱

- `PeliculaCard` ahora acepta `posterAspectRatio` (por defecto `0.78f`, el valor original) y lo aplica con `Modifier.aspectRatio()`.
- En el modo de layout **"Cuadrícula (2 col)"** (`catalogLayoutMode == "GRID_2"`), `HomeScreen` renderiza
  un `LazyVerticalStaggeredGrid` (2 columnas, espaciado 14/16 dp) en lugar del grid uniforme: cada tarjeta
  toma su altura de un aspecto **estable por id** (`posterAspectFor`: hash → 0.72 / 0.82 / 0.77), logrando
  el efecto masonry sin recortes.
- Scroll independiente por pestaña: 4 `LazyStaggeredGridState` con `rememberSaveable` + `LazyStaggeredGridState.Saver`.
- Los efectos de auto-ocultar la barra inferior (`barBehavior`) funcionan igual en ambos modos
  (los `snapshotFlow` de canScrollForward/backward consultan el estado activo según el modo).
- Los modos "Compacto (3 col)" y "Lista" conservan el aspecto original (0.78) para mantener uniformidad.

## 5. Confeti al completar descargas 🎉

Nuevo componente `ui/components/ConfettiOverlay.kt`:

- `ConfettiOverlay(trigger: Int, originFraction: Offset = Offset(0.5f, 0.35f), modifier)` — dibujado 100 % en Canvas
  (sin librerías externas): **64 partículas** con física balística simulada (velocidad radial, gravedad,
  rotación, fade con easing `CubicBezierEasing(0.12f, 0f, 0.08f, 1f)`), 2 400 ms, paleta teal/ámbar/
  esmeralda/cielo/rosa/violeta. **No bloquea el tacto** y solo dibuja mientras `trigger > 0`.
- En `DescargasScreen`: un `LaunchedEffect(downloads)` compara el conjunto de ítems `COMPLETED` con el
  anterior; si aparece uno nuevo **con la pantalla visible**, incrementa `confettiTrigger` (monta el
  overlay) y dispara `doubleTick()`. Al abrir la pantalla no se celebra lo ya completado (sin falso
  disparo inicial).

## 6. "Reintentar todo" para descargas fallidas 🔁

- `DownloadHelper.retryAllFailed()`: toma todas las descargas `FAILED`, respeta VPN/Solo Wi-Fi/límite
  concurrente y las devuelve a `PENDING` para que el scheduler las relance en orden.
- `HomeViewModel.retryAllFailedDownloads()`: envoltorio seguro (corutina en viewModelScope, error a log).
- UI en `DescargasScreen` → pestaña "Activas": botón rojo translúcido **"Reintentar las N fallidas"**
  (o singular), visible solo si hay fallidas, con `confirm()` háptico y `testTag("btn_retry_all_failed")`.
- Cableado completo: `DescargasScreen(onRetryAllFailed)` → `MainActivity` → `viewModel.retryAllFailedDownloads()`.

---

## Archivos tocados en esta fase

| Archivo | Cambio |
|---|---|
| `ui/components/Haptics.kt` | **NUEVO** — sistema háptico. |
| `ui/components/ConfettiOverlay.kt` | **NUEVO** — confeti en Canvas. |
| `ui/components/FilterBar.kt` | Chip Favoritos + firma ampliada + hápticas. |
| `ui/components/PeliculaCard.kt` | Corazón con pop + `posterAspectRatio`. |
| `ui/components/AppBottomNav.kt` | Tick al cambiar de tab. |
| `ui/screens/HomeScreen.kt` | Hero carrusel, staggered grid, favoritos en ficha, estado vacío de favoritos, hápticas. |
| `ui/screens/DescargasScreen.kt` | Confeti, botón Reintentar todo, hápticas en acciones. |
| `ui/screens/PlayerScreen.kt` | Hápticas de gestos y bloqueo. |
| `ui/screens/AjustesScreen.kt` | Háptica en selector de tema (+ import). |
| `data/download/DownloadHelper.kt` | `retryAllFailed()`. |
| `viewmodel/HomeViewModel.kt` | `retryAllFailedDownloads()`. |
| `MainActivity.kt` | Cableo de `onToggleFavorite`, `onToggleFavorites`, `onRetryAllFailed`. |
| `app/build.gradle.kts` | `versionCode = 2`, `versionName = "1.1"`. |

## Compatibilidad y verificaciones (sin compilar, según lo acordado)

- **Todas las firmas nuevas usan valores por defecto**: el código llamante existente compila aunque no
  se cablee un callback nuevo (retro-compatible).
- Verificado programáticamente sobre los ~50 archivos `.kt`:
  - Balance de llaves/paréntesis/corchetes en **todos** los `.kt` de `app/src` ✔
  - Imports requeridos presentes en cada archivo que usa los símbolos nuevos
    (`rememberHaptics`, `ConfettiOverlay`, alias `staggeredItemsIndexed`, `delay`, `border`, etc.) ✔
  - Nombres de API del ViewModel verificados contra su declaración
    (`toggleFavorite`, `onToggleOnlyFavorites`, `favoriteIds`, `onlyFavorites`, `retryAllFailedDownloads`) ✔
  - `LazyStaggeredGridState.Saver` y `verticalItemSpacing` son API públicas de foundation (mismas que
    usa `rememberLazyStaggeredGridState` internamente) ✔
- El confeti y el hero **no añaden dependencias** (solo Compose/Coil ya presentes).

## Cómo probar

1. **Home**: carrusel destacado arriba (se mueve solo); chips → "Favoritos" filtra; corazón en un póster
   con vibración y pop; modo "Cuadrícula (2 col)" en Ajustes → alturas variadas.
2. **Ficha**: corazón junto a los metadatos; Play/Descargar vibran al confirmar.
3. **Descargas**: descarga algo y mira el confeti al completar; fuerza un fallo (VPN activa) → botón
   rojo "Reintentar…" devuelve todo a cola.
4. **Navegación inferior**: tick al cambiar de pestaña.
5. **Reproductor**: doble-tap a los lados (retrocede/avanza con pulso), arrastre horizontal (pulso al soltar), botón de bloqueo (confirmación).
