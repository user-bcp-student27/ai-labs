# Contrato observado de endpoints — auth

## Estado del contrato

- **Observado:** se deriva de `auth/` y de sus pruebas. Esta página se creó
  porque `docs/ENDPOINTS.md` no existía.
- **Acordado:** no consta una aprobación humana independiente de este contrato.
  Los ejemplos describen el comportamiento actual; no representan una garantía
  de compatibilidad adicional.
- **Riesgos pendientes:** H-AUTH-04 (errores no `IllegalStateException` sin
  respuesta caracterizada), H-AUTH-05 (CORS sin allowlist de producción),
  H-AUTH-06 (revisión SCA incompleta) y H-AUTH-08 (cuentas demo previas que
  podrían persistir en bases existentes). Ver [HALLAZGOS.md](../HALLAZGOS.md).

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
| `401 Unauthorized` | El usuario no existe o la contraseña no verifica. El mensaje incluye el `username` recibido, pero no la contraseña. | `{"message":"Invalid user example-user or password"}` |
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
  No se incluye una clave real ni de ejemplo en este documento.
- Las cuentas demo se crean en dev/test, no en nuevas bases de producción.
  H-AUTH-08 sigue Pendiente porque una base existente puede conservar cuentas
  demo de una versión anterior.
- CORS conserva la expresión amplia `/.*/`; H-AUTH-05 sigue Pendiente hasta
  acordar la allowlist de producción
  ([`application.properties:4`](../auth/src/main/resources/application.properties#L4),
  [`application.properties:5`](../auth/src/main/resources/application.properties#L5)).
- Los consumidores externos confirmaron compatibilidad con el formato JWT,
  pero no se inspeccionaron otros componentes; esta documentación describe
  únicamente el contrato observado en auth.
