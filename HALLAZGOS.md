# Hallazgos

Seguimiento de auth: 2026-10-26. `Resuelto` registra cambio y verificación
local; no sustituye una revisión humana de cierre. Se conserva el historial
previo de H-AUTH-01.

## Contrato observado

`docs/ENDPOINTS.md` se creó durante el paso de documentación con el contrato
observado; no consta un contrato acordado independientemente. Del código se
observa `POST /auth/login`
(`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15,22-24`) con
campos `username`, `password` y `client`
(`auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java:3-6`). La ruta
responde 400 si falta el request o un campo requerido
(`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:26-33`), 401 si
no encuentra usuario (`:35-37`) y 200 con token, `expiresIn` y usuario cuando
la autenticación tiene éxito (`:39-48`). El campo `client` no interviene en la
lógica observada. El catch final devuelve el mensaje y la clase de la
excepción con estado 500 (`:54-58`).

El recurso delega autenticación a `AuthService` (`AuthResource.java:42-46`),
que usa `AuthRepository` y `PasswordHasher`
(`AuthService.java:20-37`). La base SQLite se configura con `AUTH_DB_URL` o la
ruta local predeterminada (`LegacyDatabase.java:21`); al inicializar crea la
tabla, agrega semillas y migra credenciales legacy en una transacción
(`LegacyDatabase.java:27-48,73-120`). La búsqueda usa `PreparedStatement`
(`LegacyDatabase.java:51-64`). El token se emite como JWT RS256 con `sub`,
`role` y `exp` (`TokenService.java:18-24`); la ruta de la clave se recibe por
configuración externa en dev/prod (`application.properties:10-11`). La
allowlist CORS sigue sin estar restringida (`application.properties:4-5`).

## Matriz de seguimiento

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-02 | auth | Contraseñas almacenadas en texto plano | Vulnerabilidad | Las semillas y filas legacy se guardan ahora con PBKDF2-HMAC-SHA256, salt aleatorio y comparación constante. La migración idempotente de filas existentes se ejecutó contra la base local después de crear un respaldo; la suite verificó login y repetición de migración. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:27-48,73-120`; `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:15-61` | alta | Resuelto — hashing y migración idempotente verificados |
| H-AUTH-03 | auth | Token codificado en Base64 con secreto embebido | Vulnerabilidad | El emisor anterior Base64 fue reemplazado por JWT RS256 con claims `sub`, `role` y `exp`; la clave se configura externamente. Una prueba local verifica firma y claims con una clave efímera. El consumidor confirmó compatibilidad; no se inspeccionaron otros componentes. | `auth/src/main/java/com/bcp/workshop/auth/TokenService.java:10-24`; `auth/src/main/resources/application.properties:9-11`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:29-37` | alta | Resuelto — JWT firmado, claims y expiración verificados |
| H-AUTH-08 | auth | Cuentas demo con credenciales públicas pueden persistir en bases de producción existentes | Vulnerabilidad | Las nuevas cuentas demo ya no se insertan en prod; sin embargo, el código no desactiva ni elimina las cuentas `student`/`trainer` que hubieran sido sembradas por versiones anteriores. Si existen en una base usada por prod, la búsqueda y verificación de credenciales aún las acepta. El contenido de una base productiva no se revisó; no se deben borrar cuentas sin decidir cómo distinguir demos de cuentas legítimas. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32-41,58-68,86-101`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:20-22`; `auth/src/main/resources/application.properties:13`; `README.md:44` | alta | Pendiente — disposición de cuentas demo persistidas |
| H-AUTH-01 | auth | Concatenación de credenciales SQL: no encontrada en el código actual | Vulnerabilidad | Se conserva el hallazgo histórico: una versión anterior concatenaba las entradas. Actualmente el Repository enlaza `username` en SQL y `AuthService` verifica la contraseña con PBKDF2; no aparece concatenación. No se marca Resuelto. | Historial: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32-35` (versión anterior). Actual: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:51-64`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:20-22` | alta | No encontrado |
| H-AUTH-04 | auth | La respuesta de error revela detalles de excepción | Vulnerabilidad | El 500 devuelve mensaje/código genéricos para `IllegalStateException`; la prueba caracteriza ese tipo, pero otras excepciones no pasan por el handler. Falta comprobar que todos los errores internos mantengan una respuesta genérica. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:52-65`; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceUnitTest.java:12-25` | media | Pendiente — cobertura de error limitada a IllegalStateException |
| H-AUTH-05 | auth | CORS permite un patrón de origen amplio; configuración estática | Vulnerabilidad | La ruta de la clave se externaliza con `AUTH_TOKEN_SIGNING_KEY_LOCATION`; no se proporcionó su ruta real ni la allowlist de producción. CORS aún usa `/.*/`, por lo que no se inventó ni se cambió el origen. Las pruebas usaron una clave RSA efímera. | `auth/src/main/resources/application.properties:4-5,9-11` | media | Pendiente — falta configuración de despliegue |
| H-AUTH-06 | auth | Versiones de dependencias candidatas a revisión SCA | Sugerencia | Commons Text pasó a 1.15.0 tras consultar el advisory de Apache sobre CVE-2022-42889; el uso observado es `escapeHtml4`, no `StringSubstitutor`. Quarkus 3.27.4 sigue sin análisis SCA completo. CodeQL y Dependency Review remotos no se ejecutaron. | `auth/pom.xml:13,44,53-54`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:5,39` | baja | Pendiente — análisis de Quarkus y scanners no ejecutado |
| H-AUTH-07 | auth | `AuthResource.login` combina validación, persistencia, token y respuesta | Refactor | El Resource ahora valida y mapea HTTP, `AuthService` coordina autenticación, `AuthRepository` define persistencia y `LoginResponse` mantiene DTO propio. La suite confirma la forma observada del response. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:31-65`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:20-37`; `auth/src/main/java/com/bcp/workshop/auth/AuthRepository.java:3-5`; `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4` | baja | Resuelto — capas y DTO separados con regresión aprobada |

