# Arquitectura y formato criptográfico v1

## Proveedores

- Android: JCA para CSPRNG, SHA-256 y AES-GCM; Bouncy Castle 1.85.2 para Argon2id y HKDF.
- iOS: `cryptography-kotlin` 0.6.0 sobre el proveedor óptimo de Apple para CSPRNG,
  SHA-256, HKDF y AES-GCM; binding libsodium 0.9.5 únicamente para Argon2id.
- Código común: construcción del protocolo, validación, AAD, Recovery Key, rotaciones y
  ciclo de vida lógico de la DEK.

Las versiones están fijadas en el catálogo Gradle. Ninguna primitiva criptográfica se
implementa manualmente.

## Derivación desde contraseña maestra

La contraseña se conserva exactamente como la introduce el usuario: no se recorta ni se
normaliza. Se valida un mínimo de 12 caracteres y un máximo de 1024 bytes UTF-8.

El binding iOS seleccionado acepta `String` y calcula internamente una longitud de
caracteres, que sería incorrecta para UTF-8 no ASCII. Para obtener el mismo byte stream en
ambas plataformas, el protocolo `argon2id-v1` transforma primero los bytes UTF-8 exactos a
su representación hexadecimal ASCII en minúsculas. Esa representación —no el texto
original— es la entrada Argon2id.

Parámetros iniciales:

```text
algoritmo: Argon2id v1.3
memoria:   65536 KiB
tiempo:    3 iteraciones
lanes:     1
salt:      16 bytes aleatorios
salida:    32 bytes
```

El perfil se acepta de forma exacta al abrir una envoltura v1. Así, un servidor o registro
manipulado no puede pedir una asignación Argon2id arbitrariamente grande. El paralelismo 1
reemplaza el valor provisional 4 de Fase 0: la API `crypto_pwhash` de libsodium fija una sola
lane. Cambiar cualquier parámetro exige una nueva versión de protocolo, no una reducción o
elevación silenciosa.

## Envolturas y cifrado de elementos

Cada vault genera una DEK aleatoria de 32 bytes. La contraseña maestra deriva una KEK con
Argon2id; la Recovery Key deriva otra KEK con HKDF-SHA-256. Cada KEK cifra una copia
independiente de la DEK con AES-256-GCM.

```text
AAD envoltura:
mykeys:dek-wrap:v1|<vault UUID>|<envelope UUID>|<wrapper_type>|<kdf_algorithm>

AAD elemento:
mykeys:item:v1|<vault UUID>|<item UUID>|<payload_schema_version>

HKDF info de recuperación:
mykeys:recovery-kek:v1|<vault UUID>|<envelope UUID>
```

Los identificadores aceptados son UUID canónicos. La versión, algoritmo, tipo de
envoltura, parámetros KDF, tamaño de nonce y tag se validan antes del descifrado. Un fallo
de autenticación no diferencia contraseña equivocada, clave equivocada o datos corruptos.

## Recovery Key RK1

RK1 codifica 33 bytes: 32 bytes de CSPRNG y el primer byte de SHA-256 sobre esa semilla.
Los 264 bits resultantes forman 24 índices de 11 bits.

El diccionario fijo de 2048 símbolos se genera combinando 32 inicios, 8 núcleos y 8 finales.
Todos los símbolos tienen cinco letras ASCII. Este formato es propio de My Keys y **no es
BIP-39**; la versión RK1 debe conservarse para interoperabilidad futura.

La frase solo se devuelve al flujo de alta o rotación para mostrarla una vez. El servidor
recibe únicamente la envoltura derivada de ella.

## Memoria y ejecución

`UnlockedVaultKey` copia la DEK, limita el acceso al módulo criptográfico y sobrescribe su
buffer al destruirse. También se limpian semillas, KEK y representaciones temporales que
están bajo control de la aplicación. Kotlin/Native, JVM, GC y `String` impiden prometer un
borrado físico total, por lo que la garantía es `best effort`.

Argon2id es síncrono en esta frontera de bajo nivel. Los casos de uso deben ejecutarlo fuera
del hilo principal y mantener una única derivación activa por desbloqueo para evitar presión
de memoria.
