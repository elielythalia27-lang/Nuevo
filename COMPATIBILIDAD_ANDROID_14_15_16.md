# Compatibilidad de descargas Android 14–16

## Arquitectura

| Android | Ejecución principal | Motivo |
|---|---|---|
| API 24–33 | Foreground service `dataSync` existente | Compatibilidad con dispositivos anteriores |
| API 34+ | `JobScheduler` + UIDT | Transferencias largas iniciadas por el usuario |
| API 35+ | UIDT para descargas | Evita depender del límite de `dataSync` de 6 h/24 h |
| API 36+ | UIDT | Evita las cuotas ordinarias de trabajos para transferencias iniciadas por el usuario |

Android documenta UIDT como la API para transferencias de red largas iniciadas por el usuario y exige que el job se programe mientras la app está visible (salvo condiciones permitidas). Por ello, Reanudar desde una notificación en API 34+ lleva primero la acción a `MainActivity`.

## Persistencia

Cada descarga utiliza:
- estado persistente en DataStore;
- archivo parcial `<destino>.part`;
- progreso/bytes descargados;
- URL original;
- ruta local;
- estado PENDING/DOWNLOADING/PAUSED/FAILED/COMPLETED.

El archivo parcial se conserva al pausar o cuando el sistema interrumpe una transferencia, permitiendo continuar con HTTP Range cuando el servidor lo soporta.

## Notificaciones

Las notificaciones son controlables:
- Descargando → Pausar / Cancelar
- En pausa → Reanudar / Cancelar
- Fallida → Reintentar / Cancelar
- Completada → Abrir Descargas

En API 34+, Reanudar usa una Activity explícita para cumplir las condiciones de programación de UIDT.

## Importante

Ninguna aplicación puede garantizar que Android nunca detenga una transferencia: el sistema puede intervenir por memoria, térmica, restricciones de red u otras condiciones. La arquitectura está diseñada para persistir el estado y recuperar la transferencia, no para intentar evadir las políticas del sistema.
