# Proposal

## Why

El Issue #27 (HU-01) aborda H-AUTH-05 y H-AUTH-08: hoy CORS acepta orígenes no incluidos en una allowlist y cuentas demo preexistentes podrían seguir autenticándose. El solicitante aprobó `https://www.viabcp.com` como único origen CORS de producción y las cuentas semilla `student` y `trainer` como demos que no deben autenticar en producción.

## What Changes

**Dentro del alcance**

- Definir comportamiento verificable para la firma del JWT, los claims y la ausencia de emisión cuando falta o no es válida la clave, preservando el comportamiento documentado.
- Impedir en producción que las cuentas demo preexistentes `student` y `trainer` obtengan JWT, preservando el login de cuentas legítimas.
- Permitir preflight de producción únicamente desde el origen exacto `https://www.viabcp.com`.
- Exigir pruebas observables para los casos anteriores y evidencia para actualizar H-AUTH-05 y H-AUTH-08.

**Comportamiento que debe preservarse**

- `POST /auth/login` conserva su request, el formato de respuesta exitosa y el JWT RS256 con claims `sub`, `role` y `exp` descritos en `docs/ENDPOINTS.md`.
- Se conservan los resultados documentados para campos requeridos inválidos (`400`), credenciales inválidas (`401`) y el error interno caracterizado (`500`). Las cuentas legítimas de control mantienen el login exitoso.
- La ubicación de la clave sigue siendo externa; la configuración ausente o inválida no debe permitir emitir tokens. La documentación actual describe que el servicio no inicia en esos casos.
- La ruta `/cas/*` no forma parte del origen enviado por el navegador en el header `Origin`; CORS se limita al host aprobado `https://www.viabcp.com`.

**Fuera del alcance**

- Restringir CORS por path: `/cas/*` es una ruta del cliente y no puede expresarse como origen CORS.
- Borrar o modificar filas persistidas; el bloqueo ocurre durante el login.
- Cambiar endpoints, payloads o componentes fuera de `auth/`, o definir ahora el diseño técnico y los pasos de implementación.

La restricción de CORS y el bloqueo de las cuentas semilla se aplican solo al perfil de producción; dev/test conserva el comportamiento actual de las cuentas demo.

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