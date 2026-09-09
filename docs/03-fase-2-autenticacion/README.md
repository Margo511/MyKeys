# My Keys — Fase 2: autenticación y MFA

**Estado:** completada en Windows/Android; build iOS diferido  
**Inicio:** 9 de septiembre de 2026

## Objetivo

Entregar el ciclo de identidad de My Keys sobre el cliente principal Kotlin Multiplatform:
registro, confirmación de email, login, logout, recuperación de la contraseña de acceso,
restauración segura de sesión y MFA TOTP obligatorio. Ningún dato privado puede atravesar
la frontera de Supabase con una sesión anónima o AAL1; PostgreSQL y RLS, no la interfaz,
son la autoridad final.

Esta fase no implementa la criptografía ni el contenido del vault. Las tablas de vault que
aparecen en la migración establecen anticipadamente la frontera de autorización para probar
pertenencia y UUID, pero solo admitirán ciphertext cuando la Fase 3/4 las utilice.

## Alcance

- Auth por email y contraseña de acceso mediante Supabase Auth.
- Confirmación de email y restablecimiento de contraseña mediante deep links PKCE.
- Sesión restaurable con access/refresh tokens guardados únicamente en Keystore/Keychain.
- Alta, selección, desafío y verificación de uno o varios factores TOTP.
- Comprobación explícita de AAL y estados de sesión expirada/AAL insuficiente.
- Esquema PostgreSQL, grants mínimos y RLS restrictiva AAL2 más políticas de propiedad.
- Pruebas pgTAP de estructura, grants y matriz `anon`/AAL1/AAL2-A/AAL2-B.
- Tests de dominio y presentación con un `SessionPort` sin dependencias de proveedor.
- Pantallas Compose compartidas; Android sigue renderizando con Jetpack Compose.
- CI para Android/KMP, prototipo Expo conservado y Supabase local.

Fuera de alcance: contraseña maestra, Recovery Key del vault, DEK, cifrado del vault,
biometría, CRUD real de secretos, proyecto Supabase remoto y despliegue/publicación.

## Decisiones

### Fronteras y dependencias

- El dominio define `SessionPort`, modelos, errores y casos de uso; no importa Supabase,
  Ktor, Android, iOS ni Compose.
- El adaptador Supabase vive en infraestructura y traduce errores del proveedor a un
  catálogo pequeño, tipado y apto para mostrar. No registra respuestas crudas.
- La interfaz usa flujo unidireccional: evento -> presenter/store -> caso de uso -> puerto ->
  estado inmutable.
- El cliente Kotlin de Supabase se fija a `supabase-kt 3.6.0`, versión estable publicada en
  Maven Central. Se usan solo los módulos Auth necesarios y motores Ktor de plataforma.
- El URI público de callback es `mykeys://auth/callback`. Android e iOS registran exactamente
  ese esquema/host y rechazan callbacks que no coincidan.

### Sesión

- El SDK no puede persistir tokens en preferencias en claro. La carga automática se
  desactiva y su guardado se redirige al `SecureSessionManager` respaldado por
  `SecureAuthStorage`; nunca se usa el almacenamiento en claro del SDK.
- Android cifra el blob de sesión con AES-GCM usando una clave no exportable de Android
  Keystore; solo el ciphertext, IV y versión se guardan en `SharedPreferences` privadas.
- iOS usa un elemento genérico de Keychain accesible únicamente después del primer
  desbloqueo y solo en este dispositivo (`AfterFirstUnlockThisDeviceOnly`).
- Un blob ilegible, incompleto o caducado se elimina y produce estado anónimo; nunca se
  conserva una sesión parcialmente restaurada.
- Logout intenta revocar la sesión en Supabase y siempre elimina la copia local.

### Política MFA obligatoria

- MFA/TOTP es obligatorio para acceder a cualquier dato del usuario.
- La Recovery Key del vault nunca recupera MFA ni permite omitirlo.
- Se admiten varios factores TOTP y la UI recomienda registrar un factor de respaldo en un
  dispositivo o aplicación distintos.
- My Keys no permite eliminar el último factor verificado. La eliminación exige AAL2 y una
  lista fresca de factores; además, un trigger en `auth.mfa_factors` rechaza el borrado o
  desverificación del último TOTP aunque se invoque directamente el endpoint. Tras eliminar
  uno de varios factores se fuerza refresh para que el JWT refleje el nivel efectivo.
- Si se pierden todos los factores no existe bypass de producto y el vault no se puede
  descifrar. Soporte, email y Recovery Key no sustituyen el segundo factor.
- Los recovery codes de Supabase continúan detrás de
  `auth.experimental.recoveryCodes`; por tanto se excluyen del MVP. Se reevaluarán solo
  cuando la API sea estable en los SDK usados y exista una migración de política revisada.

Referencias oficiales:

- <https://supabase.com/docs/guides/auth/auth-mfa>
- <https://supabase.com/docs/guides/auth/auth-mfa/totp>
- <https://supabase.com/docs/reference/javascript/auth-mfa-recovery-codes-generate>
- <https://supabase.com/docs/guides/database/postgres/row-level-security>

### Autorización de base de datos

- Se revocan privilegios de `PUBLIC`, `anon` y `authenticated` antes de conceder la lista
  mínima por tabla.
- Todas las tablas privadas tienen RLS habilitada y forzada.
- Cada operación necesita simultáneamente una política permisiva de propiedad y una política
  restrictiva cuyo JWT tenga `aal = aal2`.
