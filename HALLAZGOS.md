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
configuración externa en dev/prod (`application.properties:10-11`). Producción
permite únicamente el origen CORS `https://www.viabcp.com`; dev/test conserva
`/.*/` (`application.properties`).

## Matriz de seguimiento

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-02 | auth | Contraseñas almacenadas en texto plano | Vulnerabilidad | Las semillas y filas legacy se guardan ahora con PBKDF2-HMAC-SHA256, salt aleatorio y comparación constante. La migración idempotente de filas existentes se ejecutó contra la base local después de crear un respaldo; la suite verificó login y repetición de migración. | `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:27-48,73-120`; `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:15-61` | alta | Resuelto — hashing y migración idempotente verificados |
| H-AUTH-03 | auth | Token codificado en Base64 con secreto embebido | Vulnerabilidad | El emisor anterior Base64 fue reemplazado por JWT RS256 con claims `sub`, `role` y `exp`; la clave se configura externamente. Una prueba local verifica firma y claims con una clave efímera. El consumidor confirmó compatibilidad; no se inspeccionaron otros componentes. | `auth/src/main/java/com/bcp/workshop/auth/TokenService.java:10-24`; `auth/src/main/resources/application.properties:9-11`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:29-37` | alta | Resuelto — JWT firmado, claims y expiración verificados |
| H-AUTH-08 | auth | Cuentas demo con credenciales públicas pueden persistir en bases de producción existentes | Vulnerabilidad | Producción ahora rechaza en login los usernames exactos de las cuentas semilla `student` y `trainer` antes de emitir JWT; las filas no se borran ni modifican y dev/test conserva el flujo del taller. Verificado en SQLite temporal con un control legítimo. No se inspeccionaron datos de producción ni se descartó una colisión con una cuenta legítima que use esos usernames. | `auth/src/main/java/com/bcp/workshop/auth/AuthService.java`; `auth/src/main/resources/application.properties`; `auth/src/test/java/com/bcp/workshop/auth/ProductionAuthPolicyTest.java` | alta | Resuelto en código — rechazo y control legítimo verificados en test aislado |
| H-AUTH-01 | auth | Concatenación de credenciales SQL: no encontrada en el código actual | Vulnerabilidad | Se conserva el hallazgo histórico: una versión anterior concatenaba las entradas. Actualmente el Repository enlaza `username` en SQL y `AuthService` verifica la contraseña con PBKDF2; no aparece concatenación. No se marca Resuelto. | Historial: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32-35` (versión anterior). Actual: `auth/src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:51-64`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:20-22` | alta | No encontrado |
| H-AUTH-04 | auth | La respuesta de error revela detalles de excepción | Vulnerabilidad | El 500 devuelve mensaje/código genéricos para `IllegalStateException`; la prueba caracteriza ese tipo, pero otras excepciones no pasan por el handler. Falta comprobar que todos los errores internos mantengan una respuesta genérica. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:52-65`; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceUnitTest.java:12-25` | media | Pendiente — cobertura de error limitada a IllegalStateException |
| H-AUTH-05 | auth | CORS permite un patrón de origen amplio; configuración estática | Vulnerabilidad | Producción ahora permite únicamente el origen aprobado `https://www.viabcp.com`; un preflight desde `https://unlisted.example` no recibe `Access-Control-Allow-Origin`. Dev/test conserva `/.*/`. El path `/cas/*` no es parte del origen CORS. No se verificó un despliegue real. | `auth/src/main/resources/application.properties`; `auth/src/test/java/com/bcp/workshop/auth/ProductionAuthPolicyTest.java` | media | Resuelto en código — origen permitido/excluido verificados en test aislado |
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

## Verificación de HU-01 (2026-10-29)

- Decisiones aprobadas por el solicitante: único origen CORS de producción `https://www.viabcp.com`; cuentas semilla exactas `student` y `trainer` bloqueadas en producción. `/cas/*` es un path del cliente, no un origen CORS; no se implementa una restricción de rutas del API.
- `ProductionAuthPolicyTest` se ejecutó con perfil `prod`, SQLite temporal y clave RSA efímera. Antes del cambio, ambas cuentas demo y `legitimate-control` autenticaron con `200`, y preflight de ViaBCP y de un origen no listado fue autorizado. Después, las cuentas demo respondieron `401`, el control legítimo mantuvo `200`, el origen aprobado fue autorizado y el no listado careció de `Access-Control-Allow-Origin`.
- `mvn test` y `mvn verify` desde `auth/`: 27 pruebas en siete suites, 0 fallos, 0 errores y 0 omitidas. JaCoCo: `AuthService` 100% de líneas; paquete `auth` 93.5% (174/186 líneas). `git diff --check` pasó.
- Las filas no se alteraron. No se inspeccionó la base de producción ni se ejecutó el runtime real; queda el riesgo de que un usuario legítimo use exactamente `student` o `trainer`.

