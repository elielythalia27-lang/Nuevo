# Cambios realizados

## Descargas (bugs corregidos)
- **Cancelar desde la app no borraba el archivo `.part`** (el item ya no estaba en la lista cuando el servicio buscaba su ruta). Ahora se pasa la ruta explícitamente.
- **Android 14+: las descargas en cola (PENDING) pasaban a PAUSED** al cambiar el límite de descargas simultáneas o activar "Solo Wi-Fi", y nunca se reanudaban solas. Ahora se conservan en cola.
- **Reintento automático estilo IDM**: ante cortes de red, timeouts o errores HTTP 5xx/429 se reintenta (2, 5, 10, 20 y 30 s) continuando desde el `.part` con HTTP Range, tanto en el servicio (API <34) como en el job UIDT (API 34+). Solo tras agotar los reintentos pasa a "Fallida".
- UIDT: ya no se llama a `jobFinished` después de `onStopJob`, y una descarga en cola ya no muestra notificación de "En pausa".

## Notificaciones (estilo IDM)
- Título = nombre del archivo; línea compacta `42% • 12.4 MB/s • 1m 20s`; vista expandida con tamaño, velocidad y tiempo restante.
- Notificación resumen con progreso global, velocidad total y ETA total.
- Al terminar: toca para **abrir el vídeo directamente** (FileProvider) + botones *Abrir* / *Descargas*.
- Subtítulos de estado (Descargando / En pausa / Descarga fallida / Completada), visibilidad pública en pantalla de bloqueo.

## Interfaz / sistema
- Formas Material 3 más redondeadas (`Shapes`) en el tema.
- Gesto de volver predictivo habilitado (`enableOnBackInvokedCallback`).

## Aviso
- `release.keystore` está incluido en el repo con contraseña por defecto `android`. Genera una clave propia antes de publicar.
- No se pudo compilar en el entorno de trabajo (sin Android SDK); revisa con `./gradlew assembleDebug`.
