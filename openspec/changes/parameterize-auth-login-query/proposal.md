# Proposal

## Why

El Issue [#21](https://github.com/user-bcp-student27/ai-labs/issues/21) (H-AUTH-01, severidad alta) pide que el login de `auth/` no construya SQL concatenando `username` y `password`, porque un valor como `' OR '1'='1` permitiría iniciar sesión sin conocer la contraseña. Una versión anterior de `LegacyDatabase` tenía ese defecto.

La evidencia actual difiere del Issue: `findByCredentials` ya no existe; `LegacyDatabase.findByUsername` enlaza `username` con `PreparedStatement` y `AuthService` verifica la contraseña con PBKDF2 (`HALLAZGOS.md`, H-AUTH-01, estado "No encontrado"). Lo que falta es una garantía verificable: no hay pruebas que ejecuten un intento de inyección, así que el comportamiento seguro puede regresar sin ser detectado. Este cambio convierte el criterio del Issue en requisitos y escenarios observables.

## What Changes

**Dentro del alcance**

- Exigir que el login trate `username` y `password` como datos y nunca como parte del SQL.
- Exigir que un intento de inyección en `username` o `password` responda `401` y no devuelva `token`.
- Exigir una prueba de ese intento en `AuthResourceTest` y que `mvn test` pase desde `auth/`.
- Actualizar solo la fila H-AUTH-01 de `HALLAZGOS.md` con la evidencia de las pruebas, sin marcarla Resuelto sin revisión humana.

**Comportamiento que debe preservarse** (baseline: `docs/ENDPOINTS.md`, sección auth)

- `POST /auth/login` conserva su request (`username`, `password`, `client` opcional) y la respuesta `200` con `token`, `expiresIn`, `user` y `debug`.
- Se conservan los códigos `400` (campos requeridos), `401` (credenciales inválidas) y `500` (`IllegalStateException`), y el formato de `{"message": ...}`.
- El JWT sigue siendo RS256 con claims `sub`, `role` y `exp`.
- Las cuentas demo `student` y `trainer` conservan su login en dev/test y responden `401` en producción. El criterio del Issue "`student` / `Workshop123!` responde 200" aplica solo a dev/test; el Issue no cambia la regla de producción de `docs/ENDPOINTS.md`.

**Fuera del alcance**

- Cambiar el hash de contraseñas, el emisor del token, CORS o las cuentas demo.
- Cambiar endpoints, payloads o componentes fuera de `auth/` (`api/`, `web/`).
- Definir el diseño técnico o los pasos de implementación (`design.md`, `tasks.md`).
- Reintroducir `findByCredentials`; el Issue lo cita como localización histórica, no como API a conservar.

## Capabilities

### New Capabilities

- `login-sql-injection-resistance`: el login resiste entradas con sintaxis SQL, con escenarios observables sobre `POST /auth/login`.

### Modified Capabilities

Ninguna. `openspec/specs/` no contiene specs existentes.

## Impact

- Componente `auth/`: pruebas de `POST /auth/login` en `AuthResourceTest`; la capa de repositorio es el punto verificado, sin cambio funcional previsto.
- Documentación general: solo la fila H-AUTH-01 de `HALLAZGOS.md`, preservando las filas de api y web.
- Issue de referencia: [#21](https://github.com/user-bcp-student27/ai-labs/issues/21).
- Pregunta abierta: la premisa del Issue ya no coincide con el código; se asume que el trabajo comprometido es la protección verificable por pruebas, no un cambio de producción.
