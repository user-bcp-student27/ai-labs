# Plan de auth

Estado: implementación autorizada para H-AUTH-02 a H-AUTH-07. Los cambios y
ejecuciones locales están registrados por ID; H-AUTH-05 y H-AUTH-06 permanecen
Pendientes por evidencia externa faltante.

La selección recibida incluye todos los hallazgos Pendientes H-AUTH-02 a
H-AUTH-07. El motivo indicado explícitamente fue priorizar H-AUTH-02 por el
riesgo de exposición de contraseñas y H-AUTH-04 por su alcance acotado. Los
demás se incluyen porque también fueron seleccionados; su justificación
individual se describe en su apartado. H-AUTH-01 no se planifica porque su
estado actual en HALLAZGOS.md es `No encontrado`.

## Contrato y caracterización común

Al redactar este plan, `docs/ENDPOINTS.md` no existía y el contrato observado
se derivó del código. El contrato observado se documentó posteriormente en
`docs/ENDPOINTS.md`; no consta aprobación independiente de ese contrato. Antes
de cambiar producción, agregar o completar pruebas de caracterización de
`POST /auth/login` en
`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` para registrar:

- Request con `username`, `password` y `client`; `client` se acepta pero no se
  usa en el flujo actual.
- Respuestas actuales: 400 para request/campos requeridos ausentes, 401 para
  credenciales inválidas, 200 para acceso válido y 500 ante excepción.
- Forma completa del 200: `token`, `expiresIn`, `user` y `debug`; campos de
  `user`, incluida la transformación actual de `displayName`.
- Login de ambos usuarios de prueba existentes y contraseña incorrecta, con
  datos ficticios y base aislada para pruebas que necesiten escribir datos.

La ejecución documentada es `python tools/lab.py test auth` desde la raíz; para
ejecuciones directas de Maven, usar `mvn verify` con cwd `auth/`. La cobertura
objetivo del código modificado es 85% o superior. No se ejecutaron pruebas al
preparar este plan.

## Orden y dependencias

1. Escribir y ejecutar la caracterización anterior antes de modificar código.
2. Investigar H-AUTH-06 antes de elegir dependencias para hashing o tokens; no
   silenciar CodeQL, Dependency Review ni JaCoCo.
3. Separar Resource, Service, Repository y seguridad en cambios revisables
   (H-AUTH-07) después de fijar el comportamiento HTTP.
4. Resolver configuración y orígenes permitidos (H-AUTH-05) antes de emitir
   tokens firmados (H-AUTH-03).
5. Diseñar y probar migración de credenciales existentes antes de activar el
   hashing (H-AUTH-02). No migrar la base local existente sin decisión y
   respaldo.
6. Aplicar los cambios de token y errores en incrementos separados; ejecutar
   regresión después de cada uno.

## H-AUTH-02 — Contraseñas almacenadas en texto plano

- **Problema:** `legacy_users.password` recibe y compara valores sin hash.
  Una copia de la base podría revelar las credenciales guardadas.
