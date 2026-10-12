# Download Free — Bottom navigation v1.4 (versionCode 5)

## Rediseño basado en la referencia enviada

`ui/components/AppBottomNav.kt` ahora usa un rail flotante con una muesca cóncava en el centro (`CenterNotchShape`): el botón de Descargas se integra en la curva y sobresale 8 dp, inspirado en la silueta de la imagen. Se conservan solo las tres pestañas de Download Free: **Catálogo**, **Descargas** y **Ajustes**.

- Rail compacto de 64 dp, márgenes laterales de 16 dp, separación inferior y borde fino; el botón central es circular y queda elevado sobre la muesca.
- Catálogo y Ajustes muestran icono y etiqueta de forma permanente. El centro mantiene su badge y anillo de progreso.
- El indicador de selección continúa deslizándose con spring. El botón central solo recibe el color de énfasis y glow cuando Descargas está seleccionada; en reposo conserva un disco neutral con icono outline.
- El badge cuenta `DOWNLOADING + PENDING`, se oculta en cero y coincide con el contador `En curso/cola` de la pantalla de Descargas. Las pausadas y fallidas no entran en ese conteo.
- El anillo refleja el progreso medio de las transferencias activas; se mantienen hápticas, rebote de iconos, accesibilidad y respeto por **Quitar animaciones** del sistema.

## Respeto del tema escogido

La barra ya no fija el teal ni el fondo por su cuenta. Obtiene los colores del `MaterialTheme.colorScheme` activo: `primary` para la selección, una tinta clara/oscura calculada por contraste sobre el primario para el botón y el badge, `onSurfaceVariant` para elementos inactivos, `surface` para el rail y `background` para el degradado inferior. Así sigue los temas oscuros/claros, los presets y los colores personalizados que se cambien en Ajustes. El icono de aplicación conserva su identidad de marca blanca/teal, independiente del color de énfasis elegido para la interfaz.

## Se conserva de v1.3

- Degradado inferior y 136 dp de espacio final en listas/grillas para que el último elemento pueda pasar por encima del dock.
- Retocar la pestaña activa vuelve el catálogo al inicio; la barra no desaparece al desplazarse.
- Entrada escalonada de tarjetas, press-scale, hápticas y confeti al completar descargas.
- Launcher de fondo blanco, disco teal, variantes adaptativas/redondas/monocromáticas y splash teal.
- Sin carrusel hero ni interfaz de Favoritos.

## Archivos tocados en v1.4

| Archivo | Cambio |
|---|---|
| `ui/components/AppBottomNav.kt` | Muesca central cóncava, botón elevado y colores totalmente ligados al `MaterialTheme`. |
| `MainActivity.kt` | Degradado inferior toma el fondo del tema actual. |
| `app/build.gradle.kts` | `versionCode = 5`, `versionName = "1.4"`. |
| `MEJORAS_UI.md` | Documentación de esta versión. |

## Verificación

- La selección, superficie, textos, botón, badge, anillo y degradado usan el esquema de color vigente.
- Se revisaron el conteo compartido entre badge/pantalla, la geometría de la muesca, los estados de selección y los callbacks.
- Se hizo revisión estática de Kotlin/XML y balance de delimitadores; **no se ejecutó Gradle ni se compiló**, según la indicación del usuario.

## Prueba manual sugerida

1. Abre la app en tema oscuro y claro: rail, texto y botón deben acompañar el tema.
2. Cambia entre varios colores de énfasis en Ajustes: la píldora, el icono seleccionado, el badge y el anillo deben usar el color elegido.
3. Toca cada pestaña: verifica el movimiento del indicador, la muesca central, las etiquetas, la háptica y el rebote; activa **Quitar animaciones** y confirma que el movimiento se desactive.
4. Inicia descargas y deja una en cola: badge y contador `En curso/cola` deben coincidir, y el anillo debe actualizarse.
5. Comprueba el launcher: fondo blanco y marca de descarga teal.
