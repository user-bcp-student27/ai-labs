# Pruebas de auth

Este documento relaciona las pruebas ejecutables con `auth/` y los IDs de
HALLAZGOS.md. Los ejemplos de código son extractos de los tests; los archivos
enlazados contienen las implementaciones completas.

## Matriz de casos

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | `returnsUpWithoutAuthentication` | Camino feliz | 200, `status=UP`; no requiere `Authorization`. El test registra un check `UP` solo en el perfil de pruebas. |
| `GET /q/health` | `supportsWildcardAcceptHeaderWithoutAuthentication` | Borde | 200 con `Accept: */*`, sin autenticación. |
| `GET /q/health` | `returnsServiceUnavailableWhenAHealthCheckIsDown` | Error | 503, `status=DOWN`, check de prueba en `DOWN`. |
| `GET /q/health` | Error interno del productor (HTTP 500) | Pendiente | auth no tiene un check propio ni un camino controlado que produzca este fallo; no se inventa cuerpo ni se afirma que esté probado. |
| `POST /auth/login` | `logsInWithWorkshopUser` | Camino feliz | 200 con `token`, `expiresIn`, `user` y `debug`; `token` no vacío. La cuenta es fixture dev/test. |
| `POST /auth/login` | `logsInWithWorkshopTrainer`; `logsInWithoutOptionalClientField` | Borde | 200 para trainer; `client` puede omitirse. |
| `POST /auth/login` | `rejectsMissingRequiredFields`; `rejectsBlankRequiredFields`; request nulo | Error / borde | 400 con el mensaje correspondiente; null se comprueba en el test unitario del Resource. |
| `POST /auth/login` | `rejectsInvalidCredentials`; `rejectsCredentialsContainingAQuote` | Error | 401 para credenciales inválidas; no autenticar un nombre con comilla inválido. |
| `POST /auth/login` | `rejectsMalformedJson` | Error de deserialización | 400. El status se comprueba; el cuerpo lo genera Quarkus y no se fija como contrato de auth. |
| `POST /auth/login` | `rejectsUnsupportedContentType` | Error de media type | 415 para `text/plain`. El cuerpo lo genera Quarkus y no se fija como contrato de auth. |
| `POST /auth/login` | `returnsGenericErrorWithoutExposingExceptionDetails` | Error interno | 500 con `message=Internal server error` y `exception=INTERNAL_ERROR` cuando el Service arroja `IllegalStateException`. Otros tipos de excepción siguen pendientes en H-AUTH-04. |
| `POST /auth/login` | `issuesSignedTokenWithIdentityRoleAndExpiration` | Seguridad / camino feliz | 200; firma RS256 verificable con clave efímera, claims `sub`, `role`, `exp`. |
| `POST /auth/login` | `migratesLegacyPasswordIdempotentlyAndPreservesLogin` | Regresión de persistencia | Hash PBKDF2 almacenado idempotentemente y login válido conservado. |
| `POST /auth/login` | `doesNotSeedDemoAccountsWhenSeedingIsDisabled` | Seguridad / configuración | Con `auth.seed-demo-users=false` en una base vacía, no crear usuarios demo. No cubre cuentas demo ya persistidas; H-AUTH-08 sigue Pendiente. |
| `POST /auth/login` | `escapesDisplayNameInCurrentLoginResponse`; `hashesWithUniqueSaltAndVerifiesMatchingPassword` | Seguridad / regresión | Escape HTML conservado; hash con salt distinto y verificación correcta de contraseña. |

## Código de las pruebas

Las clases completas están en [AuthResourceTest.java](../auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java),
[AuthResourceUnitTest.java](../auth/src/test/java/com/bcp/workshop/auth/AuthResourceUnitTest.java),
[HealthEndpointTest.java](../auth/src/test/java/com/bcp/workshop/auth/HealthEndpointTest.java),
[AuthTestHealthCheck.java](../auth/src/test/java/com/bcp/workshop/auth/AuthTestHealthCheck.java),
[NoDemoSeedingTest.java](../auth/src/test/java/com/bcp/workshop/auth/NoDemoSeedingTest.java),
[SigningKeyStartupCheckTest.java](../auth/src/test/java/com/bcp/workshop/auth/SigningKeyStartupCheckTest.java)
y [PasswordHasherTest.java](../auth/src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java).

### Health

```java
@Test
void returnsUpWithoutAuthentication() {
    given()
            .accept("application/json")
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("status", is("UP"))
            .body("checks[0].name", is("auth-test-health"))
            .body("checks[0].status", is("UP"));
}

@Test
void returnsServiceUnavailableWhenAHealthCheckIsDown() {
    AuthTestHealthCheck.setHealthStatus(AuthTestHealthCheck.HealthStatus.DOWN);

    given()
            .when().get("/q/health")
            .then()
            .statusCode(503)
            .body("status", is("DOWN"))
            .body("checks[0].name", is("auth-test-health"))
            .body("checks[0].status", is("DOWN"));
}
```