- **Cambio propuesto:** usar hash lento con salt, preferentemente BCrypt según
  `docs/GOOD_PRACTICES.md`; verificar con el hasher durante el login. Preparar
  una migración idempotente para registros existentes y semillas. Elegir una
  dependencia solo después de revisar compatibilidad y advisories; no almacenar
  ni registrar passwords en texto plano.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`
  (o el Repository del H-AUTH-07), `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
  solo si el Service cambia la llamada, `auth/pom.xml` únicamente si se justifica
  una biblioteca, y pruebas en `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes de tocar código:** ampliar `AuthResourceTest` para
  comprobar que los usuarios ficticios válidos siguen recibiendo 200 y la misma
  forma de respuesta, y que contraseña incorrecta/usuario desconocido siguen
  dando 401. Añadir un caso de campos vacíos con 400. Después del cambio, probar
  que el almacenamiento contiene un hash verificable y no el texto original, y
  que la migración de una base legacy temporal es idempotente.
- **Motivo de selección:** riesgo alto de exposición de credenciales.
- **Pendiente:** definir migración de bases existentes y dependencia de hashing
  con evidencia; no hay decisión aprobada.

## H-AUTH-03 — Token Base64 con secreto embebido

- **Problema:** Base64 es codificación reversible, no firma. El token contiene
  identidad, rol y expiración, pero el código observado no aporta integridad
  criptográfica ni verificación en auth.
- **Cambio propuesto:** emitir un token firmado con claims explícitos y
  expiración validable, usando una biblioteca mantenida escogida después de
  H-AUTH-06. Obtener la clave de firma de configuración externa, sin secretos
  reales en código ni fixtures. Mantener `POST /auth/login` y la forma del 200
  observada (`token`, `expiresIn`, `user`, `debug`); usar una constante/config
  nombrada para la duración en vez del literal `3600`.
- **Archivos tocados:** `auth/src/main/java/com/bcp/workshop/auth/TokenService.java`
  reemplaza `LegacyTokenUtil.java`; `AuthService.java` delega la emisión,
  `application.properties` configura la ruta externa de clave, `auth/pom.xml`
  añade el builder gestionado por el BOM y las pruebas validan firma/claims.
- **Caracterización antes de tocar código:** comprobar 200 y la forma completa
  del response, token no vacío, duración observada y datos de `user`. No fijar
  Base64 como formato contractual sin evidencia. Después, verificar firma,
  claims, expiración y rechazo de token alterado con una clave de prueba aislada.
- **Motivo de selección:** riesgo alto para la integridad del token; se eligió
  explícitamente al seleccionar todos los hallazgos Pendientes.
- **Dependencia externa:** el formato del token puede ser consumido fuera de
  auth. Confirmar compatibilidad con su responsable antes de cambiarlo; no se
  inspeccionaron otros componentes.
- **Justificación de biblioteca:** la guía oficial de Quarkus documenta
  `quarkus-smallrye-jwt-build`, firmado RS256 y clave RSA desde
  `smallrye.jwt.sign.key.location`:
  https://quarkus.io/guides/security-jwt-build. La dependencia se alinea al BOM
  existente; no se fijará una versión independiente. CodeQL y Dependency Review
  remotos siguen sin ejecutarse.

## H-AUTH-04 — La respuesta de error revela detalles internos

- **Problema:** el 500 devuelve el mensaje y la clase de excepción, exponiendo
  detalles de implementación al cliente.
- **Cambio propuesto:** conservar el estado 500 y un envelope JSON estable, pero
  devolver un mensaje genérico y un código de error estable no sensible; enviar
  el detalle completo solo a logging controlado del servidor, sin silenciar el
  error ni registrar credenciales.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`
  o el mapper de errores introducido por H-AUTH-07; pruebas en
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterización antes de tocar código:** provocar una excepción determinista
  mediante un doble de prueba local y registrar el status y las claves/valores
  del 500 actual. Después, verificar 500 y envelope estable sin mensaje ni clase
  real de la excepción; confirmar que 400, 401 y 200 no cambian.
- **Motivo de selección:** cambio acotado en el borde HTTP, según la razón
  indicada al seleccionar los IDs.
- **Contrato:** `docs/ENDPOINTS.md` documenta ahora el 500 observado. Mantener el
  status y envelope JSON; el cambio de valores sensibles se limita a valores
  genéricos y queda sujeto a revisión.

## H-AUTH-05 — Secreto y CORS configurados de forma amplia

- **Problema:** el secreto de token está en una constante Java y CORS usa una
  expresión de origen amplia. No se verificó el comportamiento efectivo en
  runtime.
- **Cambio propuesto:** externalizar la clave de firma con configuración
  requerida en producción y valor ficticio aislado en pruebas; reemplazar la
  expresión abierta por una allowlist explícita por entorno. Mantener un origen
  de desarrollo local aprobado y no inventar el origen de producción.
- **Archivos a tocar:** `auth/src/main/resources/application.properties`,
  `auth/src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` o el servicio
  de tokens del H-AUTH-03, y pruebas de configuración/CORS en
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes de tocar código:** agregar prueba de preflight para el
  origen local observado y otra para un origen no aprobado, registrando primero
  el comportamiento actual. Después, comprobar que solo los orígenes acordados
  reciben `Access-Control-Allow-Origin` y que auth funciona con la clave de
  prueba externa.
- **Motivo de selección:** cerrar configuración permisiva y habilitar de forma
  segura el token firmado del H-AUTH-03.
