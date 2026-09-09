# Fase 3 — Criptografía

**Estado:** en curso desde el 9 de septiembre de 2026.

La primera entrega introduce el núcleo criptográfico multiplataforma del vault sin conectarlo
todavía al onboarding ni a Supabase. El objetivo de esta separación es poder validar el
protocolo y la compatibilidad Android/iOS antes de persistir un solo secreto real.

## Implementado

- Frontera común `VaultCryptoPrimitives` con implementaciones Android e iOS.
- DEK aleatoria de 256 bits por vault.
- Envolturas independientes de la misma DEK para contraseña maestra y Recovery Key.
- Argon2id v1 con parámetros almacenables y perfil aceptado exacto de 64 MiB, 3 iteraciones,
  paralelismo 1 y salida de 32 bytes; los metadatos remotos no pueden elevar el coste y
  provocar una asignación arbitraria.
- HKDF-SHA-256 con `salt` aleatorio e `info` ligado al vault y a la envoltura.
- AES-256-GCM con nonce aleatorio de 96 bits, tag de 128 bits y AAD contextual.
- Recovery Key RK1 de 24 palabras, 256 bits aleatorios y checksum de 8 bits.
- Rotación de contraseña maestra y Recovery Key sin volver a cifrar los elementos.
- Errores cerrados para contraseña incorrecta, corrupción y sustitución de contexto.
- Contenedores de clave destruibles y limpieza `best effort` de buffers temporales.
- Tests comunes de vectores HKDF, AES-GCM y Argon2id, además de corrupción, clave
  incorrecta, checksum, separación de envolturas y rotaciones.

## Fuera de esta entrega

- Onboarding, persistencia de envolturas y CRUD/sincronización pertenecen a Fase 4.
- Custodia biométrica de una clave de dispositivo, auto-lock y ciclo de vida pertenecen a
  Fase 6. La DEK de esta entrega solo vive en memoria.
- No se guarda contraseña maestra, Recovery Key, KEK, DEK ni plaintext en Supabase o en el
  almacenamiento local.

## Puerta de salida

La fase **no está completada**. Para cerrarla faltan:

1. ejecutar el gate Android y los vectores con las dependencias descargadas en un terminal
   sin la restricción de loopback del sandbox;
2. compilar y ejecutar los mismos tests en iOS real/simulador con macOS y Xcode;
3. comprobar ciphertext cruzado Android ↔ iOS;
4. medir Argon2id en los dispositivos mínimos objetivo y confirmar el rango aproximado de
   750–1500 ms sin OOM;
5. sustituir o aprobar explícitamente el binding iOS de libsodium, cuyo propio mantenedor
   advierte que requiere revisión comunitaria;
6. realizar la revisión criptográfica independiente exigida por el roadmap.

Consulta [Arquitectura y formato](ARQUITECTURA-Y-FORMATO.md) y
[Pruebas y pendientes](PRUEBAS-Y-PENDIENTES.md).