## Discovery api (2026-11-02)

### Contrato observado de la API

`docs/ENDPOINTS.md` existe, pero documenta únicamente auth; no se encontró allí un contrato para api. El siguiente resumen se deriva de `api/app/main.py`; no se modifica ni se crea `docs/ENDPOINTS.md` durante este paso.

- **Observado:** todas las rutas listadas abajo dependen de `legacy_identity`. El encabezado `Authorization` se decodifica como Base64 y se interpreta como `username|role|expiracion|secreto`; se comprueba el secreto literal y la expiración, y cualquier excepción de validación resulta en 401 (`api/app/main.py:14,17-26`). La respuesta de error incluye el texto de la excepción.
- **Observado:** `GET /api/tasks` devuelve tareas ordenadas por id con `id`, `title`, `description`, `status`, `owner`, `priority` y `createdAt`; `owner` es opcional, con valor predeterminado de la identidad (`api/app/main.py:56-65,67-78`).
- **Observado:** `POST /api/tasks` recibe un objeto JSON; `title` y `description` tienen predeterminado vacío, `status` predeterminado `TODO`, `priority` predeterminado `MEDIUM` y `owner` predeterminado a la identidad. Responde 201 con la tarea creada (`api/app/main.py:80-98`).
- **Observado:** `GET /api/summary` acepta `owner` opcional y responde `owner`, `total`, `byStatus` y `highPriorityOpen` (`api/app/main.py:100-118`).
- **Observado:** `POST /api/requirements` recibe un archivo multipart, lo guarda y responde `owner`, `fileName`, `storedAt` y `size` (`api/app/main.py:120-125`).
- **Observado:** `GET /api/backend-health` consulta `AUTH_HEALTH_URL` o `http://localhost:8080/q/health` y responde `requestedBy`, `status` y `body`; ante excepción devuelve status 0 y su texto (`api/app/main.py:127-134`).
- **Observado:** SQLite usa `WORKSHOP_DB_PATH` o `./data/workshop.db`, crea la tabla `tasks` y, si está vacía, inserta tres filas iniciales. La conexión se comparte desde el cierre de la aplicación y se cierra en `shutdown` (`api/app/main.py:29-43,136-138`). CORS permite cualquier origen, método y header, junto con credenciales (`api/app/main.py:48-54`).
- **Acordado:** no consta aprobación humana independiente del contrato de api. Este registro describe el comportamiento observado y no afirma compatibilidad acordada.
- **Riesgos pendientes:** H-API-02, H-API-03, H-API-05, H-API-06 y H-API-07 siguen sin remediar. H-API-01 requiere análisis de advisories antes de concluir aplicabilidad o cambio de dependencias. H-API-04 quedó resuelto localmente con consultas parametrizadas; las ejecuciones se registran en `PLAN.md`.

### Matriz de seguimiento

Filas de api ordenadas por severidad. La numeración sigue el orden de áreas de `RETO.md`; no se encontraron IDs previos de api en este archivo.

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-02 | api | Token Base64 con secreto embebido y sin firma | Vulnerabilidad | Base64 no garantiza integridad: quien pueda construir el formato con el secreto fijo puede presentar identidad y rol arbitrarios. Se comprueba expiración, pero no firma, emisor ni audiencia; el 401 también incorpora detalles de excepciones. | `api/app/main.py:14,17-26` | alta | Aprobado |
| H-API-03 | api | El cliente puede consultar y asignar tareas a otro owner | Vulnerabilidad | `GET /api/tasks` y `/api/summary` aceptan un owner enviado por query y lo prefieren a la identidad; `POST /api/tasks` también admite owner en el body. Un usuario con token puede consultar datos de otro propietario o crear registros atribuidos a él. | `api/app/main.py:68-69,81,86,101-102` | alta | Aprobado |
| H-API-04 | api | Consultas SQLite construidas concatenando entradas | Vulnerabilidad | Los filtros de owner, insert de tareas y recuperación por id usan parámetros enlazados; las pruebas con apóstrofes en query, título y descripción pasan sin alterar la respuesta esperada. | `api/app/main.py:70-74,87-97,103-106`; `api/tests/test_api.py:41-49,54-68,72-90` | alta | Resuelto — SQL parametrizado y 3 pruebas de caracterización aprobadas |
| H-API-05 | api | Carga de archivos usa directamente el nombre y no limita tamaño ni extensión | Vulnerabilidad | El nombre entregado por el cliente se une al directorio de uploads y se escribe sin normalización; además se lee el archivo completo en memoria. Esto puede permitir escritura fuera del directorio previsto/sobrescritura, o consumo excesivo de disco y memoria. | `api/app/main.py:120-125` | media | Aprobado |
| H-API-06 | api | Rutas, persistencia, reglas y serialización concentradas en un módulo | Refactor | `create_app` inicializa SQLite y semillas, define el mapeo de filas y declara las rutas que ejecutan SQL y calculan respuestas. Esta concentración dificulta probar y cambiar cada responsabilidad sin efectos colaterales. | `api/app/main.py:29-65,67-138` | baja | Aprobado |
| H-API-07 | api | Tipado crudo, manejo amplio de excepciones y lógica anidada | Refactor | Requests/responses usan `dict` y `Any`; varios caminos capturan `Exception`, y el cálculo de resumen anida condiciones. La selección de owner y filtros SQL también se repite entre rutas. Esto hace menos explícitos los contratos y complica validar cambios y errores. | `api/app/main.py:8,17-26,56,68,81,101,107-117,133-134` | baja | Aprobado |
| H-API-01 | api | Dependencias urllib3 y python-multipart candidatas a revisión SCA | Sugerencia | Las versiones fijadas coinciden con las señaladas en `RETO.md`, pero este discovery no consultó advisories ni confirmó una vulnerabilidad aplicable. Sin ese análisis no corresponde afirmar un CVE ni actualizar dependencias. | `api/requirements.txt:4-5` | baja | Aprobado |