- **Pendiente externo:** el responsable de despliegue debe proporcionar la
  allowlist de producción; no leer ni modificar configuración de otros
  componentes.
- **Aplicación parcial:** se externalizó la ruta de clave de firma junto al
  H-AUTH-03. No se modificó `quarkus.http.cors.origins` porque la allowlist no
  fue proporcionada; H-AUTH-05 permanece Pendiente.

## H-AUTH-06 — Dependencias candidatas a revisión SCA

- **Problema:** `auth/pom.xml` fija Quarkus 3.27.4 y Commons Text 1.9. La
  versión 1.9 de Commons Text antecede a la versión 1.10.0 corregida para
  CVE-2022-42889 cuando se usa `StringSubstitutor` con entrada no confiable.
  El flujo auth usa `StringEscapeUtils.escapeHtml4`, por lo que la aplicabilidad
  directa de ese CVE no se confirmó.
- **Cambio aplicado:** `commons-text` se actualizó a 1.15.0, release indicada en
  Maven Central; se conservó el escape HTML mediante la misma API. No se cambió
  Quarkus 3.27.4 por falta de resultado de un análisis de advisories compatible.
- **Archivos tocados:** `auth/pom.xml`; no fue necesario cambiar la llamada de
  escape en `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`.
- **Caracterización antes/después:** `AuthResourceUnitTest` registra el escape
  de caracteres HTML antes de la actualización; la suite posterior pasó.
  `mvn verify` confirmó resolución y compatibilidad local.
- **Motivo de selección:** las versiones están señaladas en RETO.md y requieren
  evidencia SCA antes de actualizar o retirar dependencias.
- **Pendiente:** CodeQL, Dependency Review y escaneo SCA remoto no se ejecutaron
  en este recorrido. H-AUTH-06 no se marca Resuelto mientras Quarkus 3.27.4 no
  tenga revisión de advisories y scanner.
- **Evidencia pública consultada:** Apache indica que CVE-2022-42889 afecta a
  Commons Text anterior a 1.10.0 cuando se usa `StringSubstitutor` con entrada
  no confiable:
  https://commons.apache.org/proper/commons-text/security.html. El código auth
  observado usa `StringEscapeUtils.escapeHtml4`, no `StringSubstitutor`. Maven
  Central publica 1.15.0 como release más reciente:
  https://repo1.maven.org/maven2/org/apache/commons/commons-text/maven-metadata.xml.
  Se propone subir Commons Text a 1.15.0 y conservar H-AUTH-06 Pendiente para
  revisar Quarkus 3.27.4; los escáneres remotos no se ejecutarán.

## H-AUTH-07 — Separación de responsabilidades en el Resource

- **Problema:** `AuthResource.login` valida, accede a persistencia, crea el
  token, construye la respuesta y maneja excepciones con anidamiento profundo.
