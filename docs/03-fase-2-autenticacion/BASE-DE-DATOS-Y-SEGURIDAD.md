# Fase 2 — Base de datos, RLS y seguridad

## Principio de autorización

Toda tabla de `public` creada por My Keys se considera privada. El rol `authenticated` solo
obtiene una operación si se cumplen dos condiciones simultáneas:

1. el JWT declara `aal2`;
2. `auth.uid()` es propietario directo de la fila o propietario del vault relacionado.

La primera condición se implementa con políticas restrictivas; la segunda, con políticas
permisivas por operación. Una política restrictiva nunca concede acceso por sí sola.

## Migración canónica

La definición completa está en
`supabase/migrations/20260909153000_auth_mfa_rls.sql`. Un reset local debe poder reconstruir
el esquema sin pasos manuales.

## Tablas

### `profiles`

Perfil mínimo asociado uno a uno con `auth.users`. Permite leer la fila propia y actualizar
solo `display_name`/`notifications_enabled` con AAL2. La fila se crea desde un trigger de
alta de cuenta.

### `user_preferences`

Preferencias no sensibles de la cuenta: tema, locale, bloqueo automático y limpieza de
portapapeles. Solo admite lectura propia y actualización de columnas permitidas con AAL2.

### `vaults`

Contenedor lógico propiedad de un usuario. Incluye columnas para metadata cifrada, nonce y
versión criptográfica. En Fase 2 se usa para probar propiedad y aislamiento; la app todavía
no crea el vault de producto ni cifra su metadata.

### `vault_key_envelopes`

Reserva el formato de sobres de DEK: tipo de wrapper, KDF, parámetros, salt, nonce,
ciphertext y versiones. RLS deriva la propiedad mediante `vaults`. La creación criptográfica
real pertenece a Fase 3.

### `vault_items`

Reserva el almacenamiento de payload cifrado, nonce, algoritmo, versiones, revisión y
papelera. No contiene columnas en claro para nombre, usuario, contraseña, URL, notas,
categoría o favorito. Fase 2 verifica que un usuario no pueda leer, crear, mover, actualizar
o borrar elementos de otro vault.

### `security_events`

Eventos genéricos sin contenido del vault. El cliente solo tiene `SELECT` sobre eventos
propios; no tiene `INSERT`, por lo que no puede falsificar auditoría. El subsistema completo
de outbox, emails y eventos pertenece a Fase 7.

## Creación automática de cuenta

`handle_new_user()` se ejecuta después de insertar en `auth.users` y crea:

- una fila en `profiles`;
- una fila en `user_preferences`;
- un evento crítico `ACCOUNT_CREATED`.

Es `SECURITY DEFINER`, fija `search_path` vacío y usa nombres cualificados. Su permiso de
ejecución se revoca a `PUBLIC`, `anon` y `authenticated`; solo el trigger debe invocarla.

## Protección del último TOTP

`prevent_last_verified_totp_factor()` se ejecuta antes de borrar un factor o cambiar su
estado. Para una cuenta existente, rechaza con SQLSTATE `23514` y el mensaje
`MYKEYS_LAST_VERIFIED_TOTP_FACTOR` si desaparecería el último TOTP verificado.

La función permite el cascade al borrar `auth.users`, porque entonces la cuenta ya no
existe. También permite eliminar uno de varios factores verificados. Esta defensa duplica
intencionadamente la regla del dominio para cubrir clientes modificados y llamadas directas
al endpoint Auth.

## Matriz de acceso

### Rol anónimo

- Tiene `USAGE` del esquema necesario para resolver endpoints.
- No tiene grants sobre tablas, secuencias ni funciones privadas.
- Consultar perfiles o datos del vault falla por privilegios.

### Usuario autenticado AAL1

- Puede completar operaciones de Supabase Auth.
- Aunque posea grants mínimos de tabla, las políticas restrictivas producen cero filas o
  deniegan escrituras.
- No puede consultar ni crear datos privados propios antes de MFA.

### Usuario autenticado AAL2

