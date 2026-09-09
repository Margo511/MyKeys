# Estado y roadmap de My Keys

**Corte documental:** 9 de septiembre de 2026  
**Última fase completada:** Fase 2 — Autenticación + MFA  
**Cliente principal:** Kotlin Multiplatform + Compose Multiplatform

## Qué existe hoy

El repositorio contiene una aplicación Android ejecutable y una base compartida preparada
para Android e iOS. El usuario puede registrarse, confirmar el email, iniciar y cerrar
sesión, recuperar la contraseña de acceso y completar MFA TOTP. El backend local aplica
aislamiento por usuario y exige AAL2 para todas las tablas privadas.

También se conserva el prototipo Expo de Fase 1 como referencia visual y de migración. No
es el cliente de producto sobre el que avanzan las fases siguientes.

## Estado por fase

### Fase 0 — Diseño técnico: completada

Define el modelo zero-knowledge, separa la contraseña de acceso de la contraseña maestra,
describe la envolvente de claves, el modelo de datos, las amenazas, los flujos y las puertas
de salida del MVP. La decisión de cliente fue enmendada en Fase 1 para adoptar KMP.

### Fase 1 — Base: completada con validación iOS diferida

Entrega la base KMP/Compose, arquitectura por capas pragmática, hosts Android/iOS, tema,
toolchain reproducible, Supabase local, CI y controles contra secretos. Android fue
compilado, probado, analizado con Lint y revisado en emulador. El build iOS requiere
macOS/Xcode y permanece como verificación pendiente de plataforma.

### Fase 2 — Autenticación + MFA: completada con validación iOS diferida

Entrega Auth por email, verificación, recuperación protegida por MFA, TOTP múltiple, sesión
segura por plataforma, deep links, RLS AAL2, grants mínimos, pruebas pgTAP e integración
real contra Supabase local. La implementación común participa en el target Android; el
shell y almacenamiento iOS están implementados pero no compilados en Windows.

### Fase 3 — Criptografía: no iniciada

Debe implementar Argon2id, HKDF, AEAD, DEK, envolturas de contraseña y Recovery Key,
almacenamiento seguro de claves, vectores conocidos y pruebas de corrupción/rotación. Las
columnas de base de datos existen como frontera de autorización, pero no equivalen a esta
funcionalidad.

### Fase 4 — Vault: no iniciada

Debe entregar el onboarding del vault y CRUD local/remoto de elementos cifrados, búsqueda
local, favoritos, categorías, papelera y sincronización de ciphertext.

### Fase 5 — Generador: no iniciada

Debe entregar generación mediante CSPRNG, presets, cálculo de entropía y pruebas de límites
y distribución razonable.

### Fase 6 — Seguridad móvil: no iniciada

Debe entregar bloqueo por inactividad, biometría, protección de pantallas sensibles,
portapapeles temporal y tratamiento de dispositivos comprometidos.

### Fase 7 — Emails y auditoría: no iniciada

Debe entregar eventos de seguridad de servidor, outbox, plantillas sin datos del vault y
notificaciones transaccionales.

### Fase 8 — Calidad: no iniciada

Debe completar accesibilidad, rendimiento, pruebas de extremo a extremo, revisión de
dependencias y una auditoría de seguridad previa a producción.

### Fase 9 — Publicación: no iniciada

Debe cerrar privacidad, metadatos de tiendas, builds firmados, canales de release,
monitorización y procedimiento de respuesta a incidentes.

## Límites que no deben confundirse con entregas

- Las tablas `vaults`, `vault_key_envelopes` y `vault_items` ya existen para validar RLS,
  pero la app todavía no cifra, crea ni muestra secretos reales.
- La pantalla autenticada confirma identidad AAL2; no es aún la interfaz del vault.
- La Recovery Key está diseñada, pero no generada ni almacenada por el código actual.
- La biometría y el bloqueo automático están diseñados, no implementados.
- `security_events` recibe el evento de creación de cuenta, pero el subsistema completo de
  auditoría y emails pertenece a la Fase 7.
- El código iOS existe, pero su build y ejecución no se han validado por falta de
  macOS/Xcode.

## Próximo punto de entrada

La siguiente fase correcta es la **Fase 3 — Criptografía**. Antes de comenzar conviene
convertir sus decisiones pendientes en criterios verificables: biblioteca nativa concreta,
parámetros Argon2id medidos en dispositivos objetivo, formato versionado de las envolturas,
representación de Recovery Key y estrategia de pruebas cruzadas Android/iOS.