- **Cambio propuesto:** dejar el Resource como adaptador HTTP; mover reglas de
  autenticación a un Service, acceso SQL a un Repository y emisión de token a
  un componente de seguridad. Mapear la respuesta con DTO propio y conservar
  responsabilidades de un solo método, nombres explicativos, guard clauses,
  sin duplicación ni valores mágicos.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`,
  `LegacyDatabase.java`, `LoginRequest.java`, `UserRecord.java`, nuevas clases
  `AuthService.java`, `AuthRepository.java` y DTO(s) de respuesta bajo
  `auth/src/main/java/com/bcp/workshop/auth/`, y
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterización antes de tocar código:** completar primero las pruebas
  comunes de contrato; añadir casos para request nulo y campos vacíos (400),
  credenciales inválidas (401), éxito (200 con forma exacta), error controlado
  (500) y `displayName` escapado. Ejecutar esas pruebas antes de extraer capas y
  repetirlas después; medir cobertura del código modificado con meta mínima de
  85%.
- **Motivo de selección:** deuda de arquitectura confirmada en el método actual
  y seleccionada junto con los hallazgos de seguridad.

## Decisiones pendientes antes de implementación

- Acordar con el consumidor externo el formato de token firmado; no se revisaron
  otros componentes.
- Obtener orígenes CORS de producción del responsable de despliegue.
- Definir migración segura de las contraseñas ya persistidas.
- Justificar cualquier dependencia de hashing/JWT tras la revisión SCA.
- Revisar el cambio de valores del error 500, ya que el contrato documentado
  está ausente.

La aprobación del solicitante y los resultados de pruebas locales constan en
los registros anteriores. No se presume aprobación ni evidencia para la
allowlist de CORS de producción, CodeQL o Dependency Review remotos.

## Verificación de aprobación previa a implementación — 2026-10-26

- Componente: auth.
- IDs seleccionados: H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05,
  H-AUTH-06 y H-AUTH-07.
- Resultado: bloqueado antes de implementar. PLAN.md indica que no hay
  aprobación humana registrada; no se encontró una aprobación por ID.
- Código editado en esta ejecución: no.
- Pruebas ejecutadas en esta ejecución: ninguna.
- HALLAZGOS.md no se actualizó; los estados se conservan.

## Aprobación humana para implementación — 2026-10-26

- Componente: auth.
- IDs autorizados: H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05,
  H-AUTH-06 y H-AUTH-07.
- Decisión: el solicitante autorizó implementar el alcance de PLAN.md mediante
  la respuesta explícita «Sí, autorizo» recibida el 2026-10-26 a las 15:43.
- H-AUTH-01 queda fuera de este alcance porque su estado en HALLAZGOS.md es
  `No encontrado`.
- Esta autorización no reemplaza las decisiones externas aún pendientes que
  PLAN.md identifica para la allowlist de producción, la compatibilidad del
  token con sus consumidores y la migración de credenciales persistidas.

## Aclaraciones recibidas para la implementación — 2026-10-26

- H-AUTH-02: el solicitante autorizó migración idempotente de bases existentes
  con respaldo previo. Las pruebas usarán una base temporal; no se modificarán
  datos reales durante la validación.
- H-AUTH-03: el solicitante confirma que los consumidores aceptan el nuevo token
  firmado. No se inspeccionaron otros componentes ni se verificó esta
  compatibilidad de forma independiente.
- H-AUTH-05: no se proporcionó la allowlist de CORS de producción. No se
  inventará; el cambio de CORS de producción permanece pendiente.
- H-AUTH-05/H-AUTH-03: no se proporcionó una ruta real para
  `AUTH_TOKEN_SIGNING_KEY_LOCATION`; las pruebas usaron una clave RSA efímera.
  No se inició el servicio dev/prod con una clave de despliegue.
- H-AUTH-06: el solicitante decidió dejar Quarkus 3.27.4 sin cambios y mantener
  el hallazgo Pendiente hasta disponer de evidencia SCA.
- CodeQL y Dependency Review remotos no se ejecutarán en este recorrido local.
  La revisión SCA que requiera evidencia externa se registrará como pendiente.

## Ejecuciones

| fecha | componente | IDs | comando / cwd | propósito | resultado |
|---|---|---|---|---|---|
| 2026-10-26 | auth | H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07 | `mvn test` (cwd `auth/`; `AUTH_DB_URL` apuntó a una SQLite temporal bajo `auth/target/`) | Caracterización previa a cambios de producción: contrato válido/inválido, 400, respuesta de error, escape de displayName y CORS actual. | Aprobado: 9 pruebas, 0 fallas, 0 errores. No se modificó código de producción. |
| 2026-10-26 | auth | H-AUTH-07 | `mvn verify` (cwd `auth/`; `AUTH_DB_URL` apuntó a SQLite temporal bajo `auth/target/`) | Regresión tras separar Resource, Service y Repository con DTO de salida. | Aprobado: 10 pruebas, 0 fallas, 0 errores; JaCoCo generó informe. |
| 2026-10-26 | auth | H-AUTH-02 | `mvn -Dtest=AuthResourceTest#migratesLegacyPasswordIdempotentlyAndPreservesLogin test` (cwd `auth/`; SQLite temporal) | Primer intento de prueba roja para migración de contraseña. | Fallido antes de la aserción: la prueba abrió la base temporal antes de inicializar la tabla (`no such table: legacy_users`). Se corrigió el setup de prueba; no se había modificado producción. |
| 2026-10-26 | auth | H-AUTH-02 | `mvn -Dtest=AuthResourceTest#migratesLegacyPasswordIdempotentlyAndPreservesLogin test` (cwd `auth/`; SQLite temporal) | Prueba de corrección roja antes de migrar credenciales. | Falló en la aserción del prefijo PBKDF2: el código actual conservaba la contraseña en texto plano, como se esperaba antes del cambio. No se había modificado producción. |
| 2026-10-26 | auth | H-AUTH-02 | `mvn -Dtest=AuthResourceTest#migratesLegacyPasswordIdempotentlyAndPreservesLogin test` (cwd `auth/`; SQLite temporal) | Repetición de la prueba de migración tras el cambio PBKDF2. | Aprobado: 1 prueba, 0 fallas, 0 errores; migración idempotente y login de la cuenta ficticia conservado. |
| 2026-10-26 | auth | H-AUTH-04 | `mvn -Dtest=AuthResourceUnitTest#returnsGenericErrorWithoutExposingExceptionDetails test` (cwd `auth/`) | Prueba roja del error 500 antes de sanitizar la respuesta. | Falló como se esperaba: el cliente recibió `fixture database failure`; la producción aún no estaba modificada para H-AUTH-04. |
| 2026-10-26 | auth | H-AUTH-04 | `mvn -Dtest=AuthResourceUnitTest#returnsGenericErrorWithoutExposingExceptionDetails test` (cwd `auth/`) | Regresión tras responder con error genérico y registrar el detalle solo en servidor. | Aprobado: 1 prueba, 0 fallas, 0 errores; status 500 y envelope estable sin el mensaje ni la clase real de excepción. |
| 2026-10-26 | auth | H-AUTH-03 | `mvn -Dtest=AuthResourceTest#issuesSignedTokenWithIdentityRoleAndExpiration test` (cwd `auth/`) | Primer intento de compilar el test de firma. | Falló en compilación por llaves mal balanceadas en `AuthResourceTest`; se corrigió el test antes de ejecutar la aserción. |
| 2026-10-26 | auth | H-AUTH-03 | `mvn -Dtest=AuthResourceTest#issuesSignedTokenWithIdentityRoleAndExpiration test` (cwd `auth/`; SQLite temporal; clave RSA efímera de test) | Prueba roja del formato firmado y claims antes del cambio. | Falló como se esperaba: el token actual tenía un solo segmento Base64, no los tres segmentos de un JWT firmado. |
| 2026-10-26 | auth | H-AUTH-03 | `mvn -Dtest=AuthResourceTest#issuesSignedTokenWithIdentityRoleAndExpiration test` (cwd `auth/`) | Primer intento de compilar el builder JWT. | Falló en compilación porque la versión de la API no expone `io.smallrye.jwt.SignatureAlgorithm`; se retiró el enum y se configuró RS256 explícitamente. |
| 2026-10-26 | auth | H-AUTH-03 | `mvn -Dtest=AuthResourceTest#issuesSignedTokenWithIdentityRoleAndExpiration test` (cwd `auth/`; SQLite temporal; clave RSA efímera) | Verificar JWT, claims y firma RS256 después del cambio. | Aprobado: 1 prueba, 0 fallas, 0 errores. |
| 2026-10-26 | auth | H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07 | `mvn verify` (cwd `auth/`; SQLite temporal; clave RSA efímera) | Regresión completa y JaCoCo después de hashing, JWT, error genérico, capas y Commons Text 1.15.0. | Aprobado: 15 pruebas, 0 fallas, 0 errores. Cobertura por líneas: AuthResource 100%, AuthService 100%, TokenService 100%, LegacyDatabase 86.7%, PasswordHasher 92.9%; mínimo observado 85%. |
| 2026-10-26 | auth | H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07 | `mvn verify` (cwd `auth/`; `AUTH_DB_URL=jdbc:sqlite:./data/workshop.db`; respaldo previo en sesión) | Aplicar migración idempotente a la base local autorizada y repetir regresión completa. | Aprobado: 15 pruebas, 0 fallas, 0 errores; las credenciales de la base local quedaron migradas a hash. |
| 2026-10-26 | auth | H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07 | `mvn verify` (cwd `auth/`; `AUTH_DB_URL=jdbc:sqlite:./data/workshop.db`) | Verificación final tras centralizar el separador del hash; regresión y JaCoCo con base migrada. | Aprobado: 15 pruebas, 0 fallas, 0 errores. Cobertura por líneas: AuthResource 100%, AuthService 100%, TokenService 100%, LegacyDatabase 86.7%, PasswordHasher 92.9%. |
| 2026-10-26 | auth | H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07, H-AUTH-08 | Revisión local de `git diff main -- auth/` y documentos permitidos (cwd raíz) | Verificar hallazgos Resuelto, nuevos archivos y riesgos del diff en `day1/prompting`. | Sin pruebas en esta revisión. H-AUTH-04 vuelve a Pendiente por cobertura incompleta del manejador; se agrega H-AUTH-08 por credenciales semilla conocidas. |
| 2026-10-26 | auth | H-AUTH-08 | `mvn -Dtest=NoDemoSeedingTest test` (cwd `auth/`) | Primer setup de caracterización con perfil que deshabilita seeds. | Error de fixture: el test consultó una URL distinta de la usada por `LegacyDatabase`; se ajustó para usar la misma `AUTH_DB_URL`. No se había cambiado producción. |
| 2026-10-26 | auth | H-AUTH-08 | `mvn -Dtest=NoDemoSeedingTest test` (cwd `auth/`; SQLite temporal) | Prueba roja antes de condicionar el seeding por perfil. | Falló como se esperaba: encontró 2 usuarios demo aunque `auth.seed-demo-users=false`. |
| 2026-10-26 | auth | H-AUTH-08 | `mvn verify` (cwd `auth/`; SQLite temporal) | Primer intento de suite completa con dos perfiles Quarkus distintos. | Falló porque el segundo contexto no encontró driver SQLite para `AuthResourceTest`; se reemplazó el test multi-perfil por un test unitario aislado. No se observó un fallo de la política de seeding. |
| 2026-10-26 | auth | H-AUTH-08 | `mvn -Dtest=NoDemoSeedingTest test` (cwd `auth/`) | Prueba aislada del inicializador con seeding deshabilitado. | Aprobado: 1 prueba, 0 fallas, 0 errores; no se crean cuentas demo. |
| 2026-10-26 | auth | H-AUTH-08 | `mvn -Dtest=NoDemoSeedingTest test` (cwd `auth/`; SQLite temporal) | Repetición con perfil de prueba aislado tras deshabilitar seeds. | Aprobado: 1 prueba, 0 fallas, 0 errores; se valida ausencia de las cuentas demo cuando el flag está deshabilitado. |
| 2026-10-26 | auth | H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07, H-AUTH-08 | `mvn verify` (cwd `auth/`; SQLite temporal; clave RSA efímera) | Regresión completa final después de limitar cuentas demo a dev/test. | Aprobado: 16 pruebas, 0 fallas, 0 errores. Cobertura por líneas: AuthResource 100%, AuthService 100%, TokenService 100%, LegacyDatabase 89.2%, PasswordHasher 92.9%. |
| 2026-10-26 | auth | H-AUTH-01, H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07, H-AUTH-08 | Documentación de endpoint (sin comando de pruebas) | Crear `docs/ENDPOINTS.md` con contrato observado de `/q/health` y `/auth/login`, errores verificables y preguntas abiertas. | Documento creado; no se ejecutaron pruebas por ser un cambio documental. |
| 2026-10-26 | auth | H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07, H-AUTH-08 | `mvn verify` (cwd `auth/`; `AUTH_DB_URL` apuntó a SQLite temporal; clave RSA efímera) | Caracterizar los dos endpoints documentados, errores HTTP y regreso de salud, además de regresión auth. | Aprobado: 22 pruebas, 0 fallas, 0 errores. JaCoCo actualizado; cobertura por líneas de `LegacyDatabase` 89.2% y `PasswordHasher` 92.9%. |
| 2026-10-26 | auth | H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06, H-AUTH-07, H-AUTH-08 | `mvn verify` (cwd `auth/`; SQLite temporal; clave RSA efímera) | Regresión final, incluyendo seeding dev/test habilitado y seeding deshabilitado en fixture separada. | Aprobado: 16 pruebas, 0 fallas, 0 errores. Cobertura por líneas: AuthResource 100%, AuthService 100%, TokenService 100%, LegacyDatabase 89.2%, PasswordHasher 92.9%. |

