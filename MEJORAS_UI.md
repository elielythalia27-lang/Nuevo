# Download Free — Rediseño visual v1.2 (versionCode 3)

> Segunda fase de rediseño, a partir del paquete v1.1. Se eliminaron el carrusel hero y la UI de
> favoritos (a petición del usuario) y se aplicó una pasada completa de identidad visual:
> barra de navegación de diseño propio, iconos de otro estilo, animaciones visibles y launcher nuevo.

---

## 1. Barra de navegación única (diseño propio) 🚀

`AppBottomNav` reescrito por completo (`ui/components/AppBottomNav.kt`):

- **Dock flotante de vidrio**: cápsula redondeada (24 dp) despegada de los bordes, translúcida en
  oscuro/claro, con borde luminoso y glow del color primario por debajo.
- **Pestaña activa = píldora morfeante**: pasa de círculo compacto (46 dp) a cápsula ancha (96 dp)
  con degradado del color primario; la etiqueta ("Catálogo", "Descargas", "Ajustes") se expande con
  `expandHorizontally` dentro de la píldora, estilo pill de apps premium.
- **Muesca luminosa viajera**: barra degradada de 3.5 dp en el borde superior que se desliza con
  muelle hasta la pestaña activa.
- **Icono con rebote elástico** (`Animatable` + `DampingRatioMediumBouncy`) al ganar la selección y
  **respiración sutil** mientras está activa.
- **Badge de descargas activas** con onda expansiva + pulso; el icono de la pestaña se convierte en
  la **flecha de descarga animada estilo sistema** (`SystemDownloadAnimatedIcon`, conservada).
- **Nueva función**: re-tocar la pestaña ya activa devuelve el catálogo al inicio
  (`scrollToTopTick` en `MainActivity` → `HomeScreen`).
- El cambio de página usa **muelle suave** en el pager (en vez del tween plano).
- La barra **nunca desaparece** (decisión previa del usuario, se mantiene).

## 2. Animaciones que se notan ✨

| Animación | Dónde |
|---|---|
| **Entrada escalonada** (`StaggeredAppear`, fade + subida + escala, cascada por oleadas) | Todas las tarjetas del catálogo, en ambos modos de grilla (masonry 2 col y compacta/lista). |
| **Press-scale** (`Modifier.pressScale`, se hunde con el dedo y vuelve con muelle) | 3 tarjetas de Descargas (activa, en cola, descargada), fila "Ubicación de descargas" en Ajustes. |
| Confeti al completar descarga | Se mantiene de la fase anterior. |
| Hápticas en acciones | Se mantienen (chips, Play/Descargar de la ficha, nav, pausa/reanudar/cancelar/reintentar, gestos del player). |

Componente nuevo: `ui/components/Interactions.kt` (`pressScale` + `StaggeredAppear`), sin
dependencias nuevas (solo Compose).

## 3. Iconos con estilo propio 🔷

- **Migración global a `Icons.Rounded`** (set bold de esquinas redondeadas, estética tipo Cubacel
  con trazos gruesos): ~70 usos en 15 archivos — catálogo, descargas, ajustes, player, diálogos,
  toasts, VPN screen, banners y la barra de navegación.
- Verificado: cada icono usado tiene su import del paquete `rounded`/`automirrored.rounded`, y la
  dependencia `material-icons-extended` ya está en el build.
- **Contenedores duotone en Ajustes**: los cuadros con tinte plano ahora son **círculos con color
  semántico** — carpeta verde `#10B981`, descargas azul `#2A7DE1`, notificaciones cian `#06B6D4`,
  batería ámbar `#F59E0B`, auto-inicio violeta `#8B5CF6` (los que indican estado activo conservan
  su verde condicional).
- Limpieza: 17 imports de iconos muertos eliminados.

## 4. Launcher y splash nuevos 📱

