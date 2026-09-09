# Arquitectura móvil de My Keys

## Decisión

My Keys adopta Kotlin Multiplatform con Compose Multiplatform. Android se renderiza con
Jetpack Compose nativo e iOS comparte la UI de Compose cuando no necesita una integración
específica de plataforma.

La arquitectura combina tres ideas compatibles:

- Clean Architecture para que dominio y casos de uso no dependan de frameworks.
- Flujo unidireccional de datos en presentación, con estado inmutable y eventos explícitos.
- Puertos y adaptadores en límites de seguridad y sistema operativo.

No se crea una capa por ceremonia. Las funcionalidades simples pueden ir de UI a caso de
uso y puerto; una capa de dominio adicional solo aparece cuando contiene reglas reutilizables
o complejas.

## Regla de dependencias

`presentation -> domain <- data/platform`

El dominio define qué necesita. Los adaptadores deciden cómo hacerlo. Por tanto, Supabase,
SQLDelight, Android Keystore, Apple Keychain, biometría y autofill no pueden importarse desde
el dominio.

## Módulos iniciales

- `androidApp`: aplicación Android y configuración de seguridad del sistema.
- `iosApp`: host iOS y puente a la UI compartida.
- `shared`: código común organizado por `domain`, `data`, `presentation` y `designsystem`.

Se dividirá `shared` en módulos Gradle separados solo cuando los límites crezcan lo suficiente
para que la compilación, ownership o aislamiento lo justifiquen. En Fase 1 los paquetes ya
marcan esos límites sin imponer coste estructural prematuro.

## Límites de seguridad previstos

- `CryptoPort`: Argon2id, AEAD, generación aleatoria y borrado best-effort de buffers.
- `SecureKeyStorePort`: wrapping de claves con Keystore/Keychain y política biométrica.
- `VaultRepository`: persistencia local de ciphertext y sincronización remota.
- `SessionPort`: autenticación Supabase separada de la contraseña maestra.
- `ClipboardPort`: copiado temporal y limpieza best-effort.

La UI nunca recibe una clave criptográfica. Los adaptadores remotos nunca reciben plaintext
del vault ni la contraseña maestra.

## Versiones fijadas en Fase 1

- Kotlin 2.4.10.
- Compose Multiplatform 1.11.1.
- Android Gradle Plugin 9.1.0.
- Gradle 9.6.1 con checksum fijado.
- `compileSdk` y `targetSdk` 36; `minSdk` 26.

API 36 se mantiene mientras sea el último nivel estable probado por AGP 9.1. El SDK 37
preliminar puede estar instalado para evaluación, pero no define el target de producción.
