# Correcciones aplicadas — 11 de octubre de 2026

Revisión completa del proyecto (50 archivos Kotlin, ~19.000 líneas, manifest, recursos y
configuración Gradle). Se encontraron y corrigieron 4 errores: 2 críticos y 2 de robustez.

---

## 1. 🔴 CRÍTICO — El proyecto no compilaba: dependencia `appcompat` eliminada pero aún en uso

**Síntoma:** `build.gradle.kts` y `libs.versions.toml` de esta versión **quitaron** la
dependencia `androidx.appcompat`, pero el código y los recursos **siguen usándola**:

- `MovieApp.kt` y `HomeViewModel.kt` → `import androidx.appcompat.app.AppCompatDelegate`
  (error de compilación: *unresolved reference: appcompat*).
- `res/values/themes.xml` y `res/values-night/themes.xml` →
  `parent="Theme.AppCompat.DayNight.NoActionBar"` (error de AAPT: recurso de tema no encontrado).

La copia anterior incluida en el ZIP (`reporefactor-ai-corregida/`) sí declaraba la dependencia
y con ella `assembleDebug` pasó correctamente; alguien la eliminó al "limpiar" el build
dejando los usos huérfanos.

**Corrección** (espejo exacto del estado que compilaba):
- `gradle/libs.versions.toml`: se añadieron `appcompat = "1.7.0"` y la entrada
  `androidx-appcompat` en `[libraries]`.
- `app/build.gradle.kts`: `implementation(libs.androidx.appcompat)`.

---

## 2. 🔴 CRÍTICO — En Android 14+, un job UIDT reprogramado por el sistema descargaba en silencio descargas en PAUSA

**Síntoma:** `DownloadUidtJobService.onStartJob()` comprobaba que el item existiera, pero **no
su estado**. Cuando el sistema detenía un job (`onStopJob` devolvió `true` → el job se
reprograma con backoff) y el item ya había quedado en `PAUSED`, el job arrancaba de nuevo,
descargaba el vídeo completo **sin notificación de progreso y sin consentimiento del usuario**
(gastando datos móviles), y al final lo marcaba como completado.

**Corrección:** en `onStartJob` se añadió la guardia:
`solo continuar si item.status == DOWNLOADING` (todas las rutas legítimas — iniciar, reanudar,
recuperación al abrir la app — marcan el estado antes de programar el job).

---

## 3. 🟠 Crash del catálogo: `Pelicula.id` se había degradado (ids vacíos / duplicados)

**Síntoma:** la fórmula del `id` era `peli?.trim() ?: (nombre?.trim() ?: "")`:
- Si `peli` era cadena **vacía** (no nula), el `?:` no salta y el id quedaba `""`.
- Varias películas con `peli` vacía → todas con id `""` → `LazyVerticalGrid` lanza
  `IllegalArgumentException: Key … was already used` (crash al abrir el catálogo).

La versión anterior ya había corregido esto (informe: *"IDs duplicados o vacíos"*) pero la
fórmula se perdió al regenerar el archivo.

**Corrección:**
- `Pelicula.kt`: fórmula robusta restaurada — `peli → url → nombre`, con `trim()` y
  descartando vacíos.
- `HomeViewModel.applyFilter()`: se añade `distinctBy { it.id }` como blindaje final contra
  duplicados reales del servidor (la lista ya queda limpia antes de llegar a la `LazyGrid`).

---

## 4. 🟠 Reanudar en Android 14+ quedaba "mudo" si el sistema rechazaba el job

**Síntoma:** `DownloadForegroundService.resumeDownload()` (API 34+) programaba el UIDT y, si
`schedule()` fallaba, no hacía nada: el item quedaba marcado `DOWNLOADING` sin motor detrás
(hasta que la recuperación al abrir la app lo rescatara).

**Corrección:** si el sistema rechaza el job, el item pasa a `PENDING` (queda en cola) y se
avisa con un toast — mismo comportamiento que ya tenía `startDownload()`.

---

## Comprobaciones realizadas (sin compilar, según lo acordado)

- ✅ Balance de llaves/paréntesis/corchetes en los 50 archivos Kotlin.
- ✅ Imports de iconos de Compose verificados uno a uno contra los usos en cada archivo
  (`Icons.Default/Outlined/Rounded/AutoMirrored`) — sin import faltantes.
- ✅ Uso de clases comunes (`File`, `Intent`, `Uri`, `URL`, etc.) sin import sin resolver — ninguno.
- ✅ `gradle/libs.versions.toml` parsea como TOML válido (tomllib) tras el cambio.
- ✅ Los 25 archivos XML del proyecto (res + manifest) parsean sin errores.
- ✅ Todas las referencias a recursos (`R.drawable`, `R.string`, `R.font`, `R.raw`, `R.xml`) existen.
- ✅ Manifiesto: permisos coherentes con el código (UIDT, `FOREGROUND_SERVICE_DATA_SYNC`,
  `RUN_USER_INITIATED_JOBS`, `POST_NOTIFICATIONS`), receiver/servicios/provider declarados.
- ✅ Cadena de build verificada contra requisitos oficiales: AGP 9.1.1 requiere Gradle 9.3.1
  y JDK 17 — el wrapper usa 9.3.1 y el workflow de GitHub Actions usa JDK 17. Coherente.
- ✅ Compatibilidad de APIs con las versiones fijadas: Compose BOM 2024.09.00 (PullToRefreshBox,
  HorizontalPager, LocalOverscrollConfiguration), lifecycle 2.8.7 (LocalLifecycleOwner,
  collectAsStateWithLifecycle), media3 1.5.1, Coil 2.7, Lottie 6.6.2, DataStore 1.1.7.
- ✅ Revisión línea a línea de: modelos, API segura, repositorio, DataStore, `DownloadHelper`,
  `DownloadForegroundService`, `DownloadUidtJobService`, notificaciones, VPN/proxy, permisos,
  almacenamiento, reproductor (ExoPlayer) y las 3 pantallas + componentes Compose.
- ✅ Los 4 tests del proyecto siguen siendo válidos con los cambios.

## Veredicto

Con las 4 correcciones aplicadas, no quedan errores conocidos que impidan la compilación o
causen crash en ejecución. El proyecto está listo para compilar y probar en el móvil.

## Recomendaciones (no son errores, decisión tuya)

1. **`versionCode`/`versionName`**: al publicar esta versión corregida, sube `versionCode = 2`
   y `versionName = "1.1"` en `app/build.gradle.kts` para que Android la reconozca como actualización.
2. **`release.keystore` está en el repo con contraseña `android`** (ya estaba avisado en
   CAMBIOS.md): genera tu propio keystore antes de publicar en Play Store.
3. **`usesCleartextTraffic="true"`**: necesario si el servidor de vídeos usa `http://`; si
   algún día pasa a `https://`, quítalo para mayor seguridad.
4. **Compose BOM 2024.09.00** es algo antiguo pero funciona con Kotlin 2.2.10; actualizarlo
   es opcional y podría requerir ajustes — no lo toqué para no arriesgar estabilidad.


## Nota adicional

La carpeta `reporefactor-ai-corregida/` (copia antigua e incompleta del proyecto dentro del
propio proyecto) **se excluyó del ZIP corregido** para evitar confusión de proyectos en Android
Studio; el build solo usa `:app` de la raíz. Sigue disponible en el ZIP original.
