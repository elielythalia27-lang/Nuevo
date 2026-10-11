# Cambios realizados

## Descargas (bugs corregidos)
- **Cancelar desde la app no borraba el archivo `.part`** (el item ya no estaba en la lista cuando el servicio buscaba su ruta). Ahora se pasa la ruta explícitamente.
- **Android 14+: las descargas en cola (PENDING) pasaban a PAUSED** al cambiar el límite de descargas simultáneas o activar "Solo Wi-Fi", y nunca se reanudaban solas. Ahora se conservan en cola.
- **Reintento automático estilo IDM**: ante cortes de red, timeouts o errores HTTP 5xx/429 se reintenta (2, 5, 10, 20 y 30 s) continuando desde el `.part` con HTTP Range, tanto en el servicio (API <34) como en el job UIDT (API 34+). Solo tras agotar los reintentos pasa a "Fallida".
- UIDT: ya no se llama a `jobFinished` después de `onStopJob`, y una descarga en cola ya no muestra notificación de "En pausa".

## VPN / proxy
- Si se activa una VPN o proxy **con descargas en curso**, se **pausan** (no fallan): se conserva el `.part`, la notificación indica "En pausa por VPN/proxy" y el motor deja de abrir conexiones. Se comprueba en tiempo real (callback de red) y en cada ciclo de lectura.
- Al desactivar la VPN/proxy, las descargas pausadas **por la VPN** se reanudan solas (las pausadas a mano por el usuario no). Si la app está cerrada, se reanudan al volver a abrirla.
- No se puede iniciar ni reanudar ninguna descarga con VPN/proxy activo (también desde los botones de la notificación).
- Un corte de red causado por activar la VPN ya no cuenta como error ni gasta reintentos.
- Al volver a la app desde segundo plano con VPN/proxy activo se vuelve a comprobar al instante y se muestra la pantalla de bloqueo.

## Pausar / Cancelar (corregido tras probar en el móvil)
- **Al pausar la segunda descarga desaparecían todas las notificaciones.** Al cancelar la notificación resumen de un grupo, Android borra también todas sus hijas (incluidas las "En pausa"). Ahora el resumen se mantiene mientras haya descargas en pausa (pasa a "N descargas en pausa", no ongoing) y solo se quita cuando no queda ninguna notificación del grupo. Aplica a Android < 14 (servicio) y ≥ 14 (jobs UIDT).
- **Al pulsar Parar/Cancelar aparecía "Descarga fallida … 0 B" y la descarga volvía a la lista.** Cancelar corta el socket y eso lanzaba una excepción que se trataba como fallo. Ahora, si la descarga fue cancelada o pausada, no se marca como fallida ni se vuelve a insertar, y la notificación se retira.
- Los botones se llaman **Cancelar** (y "Cancelar todo") en lugar de "Parar".

## Más correcciones
- **Eliminar una descarga completada no borraba el vídeo** del almacenamiento (solo la quitaba de la lista). Ahora lo borra.
- **«Cancelar todo» también borraba de la lista las descargas ya completadas.** Ahora solo afecta a las activas, en cola, en pausa o fallidas.

- **Al terminar, la descarga se marcaba "completada" aunque el vídeo no se hubiera guardado**: si `renameTo` fallaba (carpeta pública / SD) el `.part` quedaba sin renombrar y el archivo final se borraba antes. Ahora se copia y verifica el tamaño antes de dar la descarga por buena; si no se puede, queda "Fallida" conservando el `.part`.

## Segunda revisión (pantallas y datos)
- Descargas fallidas por **corte de red largo** se reintentan solas al volver la conexión.
- Una descarga cancelada ya no puede **reaparecer** al reiniciar (el guardado periódico de progreso reinsertaba items borrados).
- La migración de archivos completados a la carpeta pública ahora **sí guarda la nueva ruta** (antes quedaban apuntando a un archivo borrado).
- Reproductor: el vídeo **se pausa al salir de la app** (antes seguía sonando); **Reintentar** conserva la posición; el avance de 10 s y el arrastre no saltan al inicio cuando la duración es desconocida.
- Catálogo: una sola carga a la vez (una respuesta vieja ya no pisa a la nueva); no se reordena todo el catálogo en cada escritura de descargas.
- "Continuar viendo" se limpia si borras el vídeo al que apunta (antes abría un archivo inexistente).
- Textos corregidos: "Iniciando descarga…", "Reproducir vídeo/película descargada" y mensaje de error del catálogo (distingue sin conexión de error del servidor).

## Agrupación de notificaciones
- En Android 14+ no se publicaba la notificación resumen del grupo, así que las notificaciones de cada descarga no se agrupaban en un solo bloque desplegable (salvo que hubiera 4 o más). Ahora se publica y actualiza un resumen con progreso global, velocidad total y botones Pausar todo / Cancelar todo, como ya ocurría en Android < 14.

## Notificaciones (formato 1DM+, según capturas)
- Cada descarga: título = nombre del archivo, barra de progreso y **una sola línea** `10% | 25.0MB | 96.30KB/s | 36s/4m10s` (porcentaje | descargado | velocidad | transcurrido/total estimado) con botones **Pausar** y **Parar**. Sin subtítulo ni estilo expandido, para que dentro del grupo cada descarga se vea como una fila compacta con su barra.
- Con varias descargas se agrupan en un solo bloque desplegable (resumen + una fila por archivo).
- (Descripción anterior, reemplazada:) Título = nombre del archivo; línea compacta `42% • 12.4 MB/s • 1m 20s`; vista expandida con tamaño, velocidad y tiempo restante.
- Notificación resumen con progreso global, velocidad total y ETA total.
- Al terminar: toca para **abrir el vídeo directamente** (FileProvider) + botones *Abrir* / *Descargas*.
- Subtítulos de estado (Descargando / En pausa / Descarga fallida / Completada), visibilidad pública en pantalla de bloqueo.

## Interfaz / sistema
- Formas Material 3 más redondeadas (`Shapes`) en el tema.
- Gesto de volver predictivo habilitado (`enableOnBackInvokedCallback`).

## Aviso
- `release.keystore` está incluido en el repo con contraseña por defecto `android`. Genera una clave propia antes de publicar.
- No se pudo compilar en el entorno de trabajo (sin Android SDK); revisa con `./gradlew assembleDebug`.
