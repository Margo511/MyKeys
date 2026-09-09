# Fase 2 — Trazabilidad de requisitos

Este documento enlaza cada resultado funcional con su implementación y su prueba principal.
Las rutas son relativas a la raíz del repositorio.

## AUTH-01 — Registro seguro

**Requisito:** validar email y contraseña, crear la cuenta y no iniciar una sesión privada
antes de confirmar el email.

**Implementación:** `AuthUseCases.kt`, `SessionPort.kt`, `SupabaseSessionAdapter.kt` y las
pantallas de registro/verificación en `AuthScreen.kt`.

**Evidencia:** `AuthUseCasesTest.kt`, `AuthPresenterTest.kt` y
`scripts/test-supabase-auth-integration.mjs`.

## AUTH-02 — Confirmación por deep link

**Requisito:** aceptar únicamente el callback canónico y continuar el onboarding en AAL1.

**Implementación:** `HandleAuthDeepLink`, validación redundante del adaptador,
`AndroidManifest.xml`, `Info.plist` y `supabase/config.toml`.

**Evidencia:** test de caso de uso, integración con email real en Mailpit y comprobación del
redirect recibido.

## AUTH-03 — Login y restauración

**Requisito:** autenticar credenciales, restaurar sesiones válidas y eliminar material
caducado o corrupto.

**Implementación:** `Login`, `RestoreSession`, `SupabaseSessionAdapter`,
`SecureSessionManager`, `AndroidSecureAuthStorage` e `IosSecureAuthStorage`.

**Evidencia:** `SecureAuthStorageTest.kt`, `AuthPresenterTest.kt`, build Android e integración
de login posterior a recovery.

## AUTH-04 — MFA TOTP obligatorio

**Requisito:** enrolar, seleccionar y verificar TOTP antes de acceder a datos privados.

**Implementación:** casos de uso TOTP, métodos MFA del adaptador, estados
`TotpEnrollmentRequired`/`TotpChallengeRequired`, QR local y políticas RLS restrictivas.

**Evidencia:** tests KMP, integración Auth/TOTP y aserciones pgTAP AAL1/AAL2.

## AUTH-05 — Factores de respaldo

**Requisito:** admitir varios TOTP y no eliminar el último verificado.

**Implementación:** `RemoveTotpFactor`, comprobación en adaptador y trigger
`keep_last_verified_totp_factor`.

**Evidencia:** test de dominio, pgTAP de rechazo/aceptación e integración directa con el
endpoint de Supabase Auth.

## AUTH-06 — Recuperación de contraseña protegida

**Requisito:** el email de recovery no debe omitir MFA y el mensaje no debe enumerar
cuentas.

**Implementación:** estados `PasswordRecoveryMfaRequired`/`PasswordRecoveryReady`, mensajes
genéricos del presenter y actualización de usuario solo en el estado adecuado.

**Evidencia:** integración que adopta el enlace, comprueba cero datos en AAL1, eleva con
TOTP, cambia la contraseña y repite login AAL1/AAL2.

## AUTH-07 — Logout seguro

**Requisito:** intentar revocación remota y borrar siempre la copia local.

**Implementación:** `Logout`, `SupabaseSessionAdapter.logout` y `SecureSessionManager.clear`.

**Evidencia:** tests de presenter/almacenamiento y compilación del flujo completo.

## AUTH-08 — Errores sanitizados

**Requisito:** no exponer tokens, secretos TOTP, cuerpos crudos o detalles de proveedor.

**Implementación:** `AuthFailure`, `mapFailure`, `userMessage` y wrappers de scripts con
saneado de salida.

**Evidencia:** tests de transiciones de error, `npm run scan:source` y
`npm run scan:android-config`.

## DATA-01 — RLS AAL2 global

**Requisito:** ningún dato privado disponible para `anon` o AAL1.

**Implementación:** seis políticas restrictivas `*_require_aal2`, RLS habilitada y forzada,
y grants reiniciados desde mínimo privilegio.

**Evidencia:** 47 aserciones pgTAP e integración REST con `anon`, AAL1 y AAL2.

## DATA-02 — Aislamiento entre propietarios

**Requisito:** el usuario A no puede observar ni modificar datos de B aunque conozca UUID.

**Implementación:** políticas de propiedad, pertenencia derivada desde `vaults`,
`WITH CHECK`, FKs y grants de columna.

**Evidencia:** fixtures A/B de pgTAP e integración con dos cuentas reales locales.

## DATA-03 — Eventos no falsificables

**Requisito:** el cliente no puede insertar eventos de auditoría.

**Implementación:** ausencia de grant `INSERT` sobre `security_events`; evento inicial desde
`handle_new_user()` protegido.

**Evidencia:** pgTAP comprueba privilegios y visibilidad solo del evento propio.

## SEC-01 — Tokens protegidos en reposo

**Requisito:** no guardar access/refresh tokens en preferencias en claro ni migrarlos por
backup.

**Implementación:** persistencia automática del SDK desactivada, AES-GCM/Keystore en
Android, Keychain `ThisDeviceOnly` en iOS y reglas Android de backup vacío.

**Evidencia:** tests de round-trip/corrupción del blob y configuración pública, Android
Lint/build e inspección de configuración. El build iOS queda diferido.

## SEC-02 — Solo configuración pública en el cliente

**Requisito:** impedir secret/service-role keys y protocolos inseguros en release.

**Implementación:** validación Gradle del prefijo publishable, URL HTTPS obligatoria en
release, network security config local para debug y scripts de secret scan.

**Evidencia:** `npm run scan:source`, `npm run scan:android-config` y job Android de CI.

## OPS-01 — Reproducción local y CI

**Requisito:** reconstruir esquema, ejecutar pruebas y compilar desde un checkout limpio.

**Implementación:** Gradle wrapper/checksum, lockfile npm, migración única reproducible,
wrapper Supabase multiplataforma y workflow con tres jobs.

**Evidencia:** puerta completa descrita en [Pruebas y operación](PRUEBAS-Y-OPERACION.md).

## Pendientes fuera de Fase 2

- Build y ejecución iOS en macOS/Xcode.
- Contraseña maestra, Recovery Key, DEK y cifrado cliente: Fase 3.
- CRUD de secretos y sincronización: Fase 4.
- Biometría y auto-lock: Fase 6.
- Pipeline completo de eventos/emails: Fase 7.
- Auditoría profesional y publicación: Fases 8 y 9.