## Discovery web — candidatos para revisión humana (2026-11-03)

`CANDIDATO` es un estado provisional de esta revisión y no representa
confirmación humana. Alcance: solo `web/`; no se leyó código de auth ni api, y
`docs/ENDPOINTS.md` solo se usó para el contrato de consumo. No se modificó
código, no se ejecutaron pruebas ni `npm audit` y no se crearon issues.

Comportamiento observado: `LoginComponent` llama `POST http://localhost:8080/auth/login`
con `username`, `password` y `client` y guarda token, usuario y preferencias en
`localStorage` (`web/src/app/login/login.component.ts:41-49`).
`DashboardComponent` lee esas claves, llama
`GET http://localhost:8000/api/tasks?owner=<username>` con `Authorization: Bearer`,
oculta tareas `ARCHIVED`, las ordena por prioridad y las muestra
(`web/src/app/dashboard/dashboard.component.ts:36-72`). Nginx solo sirve la SPA con
`try_files` (`web/nginx.conf:7-9`).

### H-WEB-01 — Token y usuario persisten en `localStorage`
- Estado: CANDIDATO
- Evidencia: `web/src/app/login/login.component.ts:47-48` guarda `workshop_token` y `workshop_user`; `web/src/app/dashboard/dashboard.component.ts:36-37` los lee y `:75-76` los elimina solo en logout. `web/src/app/app.routes.ts:7-10` no define guard para `/dashboard`. `web/nginx.conf:1-10` no declara cabeceras como CSP.
- Impacto: cualquier script que se ejecute en el origen de la SPA (XSS o dependencia comprometida) puede leer el token. El token no se borra al expirar ni existe lógica de expiración en el cliente. No se demostró una vía de XSS concreta.
- Prioridad propuesta: alta.
- Contrato potencialmente afectado: forma de la respuesta 200 de `POST /auth/login` (`token`, `user`) y claves `workshop_token`/`workshop_user`; header `Authorization: Bearer` hacia `/api/tasks`.
- Cómo validarlo: prueba con almacenamiento simulado que confirme que tras login el token queda legible en `localStorage`; acordar con el responsable de auth una alternativa acotada al laboratorio (p. ej. almacenamiento en memoria/`sessionStorage` o cookie `HttpOnly`, esta última exige cambios fuera de web) antes de implementar.

### H-WEB-02 — Mensaje dinámico con `[innerHTML]` incorpora entrada del usuario y texto del backend
- Estado: CANDIDATO
- Evidencia: `web/src/app/login/login.component.html:39` usa `[innerHTML]="message"`; `web/src/app/login/login.component.ts:56` concatena `this.username` y `backendMessage` dentro de HTML. Los mensajes fijos de `:34,37,52` también son HTML.
- Impacto: Angular sanea `[innerHTML]`, por lo que no se afirma ejecución de scripts, pero el marcado inyectado por el usuario o por una respuesta del backend se renderiza (p. ej. enlaces, formularios o texto engañoso) y el patrón se rompe si alguien usa `bypassSecurityTrust*`. Es evitable porque el mensaje no necesita HTML.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: texto visible de errores de login; no cambia `POST /auth/login`.
- Cómo validarlo: prueba de componente con usuario como `<b>x</b>` y backend que responda un mensaje con marcado, comprobando el DOM resultante; tras el cambio, verificar que se renderiza como texto y que los mensajes de validación se conservan.

