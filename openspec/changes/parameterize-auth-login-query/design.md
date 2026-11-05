# Design

## Context

Issue [#21](https://github.com/user-bcp-student27/ai-labs/issues/21) (H-AUTH-01) describe un login que concatena `username` y `password` en SQL. El código actual de `auth/` ya no lo hace:

- `AuthResource.login` valida el request y delega en `AuthService.login`.
- `AuthService.login` llama a `AuthRepository.findByUsername(username)`, verifica la contraseña en Java con `PasswordHasher.verify` (PBKDF2) y, si `auth.block-demo-users` está activo, rechaza `student` y `trainer`.
- `LegacyDatabase.findByUsername` usa `SELECT ... WHERE username=?` con `PreparedStatement`. La contraseña no viaja al SQL.
- El resto de las sentencias de `LegacyDatabase` (esquema, seed, migración) también usan parámetros o SQL constante sin entrada externa.
- `AuthResourceTest` no contiene ningún intento de inyección.

Por tanto el diseño no cambia código de producción: añade evidencia ejecutable de que la propiedad de la spec `login-sql-injection-resistance` se cumple y detecta regresiones.

## Goals / Non-Goals

**Goals**

- Cubrir con pruebas los escenarios de inyección de la spec (`username`, `password`, comentario `--`).
- Verificar que la contraseña nunca aparece en la respuesta y que el `401` no emite `token`.
- Verificar que el contrato de `POST /auth/login` de `docs/ENDPOINTS.md` no cambia.
- Dejar la evidencia lista para actualizar solo la fila H-AUTH-01 de `HALLAZGOS.md`.

**Non-Goals**

- Modificar `LegacyDatabase`, `AuthService`, `AuthResource`, el hash, el JWT, CORS o las cuentas demo, salvo que una prueba demuestre un defecto real (ver Riesgos).
- Reintroducir `findByCredentials` o cambiar la interfaz `AuthRepository`.
- Tocar `api/` o `web/`, ni crear dependencias nuevas.
- Rediseñar la capa de persistencia ni migrar de SQLite.

## Contratos protegidos

| Contrato | Fuente | Protección |
|---|---|---|
| Request `username`, `password`, `client` opcional | `docs/ENDPOINTS.md` | Pruebas existentes de login |
| `200` con `token`, `expiresIn` (3600), `user.*`, `debug` | `docs/ENDPOINTS.md` | `logsInWithWorkshopUser` y similares |
| `400` por campos vacíos o nulos, mensajes documentados | `docs/ENDPOINTS.md` | `rejectsMissingRequiredFields`, `rejectsBlankRequiredFields` |
| `401` con `Invalid user <username> or password`, sin contraseña | `docs/ENDPOINTS.md` | Nuevas pruebas |
| `500` ante `IllegalStateException` | `docs/ENDPOINTS.md` | Pruebas existentes de error interno |
| JWT RS256 con `sub`, `role`, `exp` | `docs/ENDPOINTS.md` | `issuesSignedTokenWithIdentityRoleAndExpiration` |
| `student`/`trainer` responden `401` en producción | `docs/ENDPOINTS.md` | Pruebas de `auth.block-demo-users` existentes |

## Componentes afectados

- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: nuevas pruebas a nivel HTTP, con la base de datos real de prueba.
- `auth/src/test/java/com/bcp/workshop/auth/AuthResourceUnitTest.java` o una prueba de `LegacyDatabase`: opcional, para comprobar a nivel de repositorio que un `username` con sintaxis SQL devuelve `null` y no una fila.
- `HALLAZGOS.md`: solo la fila H-AUTH-01, preservando las filas de api y web.
- Sin cambios en código de `auth/src/main`.

## Decisiones técnicas

1. **Prueba a nivel HTTP como evidencia principal.** Se ejercita `POST /auth/login` con la base de prueba, porque el requisito es observable en el endpoint y cubre las tres capas.
   - Alternativa: probar solo `LegacyDatabase`. Se descarta como único nivel porque no demuestra la ausencia de `token` en la respuesta; se admite como prueba complementaria.
2. **Intentos de inyección con valores fijos** (`' OR '1'='1` en cada campo y `student'--`), con una fila existente (`student`) para que un SQL alterado devolviera un usuario si hubiera fallo.
   - Alternativa: fuzzing o lista amplia de payloads. Se descarta por costo y no aporta a los escenarios aprobados.
3. **No añadir código de producción.** El código ya cumple la spec; añadir sanitización o listas de caracteres duplicaría la protección real (parámetros enlazados) y podría rechazar usernames legítimos.
   - Alternativa: validar `username` con una expresión regular. Se descarta por cambiar el contrato `400`/`401` documentado.
4. **No crear un helper de pruebas nuevo salvo que haya repetición.** Si tres pruebas repiten el cuerpo JSON, se extrae un método privado en `AuthResourceTest`, conforme a la regla de clean code de `AGENTS.md`.
5. **Comparación de la contraseña fuera de SQL se conserva.** Es la razón por la que la inyección en `password` no puede autenticar.

## Estrategia incremental

1. Ejecutar `mvn test` desde `auth/` y registrar la línea base verde.
2. Añadir las pruebas de caracterización de inyección. Deben pasar sin cambiar `src/main`; ese resultado es la evidencia de que el defecto histórico no existe.
3. Si alguna prueba falla, tratarlo como defecto real: detenerse, informar y acordar un cambio mínimo en `LegacyDatabase` antes de continuar.
4. Actualizar la fila H-AUTH-01 de `HALLAZGOS.md` con la evidencia, sin marcar Resuelto sin revisión humana.
5. Ejecutar `mvn test` completo y verificar la cobertura JaCoCo del código tocado.

## Pruebas de caracterización necesarias

Antes de cualquier cambio de comportamiento, y como evidencia final, las siguientes pruebas HTTP en `AuthResourceTest`:

- `username` = `' OR '1'='1`, `password` no vacío: `401`, sin `token`.
- `username` = `student`, `password` = `' OR '1'='1`: `401`, sin `token`.
- `username` = `student'--`, `password` no vacío: `401`, sin `token`.
- En los tres casos, el mensaje no contiene el valor de `password`.
- Control: `student` / `Workshop123!` sigue respondiendo `200` en el perfil de test.

Las pruebas existentes de `400`, `401`, `500` y JWT se conservan como caracterización del contrato y no se modifican.

## Riesgos y trade-offs

- **Premisa del Issue desfasada:** el Issue pide cambiar código que ya cambió. Se asume que el compromiso es la protección por pruebas; si el solicitante espera un cambio de producción, hay que aclararlo antes de `tasks.md`.
- **Falso positivo de seguridad:** una prueba que pasa no prueba ausencia total de inyección en otras rutas. La spec se limita a `POST /auth/login`.
- **Base compartida entre pruebas:** las pruebas de `AuthResourceTest` comparten base; las nuevas no deben escribir filas. Las inyecciones solo leen.
- **Eco del `username` en el `401`:** el contrato actual lo incluye en el mensaje; se preserva, y se aceptan los riesgos de reflejar entrada que ya documenta H-AUTH.
- **Trade-off de cobertura:** no hay código nuevo en `src/main`, por lo que la meta de 85% del código modificado se evalúa sobre el código de prueba y no cambia la cobertura global.

## Rollback

Los cambios son solo pruebas y la fila de `HALLAZGOS.md`; se revierten con `git revert` del commit correspondiente sin efecto en runtime. Si se llegara a modificar `LegacyDatabase` por un defecto hallado, ese cambio irá en un commit separado para revertirlo de forma independiente.
