# Design

## Context

Ver [proposal.md](proposal.md) para motivación y alcance, y las specs para los requisitos observables.

- `auth/src/main/resources/application.properties` externaliza `AUTH_TOKEN_SIGNING_KEY_LOCATION` en dev/prod, define RS256 y actualmente permite CORS con `/.*/`.
- `SigningKeyStartupCheck` impide iniciar si la clave falta, no se puede leer o no es una clave privada válida para RS256. `TokenService` emite `sub`, `role` y `exp`; las pruebas ya verifican firma y claims.
- `AuthService` autentica contra `AuthRepository` y emite el token tras validar la contraseña. `LegacyDatabase` desactiva el seeding en prod, pero no identifica ni elimina filas demo preexistentes. `NoDemoSeedingTest` comprueba solo que no se creen cuentas nuevas cuando el seeding está desactivado.
- Decisión aprobada por el solicitante (2026-10-29): producción permite el origen exacto `https://www.viabcp.com`; el path `/cas/*` no forma parte del header CORS `Origin`. Las cuentas semilla `student` y `trainer` se bloquean en producción, sin modificar filas; dev/test conserva el comportamiento actual.

## Goals / Non-Goals

**Goals:**

- Mantener la firma RS256, los claims y el formato de `POST /auth/login` para cuentas legítimas.
- Fallar de forma cerrada ante una clave de firma ausente o inválida, sin incluir claves en el repositorio ni emitir tokens de respaldo.
- Limitar la autorización CORS de producción a la allowlist aprobada, sin alterar el payload de login.
- Una vez aprobada la regla de identificación, impedir tokens para las cuentas demo preexistentes identificadas y preservar el acceso de cuentas legítimas.
- Mantener las cuentas persistidas intactas y hacer reversible el cambio sin una migración destructiva.

**Non-Goals:**

- Añadir otros orígenes a la allowlist aprobada o intentar restringir CORS por path.
- Borrar, desactivar o reescribir datos de cuentas existentes.
- Cambiar endpoints, formato de request/response, expiración o claims JWT, ni modificar `api/` o `web/`.
- Rediseñar la autenticación o la persistencia completa.

## Contratos protegidos

- `POST /auth/login` conserva campos requeridos `username` y `password`, `client` opcional y las respuestas documentadas en `docs/ENDPOINTS.md`: `200` al autenticar, `400` para campos inválidos, `401` para credenciales no válidas y `500` para el error interno caracterizado.
- La respuesta exitosa mantiene `token`, `expiresIn`, `user` y `debug`. El JWT mantiene firma RS256 y claims `sub`, `role` y `exp`.
- La ruta de clave continúa siendo externa. Si falta o no es válida, el runtime no queda disponible para emitir tokens, conforme al comportamiento actual documentado.
- La elegibilidad CORS es el único cambio deliberado en el acceso por origen: producción autoriza solo `https://www.viabcp.com`. El path `/cas/*` no viaja en `Origin` y no se usa como criterio CORS. Métodos, headers y contrato HTTP de login quedan fuera de ese cambio.
- En producción, `student` y `trainer` no reciben token y obtienen el `401` existente para credenciales inválidas. Dev/test conserva el login de esas cuentas; ninguna fila persistida se modifica.

## Componentes afectados

- `auth/src/main/resources/application.properties`: comportamiento de CORS por entorno y configuración de la clave.
- `auth/src/main/java/com/bcp/workshop/auth/`: validación de login, decisión de elegibilidad de cuenta y emisión de token, solo en lo necesario para cumplir las specs.
- `auth/src/test/java/com/bcp/workshop/auth/`: pruebas de firma/configuración, login, cuentas persistidas y preflight.
- Documentación general, solo en las secciones de auth de `docs/ENDPOINTS.md` y para actualizar H-AUTH-05/H-AUTH-08 en `HALLAZGOS.md`; preservar las secciones de `api` y `web`.

No se modifican componentes fuera de `auth/` ni sus pruebas.

## Decisions

### Clave de firma y emisión JWT

Se conserva la configuración externa existente mediante `AUTH_TOKEN_SIGNING_KEY_LOCATION` y la validación de startup. La ausencia o invalidez de la clave continúa impidiendo que el servicio emita tokens; no se añade una clave por defecto ni se genera una clave efímera en runtime.

**Alternativas consideradas:** incluir una clave en el repositorio o generar una automáticamente para mantener el servicio disponible. Se descartan porque exponen material de firma o permiten emitir tokens con una identidad criptográfica inestable. Las pruebas usan claves RSA efímeras en directorios temporales, nunca claves de despliegue.

### Cuentas demo persistidas