### Login

```java
@Test
void logsInWithWorkshopUser() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(200)
            .body("user.username", is("student"))
            .body("user.displayName", is("Workshop Student"))
            .body("user.role", is("STUDENT"))
            .body("token", notNullValue())
            .body("expiresIn", is(3600))
            .body("debug", is("authenticated-with-legacy-flow"));
}

@Test
void rejectsInvalidCredentials() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"invalid\",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(401);
}

@Test
void rejectsMalformedJson() {
    given()
            .contentType("application/json")
            .body("{")
            .when().post("/auth/login")
            .then()
            .statusCode(400);
}

@Test
void rejectsUnsupportedContentType() {
    given()
            .contentType("text/plain")
            .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(415);
}
```

### Error interno

```java
@Test
void returnsGenericErrorWithoutExposingExceptionDetails() {
    AuthResource resource = new AuthResource();
    resource.authService = new AuthService() {
        @Override
        public LoginResponse login(String username, String password) {
            throw new IllegalStateException("fixture database failure");
        }
    };

    Response response = resource.login(loginRequest());
    Map<?, ?> body = (Map<?, ?>) response.getEntity();

    assertEquals(500, response.getStatus());
    assertEquals("Internal server error", body.get("message"));
    assertEquals("INTERNAL_ERROR", body.get("exception"));
}
```

## Ejecuciones registradas

| componente | IDs | comando | cwd | código de salida | resultado |
|---|---|---|---|---:|---|
| auth | H-AUTH-02–H-AUTH-08 | `mvn verify` (con `AUTH_DB_URL` apuntando a SQLite temporal y clave RSA efímera generada por `AuthJwtTestResource`) | `auth/` | 0 | 22 pruebas; 0 fallas, 0 errores. JaCoCo por líneas: `LegacyDatabase` 89.2%, `PasswordHasher` 92.9%, `AuthResource`, `AuthService` y `TokenService` 100%. |
| auth | H-AUTH-08 | `mvn -Dtest=NoDemoSeedingTest test` | `auth/` | 0 | 1 prueba; seeding deshabilitado no crea las cuentas demo en la base temporal vacía. |
| auth | H-AUTH-08 | `mvn -Dtest=NoDemoSeedingTest test` (antes de corregir el seeding) | `auth/` | 1 | Falló como se esperaba: encontró dos cuentas demo con `auth.seed-demo-users=false`. |
| auth | H-AUTH-03, H-AUTH-05 | `mvn -Dtest=SigningKeyStartupCheckTest test` | `auth/` | 0 | 3 pruebas: acepta clave RSA privada válida; rechaza ubicación ausente/vacía y archivos ausentes/inválidos. |
| auth | H-AUTH-03, H-AUTH-05 | `mvn verify` (baseline previo) | `auth/` | 0 | 22 pruebas, 0 fallas, 0 errores; SQLite temporal y clave efímera de prueba. |
| auth | H-AUTH-03, H-AUTH-05 | `mvn verify` (regresión posterior) | `auth/` | 0 | 25 pruebas, 0 fallas, 0 errores; JaCoCo: `SigningKeyStartupCheck` 100% de líneas. |

## Fallos corregidos, pendientes y no ejecutados

- **Fallos corregidos de harness:** un primer setup consultó una URL SQLite
  distinta a la inicializada; luego un test con dos perfiles Quarkus produjo
  `No suitable driver`. Se sustituyó por `NoDemoSeedingTest`, que crea y elimina
  una base temporal directa. El `mvn verify` final pasó.
- **Pendientes:** no existe en auth un check que haga fallar el productor de
  health para probar HTTP 500; H-AUTH-04 deja sin caracterizar excepciones
  distintas de `IllegalStateException`; H-AUTH-08 no verifica ni modifica
  cuentas demo ya persistidas; H-AUTH-05 no prueba la allowlist de producción.
- **Propuesto, no ejecutado:** agregar un check de health controlado que haga
  fallar el procesamiento para verificar si la respuesta 500 estándar resulta
  reproducible; diseñar una migración segura para cuentas demo legacy una vez
  aprobado cómo distinguirlas de cuentas legítimas.
- **No ejecutado:** CodeQL y Dependency Review remotos, conforme a la
  restricción del recorrido.
- **Alcance de ISSUE-01:** solo se implementa la validación de la ubicación y
  contenido de clave al inicio. CORS y disposición de cuentas demo persistidas
  quedan fuera de esta iteración y siguen pendientes en H-AUTH-05/H-AUTH-08.