## Validación registrada

La suite `python tools/lab.py test auth` se ejecutó anteriormente: compiló las
clases principales y reportó 3 pruebas aprobadas (login válido, credenciales
inválidas y entrada con comilla), en
`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:13,25,35`.
No se volvió a ejecutar durante este Discovery. La prueba diferenciadora con
un usuario local válido cuyo nombre contenga una comilla no se ha ejecutado.
La limpieza `mvn clean verify` se intentó anteriormente y no pudo borrar un
JAR bloqueado por otro proceso; la ejecución posterior del comando de la
suite sí terminó correctamente.

### Implementación autorizada

- Antes de cambios de producción: `mvn test` con cwd `auth/` y SQLite
  temporal; 9 pruebas aprobadas.
- Las pruebas de corrección de H-AUTH-02, H-AUTH-03 y H-AUTH-04 fallaron antes
  de su corrección por las condiciones esperadas (texto plano, token de un
  segmento y detalle de excepción expuesto).
- Después de cambios: `mvn verify` con cwd `auth/`, contra la base local y con
  respaldo previo no versionado; 15 pruebas aprobadas, 0 fallas y 0 errores.
  JaCoCo informó cobertura por líneas superior o igual a 85% en todas las
  clases modificadas (mínimo: LegacyDatabase, 86.7%).
- No se ejecutaron CodeQL ni Dependency Review remotos. No se comprobó CORS de
  producción porque falta la allowlist acordada; H-AUTH-05 y H-AUTH-06 siguen
  Pendientes.

### Revisión local del diff

- H-AUTH-04 vuelve a `Pendiente`: el manejo genérico probado no cubre otras
  clases de excepción.
- Se registra H-AUTH-08 como `Pendiente`: las cuentas semilla conocidas siguen
  habilitadas en todos los perfiles. No se inspeccionaron consumidores fuera
  de auth.
- No se ejecutaron pruebas durante esta revisión; se conserva como evidencia
  el último `mvn verify` registrado arriba.

### Implementación posterior de H-AUTH-08

- El solicitante autorizó corregir el hallazgo de mayor severidad.
- El seeding de nuevas cuentas está deshabilitado en prod y habilitado en
  dev/test. La prueba con `auth.seed-demo-users=false` en una base vacía pasó.
- Antes del cambio, la prueba roja encontró las 2 cuentas demo aun con el flag
  deshabilitado; después, `NoDemoSeedingTest` confirmó que no se crean.
- La suite final `mvn verify` con cwd `auth/` terminó con 16 pruebas aprobadas,
  0 fallas y 0 errores; JaCoCo mostró 89.2% de líneas en `LegacyDatabase`.
- La revisión posterior detectó que no se revocan filas demo preexistentes. Por
  eso H-AUTH-08 vuelve a `Pendiente`; no se eliminó ni deshabilitó ninguna
  cuenta existente sin una decisión de cómo distinguirla de cuentas legítimas.
- El solicitante indicó no modificar las cuentas existentes y conservar el
  estado `Pendiente`.