La regla aprobada identifica como cuentas demo los usernames exactos `student` y `trainer`, que son los usuarios creados por `LegacyDatabase.seedDefaultUsers`. Solo producción los rechaza, después de verificar las credenciales y antes de emitir el JWT; la respuesta es el `401` existente. Dev/test mantiene el comportamiento actual del taller. El cambio no altera ni elimina filas.

**Riesgo aceptado:** si una cuenta legítima de producción reutiliza exactamente uno de esos usernames, también quedará bloqueada. El control de prueba usa un username distinto para demostrar que las cuentas legítimas no afectadas mantienen el login.

### CORS de producción

Producción autoriza solo el origen exacto `https://www.viabcp.com`; los perfiles de desarrollo y prueba conservan `/.*/`. Los navegadores no incluyen el path `/cas/*` en el header `Origin`, por lo que la política no puede distinguir paths de la misma web. No se aplica una restricción de rutas HTTP del API porque no forma parte de este cambio aprobado.

**Alternativas consideradas:** mantener `/.*/`, lo que no resuelve H-AUTH-05; aplicar la restricción globalmente, lo que cambia el flujo local fuera del alcance indicado; o codificar dominios sin aprobación, lo que inventaría una política. Se elige una restricción de producción ligada a la allowlist aprobada.

### Persistencia y reversibilidad

El cambio no necesita migrar, borrar ni reescribir registros para negar el login a una cuenta identificada. La base conserva sus filas y el control de acceso se aplica al flujo de autenticación. Esto reduce el riesgo de afectar a usuarios legítimos y permite revertir el comportamiento sin reconstruir datos.

## Estrategia incremental

1. Decisiones de identificación de cuentas demo y allowlist CORS registradas el 2026-10-29; usar únicamente los usernames y el origen enumerados en este diseño.
2. Añadir o conservar pruebas de caracterización y ejecutarlas en `auth/` antes de cambiar el comportamiento. Mantener el contrato de login y de firma como control de regresión.
3. Implementar cada política solo con sus datos aprobados, de forma aislada y revisable; no mezclar la decisión de cuentas con la de orígenes CORS.
4. Ejecutar `mvn verify` desde `auth/`, revisar JaCoCo para el código modificado y cumplir la meta del componente de al menos 85% de cobertura.
5. Actualizar únicamente la documentación de auth y los estados H-AUTH-05/H-AUTH-08 después de obtener resultados reales; no declarar un hallazgo resuelto antes de esa evidencia.

## Pruebas de caracterización

- **Firma y contrato:** conservar `issuesSignedTokenWithIdentityRoleAndExpiration` y los casos de login exitoso de `AuthResourceTest`; deben verificar firma RS256, claims, respuesta `200` y campos del DTO. `SigningKeyStartupCheckTest` ya cubre ubicación faltante, archivo inexistente y contenido inválido con fixtures temporales; mantener esos casos antes de alterar configuración.
- **Errores HTTP:** conservar los casos de campos requeridos, credenciales inválidas y error interno para detectar cambios accidentales en los códigos o cuerpos documentados.
- **CORS:** `ProductionAuthPolicyTest` caracteriza que producción actualmente permite el origen aprobado y otro no listado; después exige que solo el aprobado reciba `Access-Control-Allow-Origin`.
- **Filas preexistentes:** `ProductionAuthPolicyTest` usa una SQLite temporal con `student`, `trainer` y `legitimate-control`. Antes del filtro se registró `200` para las tres cuentas; después, producción debe responder `401` para las demos y preservar `200`/JWT para el control. No se usan ni modifican bases reales.

## Risks / Trade-offs

- **Regla de clasificación ambigua →** una falsa coincidencia puede bloquear a un usuario legítimo; mantener la decisión como gate y exigir una cuenta legítima de control en la misma prueba aislada.
- **Allowlist no provista →** no es posible validar qué origen real debe permitirse; usar fixtures sintéticas solo para probar la semántica y no cerrar H-AUTH-05 hasta validar la allowlist aprobada.
- **Clave inválida detiene el servicio →** protege contra tokens sin firma válida, pero reduce disponibilidad; conservar el fallo cerrado y verificar configuración antes del despliegue.
- **Rollback de CORS →** restaurar `/.*/` reabre el riesgo conocido; cualquier excepción debe ser temporal, aprobada y seguida de volver a la allowlist.
- **Cobertura insuficiente en ramas nuevas →** ejecutar JaCoCo en `auth/` y cumplir el mínimo de 85% del código modificado antes de integrar.

## Migration Plan

No se requiere migración de base de datos. Desplegar primero configuración y pruebas en un entorno aislado con la clave efímera; promover a producción solo con clave y allowlist aprobadas y con la regla de cuentas documentada. Si una verificación falla, revertir la versión/configuración de auth sin cambiar ni borrar filas de la base. El rollback de CORS a `/.*/` debe tratarse como una excepción de seguridad, no como estado estable. Nunca sustituir una clave de producción por una clave incluida en el repositorio.