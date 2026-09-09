# Documentación de My Keys

La documentación está organizada por fases y describe el estado comprobable del repositorio.
Una fase solo figura como completada cuando su puerta de salida está satisfecha en el entorno
disponible; las verificaciones que requieren otra plataforma se registran como diferidas, no
como ejecutadas.

## Estado del proyecto

- **Fase 0 — Diseño técnico:** completada y aprobada el 8 de septiembre de 2026.
- **Fase 1 — Base:** completada para el objetivo Windows/Android el 9 de septiembre de 2026. El build iOS sigue pendiente de macOS/Xcode.
- **Fase 2 — Autenticación + MFA:** completada para el objetivo Windows/Android el 9 de
  septiembre de 2026. El build iOS sigue pendiente de macOS/Xcode.
- **Fases 3 a 9:** no iniciadas. Su alcance permanece en el roadmap de Fase 0 y no se
  documenta como funcionalidad entregada.

Consulta [Estado y roadmap](ESTADO-Y-ROADMAP.md) para distinguir lo implementado, lo
validado y lo pendiente.

## Documentos por fase

### 01 — Fase 0: diseño técnico

- [Documento técnico completo](01-fase-0/README.md)
- [Vista interactiva autónoma](01-fase-0/vista-interactiva.html)

El diseño de Fase 0 es la especificación de producto y seguridad. La enmienda principal es
el cambio del cliente de React Native/Expo a Kotlin Multiplatform con Compose
Multiplatform, registrado al comenzar la Fase 1.

### 02 — Fase 1: base

- [Resumen, entregables y puerta de salida](02-fase-1-base/README.md)
- [Decisión de arquitectura KMP](02-fase-1-base/KMP-ARCHITECTURE.md)
- [Entorno, ejecución y verificación](02-fase-1-base/ENTORNO-Y-VERIFICACION.md)
- [Inventario de implementación](02-fase-1-base/INVENTARIO-DE-IMPLEMENTACION.md)
- [Evidencia visual](02-fase-1-base/assets/)

### 03 — Fase 2: autenticación y MFA

- [Resumen, decisiones y puerta de salida](03-fase-2-autenticacion/README.md)
- [Arquitectura y flujos de identidad](03-fase-2-autenticacion/ARQUITECTURA-Y-FLUJOS.md)
- [Base de datos, RLS y modelo de seguridad](03-fase-2-autenticacion/BASE-DE-DATOS-Y-SEGURIDAD.md)
- [Pruebas y operación local](03-fase-2-autenticacion/PRUEBAS-Y-OPERACION.md)
- [Trazabilidad de requisitos](03-fase-2-autenticacion/TRAZABILIDAD.md)

## Convención de carpetas

```text
docs/
├─ README.md
├─ ESTADO-Y-ROADMAP.md
├─ 01-fase-0/
├─ 02-fase-1-base/
├─ 03-fase-2-autenticacion/
├─ 04-fase-3-criptografia/       # futura
├─ 05-fase-4-vault/              # futura
├─ 06-fase-5-generador/          # futura
├─ 07-fase-6-seguridad/          # futura
├─ 08-fase-7-emails/             # futura
├─ 09-fase-8-calidad/            # futura
└─ 10-fase-9-publicacion/        # futura
```

Las carpetas futuras se crearán cuando empiece la fase correspondiente. Así se evita
confundir planificación con software entregado.

## Criterios documentales

- Los estados se basan en código, configuración y pruebas presentes en el repositorio.
- Los valores sensibles reales no se incluyen en ningún documento.
- Los comandos parten de la raíz del proyecto salvo que se indique otro directorio.
- Las cifras de pruebas corresponden a la evidencia final del 9 de septiembre de 2026.
- Expo se documenta como prototipo de referencia; `mobile/` es el cliente principal.
- iOS se describe por su implementación compartida y shell nativo, pero no se afirma un
  build que no se haya ejecutado en macOS/Xcode.

## Mantenimiento

Al cerrar una fase nueva se debe:

1. crear su carpeta numerada y su `README.md`;
2. registrar alcance, exclusiones, decisiones, riesgos y puerta de salida;
3. enlazar código, migraciones, pruebas y comandos de reproducción;
4. guardar evidencias visuales no sensibles cuando aporten valor;
5. actualizar este índice, [Estado y roadmap](ESTADO-Y-ROADMAP.md) y el `README.md` raíz.
