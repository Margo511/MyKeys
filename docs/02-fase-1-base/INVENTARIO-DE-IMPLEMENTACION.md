# Fase 1 — Inventario de implementación

Este inventario relaciona los entregables de la base con los archivos que los materializan.
No incluye los componentes de autenticación añadidos posteriormente en Fase 2.

## Cliente principal KMP

- `mobile/settings.gradle.kts`: proyecto `MyKeys` y módulos `shared`/`androidApp`.
- `mobile/gradle/libs.versions.toml`: catálogo de versiones fijadas.
- `mobile/gradle/wrapper/gradle-wrapper.properties`: versión y checksum del wrapper.
- `mobile/shared/build.gradle.kts`: targets Android, iOS device/simulator, Compose y tests.
- `mobile/androidApp/build.gradle.kts`: application ID, SDK, build types y Lint estricto.
- `mobile/androidApp/src/main/kotlin/app/mykeys/mobile/MainActivity.kt`: host Compose Android.
- `mobile/iosApp/iosApp/iOSApp.swift`: entrada SwiftUI.
- `mobile/shared/src/iosMain/kotlin/app/mykeys/MainViewController.kt`: puente iOS a Compose.

## Capas compartidas

- `mobile/shared/src/commonMain/kotlin/app/mykeys/domain/`: modelos, puertos y casos de uso
  independientes de UI y proveedor.
- `mobile/shared/src/commonMain/kotlin/app/mykeys/data/`: adaptadores de infraestructura.
- `mobile/shared/src/commonMain/kotlin/app/mykeys/presentation/`: contratos, estado y
  presenters con flujo unidireccional.
- `mobile/shared/src/commonMain/kotlin/app/mykeys/designsystem/`: tema y fundamentos
  visuales compartidos.
- `mobile/shared/src/commonMain/kotlin/app/mykeys/App.kt`: composición raíz del cliente.

La regla de dependencias es `presentation -> domain <- data/platform`. El dominio declara
necesidades y la infraestructura las satisface; ninguna API Android, iOS o Supabase debe
filtrarse al núcleo.

## Configuración de seguridad Android

- `AndroidManifest.xml` desactiva backup, declara solo Internet y bloquea cleartext global.
- `res/xml/backup_rules.xml` y `data_extraction_rules.xml` excluyen los dominios de datos de
  backup y transferencia entre dispositivos.
- `res/xml/network_security_config.xml` limita la excepción HTTP de desarrollo a los hosts
  locales necesarios.
- `build.gradle.kts` valida que la credencial aportada al cliente sea una publishable key.

## Host iOS

- El shell SwiftUI contiene la integración mínima con el framework estático `Shared`.
- `Config.xcconfig` define los nombres de configuración pública sin incluir valores reales.
- Los assets incluyen el icono de aplicación y color de acento.
- El build queda pendiente de validación en macOS/Xcode; la existencia de estos archivos no
  se presenta como evidencia de ejecución.

## Prototipo Expo conservado

- `app/`: rutas y pantallas de referencia.
- `src/components/ui/`: componentes base reutilizables del prototipo.
- `src/design-system/`: tokens y proveedor de tema.
- `src/config/env.ts`: validación de variables públicas.
- `src/infrastructure/supabase/client.ts`: cliente Supabase con inicialización diferida.
- `app.json` y `eas.json`: configuración Expo, Development Builds y perfiles EAS.

## Automatización y calidad

- `package.json`: comandos canónicos Node, Expo, Supabase y escaneos.
- `package-lock.json`: resolución reproducible de dependencias npm.
- `.github/workflows/quality.yml`: jobs de referencia Expo, Android y Supabase local.
- `scripts/check-source-secrets.mjs`: escaneo de fuente y configuración generada.
- `scripts/check-bundle-secrets.mjs`: escaneo del bundle de producción.
- `scripts/supabase-local.mjs`: wrapper multiplataforma con saneado de salida.
- `infra/windows/wslconfig-docker.ini`: referencia de WSL para el entorno validado.

## Evidencias conservadas

- `assets/kmp-foundation-android.png`: base KMP ejecutada en Android.
- `assets/foundation-home-dark.png`: inicio del prototipo Expo en tema oscuro.
- `assets/foundation-details-dark.png`: vista de arquitectura del prototipo Expo.

## Decisiones que condicionan fases posteriores

- KMP/Compose es el cliente principal y Expo no recibe nuevas funcionalidades de producto.
- Los límites de dominio, sesión, criptografía, almacenamiento seguro y plataforma se
  expresan mediante puertos/adaptadores.
- Android API 26 es el mínimo; compile/target permanecen en API 36 para esta línea base.
- Se prohíben backups del almacenamiento de aplicación y secretos de servidor en clientes.
- Las comprobaciones equivalentes de iOS deben ejecutarse antes de declarar soporte iOS de
  producción.