- `auth.uid()` debe coincidir con el propietario directo o con el propietario del vault.
- `WITH CHECK` impide cambiar propietario, `user_id`, `vault_id` o pertenencia mediante
  inserciones/actualizaciones manipuladas.
- La app no inserta eventos de seguridad ni recibe privilegios de secuencia. Las futuras
  funciones `SECURITY DEFINER` revocarán `EXECUTE` público, fijarán `search_path` vacío y
  validarán UID/AAL explícitamente.
- No se incorpora `service_role`, secret key ni secreto servidor a código, configuración de
  cliente, bundle o logs.

## Modelo de amenazas de la fase

| Amenaza                                         | Control y prueba                                                                                           |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Cliente manipulado omite la pantalla MFA        | RLS restrictiva rechaza JWT AAL1 en toda tabla privada.                                                    |
| Usuario A prueba UUID de B                      | Políticas de propietario y pertenencia devuelven cero filas o deniegan escritura.                          |
| `owner_user_id`, `user_id` o `vault_id` forjado | `WITH CHECK`, claves foráneas y ausencia de columnas redundantes.                                          |
| Robo de tokens desde disco                      | Keystore/Keychain; nunca preferencias en claro, backups deshabilitados y borrado al fallar restauración.   |
| Filtración por logs o errores                   | Errores tipados/sanitizados; prohibido registrar tokens, contraseñas, TOTP, QR, secretos o cuerpos crudos. |
| Deep link inyectado o replay                    | Esquema/host allowlist, PKCE del SDK, estado del flujo y consumo único del callback.                       |
| Eliminación del último TOTP                     | Regla de dominio + trigger de base de datos + prueba pgTAP y prueba real del endpoint.                     |
| Pérdida de todos los TOTP                       | Sin bypass. Se recomienda segundo factor; Recovery Key del vault no participa.                             |
| Recovery codes inmaduros                        | API experimental excluida y sin dependencia de ella.                                                       |
| Cuenta enumerada por recuperación               | Respuesta genérica independientemente de que el email exista.                                              |

Riesgo residual conocido: un access token AAL2 ya emitido conserva ese claim hasta refrescar
o caducar. My Keys fuerza refresh tras gestionar factores y usa una expiración local corta de
15 minutos; acciones futuras destructivas validarán además sesión/reautenticación reciente en
servidor. El trigger permite el cascade al borrar la cuenta, pero no existe una ruta cliente
que pueda dejar una cuenta existente sin TOTP verificado.

La recuperación de contraseña tampoco es un bypass: para una cuenta con TOTP, el enlace de
recovery crea una sesión AAL1 y My Keys exige un desafío válido antes de llamar a
`updateUser`. La integración local comprueba después un nuevo login AAL1, ausencia de datos y
nueva elevación AAL2.

## Plan de implementación

1. Congelar este alcance y la política MFA.
2. Crear migración reproducible desde reset de esquema, funciones, grants y RLS.
3. Añadir pruebas pgTAP de estructura, AAL y aislamiento por propietario/pertenencia.
4. Ejecutar reset local, `supabase test db` y lint SQL; corregir toda desviación.
5. Añadir dominio Auth: modelos, `SessionPort`, validaciones, errores y casos de uso.
6. Añadir el puerto de almacenamiento seguro, adaptadores Android/iOS y Supabase aislado.
7. Implementar presenter/store UDF y todas las pantallas Compose de la fase.
8. Registrar deep links Android/iOS y validar callbacks.
9. Cubrir dominio, presentación, persistencia y adaptador con tests apropiados.
10. Actualizar CI, índices de documentación y ejecutar la puerta completa.

## Puerta de salida

- [x] Registro crea una cuenta y no concede datos privados antes de verificar email/MFA.
- [x] Confirmación de email vuelve a la app por deep link válido.
- [x] Login conduce a alta TOTP o selección/desafío según factores verificados.
- [x] Logout revoca/limpia sesión y recuperación permite cambiar la contraseña solo tras MFA.
- [x] Alta TOTP muestra QR local y secreto manual; el código correcto produce sesión AAL2.
- [x] Se pueden seleccionar varios factores y se recomienda uno de respaldo.
- [x] Dominio y base de datos impiden eliminar el último factor verificado.
- [x] Restauración de sesión usa Keystore/Keychain y maneja sesión expirada/corrupta.
- [x] `anon` y AAL1 no leen ni escriben ninguna tabla privada.
- [x] AAL2-A no lee ni modifica filas de B ni puede forjar UID, UUID o pertenencia.
- [x] Grants, RLS y default privileges están cubiertos por 47 aserciones pgTAP.
- [x] Trece tests KMP, integración Auth y RLS están verdes.
- [x] APK debug, Android Lint y tests KMP están verdes.
- [x] Escaneo de fuente y BuildConfig no encuentra secretos incorporados.
- [x] CI reproduce las comprobaciones locales disponibles en tres jobs separados.
- [x] Build iOS queda documentado como diferido hasta macOS/Xcode, sin afirmar validación.
- [x] La documentación describe exactamente el comportamiento entregado.

La fase solo se marcará terminada cuando todas las casillas aplicables estén verificadas con
evidencia. La evidencia local final del 9 de septiembre de 2026 es: migración reproducible,
pgTAP 47/47, lint `public` sin errores, integración Auth/Mailpit/TOTP/AAL2/reset password
verde, 13/13 tests KMP, APK debug y Android Lint verdes. iOS no se declara compilado.
