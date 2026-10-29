# Proposal

## Why

El Issue #27 (HU-01) aborda H-AUTH-05 y H-AUTH-08: hoy CORS acepta orígenes no incluidos en una allowlist y cuentas demo preexistentes podrían seguir autenticándose. Resolver ambos riesgos importa para evitar acceso cross-origin no aprobado y emisión de tokens a cuentas demo sin afectar a usuarios legítimos; la allowlist y la regla de identificación todavía requieren aprobación.

## What Changes

**Dentro del alcance**

- Definir comportamiento verificable para la firma del JWT, los claims y la ausencia de emisión cuando falta o no es válida la clave, preservando el comportamiento documentado.
- Definir el resultado del login para cuentas demo preexistentes una vez aprobada una regla que las distinga de cuentas legítimas.
- Definir el resultado de preflight para orígenes incluidos y no incluidos una vez aprobada la allowlist de producción.
- Exigir pruebas observables para los casos anteriores y evidencia para actualizar H-AUTH-05 y H-AUTH-08.

**Comportamiento que debe preservarse**

- `POST /auth/login` conserva su request, el formato de respuesta exitosa y el JWT RS256 con claims `sub`, `role` y `exp` descritos en `docs/ENDPOINTS.md`.
- Se conservan los resultados documentados para campos requeridos inválidos (`400`), credenciales inválidas (`401`) y el error interno caracterizado (`500`). Las cuentas legítimas de control mantienen el login exitoso.
- La ubicación de la clave sigue siendo externa; la configuración ausente o inválida no debe permitir emitir tokens. La documentación actual describe que el servicio no inicia en esos casos.
- La elegibilidad de orígenes CORS cambia únicamente cuando exista una allowlist aprobada; no se inventan dominios ni se amplía el alcance a otros componentes.

**Fuera del alcance**

- Elegir o inventar la allowlist de producción o la regla para identificar cuentas demo.
- Borrar, desactivar o modificar cuentas persistidas antes de aprobar esa regla.
- Cambiar endpoints, payloads o componentes fuera de `auth/`, o definir ahora el diseño técnico y los pasos de implementación.

Las decisiones pendientes son prerequisitos para completar los escenarios de H-AUTH-05 y H-AUTH-08; esta propuesta no las da por aprobadas.

## Capabilities

### New Capabilities

- `jwt-signing-contract`: preservación del contrato JWT y comportamiento verificable ante configuración de firma ausente o inválida.
- `persistent-demo-account-access`: elegibilidad de cuentas demo persistidas para autenticarse, condicionada a una regla aprobada y con protección explícita de cuentas legítimas.
- `cors-origin-allowlist`: autorización observable de preflight según la allowlist de producción aprobada.

### Modified Capabilities

Ninguna. El inventario OpenSpec actual no contiene specs existentes.

## Impact

- Componente `auth/`: emisión JWT, configuración de firma, login y preflight CORS.
- Pruebas del componente `auth/` para firma/claims, configuración, cuentas demo frente a una cuenta legítima y orígenes incluidos/no incluidos.
- Documentación general: solo las secciones de auth pertinentes en `docs/ENDPOINTS.md` y los estados H-AUTH-05/H-AUTH-08 en `HALLAZGOS.md`, preservando las secciones de los demás componentes.
- Issue de referencia: [#27](https://github.com/user-bcp-student27/ai-labs/issues/27).