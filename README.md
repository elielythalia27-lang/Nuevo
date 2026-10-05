<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Download Free — Android App

Aplicación de películas y videos con reproductor profesional integrado, descargas continuas en segundo plano, soporte para Android 8 hasta Android 16 (UIDT y dataSync), notificaciones en tiempo real y temas personalizados.

---

## 🚀 Compilación e Instalación Automática en GitHub (CI/CD)

El repositorio incluye un flujo de trabajo automatizado en `.github/workflows/android-build-apk.yml` que compila y firma automáticamente el APK Release en cada push a las ramas `main` o `master`:

### Cómo obtener el APK desde GitHub:
1. Haz un **push** a tu repositorio de GitHub o ve a la pestaña **Actions** en GitHub.
2. Selecciona el workflow **"Build & Sign Android Release APK"** y pulsa **"Run workflow"** (o se ejecutará automáticamente con cada commit).
3. Una vez finalizada la ejecución (en ~2-3 minutos), baja a la sección **Artifacts**.
4. Descarga el archivo **`app-release-apk`**, descomprímelo e instala el archivo `.apk` directamente en tu teléfono Android.

---

## 🔑 Configuración de Claves y Secretos en GitHub (Opcional)

Si deseas usar tus propias credenciales de firma en GitHub Actions, puedes configurar los siguientes **Repository Secrets** en `Settings -> Secrets and variables -> Actions`:

| Secreto | Descripción | Valor por defecto |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | Contenido en Base64 de tu keystore | Usa el `release.keystore` incluido en el repo |
| `STORE_PASSWORD` | Contraseña del almacén de claves | `android` |
| `KEY_PASSWORD` | Contraseña del alias de la clave | `android` |
| `KEY_ALIAS` | Alias de la clave de subida | `uploadKey` |

---

## 💻 Ejecución Local en Android Studio

**Requisitos:** [Android Studio Ladybug o posterior](https://developer.android.com/studio) con JDK 17 o JDK 21.

1. Abre **Android Studio**.
2. Selecciona **Open** y elige la carpeta raíz de este proyecto.
3. Espera a que Gradle sincronice las dependencias (`compileSdk = 36`).
4. Conecta un dispositivo físico Android o inicia un Emulador.
5. Haz clic en **Run ('app')** (Shift + F10).

---

## 📱 Compatibilidad de Descargas en Segundo Plano

- **Android 14, 15 y 16 (API 34–36+):** Utiliza **User-Initiated Data Transfer (UIDT)** vía `DownloadUidtJobService`, evitando el límite de 6 horas de `dataSync` de Android 15 y cumpliendo con las cuotas de JobScheduler de Android 16.
- **Android 8.0 a 13 (API 26–33):** Utiliza `DownloadForegroundService` con servicio en primer plano y notificación en la barra de estado.
- **Soporte de reconexión:** Recuperación automática ante cortes con soporte `Range` y archivos `.part`.
