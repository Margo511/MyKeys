# Fase 2 — Pruebas y operación local

## Requisitos

- Node.js 22.13.0 o posterior compatible.
- Dependencias instaladas con `npm ci`.
- JDK 21 y Android SDK/API 36.
- Docker y Supabase CLI 2.111.0.
- En Windows: WSL 2 con Ubuntu 24.04 por defecto y Docker operativo dentro de la distro.

La preparación detallada de la máquina está en
[`../02-fase-1-base/ENTORNO-Y-VERIFICACION.md`](../02-fase-1-base/ENTORNO-Y-VERIFICACION.md).

## Puerta completa local

Desde la raíz:

```powershell
npm ci
npm run check
npm run doctor
npm run verify:bundle
npm run supabase:start
npm run supabase:reset
npm run supabase:test
npm run supabase:lint
npm run supabase:test:auth
$env:MY_KEYS_SUPABASE_PUBLISHABLE_KEY = npm run --silent supabase:publishable-key
Set-Location mobile
.\gradlew.bat :androidApp:assembleDebug :shared:testAndroidHostTest :androidApp:lintDebug
Set-Location ..
npm run scan:android-config
```

Al terminar:

```powershell
npm run supabase:stop
```

No se debe copiar a documentación, issues o logs el resultado completo de
`supabase status -o env`. El wrapper expone por separado solo la publishable key cuando el
build local la necesita.

## Pruebas KMP

```powershell
Set-Location mobile
.\gradlew.bat :shared:testAndroidHostTest
```

La suite de cierre contiene 13 tests:

- validaciones de email, contraseña y TOTP;
- normalización de email;
- protección de eliminación del último factor;
- transiciones del presenter para restauración, registro, desafío y errores;
- round-trip y corrupción del blob de sesión, y rechazo de configuración cliente insegura;
- caso de uso base de Fase 1.

Los dobles implementan `SessionPort`, por lo que estas pruebas no arrancan Supabase ni
dependen de SDKs de plataforma.

## Pruebas pgTAP

```powershell
npm run supabase:start
npm run supabase:test
```

Las 47 aserciones verifican:

- existencia de seis tablas privadas;
- RLS habilitada y forzada en las seis;
- grants mínimos y protección de columnas;
- función y trigger del último TOTP;
- creación automática de perfil/preferencias/evento;
- rechazo del último factor y aceptación cuando queda respaldo;
- denegación a `anon` y AAL1;
- acceso propio AAL2;
- aislamiento entre usuario A y usuario B;
- rechazo de owner, vault o UUID manipulados;
- cobertura AAL2 explícita en toda tabla privada.

El test abre una transacción y hace `rollback`, por lo que no deja los usuarios de fixture.

## Lint de base de datos

```powershell
npm run supabase:lint
```

Ejecuta `supabase db lint --local --schema public --level warning`. El resultado de cierre
no contiene errores del esquema `public`.

## Integración Auth/Mailpit/TOTP/RLS

```powershell
npm run supabase:test:auth
```

El script usa cuentas efímeras y comprueba contra servicios reales locales:

1. `anon` no consulta perfiles;
2. registro sin sesión antes de confirmar email;
3. recepción del email en Mailpit y callback `mykeys://auth/callback`;
4. sesión confirmada AAL1 sin acceso privado;
5. alta y verificación TOTP con cálculo RFC compatible;
6. sesión AAL2 con acceso al perfil propio;
7. rechazo al eliminar el último TOTP;
8. alta de respaldo y eliminación válida del factor anterior;
9. aislamiento de dos usuarios y sus vaults;
10. rechazo de propietario, pertenencia y UUID malformados;
11. recovery password que sigue bloqueado en AAL1;
12. cambio de contraseña solo tras MFA;
13. login posterior que vuelve a exigir elevación AAL2.

La ejecución satisfactoria termina con:

```text
Integracion local registro/email/TOTP/AAL2/RLS: OK
```

## Build, Lint y escaneo Android

```powershell
$env:MY_KEYS_SUPABASE_PUBLISHABLE_KEY = npm run --silent supabase:publishable-key
Set-Location mobile
.\gradlew.bat :androidApp:assembleDebug :androidApp:lintDebug
Set-Location ..
npm run scan:android-config
```

El build valida el formato de la credencial y el escaneo revisa el `BuildConfig` generado.
Los warnings de Kotlin y Android Lint se tratan como errores. La excepción HTTP de debug se
limita al backend local; release exige HTTPS.

## Pipeline Node/Expo de referencia

```powershell
npm run check
npm run doctor
npm run verify:bundle
```

Aunque Expo no sea el cliente principal, se conserva verde para que el prototipo siga siendo
una referencia confiable y no acumule secretos o roturas silenciosas.

## CI

`.github/workflows/quality.yml` define tres jobs sin permisos de escritura:

- `expo-reference`: instalación limpia, calidad Node, Expo Doctor y bundle/secret scan;
- `android`: JDK 21, SDK 36, build, tests KMP, Android Lint y scan de BuildConfig;
- `supabase-local`: arranque, pgTAP, lint SQL e integración Auth real.

Los jobs tienen timeouts de 15–20 minutos y se ejecutan en push a `main`/`master` y en pull
requests.

## Diagnóstico rápido

### Supabase no arranca

1. ejecutar `wsl --status`;
2. comprobar que la distro configurada existe;
3. revisar `systemctl status docker` dentro de WSL;
4. volver a ejecutar `npm run supabase:start`;
5. usar `npm run supabase:status` para confirmar URLs saneadas.

### Android no alcanza Supabase

- En emulador se debe usar `10.0.2.2`, no `127.0.0.1`.
- Confirmar que la API local responde en el host y que la publishable key se pasó al build.
- No sustituir la publishable key por una secret/service-role key.

### Falla un email de integración

- Confirmar que Mailpit responde en `127.0.0.1:54324`.
- Ejecutar un reset local para reaplicar `supabase/config.toml`.
- El script espera hasta 20 segundos y busca el tipo exacto `signup` o `recovery`.

### Falla un código TOTP

- Sincronizar el reloj del host/VM.
- La integración prueba el intervalo actual y los dos intervalos adyacentes; un fallo
  persistente suele indicar desajuste significativo o reinicio incompleto de Auth.

## Evidencia de cierre

El 9 de septiembre de 2026 se registró: pgTAP 47/47, lint SQL verde, integración completa
verde, tests KMP 13/13, APK debug, Android Lint y escaneos de fuente/BuildConfig verdes. iOS
permanece explícitamente fuera de la evidencia ejecutada.
