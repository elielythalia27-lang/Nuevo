# Compatibilidad de descargas Android 14 / 15 / 16

## Arquitectura

| Android | Ejecución de descargas largas | Motivo |
|---|---|---|
| 24–33 | `DownloadForegroundService` (`dataSync`) | Compatibilidad con versiones antiguas |
| 34–36+ | `DownloadUidtJobService` (UIDT) | Transferencias largas iniciadas por el usuario sin depender del límite `dataSync` |

## Android 15

No se usa `dataSync` para las descargas modernas. Android 15 limita los servicios foreground `dataSync` a un total de 6 horas en 24 horas. La ruta UIDT evita que esa cuota sea el mecanismo de terminación de las descargas.

## Android 16

UIDT es especialmente importante porque los trabajos normales y los long-running workers están sujetos a las nuevas cuotas de JobScheduler. Las transferencias iniciadas por el usuario mediante UIDT están exentas de las cuotas ordinarias de jobs.

## Notificaciones

Cada descarga conserva un ID estable de notificación. Pausar y reanudar actualiza la misma notificación; cancelar la elimina; completar cambia a la notificación de finalización.

## Recuperación

El archivo parcial se guarda como `archivo.ext.part`. El servidor se solicita con `Range` cuando existe progreso previo. El estado se persiste para que el proceso pueda recuperarse después de una detención del sistema.

## Validación

La compilación automática no pudo ejecutarse en el entorno de revisión porque Gradle 9.3.1 no estaba disponible localmente y el entorno no pudo acceder a `services.gradle.org`. Se realizó validación estática de manifest, referencias, rutas de ejecución y estructura Kotlin.