### Pruebas posteriores de endpoints

En el paso de documentación/testing se añadieron checks de `GET /q/health`,
body malformado y media type de `POST /auth/login`. `mvn verify` con cwd
`auth/` terminó con 22 pruebas aprobadas, 0 fallas y 0 errores; la cobertura de
líneas de `LegacyDatabase` fue 89.2% y la de `PasswordHasher` 92.9%.

## Discovery auth — candidatos para revisión humana (2026-10-27)

Los siguientes candidatos revalidan IDs existentes. `CANDIDATO` es un estado
provisional de esta revisión y no representa confirmación humana ni cambia la
historia de estados de la matriz anterior. No se modificó código ni se
ejecutaron pruebas en este discovery.

### H-AUTH-08 — Cuentas demo existentes podrían seguir autenticándose
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32-41,58-68,86-101`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:20-22`; `auth/src/main/resources/application.properties:13`. El perfil prod deshabilita nuevas seeds, pero la búsqueda no filtra cuentas persistidas por entorno.
- Impacto: una cuenta demo que ya exista en una base de producción podría superar la verificación de contraseña y recibir un JWT. No se inspeccionó ninguna base de producción.
- Prioridad propuesta: alta.
- Contrato potencialmente afectado: `POST /auth/login`; una cuenta demo persistida puede obtener el `200` y el token documentados en `docs/ENDPOINTS.md`.
- Cómo validarlo: en una SQLite temporal, precrear una cuenta demo y una cuenta legítima, iniciar con seeding deshabilitado y comprobar si la primera puede recibir token y la segunda conserva login. No modificar cuentas reales; acordar primero cómo distinguir demos de usuarios legítimos.

### H-AUTH-05 — CORS acepta orígenes no incluidos en una allowlist
- Estado: CANDIDATO
- Evidencia: `auth/src/main/resources/application.properties:4-5` habilita CORS y configura una expresión amplia; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:211-225` comprueba que tanto el origen local como uno no listado reciben autorización CORS.
- Impacto: navegadores de orígenes no aprobados pueden leer respuestas cross-origin cuando el navegador permite la solicitud. El impacto depende de los consumidores y del despliegue; no se inspeccionaron otros componentes.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: acceso desde navegador a `POST /auth/login`; no cambia el payload HTTP, pero afecta qué orígenes pueden leer la respuesta.
- Cómo validarlo: obtener primero la allowlist aprobada y ejecutar preflight para un origen permitido y otro no permitido; verificar la cabecera `Access-Control-Allow-Origin` en ambos casos.

### H-AUTH-04 — Errores distintos de IllegalStateException no tienen respuesta caracterizada
- Estado: CANDIDATO
- Evidencia: `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:52-53` captura solo `IllegalStateException`; el envelope genérico está en `:61-65`. `auth/src/test/java/com/bcp/workshop/auth/AuthResourceUnitTest.java:12-27` solo caracteriza ese tipo.
- Impacto: otras excepciones no pasan por el manejador explícito; el status y cuerpo resultantes pueden diferir del error genérico documentado. No se comprobó fuga de detalles para esas excepciones.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: respuesta `500` de `POST /auth/login` en `docs/ENDPOINTS.md`.
- Cómo validarlo: inyectar en una prueba una excepción de tipo distinto a `IllegalStateException` desde el Service y comprobar status, cuerpo genérico y ausencia de mensaje interno; registrar el comportamiento actual antes de proponer cambio.

### H-AUTH-06 — Revisión SCA de Quarkus 3.27.4 pendiente
- Estado: CANDIDATO
- Evidencia: `auth/pom.xml:13` fija Quarkus 3.27.4; `HALLAZGOS.md:43` registra que no se ejecutó el análisis SCA completo ni CodeQL/Dependency Review remotos.
- Impacto: no hay evidencia en este discovery para determinar si existen vulnerabilidades aplicables o si se afecta el runtime de los endpoints. No se afirma un CVE.
- Prioridad propuesta: baja hasta contar con evidencia del análisis.
- Contrato potencialmente afectado: endpoints del runtime Quarkus, incluidos `/q/health` y `/auth/login`, solo si un advisory aplicable demuestra impacto.
- Cómo validarlo: ejecutar el análisis de dependencias aprobado para auth, revisar advisories de la versión directa y transitiva, y adjuntar el reporte con versiones afectadas/corregidas. No cambiar versiones ni silenciar findings sin esa evidencia.

Detener discovery aquí para revisión humana de estos cuatro candidatos; no se
crearon issues automáticamente.