### H-WEB-03 — Dependencias `lodash 4.17.21` y `axios 0.31.0` sin análisis SCA registrado
- Estado: CANDIDATO
- Evidencia: `web/package.json:15-16` fija ambas versiones; `web/src/app/login/login.component.ts:5-6,25` usa `axios` y `lodash/merge`, esta última sobre `JSON.parse` de `localStorage` (`:24`); `web/src/app/dashboard/dashboard.component.ts:4,43` usa `axios`. `web/package.json:10` define `audit:workshop` (`npm audit --audit-level=moderate`), cuyo resultado no está registrado.
- Impacto: no hay evidencia aquí de una vulnerabilidad aplicable; no se afirma ningún CVE. `merge` es el único uso de lodash y fusiona datos controlables desde el navegador, lo que justifica revisarlo.
- Prioridad propuesta: baja hasta contar con el reporte; sube si un advisory aplica al uso observado.
- Contrato potencialmente afectado: llamadas HTTP a `/auth/login` y `/api/tasks`; carga de preferencias. Solo si se cambia o elimina una dependencia.
- Cómo validarlo: desde `web/`, `npm run audit:workshop` y `npm ls lodash axios`; revisar los advisories y si el uso (`merge` de preferencias, `axios.get/post`) está afectado, y adjuntar el reporte. No actualizar ni excluir hallazgos sin esa evidencia.

### H-WEB-04 — Componentes mezclan HTTP, URLs, sesión, navegación y tipado `any`
- Estado: CANDIDATO
- Evidencia: URLs fijas `http://localhost:8080/auth/login` (`web/src/app/login/login.component.ts:42`) y `http://localhost:8000/api/tasks` (`web/src/app/dashboard/dashboard.component.ts:43`); `localStorage` y claves repetidas en ambos componentes (`login.component.ts:24,47-49`; `dashboard.component.ts:36-37,75-76`); `: any` en estado y respuestas (`login.component.ts:16-20,41`; `dashboard.component.ts:23-27,43,54`; `web/src/app/utils/legacy-format.ts:1`); `if` anidados en `login.component.ts:33-60`; `owner` se arma por concatenación en la URL (`dashboard.component.ts:43`).
- Impacto: cambiar un endpoint, clave de sesión o el formato de datos exige editar componentes; no se puede probar la lógica sin HTTP/DOM. La URL fija impide usar otra base en un despliegue distinto de localhost. El `owner` enviado por el cliente se relaciona con el riesgo H-API-03 del componente api; no se verificó aquí su efecto.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: `POST /auth/login`, `GET /api/tasks`, claves de sesión y navegación `/login`↔`/dashboard`; deben conservarse al refactorizar.
- Cómo validarlo: antes de refactorizar, pruebas de caracterización con `axios` y `localStorage` simulados que fijen URL, cuerpo, header y navegación; después comprobar que pasan sin cambios y que no queda `any`/URL/clave literal en los componentes.

### H-WEB-05 — Orden por prioridad comparado como texto y cobertura limitada a una utilidad
- Estado: CANDIDATO
- Evidencia: `web/src/app/dashboard/dashboard.component.ts:54` ordena con `a.priority > b.priority ? -1 : 1` sobre cadenas (nunca devuelve 0); `docs/ENDPOINTS.md` documenta valores como `HIGH` y `MEDIUM`. El único spec es `web/src/app/utils/legacy-format.spec.ts:5-7` (un caso) y `web/vitest.config.ts:5` solo incluye `src/**/*.spec.ts` en entorno `node`.
- Impacto: por orden alfabético descendente `MEDIUM` precede a `LOW` y a `HIGH`, así que las tareas de prioridad alta quedarían al final (por confirmar con datos reales). Sin pruebas de login, dashboard ni reglas de negocio, un cambio de este tipo o un refactor pasaría inadvertido.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: orden y filtrado (`ARCHIVED`) de tareas mostradas desde `GET /api/tasks`, y etiquetas de `formatTaskLabel`/`statusLabel`.
- Cómo validarlo: prueba unitaria con tareas `HIGH`, `MEDIUM`, `LOW` que fije el orden actual y el esperado acordado con producto; `python tools/lab.py test web` (o `npm test` en `web/`) y `npm run test:coverage` para medir cobertura base frente a la meta de 85% del código modificado.

Detener discovery aquí para revisión humana de estos cinco candidatos; no se
crearon issues automáticamente.
