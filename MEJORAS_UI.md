# Download Free — revisión y mejoras v1.6 (versionCode 7)

## Navegación inferior

- El botón central de Descargas conserva la muesca cóncava y el relieve, pero ya no dibuja una cápsula detrás: esa franja quedaba visible como un óvalo pequeño bajo el botón.
- El indicador deslizante con spring se mantiene en Catálogo/Ajustes, ahora como cápsula de selección más legible con borde y color del tema. Se desvanece al seleccionar Descargas; allí la selección la marca el botón elevado, su contorno/glow y la etiqueta.
- Se conserva el dock flotante, sus márgenes, el fade, las etiquetas, badge, anillo de progreso, hápticas, accesibilidad y reducción de movimiento.

## Permiso de batería y Ajustes

- La exención estándar de Android se consulta al iniciar y al volver a la app; se vuelve a leer tras el resultado de Ajustes y con una segunda comprobación breve para fabricantes que aplican el cambio con retraso.
- Ajustes muestra un interruptor ligado al estado real de `PowerManager.isIgnoringBatteryOptimizations()`. Para activarla se pide confirmación al sistema; para desactivarla se abre la lista de optimización de Android. La app no puede conceder ni revocar ese acceso silenciosamente.
- La hoja de permisos respeta “No volver a mostrar” y el intervalo de recordatorio; ya no hay un efecto de inicio que anulaba esa elección. No aparece mientras hay transferencias activas o en cola.
- “Inicio automático” de fabricantes se presenta como opcional y no se marca falsamente como verificado al abrir una pantalla del fabricante; Android no ofrece una comprobación uniforme para ese ajuste.

## Notificaciones en pantalla de bloqueo

- Los avisos de progreso, pausa, resumen, error y finalización declaran explícitamente visibilidad pública para la pantalla de bloqueo; el canal de errores también recibe esa visibilidad al crearse.
- En Ajustes, el control de Notificaciones abre los ajustes de notificación de Android y orienta a revisar “Mostrar en pantalla de bloqueo”. No se cambian los IDs de canal para preservar las preferencias del usuario.
- Android y el fabricante siguen teniendo la última palabra: permiso/canal desactivado, ocultar notificaciones en bloqueo, modo No molestar o filtros de pantalla pueden impedir mostrarlas. La app no fuerza ni evita esas preferencias.

## Descargas: revisión estática y correcciones

- Corregido el reanudar desde una notificación en Android 14+: ahora pasa por la cola del `DownloadHelper`, valida red/VPN/Wi‑Fi/concurrencia y marca la descarga como activa antes de programar el UIDT. Antes el JobService recibía un elemento `PAUSED` y lo descartaba.
- Cuando Android detiene un UIDT para reintentarlo, se conserva el estado y el progreso necesarios para que JobScheduler pueda continuar desde el `.part`; una pausa o cancelación del usuario sigue siendo respetada.
- La carga de DataStore se espera antes de decidir duplicados y límite de concurrencia al iniciar una descarga.
- Si Android rechaza el inicio del servicio en versiones anteriores a 14, la descarga vuelve a `PENDING`, se informa al usuario y no se muestra un falso aviso de “Descarga iniciada”.
- Si se borró fuera de la app un archivo marcado como completado, se permite descargarlo otra vez desde el catálogo.
- La finalización del `.part` ya no borra un archivo final previo si la promoción/copia falla; usa un archivo temporal y verifica el tamaño.
- Los fallos guardan un motivo breve que aparece en la tarjeta y notificación. Al reintentar se limpia el motivo anterior.
- Al reintentar o eliminar, también se limpian notificaciones obsoletas de completado/fallo.

## Verificación y límites

- Revisión estática de los flujos de inicio/pausa/reanudación/cancelación/cola, estados UIDT y notificaciones; balance de delimitadores Kotlin y XML bien formado.
- **No se ejecutó Gradle ni se compiló**, de acuerdo con la indicación previa. Tampoco se pudo hacer una prueba en dispositivo/emulador; la validación final de permisos y descargas debe hacerse en Android.

## Pruebas manuales recomendadas

1. En Ajustes > Notificaciones > Ajustes, permitir avisos y activar “Mostrar en pantalla de bloqueo” para Progreso, Completadas y Errores; probar una descarga con el móvil bloqueado.
2. Seleccionar Catálogo, Descargas y Ajustes: confirmar que no queda el óvalo bajo Descargas y que las cápsulas laterales se distinguen en tema claro/oscuro.
3. En Ajustes, activar la exención, aceptar en Android y volver: el interruptor debe reflejarla. Desactivarla desde la lista del sistema y volver: debe quedar apagado.
4. Iniciar una descarga, pausarla y reanudarla desde la app y desde su notificación; verificar también la cola y “Solo Wi-Fi”.
5. Dejar que Android detenga un trabajo UIDT o cambiar la conectividad: al reprogramarse debe continuar desde el progreso guardado.
6. Probar un error HTTP/sin red y confirmar que el motivo se vea; reintentar y comprobar que el texto se limpie.
7. Borrar manualmente un vídeo completado y volver a pulsar Descargar desde el catálogo.
