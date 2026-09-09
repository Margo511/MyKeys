# My Keys Mobile

Aplicación móvil nativa compartida con Kotlin Multiplatform y Compose Multiplatform. En
Android, Compose Multiplatform utiliza Jetpack Compose; iOS reutiliza la misma UI mediante
un `UIViewController` de Compose.

La base procede de la plantilla oficial `Kotlin/KMP-App-Template`, fijada en el commit
`30aa52f7430d304c7069007e276c46496f47fc72` y reducida a las dependencias necesarias.

## Arquitectura

- `androidApp`: entry point Android, manifiesto y recursos de plataforma.
- `iosApp`: shell SwiftUI y configuración Xcode.
- `shared/domain`: modelos, invariantes, casos de uso y puertos sin dependencias de UI.
- `shared/data`: adaptadores que implementan los puertos del dominio.
- `shared/presentation`: estado y UI con flujo unidireccional.
- `shared/designsystem`: tema y componentes visuales compartidos.

Se usa Clean Architecture pragmática por funcionalidad, reforzada con puertos y adaptadores
en los límites sensibles. La sesión Auth usa una implementación Android Keystore y otra iOS
Keychain; el dominio no conoce ninguna de ellas ni depende de Supabase.

## Autenticación local

El build no contiene ninguna clave servidor. Para conectar Android al Supabase local, inicia
el stack desde la raíz y expone solo su publishable key durante el build:

```powershell
npm run supabase:start
$env:MY_KEYS_SUPABASE_PUBLISHABLE_KEY = npm run --silent supabase:publishable-key
Set-Location mobile
.\gradlew.bat :androidApp:assembleDebug
```

Android usa `http://10.0.2.2:54321` únicamente en debug y limita cleartext a los hosts
locales. Release exige `-Pmykeys.supabase.url=https://...`; tanto Gradle como el adaptador
rechazan una secret key o service-role key. En iOS se configuran
`MY_KEYS_SUPABASE_URL` y `MY_KEYS_SUPABASE_PUBLISHABLE_KEY` mediante un `.xcconfig` local
de Xcode, sin versionar valores reales.

El callback único es `mykeys://auth/callback` en ambos shells nativos.

## Verificación

Desde este directorio:

```powershell
.\gradlew.bat :androidApp:assembleDebug :shared:testAndroidHostTest :androidApp:lintDebug
```

El workspace actual contiene un carácter no ASCII en su ruta. Android Studio puede abrir
`mobile` directamente; para procesos Java aislados en Windows se verificó también el mismo
directorio a través de la unidad virtual `M:` y con una ruta temporal corta.

El proyecto iOS debe compilarse en macOS con Xcode. Windows sí compila y prueba todo el
código común que participa en el target Android.