## H-AUTH-08 — Credenciales demo sembradas en producción

- **Problema:** `LegacyDatabase.initialize()` crea las cuentas de demo si no
  existen, independientemente del perfil. Hashing no evita que se use una
  contraseña pública para autenticarse.
- **Cambio propuesto:** hacer el seeding explícito mediante
  `auth.seed-demo-users`: habilitado en dev/test y deshabilitado por defecto y
  en prod. Conservar el login de los usuarios ficticios en dev/test. No crear
  usuarios productivos ni inventar un proceso externo de provisionamiento.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`,
  `auth/src/main/resources/application.properties` y pruebas bajo
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes de tocar código:** mantener pruebas del login válido
  de student/trainer en test; agregar una prueba con `auth.seed-demo-users=false`
  y SQLite temporal que falle en el estado actual al encontrar las cuentas.
  Después, verificar que con el seeding deshabilitado no se crean cuentas demo
  y que el login válido sigue funcionando cuando las cuentas están provisionadas
  en la fixture de test.
- **Riesgo pendiente:** una base productiva vacía no tendrá cuentas hasta que
  exista un proceso de provisionamiento aprobado, fuera del alcance auth de
  este cambio. Además, una base ya sembrada puede conservar `student` o
  `trainer`; no se eliminarán ni deshabilitarán sin identificar de forma segura
  las cuentas demo frente a usuarios legítimos.
- **Autorización:** el solicitante eligió `Fix highest severity issues` y
  confirmó explícitamente corregir H-AUTH-08 el 2026-10-26.

### Revisión posterior de H-AUTH-08

La prueba demuestra que seeding deshabilitado no crea cuentas en una base vacía,
pero no cubre la revocación de cuentas ya persistidas. H-AUTH-08 vuelve a
`Pendiente` hasta que se decida cómo distinguir demos existentes sin afectar
cuentas legítimas; no se cambió ni borró ningún registro existente como parte
de este follow-up.

El solicitante confirmó que no se modifiquen cuentas existentes y que
H-AUTH-08 permanezca `Pendiente` hasta una decisión segura sobre su
identificación.

## Alcance autorizado de ISSUE-01 / HU-01 — 2026-10-27

- Referencias indicadas por el solicitante: issue `ISSUE-01`, spec `HU-01`,
  arquitectura `SR Arch`. No se proporcionó identidad del aprobador ni un
  número de issue de GitHub coincidente.
- Alcance implementable de esta iteración: validar al iniciar que la ubicación
  configurada para la clave de firma existe, contiene una clave privada RSA
  válida para RS256 y es legible.
- Comportamiento aprobado: si la ubicación falta o la clave no es válida, el
  inicio del servicio falla.
- CORS se excluye de esta iteración; se mantiene abierto y H-AUTH-05 sigue
  `Pendiente`.
- La clasificación o modificación de cuentas demo persistidas se excluye;
  H-AUTH-08 sigue `Pendiente`.
- No se modificará el emisor ni el formato JWT. No se añadirán secretos reales
  ni dependencias.

## Ejecuciones ISSUE-01 — validación de clave al inicio

| fecha | componente | IDs | comando / cwd | propósito | resultado |
|---|---|---|---|---|---|
| 2026-10-27 | auth | H-AUTH-03, H-AUTH-05 | `mvn verify` (cwd `auth/`; `AUTH_DB_URL` apuntó a SQLite temporal; test resource generó clave RSA efímera) | Baseline previo: contrato y suite auth existentes. | Aprobado: 22 pruebas, 0 fallas, 0 errores. |
| 2026-10-27 | auth | H-AUTH-03, H-AUTH-05 | `mvn -Dtest=SigningKeyStartupCheckTest test` (cwd `auth/`) | Validar clave RSA correcta, ubicación ausente/vacía y archivo ausente/inválido mediante el mismo método observado durante StartupEvent. | Aprobado: 3 pruebas, 0 fallas, 0 errores. |
| 2026-10-27 | auth | H-AUTH-03, H-AUTH-05 | `mvn verify` (cwd `auth/`; `AUTH_DB_URL` apuntó a SQLite temporal; test resource generó clave RSA efímera) | Regresión completa; el runtime Quarkus de las pruebas inicia con una clave válida y el check de startup se ejecuta. | Aprobado: 25 pruebas, 0 fallas, 0 errores. JaCoCo: `SigningKeyStartupCheck` 100% de líneas; mínimo del código modificado 100%. |
