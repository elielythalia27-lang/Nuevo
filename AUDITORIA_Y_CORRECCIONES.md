# Auditoría y correcciones — reporefactor-ai

Fecha: 2026-10-05

## Alcance
Se revisó el árbol principal `app/` del proyecto, incluyendo:
- AndroidManifest y configuración Gradle
- servicio de descargas foreground
- gestor/estado/persistencia de descargas
- notificaciones y PendingIntent
- permisos y almacenamiento
- Home, Descargas, Ajustes y Player
- componentes Compose y ciclo de vida
- búsqueda estática de patrones peligrosos y consistencia de llaves/XML

## Correcciones aplicadas

### 1. Acciones de notificación: Pausar/Cancelar/Cola
Las acciones de control estaban enviando `startForegroundService()` incluso cuando la operación era Pausar, Cancelar o poner en cola.

Eso es incorrecto porque esas acciones no necesitan convertirse en un servicio foreground y no ejecutan `startForeground()`. En versiones modernas de Android puede provocar que el sistema mate el servicio por no completar el arranque foreground.

Ahora:
- iniciar/reanudar una descarga usa `startForegroundService()`;
- pausar/cancelar/poner en cola usa `startService()`;
- la notificación conserva su ID individual y no depende de reiniciar el foreground service para actualizarse.

### 2. Límite de `dataSync` de Android 15+
El servicio declara `dataSync` y el proyecto apunta a SDK 36. Android 15+ limita los servicios foreground `dataSync` a un total de 6 horas por cada periodo de 24 horas.

Se añadió `Service.onTimeout(startId, fgsType)` para que, si Android termina el periodo permitido:
- se cancelen los trabajos de descarga;
- se cierren las conexiones HTTP;
- las descargas activas se conviertan en `PAUSED`;
- se conserve el tamaño parcial del `.part`;
- se persista el estado para poder continuar;
- el servicio salga correctamente del foreground.

### 3. Persistencia ante interrupciones del servicio
Se añadió una operación de recuperación que convierte las descargas `DOWNLOADING` en `PAUSED` al recibir el timeout del sistema, evitando que queden permanentemente marcadas como "Descargando".

## Hallazgos que NO se cambiaron deliberadamente
- `POST_NOTIFICATIONS`: Android 13+ puede ocultar las notificaciones si el usuario deniega el permiso; esto es comportamiento del sistema, no un fallo que la aplicación pueda saltarse.
- `READ_MEDIA_*`: se mantiene porque forma parte del flujo actual de permisos/medios del proyecto; no se eliminó sin validar cada flujo de reproducción/selección.
- `usesCleartextTraffic="true"`: se mantiene porque el descargador contempla URLs HTTP además de HTTPS. Debería eliminarse cuando todas las fuentes sean HTTPS.
- La carpeta `reporefactor-ai-corregida/` existente dentro del ZIP se dejó intacta para no mezclarla con el árbol principal.

## Verificación
- XML del manifest: válido.
- Balance de llaves Kotlin en `app/src/main/java`: correcto.
- Búsqueda de `TODO`/`FIXME`: sin resultados.
- Búsqueda de `GlobalScope`: sin resultados.
- Búsqueda de `!!`: sin resultados.
- Se intentó ejecutar Gradle con `testDebugUnitTest` y `assembleDebug`, pero el entorno no pudo descargar Gradle 9.3.1 desde `services.gradle.org` por falta de acceso de red. Por ello, la compilación final debe ejecutarse en Android Studio o CI con acceso a dependencias.

## Resultado
Los cambios se aplicaron únicamente al árbol principal `app/` del proyecto.

## Actualización: arquitectura Android 14–16 para descargas largas

Se añadió una segunda ruta de ejecución para las versiones modernas de Android:

- Android 14 (API 34) y posteriores: las descargas iniciadas por el usuario usan `DownloadUidtJobService` mediante User-Initiated Data Transfer Jobs (UIDT / JobScheduler).
- Se añadió `android.permission.RUN_USER_INITIATED_JOBS`.
- Se registró `DownloadUidtJobService` como `JobService` con `BIND_JOB_SERVICE`.
- Android 13 (API 33) e inferiores: se conserva `DownloadForegroundService` con `dataSync` como fallback.
- En Android 14+ ninguna acción de pausa/reanudación/cancelación de notificación inicia accidentalmente el foreground service `dataSync`.
- Las acciones de notificación de reanudación usan un servicio normal para entregar la acción y después programan UIDT.
- Pausar UIDT cancela el JobScheduler job, conserva el archivo `.part`, guarda bytes/progreso y muestra nuevamente la notificación de pausa.
- Cancelar UIDT cancela el trabajo, elimina el estado persistido y elimina el `.part` cuando se conoce su ruta.
- Si Android detiene un UIDT, el estado se convierte en PAUSED y queda listo para reanudarse.
- El progreso se persiste periódicamente a través de `DownloadHelper`, y la descarga usa el mismo archivo `.part` para continuar desde donde quedó.

### Limitación importante
UIDT evita la cuota normal de los foreground services `dataSync` y está diseñado para transferencias largas iniciadas por el usuario, pero Android sigue pudiendo detener trabajos por condiciones del sistema, restricciones térmicas, memoria u otras razones. La aplicación por ello conserva siempre el estado y el archivo parcial para reanudar.

La documentación oficial de Android indica que `dataSync` tiene un límite acumulado de 6 horas en 24 horas para apps que apuntan a Android 15+, y recomienda UIDT para transferencias largas iniciadas por el usuario. Android 16 además recomienda UIDT para evitar las cuotas ordinarias de JobScheduler en este caso.
