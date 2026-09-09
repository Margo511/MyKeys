# Fase 1 — Entorno, ejecución y verificación

Este documento permite reproducir la base de My Keys sin depender del historial de la
máquina donde se creó. Los comandos parten de la raíz del repositorio salvo que se indique
lo contrario.

## Componentes necesarios

### Comunes

- Git.
- Node.js 22.13.0 o compatible con el requisito `>=22.13.0`.
- npm y el `package-lock.json` del repositorio.
- JDK 21; el bytecode Android se genera con objetivo JVM 17.
- Android SDK Platform 36 y Build Tools 36.0.0.
- Docker Engine y Docker Compose para Supabase local.
- Supabase CLI 2.111.0.

### Windows

La configuración validada usa WSL 2.7.13, Ubuntu 24.04.4 LTS y `systemd`. Los scripts npm
detectan Windows, arrancan Docker en la distribución indicada por
`MY_KEYS_WSL_DISTRO` —por defecto `Ubuntu-24.04`— y ejecutan Supabase dentro de WSL.

La plantilla `infra/windows/wslconfig-docker.ini` contiene la configuración usada para
mantener la VM disponible durante el trabajo con Docker. Es una referencia: no debe copiarse
sin revisar los recursos disponibles en la máquina de destino.

### iOS

Se necesita macOS con una versión de Xcode compatible con Kotlin 2.4.10 y Compose
Multiplatform 1.11.1. El proyecto Xcode está en `mobile/iosApp`. Windows no puede cerrar
esta verificación.

## Instalación reproducible del prototipo de referencia

```powershell
npm ci
npm run check
npm run doctor
npm run verify:bundle
```

`npm run check` ejecuta formato, ESLint, TypeScript, Jest y el escaneo de secretos de la
fuente. `verify:bundle` exporta el bundle web de producción a `dist` y comprueba que no
contenga secretos de servidor, claves privadas ni JWT incrustados.

El prototipo Expo es material de referencia. Los cambios de producto deben implementarse en
el cliente KMP bajo `mobile/`.

## Compilación y controles Android/KMP

```powershell
Set-Location mobile
.\gradlew.bat :androidApp:assembleDebug :shared:testAndroidHostTest :androidApp:lintDebug
```

El comando debe terminar con `BUILD SUCCESSFUL` y produce, entre otros artefactos:

- APK debug de `app.mykeys.mobile`;
- tests host del código compartido;
- informe Android Lint con warnings tratados como errores.

El wrapper fija Gradle 9.6.1 y su checksum. Gradle resuelve JDK mediante Foojay si el entorno
no aporta un toolchain compatible.

## Supabase local

```powershell
Set-Location ..
npm run supabase:start
npm run supabase:status
```

Servicios locales documentados por el wrapper:

- API: `http://127.0.0.1:54321`;
- PostgreSQL: `127.0.0.1:54322`;
- Studio: `http://127.0.0.1:54323`;
- Mailpit: `http://127.0.0.1:54324`.

En Windows, el wrapper crea o reutiliza una red Docker cuyo binding por defecto es
`127.0.0.1`. La salida que pudiera contener credenciales se sanea antes de imprimirse.

Para detener el entorno:

```powershell
npm run supabase:stop
wsl --shutdown
```

El segundo comando es opcional y libera por completo la memoria de WSL cuando no se usa
ningún otro servicio de esa VM.

## Configuración pública del cliente Android

La URL debug de Supabase es `http://10.0.2.2:54321`, alias del host desde el emulador. El
tráfico HTTP solo se permite en debug hacia los hosts locales declarados. Para compilar con
la publishable key local:

```powershell
$env:MY_KEYS_SUPABASE_PUBLISHABLE_KEY = npm run --silent supabase:publishable-key
Set-Location mobile
.\gradlew.bat :androidApp:assembleDebug
```

La validación Gradle acepta únicamente el formato `sb_publishable_...`. Nunca deben usarse
una secret key, una service-role key ni credenciales extraídas manualmente de logs.

Release requiere una URL HTTPS explícita mediante `-Pmykeys.supabase.url=...` y la misma
clase de publishable key. El valor real no se versiona.

## Instalación y revisión en emulador

Con un AVD iniciado:

```powershell
Set-Location mobile
.\gradlew.bat :androidApp:installDebug
```

La evidencia de cierre se obtuvo en el AVD `medium_phone`, confirmando el proceso de
`app.mykeys.mobile` y `MainActivity` como actividad reanudada. La captura conservada está en
[`assets/kmp-foundation-android.png`](assets/kmp-foundation-android.png).

## Particularidades conocidas de Windows

- Una ruta con caracteres no ASCII puede afectar procesos Java que abren sockets AF_UNIX.
  AGP se validó con su excepción oficial y, cuando fue necesario, el mismo workspace se
  expuso mediante una unidad virtual con ruta ASCII corta.
- Antes de ejecutar Gradle conviene comprobar
  `Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe')`. Si devuelve `False`, se debe apuntar
  `JAVA_HOME` temporalmente a un JDK 21 existente; no hace falta modificar la configuración
  global para verificar el proyecto.
- El error `Unable to establish loopback connection` puede proceder de un terminal
  fuertemente aislado que impide la comunicación interna de Gradle. En ese caso debe
  repetirse la puerta desde un terminal de desarrollo normal o un runner CI autorizado.
- Supabase se ejecuta dentro de WSL para evitar diferencias entre Docker Desktop y el
  entorno Linux usado por CI.
- Si `npm run supabase:status` falla, primero se debe comprobar `wsl --status`, después el
  estado de Docker dentro de la distribución y por último ejecutar de nuevo
  `npm run supabase:start`.

## Resultado de cierre de Fase 1

El 9 de septiembre de 2026 quedaron verdes el build Android, el test de dominio disponible
en esa fase, Android Lint, el pipeline Node/Expo, Expo Doctor 21/21, el bundle de producción
y el escaneo de secretos. La aplicación se instaló y revisó en emulador. No se registró un
build iOS por falta de macOS/Xcode.
