# My Keys

Gestor móvil personal de secretos con cifrado del lado cliente y arquitectura zero-knowledge.

## Estado actual

El proyecto ha completado **FASE 2 — Autenticación + MFA** en el objetivo validable desde
Windows/Android. La aplicación principal usa Kotlin Multiplatform con Compose Multiplatform:
Jetpack Compose nativo en Android y UI compartida para iOS. Registro, email, login, logout,
recuperación protegida por MFA, sesión segura y TOTP están implementados; RLS exige AAL2
para todo dato privado. El build iOS permanece diferido hasta macOS/Xcode. Expo se conserva
solo como prototipo de referencia.

## Documentación

- [Índice de documentación](docs/README.md)
- [Fase 0 — Diseño técnico completo](docs/01-fase-0/README.md)
- [Fase 0 — Vista interactiva](docs/01-fase-0/vista-interactiva.html)
- [Fase 1 — Base](docs/02-fase-1-base/README.md)
- [Fase 2 — Autenticación + MFA](docs/03-fase-2-autenticacion/README.md)

## Decisión de seguridad principal

La contraseña de acceso de Supabase Auth y la contraseña maestra del vault son secretos distintos. Supabase recibe la primera para autenticar la cuenta, pero la contraseña maestra nunca sale del dispositivo.

## Desarrollo local

Abre `mobile` en Android Studio. La puerta local de Android es:

```powershell
Set-Location mobile
.\gradlew.bat :androidApp:assembleDebug :shared:testAndroidHostTest :androidApp:lintDebug
```

Consulta la documentación de Fase 1 para los requisitos base y la de Fase 2 para la puerta
completa: migración, 47 aserciones pgTAP, integración Auth/Mailpit/TOTP/AAL2, escaneo de
secretos y gate Android.