- Puede leer su perfil/preferencias y actualizar solo las columnas permitidas.
- Puede operar únicamente sobre su vault, sobres e items según los grants definidos.
- Puede leer únicamente sus eventos de seguridad.
- Los UUID conocidos de otro usuario no revelan filas ni permiten forjar relaciones.

## Grants de mínimo privilegio

La migración comienza revocando los defaults amplios para tablas, secuencias y funciones.
Después concede únicamente:

- `profiles`: `SELECT` y update de dos columnas de perfil;
- `user_preferences`: `SELECT` y update de cuatro preferencias;
- `vaults`: `SELECT`, `INSERT` y update solo de metadata cifrada/versiones;
- `vault_key_envelopes`: `SELECT` e `INSERT`;
- `vault_items`: `SELECT`, `INSERT`, `DELETE` y update solo de payload/estado permitido;
- `security_events`: `SELECT`.

No se concede update de propietarios ni de `vault_id`, y tampoco acceso a secuencias. Los
default privileges se revocan para que las tablas futuras no nazcan accidentalmente
expuestas.

## RLS por tabla

Las seis tablas tienen `ENABLE ROW LEVEL SECURITY` y `FORCE ROW LEVEL SECURITY`. Cada una
posee una política `*_require_aal2`. Las políticas de propiedad usan `auth.uid()` directamente
en perfil/preferencias/eventos y una subconsulta sobre `vaults` para sobres e items.

`WITH CHECK` acompaña inserciones y updates, de modo que una fila visible no pueda
reubicarse bajo otro propietario durante la escritura.

## Datos visibles para Supabase

En el diseño final el backend puede ver identidad de cuenta, UUID, propietario, timestamps,
estado de papelera, revisiones, algoritmos/versiones, tamaños aproximados y tipos genéricos
de evento. No debe recibir contenido descifrado, contraseña maestra, Recovery Key ni DEK.

En Fase 2 todavía no existe el cifrado cliente. Los blobs de prueba insertados por pgTAP y
la integración sirven para comprobar autorización; no representan secretos reales.

## Configuración Auth local

- callback canónico: `mykeys://auth/callback`;
- JWT con expiración de 900 segundos;
- signup por email habilitado;
- confirmación de email obligatoria;
- cambio de contraseña seguro habilitado;
- longitud mínima de contraseña: 12;
- TOTP habilitado para enrolamiento y verificación;
- límites locales de envío/intentos definidos en `supabase/config.toml`.

## Amenazas cubiertas

- Omisión visual de MFA: bloqueada por RLS AAL2.
- Acceso por UUID ajeno: bloqueado por políticas de propiedad.
- Cambio de propietario o pertenencia: bloqueado por grants de columna y `WITH CHECK`.
- Falsificación de eventos: el cliente carece de `INSERT`.
- Eliminación del último TOTP: trigger servidor y regla de dominio.
- Exposición de tokens en disco: almacenamiento seguro por plataforma y backups Android
  deshabilitados.
- Inclusión de credenciales servidor: validación de publishable key y escaneos de fuente y
  configuración generada.

## Riesgos residuales

- Un JWT AAL2 ya emitido conserva el claim hasta refrescarse o caducar.
- RLS protege confidencialidad entre usuarios, pero no evita borrado o rollback por un
  operador backend con privilegios administrativos.
- Timestamps, cantidad y tamaño de registros son metadatos visibles.
- La pérdida de todos los factores TOTP no tiene bypass en el MVP.
- La base reserva estructuras del vault antes de que exista la revisión criptográfica de
  Fase 3; cualquier cambio del formato criptográfico debe llegar mediante migración nueva,
  no reescribiendo silenciosamente la migración aplicada.

## Evidencia automatizada

`supabase/tests/database/auth_mfa_rls_test.sql` contiene 47 aserciones pgTAP que comprueban
tablas, RLS forzada, grants, triggers, último factor, matriz anon/AAL1/AAL2, aislamiento A/B,
UUID forjados y columnas protegidas. La integración real en
`scripts/test-supabase-auth-integration.mjs` repite los escenarios críticos mediante Auth,
Mailpit y la API REST.
