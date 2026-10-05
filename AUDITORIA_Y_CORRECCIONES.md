# Auditoría integral — versión de corrección de descargas

## Alcance

Se revisó el árbol principal `app/` de este ZIP como proyecto independiente. No se mezcló código de la carpeta histórica `reporefactor-ai-corregida/`.

## Correcciones principales

### 1. Descargas Android 14/15/16+
- Se mantiene `DownloadUidtJobService` para transferencias largas iniciadas explícitamente por el usuario.
- Se evita usar `dataSync` como motor de descargas en API 34+.
- Los controles de la notificación no intentan iniciar un foreground service para pausar/cancelar.
- Reanudar desde una notificación en Android 14+ abre la Activity mediante un PendingIntent explícito; la Activity, ya visible, programa el UIDT. Esto respeta la condición de Android de que un UIDT se programe desde una app visible o una condición permitida.
- Se conserva el `.part` para reanudar después de interrupciones.
- Al volver a la aplicación se recuperan descargas que quedaron `DOWNLOADING` sin progreso en memoria, sin tocar descargas marcadas explícitamente como `PAUSED`.

### 2. Notificaciones estilo gestor de descargas
- Acciones de Pausar/Cancelar pasan por `DownloadActionReceiver`.
- Reanudar en Android 14+ pasa por `MainActivity` para poder programar UIDT correctamente.
- La notificación pausada queda persistente y conserva Reanudar/Cancelar.
- Se evita que un último bloque leído por la red vuelva a publicar una notificación de "Descargando" después de que el usuario haya pulsado Pausar.
- Al cancelar UIDT se elimina primero el elemento del estado persistente/en memoria para impedir que `onStopJob()` lo resucite como pausado.
- Al pausar UIDT se publica nuevamente la notificación después de cancelar el job para evitar una carrera entre JobScheduler y NotificationManager.
- Las acciones usan PendingIntents explícitos y datos únicos por descarga.

### 3. BottomSheet de detalles
Al confirmar "Descargar", el `ModalBottomSheet` se oculta inmediatamente mediante `sheetState.hide()` y se elimina el elemento seleccionado. La descarga continúa independientemente de la UI.

### 4. Catálogo y permisos
- El catálogo ahora usa estrategia cache-first.
- Si ya existe catálogo local, se muestra inmediatamente mientras se intenta actualizar.
- Un fallo temporal de red ya no borra una lista válida que ya estaba en pantalla.
- Al recuperar conectividad se vuelve a intentar automáticamente cuando el catálogo está vacío o falló.
- Al terminar de conceder permisos se fuerza una actualización del catálogo.
- El monitor de red ya no considera suficiente que exista una interfaz con Internet: espera `NET_CAPABILITY_VALIDATED`, evitando la carrera de arranque que podía requerir desconectar/reconectar.

### 5. Reanudación y cola
- Una descarga PAUSED o FAILED reutiliza su archivo `.part` en lugar de crear innecesariamente otra descarga.
- Pulsar repetidamente sobre una descarga PENDING/DOWNLOADING no crea duplicados.
- La pausa calcula el progreso usando primero el `.part` real.
- Se evita sobrescribir silenciosamente otro archivo terminado con el mismo nombre; se genera un sufijo seguro cuando corresponde.
- Los elementos explícitamente pausados no se reanudan solos.

## Investigación de soluciones externas

Se revisaron soluciones públicas de GitHub:

- Fetch: ofrece cola persistente, pausa/reanudación, concurrencia, reintentos y notificaciones.
- Downpour: aporta una arquitectura moderna con estado persistente, reanudación y máquina de estados.
- SimpleDownloader/QDM: ofrecen ideas útiles como Range, cola, concurrencia y recuperación.

No se incorporaron literalmente como dependencias porque sus arquitecturas de ejecución no sustituyen el requisito de UIDT en Android 14+ y algunas dependen de foreground services. Se conservaron las ideas útiles (estado persistente, máquina de estados, reanudación, acciones de notificación y cola) dentro de la arquitectura de este proyecto.

## Limitación de validación

La compilación no pudo ejecutarse en este entorno porque el wrapper necesita descargar Gradle 9.3.1 desde `services.gradle.org` y este entorno no tiene acceso de red a ese host.

Se realizaron:
- comprobación estructural de llaves/paréntesis en los archivos modificados;
- búsqueda de `TODO`, `FIXME`, `GlobalScope` y `!!`;
- revisión de las rutas de acciones de notificación;
- revisión del Manifest;
- revisión de las rutas de catálogo, permisos, bottom sheet y descargas.

## Referencias técnicas

- Android UIDT: https://developer.android.com/develop/background-work/background-tasks/uidt
- Android foreground-service timeout: https://developer.android.com/develop/background-work/services/fgs/timeout
- Android 16 background behavior: https://developer.android.com/about/versions/16/behavior-changes-all
- Fetch: https://github.com/tonyofrancis/Fetch
- Downpour: https://github.com/AlirezaJavan/Downpour