- **Diseño**: badge circular azul degradado (`#2A7DE1 → #1B59A7`) con glifo blanco de descarga de
  trazo grueso y extremos redondeados, sobre fondo **navy de la marca** (`#0B1120`) — coherente con
  la paleta real de la app (el anterior era teal 3D sobre blanco).
- `ic_launcher_foreground.xml` (vector adaptive, con `monochrome` para iconos temáticos Android 13+).
- `ic_launcher_background.xml` navy, idéntico en modo día/noche.
- **PNGs regenerados en las 5 densidades** (mdpi→xxxhdpi, square + round) con supermuestreo ×4.
- **Splash** (`ic_splash_icon_light/dark.xml`) con el mismo badge, a juego.

## 5. Eliminado en esta fase (petición explícita) 🗑️

- **Carrusel hero** (`FeaturedHeroCarousel`) y su helper — quitados de `HomeScreen.kt` e imports.
- **UI de favoritos**: corazón en póster y ficha, chip "Favoritos" del `FilterBar`, estado vacío de
  favoritos, wiring en `MainActivity`. *El backend del ViewModel (`toggleFavorite`, `favoriteIds`,
  `onlyFavorites`) permanece intacto pero sin uso — no afecta.*

---

## Archivos tocados en esta fase

| Archivo | Cambio |
|---|---|
| `ui/components/AppBottomNav.kt` | **Reescrito** — dock de vidrio con píldora morfeante, muesca, rebotes. |
| `ui/components/Interactions.kt` | **NUEVO** — `pressScale` + `StaggeredAppear`. |
| `ui/screens/HomeScreen.kt` | Sin carrusel/favoritos; entrada escalonada; volver-arriba. |
| `ui/screens/DescargasScreen.kt` | Press-scale en 3 tarjetas; iconos rounded. |
| `ui/screens/AjustesScreen.kt` | Círculos duotone; press-scale en carpeta; iconos rounded. |
| `ui/components/FilterBar.kt` | Sin chip de favoritos; iconos rounded. |
| `ui/components/PeliculaCard.kt` | Sin corazón; iconos rounded; limpieza. |
| `ui/player/*.kt`, `VpnBlockedScreen`, `CustomToast`, `BatteryOptimization*`, `PermissionRequestDialog`, `ContinueWatchingBanner` | Iconos rounded. |
| `MainActivity.kt` | Scroll-arriba al re-tocar tab; muelle en pager; sin favoritos. |
| `res/drawable*` + `res/mipmap-*` | Launcher/splash nuevos + PNGs 5 densidades. |
| `app/build.gradle.kts` | `versionCode = 3`, `versionName = "1.2"`. |

## Verificaciones (sin compilar, según lo acordado)

- Balance de llaves/paréntesis/corchetes en **todos** los `.kt` ✔
- Cada icono `Icons.Rounded.*` usado tiene su import; cero imports de iconos muertos ✔
- Sin restos de carrusel/favoritos en la UI; `haptics` sin instancias huérfanas ✔
- `ScreenRoute.entries` (Kotlin 2.2.10) y `material-icons-extended` confirmados ✔
- Firmas nuevas con valores por defecto (`scrollToTopTick`, `pressScale`, `StaggeredAppear`) ✔

## Cómo probar

1. **Abre la app**: launcher y splash nuevos (badge azul, glifo bold).
2. **Catálogo**: las tarjetas entran en cascada; desliza entre pestañas con muelle.
3. **Barra inferior**: toca cada pestaña (píldora que crece, muesca que viaja, rebote del icono);
   re-toca la activa → vuelve al inicio; inicia una descarga y mira la flecha animada + badge.
4. **Descargas**: pulsa y mantén/mantén el dedo sobre una tarjeta → se hunde con muelle.
5. **Ajustes**: círculos de colores por sección; toca "Ubicación de descargas" → presión elástica.
6. Completa una descarga → confeti + doble vibración (de la fase anterior).
