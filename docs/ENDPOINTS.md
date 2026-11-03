# Contrato observado de endpoints — auth

## Estado del contrato

- **Observado:** se deriva de `auth/` y de sus pruebas. Esta página se creó
  porque `docs/ENDPOINTS.md` no existía.
- **Acordado:** no consta una aprobación humana independiente de este contrato.
  Los ejemplos describen el comportamiento actual; no representan una garantía
  de compatibilidad adicional.
- **Riesgos pendientes:** H-AUTH-04 (errores no `IllegalStateException` sin
  respuesta caracterizada) y H-AUTH-06 (revisión SCA incompleta). H-AUTH-05 y
  H-AUTH-08 tienen implementación local verificada; el runtime y los datos de
  producción no se inspeccionaron. Ver [HALLAZGOS.md](../HALLAZGOS.md).

## `GET /q/health`

### 1. Qué hace

Expone el estado de salud agregado del runtime Quarkus para sondeos de
disponibilidad. Lo proporciona la extensión SmallRye Health incluida en
[`auth/pom.xml:40`](../auth/pom.xml#L40); la ruta publicada también aparece en
[`README.md:44`](../README.md#L44). No hay implementaciones `HealthCheck`
propias en el código Java de auth, por lo que el ejemplo normal tiene `checks`
vacío.

### 2. Autenticación y headers

- No requiere `Authorization` en la configuración observada de auth. La
  especificación MicroProfile Health dice que la seguridad es opt-in; un proxy
  o gateway externo podría añadirla, pero está fuera de este alcance.
- No tiene `Content-Type` de request porque no recibe body.
- `Accept: application/json` puede enviarse para declarar el formato deseado;
  la respuesta de health es JSON.

La aplicación configura el puerto y host en
[`application.properties:1`](../auth/src/main/resources/application.properties#L1)
y [`application.properties:2`](../auth/src/main/resources/application.properties#L2).

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| body | ninguno | no | `GET` no define cuerpo de request. |
| parámetros de query | ninguno | no | auth no define parámetros para esta ruta. |

Ejemplo HTTP completo, sin cuerpo:

```http
GET http://localhost:8080/q/health
Accept: application/json
```

### 4. Response exitosa

- **Código:** `200 OK` cuando el estado agregado es `UP`.
- El formato `status` + `checks` corresponde a MicroProfile Health. La
  especificación establece que, sin procedimientos de health esperados o
  instalados, el resultado es `UP` con una lista vacía. No se encontraron
  procedimientos propios en `auth/`.

| campo | tipo | descripción |
|---|---|---|
| `status` | string | Estado agregado; para esta respuesta es `UP`. |
| `checks` | array | Resultados individuales. Está vacío en el código actual de auth. |

Ejemplo JSON observado para el servicio sin checks propios:

```json
{
  "status": "UP",
  "checks": []
}
```

### 5. Respuestas de error

Las respuestas siguientes pertenecen al contrato estándar de SmallRye /
MicroProfile Health, no a una ruta escrita por auth. El código de auth no
registra checks personalizados; por ello no se observó en este componente una
causa concreta que produzca estos estados.

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `503 Service Unavailable` | El estado agregado es `DOWN`, por ejemplo si un check registrado falla. La lista y el nombre del check dependen de la implementación que lo registre. | `{"status":"DOWN","checks":[{"name":"<nombre-del-check>","status":"DOWN"}]}` |

**Pregunta abierta:** ¿qué checks de salud se esperan en despliegue y qué
información `data` pueden publicar? El código auth actual no define ninguno;
no se inventan nombres ni datos de checks. MicroProfile Health también define
500 para un fallo interno al procesar una comprobación, pero auth no registra
checks propios ni tiene un camino reproducible para ese error; no se documenta
un cuerpo como contrato y queda pendiente de prueba si se incorpora ese caso.

### 6. Notas de seguridad

- No se exige token de login en la configuración observada. Si el entorno
  necesita restringir este endpoint, debe hacerlo mediante configuración de
  despliegue aprobada, no asumir que `/q/health` requiere bearer token.
- No se verificó un runtime de producción ni políticas de proxy externas.

Referencia de formato: [Guía SmallRye Health de Quarkus](https://quarkus.io/guides/smallrye-health/)
y [especificación MicroProfile Health 4.0](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html).

## `POST /auth/login`

### 1. Qué hace

Autentica un usuario por nombre y contraseña y, si son válidos, devuelve un JWT
firmado junto con la información pública del usuario. La ruta se declara en
[`AuthResource.java:29`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L29)
y [`AuthResource.java:31`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L31).

### 2. Autenticación y headers

- **Autenticación previa:** no requiere bearer token; es el endpoint que inicia
  el login.
- **Request:** `Content-Type: application/json`.
- **Response:** `application/json`, declarado en el recurso con
  [`AuthResource.java:15`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L15)
  y [`AuthResource.java:16`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L16).
- `Accept: application/json` es recomendable; no hay otro header de auth
  requerido por el recurso.

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| `username` | string | sí | No puede ser nulo ni vacío/solo espacios. La validación no recorta el valor antes de autenticar. |
| `password` | string | sí | No puede ser nulo ni vacío/solo espacios. |
| `client` | string | no | Campo presente en el DTO, pero no se usa en el flujo actual. |

Los campos están declarados en
[`LoginRequest.java:4`](../auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java#L4),
[`LoginRequest.java:5`](../auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java#L5)
y [`LoginRequest.java:6`](../auth/src/main/java/com/bcp/workshop/auth/LoginRequest.java#L6).
Las validaciones están en
[`AuthResource.java:32`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L32),
[`AuthResource.java:35`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L35)
y [`AuthResource.java:38`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L38).

Ejemplo JSON completo con la cuenta ficticia de desarrollo/test que aparece
en las pruebas. **No usar estas credenciales en producción**; la cuenta puede
no existir si no está provisionada:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

El ejemplo de request está en
[`AuthResourceTest.java:44`](../auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L44).

### 4. Response exitosa

- **Código:** `200 OK`, cuando el Repository devuelve un usuario y el hash de
  contraseña verifica en el Service
  ([`AuthResource.java:43`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L43),
  [`AuthResource.java:51`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L51),
  [`AuthService.java:21`](../auth/src/main/java/com/bcp/workshop/auth/AuthService.java#L21),
  [`AuthService.java:22`](../auth/src/main/java/com/bcp/workshop/auth/AuthService.java#L22)).
- `expiresIn` es la duración del token en segundos; el valor actual es 3600,
  definido a partir de una hora en
  [`TokenService.java:11`](../auth/src/main/java/com/bcp/workshop/auth/TokenService.java#L11).
- `token` es un JWT RS256 con claims `sub`, `role` y `exp`, emitido en
  [`TokenService.java:18`](../auth/src/main/java/com/bcp/workshop/auth/TokenService.java#L18)
  y [`TokenService.java:21`](../auth/src/main/java/com/bcp/workshop/auth/TokenService.java#L21).
- La respuesta es un DTO con `token`, `expiresIn`, `user` y `debug`
  ([`LoginResponse.java:3`](../auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java#L3),
  [`LoginResponse.java:4`](../auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java#L4)).

| campo | tipo | descripción |
|---|---|---|
| `token` | string | JWT firmado; valor dinámico generado por el servidor. |
| `expiresIn` | integer | Duración declarada del token en segundos. |
| `user` | object | Datos públicos del usuario autenticado. |
| `user.username` | string | Nombre del usuario. |
| `user.displayName` | string | Nombre para mostrar; se aplica escape HTML4. |
| `user.role` | string | Rol asignado al usuario. |
| `debug` | string | Valor observado: `authenticated-with-legacy-flow`. |

Ejemplo JSON completo. El valor de `token` es un marcador porque cada JWT se
firma con una clave externa y cambia por emisión:

```json
{
  "token": "<JWT RS256 emitido por el servidor>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

### 5. Respuestas de error

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `400 Bad Request` | El cuerpo JSON representa `null`. | `{"message":"Request is null"}` |
| `400 Bad Request` | `username` falta, es nulo o contiene solo espacios. | `{"message":"Username is required"}` |
| `400 Bad Request` | `password` falta, es nulo o contiene solo espacios. | `{"message":"Password is required"}` |
| `401 Unauthorized` | El usuario no existe, la contraseña no verifica o el username es `student`/`trainer` en producción. El mensaje incluye el `username` recibido, pero no la contraseña. | `{"message":"Invalid user example-user or password"}` |
| `500 Internal Server Error` | El Service/Repository lanza `IllegalStateException`, por ejemplo ante un error de consulta SQL. | `{"message":"Internal server error","exception":"INTERNAL_ERROR"}` |
| `400 Bad Request` | JSON malformado antes de invocar `AuthResource.login`. | El cuerpo lo genera Jackson/Quarkus y no está definido por auth; ver pregunta abierta. |
| `415 Unsupported Media Type` | El request no usa `Content-Type: application/json`, requerido por `@Consumes`. | El cuerpo lo genera Quarkus y no está definido por auth; ver pregunta abierta. |

Los errores 400/401/500 se mapean en
[`AuthResource.java:32`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L32),
[`AuthResource.java:35`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L35),
[`AuthResource.java:38`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L38),
[`AuthResource.java:45`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L45)
y [`AuthResource.java:52`](../auth/src/main/java/com/bcp/workshop/auth/AuthResource.java#L52).

**Pregunta abierta:** el handler genérico captura `IllegalStateException`, no
todas las excepciones. El cuerpo HTTP para otros tipos no queda definido por
este recurso; H-AUTH-04 sigue Pendiente.

También queda pendiente caracterizar el status/cuerpo que Quarkus genera para
JSON malformado y media types no soportados; no se inventa una respuesta JSON
para errores que el recurso no construye.

### 6. Notas de seguridad

- El token es firmado RS256 y expira; el recurso requiere una ruta de clave
  externa `AUTH_TOKEN_SIGNING_KEY_LOCATION` en dev/prod
  ([`application.properties:14`](../auth/src/main/resources/application.properties#L14),
  [`application.properties:15`](../auth/src/main/resources/application.properties#L15),
  [`application.properties:16`](../auth/src/main/resources/application.properties#L16)).
  El servicio valida al inicio que la ubicación exista y contenga una clave
  privada RSA válida para RS256; si falta o no es válida, el arranque falla
  ([`SigningKeyStartupCheck.java`](../auth/src/main/java/com/bcp/workshop/auth/SigningKeyStartupCheck.java)).
  No se incluye una clave real ni de ejemplo en este documento.
- Las cuentas demo `student` y `trainer` se crean en dev/test. En producción,
  el login de esos usernames responde `401` incluso si sus filas ya existían;
  no se borran ni modifican registros. Dev/test conserva el login del taller.
- En producción CORS autoriza solo el origen `https://www.viabcp.com`; dev/test
  conserva la configuración amplia actual. El path `/cas/*` no forma parte del
  header `Origin`, por lo que la política CORS no restringe por path
  ([`application.properties`](../auth/src/main/resources/application.properties)).
- Los consumidores externos confirmaron compatibilidad con el formato JWT,
  pero no se inspeccionaron otros componentes; esta documentación describe
  únicamente el contrato observado en auth.

## Contrato observado de endpoints — api

### Estado del contrato

- **Observado:** las rutas y formas de respuesta siguientes se derivan de
  `api/app/main.py` y de sus pruebas. Los ejemplos usan el servidor local de
  `README.md` en `http://localhost:8000`.
- **Acordado:** no consta una aprobación humana independiente de un contrato
  API. Esta sección describe el comportamiento actual, no una garantía de
  compatibilidad adicional. Para H-API-05 se aprobaron la allowlist `.txt`, el
  máximo de 5 MiB, el rechazo de duplicados y los estados de error descritos
  abajo.
- **Riesgos pendientes:** H-API-01 requiere evidencia SCA; H-API-02 aún usa un
  token Base64 sin firma; H-API-03 permite seleccionar/asignar otro `owner`;
  H-API-05 normaliza y confina nombres, restringe extensión y tamaño, pero no
  limita el almacenamiento temporal usado por el parsing multipart previo.
  H-API-04 tiene consultas parametrizadas. Ver [HALLAZGOS.md](../HALLAZGOS.md).

### Autenticación común

Todas las rutas de api requieren el header exacto `Authorization: Bearer
<token>`. El token observado es Base64 de cuatro campos separados por `|`:
`username|role|expiracion|secreto`. El código compara el último campo con un
secreto fijo y verifica la expiración; no verifica firma, emisor ni audiencia
([`main.py:14`](../api/app/main.py#L14),
[`main.py:17`](../api/app/main.py#L17),
[`main.py:20-26`](../api/app/main.py#L20-L26)). Este formato es solo el flujo
legacy local observado y no debe usarse como mecanismo de autenticación de
producción.

Para generar un header válido para los ejemplos locales, PowerShell puede
crear un token con expiración de una hora. El secreto incluido es un valor
ficticio ya presente en el código del taller; no usar este ejemplo fuera de
localhost:

```powershell
$expiry = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + 3600
$rawToken = "student|STUDENT|$expiry|WORKSHOP_SECRET_2026"
$token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($rawToken))
$headers = @{ Authorization = "Bearer $token" }
```

Las respuestas de error de autenticación pueden variar en el texto de `detail`
según el fallo del token, porque el código concatena el texto de la excepción.
Ejemplo observado para un token que no cumple el formato:

```json
{"detail":"Invalid token: invalid token"}
```

## `GET /api/tasks`

### 1. Qué hace

Lista en orden de id las tareas del propietario seleccionado.

### 2. Autenticación y headers

- Requiere `Authorization: Bearer <token>` como se describe en
  [Autenticación común](#autenticación-común).
- No requiere body ni `Content-Type`.
- `Accept: application/json` es opcional.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| `owner` (query) | string | no | Propietario por el que filtrar. Si se omite o está vacío, se usa el `username` del token. Un valor no vacío enviado por el cliente prevalece sobre la identidad. |

Ejemplo completo, después de definir `$headers` con el bloque de autenticación:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8000/api/tasks" -Headers $headers
```

### 4. Response exitosa

- **Código:** `200 OK`.
- La respuesta es un array; puede estar vacío.

| campo | tipo | descripción |
|---|---|---|
| `id` | integer | Identificador de la tarea en SQLite. |
| `title` | string | Título. |
| `description` | string | Descripción. |
| `status` | string | Estado almacenado. |
| `owner` | string | Propietario almacenado. |
| `priority` | string | Prioridad almacenada. |
| `createdAt` | string | Fecha y hora almacenadas como texto SQLite. |

Ejemplo con las tareas iniciales de la base nueva (los ids y timestamps pueden variar):

```json
[
  {"id":1,"title":"Refactor login","description":"Separar responsabilidades en frontend","status":"TODO","owner":"student","priority":"HIGH","createdAt":"2026-11-02 12:00:00"},
  {"id":2,"title":"Review API","description":"Revisar seguridad de endpoints","status":"IN_PROGRESS","owner":"student","priority":"MEDIUM","createdAt":"2026-11-02 12:00:00"}
]
```

### 5. Respuestas de error

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido/expirado. | `{"detail":"Invalid token: invalid token"}` |
| `500 Internal Server Error` | Error no manejado al consultar SQLite. FastAPI genera la respuesta genérica. | `{"detail":"Internal Server Error"}` |

### 6. Notas de seguridad

- El token no está firmado ni valida emisor/audiencia; `role` tampoco se usa
  para autorizar esta consulta.
- El parámetro `owner` enviado por el cliente puede seleccionar tareas de otro
  usuario; el valor se parametriza en SQL, pero no queda ligado a la identidad.
- La consulta usa un parámetro SQLite enlazado
  ([`main.py:69-74`](../api/app/main.py#L69-L74)).

## `POST /api/tasks`

### 1. Qué hace

Crea una tarea en SQLite y devuelve la fila creada.

### 2. Autenticación y headers

- Requiere `Authorization: Bearer <token>` como se describe en
  [Autenticación común](#autenticación-común).
- Requiere `Content-Type: application/json` para enviar el objeto JSON.
- `Accept: application/json` es opcional.

### 3. Request

El body debe ser un objeto JSON; no hay propiedades obligatorias en el código.
Los valores se convierten a texto con `str()`.

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| `title` | string | no | Título; valor predeterminado `""`. |
| `description` | string | no | Descripción; valor predeterminado `""`. |
| `status` | string | no | Estado; valor predeterminado `"TODO"`. |
| `priority` | string | no | Prioridad; valor predeterminado `"MEDIUM"`. |
| `owner` | string | no | Propietario; valor predeterminado al `username` del token. Si se envía, el código usa ese valor. |

Ejemplo completo:

```powershell
$body = @{
  title = "Add tests"
  description = "Improve coverage"
  status = "TODO"
  priority = "HIGH"
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8000/api/tasks" -Headers $headers -ContentType "application/json" -Body $body
```

### 4. Response exitosa

- **Código:** `201 Created`.

| campo | tipo | descripción |
|---|---|---|
| `id` | integer | Identificador asignado por SQLite. |
| `title` | string | Título almacenado. |
| `description` | string | Descripción almacenada. |
| `status` | string | Estado almacenado. |
| `owner` | string | Propietario almacenado. |
| `priority` | string | Prioridad almacenada. |
| `createdAt` | string | Fecha y hora almacenadas como texto SQLite. |

Ejemplo (el id y timestamp dependen de la base):

```json
{"id":4,"title":"Add tests","description":"Improve coverage","status":"TODO","owner":"student","priority":"HIGH","createdAt":"2026-11-02 12:00:00"}
```

### 5. Respuestas de error

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido/expirado. | `{"detail":"Invalid token: invalid token"}` |
| `422 Unprocessable Entity` | Falta el body o el JSON no representa un objeto. FastAPI genera este error de validación. | `{"detail":[{"type":"missing","loc":["body"],"msg":"Field required","input":null}]}` |
| `500 Internal Server Error` | Error no manejado al insertar o recuperar la tarea desde SQLite. | `{"detail":"Internal Server Error"}` |

### 6. Notas de seguridad

- `owner` del body se acepta y se almacena; no se fuerza al `username` del
  token. El formato de token tampoco prueba criptográficamente esa identidad.
- El insert y la recuperación por id usan parámetros enlazados
  ([`main.py:83-99`](../api/app/main.py#L83-L99)).
- El body se acepta como diccionario sin modelo Pydantic; los nombres de
  propiedad desconocidos se ignoran porque la ruta solo lee los cinco campos
  documentados.

## `GET /api/summary`

### 1. Qué hace

Resume el total y los estados de las tareas del propietario seleccionado, e
indica las tareas HIGH que no están DONE ni ARCHIVED.

### 2. Autenticación y headers

- Requiere `Authorization: Bearer <token>` como se describe en
  [Autenticación común](#autenticación-común).
- No requiere body ni `Content-Type`.
- `Accept: application/json` es opcional.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| `owner` (query) | string | no | Propietario por el que resumir. Si se omite o está vacío, se usa el `username` del token. Un valor no vacío enviado por el cliente prevalece sobre la identidad. |

Ejemplo completo, después de definir `$headers`:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8000/api/summary" -Headers $headers
```

### 4. Response exitosa

- **Código:** `200 OK`.

| campo | tipo | descripción |
|---|---|---|
| `owner` | string | Propietario resumido. |
| `total` | integer | Número total de tareas del propietario. |
| `byStatus` | object | Mapa de estado a cantidad de tareas. |
| `highPriorityOpen` | integer | Conteo de tareas `HIGH` cuyo estado no es `DONE` ni `ARCHIVED`. |

Ejemplo con la base inicial para `student`:

```json
{"owner":"student","total":2,"byStatus":{"TODO":1,"IN_PROGRESS":1},"highPriorityOpen":1}
```

### 5. Respuestas de error

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido/expirado. | `{"detail":"Invalid token: invalid token"}` |
| `500 Internal Server Error` | Error no manejado durante la consulta SQLite. | `{"detail":"Internal Server Error"}` |

### 6. Notas de seguridad

- El parámetro `owner` puede pedir el resumen de otro usuario; `role` no se
  consulta para autorizar la operación.
- La consulta usa un parámetro SQLite enlazado
  ([`main.py:101-106`](../api/app/main.py#L101-L106)).
- El token se decodifica como Base64 sin verificar firma, emisor o audiencia.

## `POST /api/requirements`

### 1. Qué hace

Guarda el archivo de requerimientos recibido en el directorio configurado para
uploads y devuelve sus metadatos.

### 2. Autenticación y headers

- Requiere `Authorization: Bearer <token>` como se describe en
  [Autenticación común](#autenticación-común).
- Requiere un body `multipart/form-data` con la parte `file`.
- No se debe fijar manualmente el boundary de multipart; el cliente HTTP lo
  genera.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| `file` (form-data) | archivo binario | sí | Archivo `.txt`, con máximo de 5 MiB. Los separadores `/` y `\` se normalizan al nombre base. |

Ejemplo completo usando un archivo local `requirements.txt`:

```powershell
curl.exe -X POST "http://localhost:8000/api/requirements" -H "Authorization: Bearer $token" -F "file=@requirements.txt;type=text/plain"
```

### 4. Response exitosa

- **Código:** `201 Created`.

| campo | tipo | descripción |
|---|---|---|
| `owner` | string | `username` extraído del token. |
| `fileName` | string | Nombre base normalizado de la parte multipart. |
| `storedAt` | string | Ruta del destino calculado por el servidor. |
| `size` | integer | Número de bytes escritos. |

Ejemplo con el directorio por defecto `./uploads`:

```json
{"owner":"student","fileName":"requirements.txt","storedAt":"uploads/requirements.txt","size":25}
```

### 5. Respuestas de error

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido/expirado. | `{"detail":"Invalid token: invalid token"}` |
| `400 Bad Request` | Nombre inválido. | `{"detail":"Invalid file name"}` |
| `400 Bad Request` | Extensión distinta de `.txt`. | `{"detail":"File extension is not allowed"}` |
| `409 Conflict` | Ya existe un archivo con el nombre normalizado. No se sobrescribe. | `{"detail":"File already exists"}` |
| `413 Content Too Large` | El archivo excede 5 MiB. | `{"detail":"File exceeds the maximum allowed size"}` |
| `422 Unprocessable Entity` | Falta la parte requerida `file`; multipart con nombre de archivo vacío también se interpreta como campo ausente. | `{"detail":[{"type":"missing","loc":["body","file"],"msg":"Field required"}]}` |
| `500 Internal Server Error` | No se puede escribir el archivo. | `{"detail":"Unable to store uploaded file"}` |
| `500 Internal Server Error` | No se puede eliminar una escritura parcial. | `{"detail":"Unable to remove partial upload"}` |

### 6. Notas de seguridad

- El nombre se normaliza al componente final, se comprueba que el destino siga
  dentro del directorio de uploads y no se sobrescriben archivos existentes.
- Solo se acepta `.txt` (sin distinción de mayúsculas) y se escriben como máximo
  5 MiB en bloques de 64 KiB
  ([`main.py:17`](../api/app/main.py#L17);
  [`main.py:20`](../api/app/main.py#L20);
  [`main.py:21`](../api/app/main.py#L21);
  [`main.py:183`](../api/app/main.py#L183)).
- El límite se aplica al archivo procesado por el endpoint; el parsing multipart
  ocurre antes de la ruta y puede usar almacenamiento temporal. No se afirma
  aquí un límite de tamaño para ese almacenamiento previo.
- La configuración observada es `WORKSHOP_UPLOAD_DIR` o `./uploads`
  ([`main.py:29-33`](../api/app/main.py#L29-L33)).

## `GET /api/backend-health`

### 1. Qué hace

Consulta el endpoint de salud de auth para exponer el status y body devueltos
por ese servicio.

### 2. Autenticación y headers

- Requiere `Authorization: Bearer <token>` como se describe en
  [Autenticación común](#autenticación-común).
- No requiere body, parámetros query ni `Content-Type`.
- `Accept: application/json` es opcional.

### 3. Request

| campo/parámetro | tipo | obligatorio | descripción |
|---|---|---|---|
| body | ninguno | no | El endpoint no recibe body. |
| parámetros de query | ninguno | no | La URL del servicio consultado se configura en el servidor, no en la query. |

Ejemplo completo, después de definir `$headers`:

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8000/api/backend-health" -Headers $headers
```

### 4. Response exitosa

- **Código:** `200 OK`, incluso si la solicitud al servicio dependiente lanza
  una excepción que la ruta captura.

| campo | tipo | descripción |
|---|---|---|
| `requestedBy` | string | `username` extraído del token. |
| `status` | integer | Status HTTP del servicio consultado; `0` si la solicitud lanza una excepción. |
| `body` | string | Body remoto decodificado como UTF-8 o texto de la excepción capturada. |

Ejemplo cuando auth devuelve `200`:

```json
{"requestedBy":"student","status":200,"body":"{\"status\":\"UP\",\"checks\":[]}"}
```

### 5. Respuestas de error

| código | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `401 Unauthorized` | Header ausente o token inválido/expirado. | `{"detail":"Invalid token: invalid token"}` |

La ruta captura excepciones de conexión/solicitud al backend y en ese caso
responde `200 OK` con `status: 0`; no se produce una respuesta HTTP de error
para ese camino. Ejemplo de respuesta exitosa con fallo de conexión:

```json
{"requestedBy":"student","status":0,"body":"<texto de excepción de urllib3>"}
```

### 6. Notas de seguridad

- El destino se obtiene de `AUTH_HEALTH_URL`, con valor predeterminado
  `http://localhost:8080/q/health`; no es controlable por un parámetro de esta
  ruta ([`main.py:127-133`](../api/app/main.py#L127-L133)).
- La respuesta puede incluir el texto crudo de una excepción de urllib3 en
  `body`; el código no lo normaliza.
- La ruta requiere el mismo token legacy no firmado que los demás endpoints.
