# Download Free — Rediseño visual v1.3 (versionCode 4)

## Nuevo bottom navigation

`ui/components/AppBottomNav.kt` se rediseñó para las tres pestañas de la app:

- **Dock flotante tipo píldora** con 16 dp de margen lateral, separación del borde inferior y rail compacto de 64 dp. Fondo azul-negro `#0B1020` al 66% en tema oscuro, borde de 1 dp blanco al 8% y brillo superior discreto: vidrio tintado, sin borde grueso ni halo general.
- **Indicador persistente** en turquesa translúcido (`#14B8A6` al 15%) que se desliza entre Catálogo, Descargas y Ajustes con spring. Todos los nombres permanecen visibles; inactivos usan iconos outline y texto gris azulado pequeño, activos iconos filled y texto turquesa.
- **Descargas al centro**: botón circular de 52 dp elevado 8 dp sobre el rail. Se rellena de turquesa y recibe glow solo cuando está seleccionado; en reposo mantiene el icono outline, sin glow. Los iconos son de la misma familia Material Rounded/Outlined.
- **Badge de cola**: número de descargas `DOWNLOADING + PENDING`; se oculta en cero y hace pop al aparecer o cambiar. El mismo criterio alimenta el contador de la pestaña de Descargas (`En curso/cola`), así ambos números coinciden. Pausadas y fallidas no se cuentan como transferencias activas ni en cola.
- **Anillo de progreso** fino cuando hay transferencias `DOWNLOADING`; refleja el promedio del progreso de las descargas en curso y se actualiza con el estado que publica el ViewModel.
- **Accesibilidad**: cada icono tiene descripción, las pestañas exponen estado seleccionado, el badge comunica el conteo y las áreas táctiles superan 48 dp. Hay háptica ligera al tocar. En tema oscuro se usa el teal pedido `#14B8A6`; en tema claro se oscurece a `#00695C` para conservar contraste AA.
- **Reducir animaciones**: observa las escalas globales de animación de Android. Si están desactivadas, el indicador y los cambios de página pasan a estado inmediato y se omiten los rebotes/transiciones del badge.
- **Pantalla de fondo**: degradado inferior de 176 dp para que las imágenes y tarjetas detrás del dock no queden visualmente cargadas; listas y grillas dejan 136 dp al final para poder desplazar el último elemento por encima de la barra.

## Icono de aplicación — fondo blanco y teal

- El fondo del launcher ahora es blanco tanto en los recursos adaptativos como en las variantes por densidad.
- El disco que antes era azul pasa a un degradado teal `#2DD4BF → #0F766E`, con el glifo de descarga blanco.
- Se actualizaron los iconos cuadrados y redondos en mdpi, hdpi, xhdpi, xxhdpi y xxxhdpi, el icono monocromático para Android 13+ y los símbolos de splash para mantener la misma identidad teal.

## Se conservan de v1.2

- Entrada escalonada de las tarjetas del catálogo, feedback de presión en tarjetas y hápticas.
- Confeti y doble tick al completar una descarga.
- Repetir el toque en la pestaña activa vuelve el catálogo al inicio.
- Sin carrusel hero ni elementos de interfaz de Favoritos, tal como se había pedido.
- Bottom navigation permanece visible; no se oculta al desplazarse.

## Archivos tocados en v1.3

| Archivo | Cambio |
|---|---|
| `ui/components/AppBottomNav.kt` | Nuevo dock, iconos outline/filled, indicador spring, badge, progreso, háptica y preferencia de movimiento reducido. |
| `MainActivity.kt` | Conteo compartido, progreso agregado, degradado inferior y navegación sin animación si el sistema la reduce. |
| `ui/screens/DescargasScreen.kt` | Contador visible sincronizado con el badge y espacio inferior extra. |
| `ui/screens/HomeScreen.kt` | Espacio final extra en ambas grillas. |
| `ui/screens/AjustesScreen.kt` | Espacio final extra para no quedar bajo el dock. |
| `data/model/Pelicula.kt` | Regla común `DownloadStatus.isInProgressOrQueued`. |
| `res/drawable*`, `res/mipmap-*` | Launcher blanco/teal, capa monocromática y splash teal. |
| `app/build.gradle.kts` | `versionCode = 4`, `versionName = "1.3"`. |

## Verificación

- Conteo del badge y pestaña de Descargas usan una sola regla de estados (`DOWNLOADING` y `PENDING`).
- Se revisaron nombres de estados, callbacks del pager, descripciones accesibles, recursos de launcher y paddings de las listas/grillas.
- Se hizo revisión estática de imports y balance de delimitadores Kotlin; **no se ejecutó Gradle ni se compiló**, según la indicación del usuario.

## Prueba manual sugerida

1. Instala v1.3 y confirma que el launcher tenga fondo blanco y disco de descarga teal.
2. Toca Catálogo, Descargas y Ajustes: verifica que la píldora se deslice, que los nombres sigan visibles y que Descargas quede elevada al centro.
3. Inicia dos descargas y deja otra en espera: el badge y el contador `En curso/cola` deben coincidir; el anillo debe reflejar el progreso en movimiento.
4. Pausa o falla una descarga: no debe contarse como activa/en cola. Al completar una, el badge debe decrementar y la pantalla conservar sus secciones de pausa/error.
5. Activa **Quitar animaciones** en Accesibilidad/Opciones de desarrollador de Android y vuelve a tocar las pestañas: los cambios deben ser inmediatos, conservando la respuesta háptica.
6. Desplázate hasta el final del catálogo, descargas y ajustes: el último elemento debe poder quedar por encima del dock.
