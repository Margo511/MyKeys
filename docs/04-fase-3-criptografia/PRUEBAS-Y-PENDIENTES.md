# Pruebas y pendientes de plataforma

## Cobertura incorporada

Los tests comunes se ejecutan contra el proveedor real de cada plataforma para estos
vectores:

- HKDF-SHA-256: RFC 5869, caso de prueba 1.
- AES-256-GCM: vector NIST con clave, nonce y bloque en cero.
- Argon2id: vector de referencia independiente con `m=12288`, `t=3`, `p=1` y 32 bytes.

La construcción de protocolo tiene además pruebas deterministas para:

- apertura de la misma DEK desde dos envolturas independientes;
- contraseña maestra incorrecta;
- corrupción de ciphertext y sustitución del UUID incluido en AAD;
- rotación de contraseña maestra;
- rotación e invalidación de Recovery Key;
- palabra desconocida y checksum incorrecto;
- nonce nuevo por operación y rechazo de parámetros KDF remotos no aprobados;
- rechazo del uso de una DEK destruida.

## Gate reproducible

Desde un terminal normal de Windows:

```powershell
Set-Location mobile
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
```

En macOS debe añadirse la compilación y ejecución de los tests de los targets iOS y una
prueba de interoperabilidad que descifre en iOS un fixture generado por Android y viceversa.

## Resultado en este entorno

No se registra el gate como verde. Gradle no ha podido crear su conexión loopback interna
en el sandbox de Windows (`Unable to establish loopback connection`), incluso con JDK 21,
ruta ASCII, caché aislada y modo sin daemon. Es la misma limitación ambiental documentada
en fases anteriores; no es evidencia de éxito ni de fallo del código.

## Pendientes antes de completar la fase

- Resolver cualquier error de compilación/API que revele el primer gate Android real.
- Validar inicialización y firmas del binding libsodium en Kotlin/Native/iOS.
- Ejecutar todos los vectores en Android e iOS y conservar evidencia de resultados.
- Medir Argon2id en el Android mínimo y el iPhone mínimo soportados.
- Verificar nonces distintos en ejecuciones reales y fixtures cruzados.
- Revisar dependencias y binarios nativos; el wrapper ionspin 0.9.5 declara explícitamente
  que no debe usarse en producción sin revisión comunitaria.
- Obtener revisión criptográfica independiente de protocolo, AAD, formato RK1, manejo de
  errores y ciclo de vida de claves.

Fuentes primarias: [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106.html),
[RFC 5869](https://www.rfc-editor.org/rfc/rfc5869.html),
[NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final),
[cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin),
[kotlin-multiplatform-libsodium](https://github.com/ionspin/kotlin-multiplatform-libsodium)
y [libsodium `crypto_pwhash`](https://doc.libsodium.org/password_hashing/default_phf).
