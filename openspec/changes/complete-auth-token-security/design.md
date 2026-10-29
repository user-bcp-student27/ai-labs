# Design

## Context

Ver [proposal.md](proposal.md) para motivación y alcance, y las specs para los requisitos observables.

- `auth/src/main/resources/application.properties` externaliza `AUTH_TOKEN_SIGNING_KEY_LOCATION` en dev/prod, define RS256 y actualmente permite CORS con `/.*/`.
- `SigningKeyStartupCheck` impide iniciar si la clave falta, no se puede leer o no es una clave privada válida para RS256. `TokenService` emite `sub`, `role` y `exp`; las pruebas ya verifican firma y claims.
- `AuthService` autentica contra `AuthRepository` y emite el token tras validar la contraseña. `LegacyDatabase` desactiva el seeding en prod, pero no identifica ni elimina filas demo preexistentes. `NoDemoSeedingTest` comprueba solo que no se creen cuentas nuevas cuando el seeding está desactivado.
- No se ha aprobado la regla para distinguir cuentas demo persistidas ni la allowlist de producción. Son prerequisitos de decisión, no valores que este diseño pueda inferir.

## Goals / Non-Goals

**Goals:**

- Mantener la firma RS256, los claims y el formato de `POST /auth/login` para cuentas legítimas.
- Fallar de forma cerrada ante una clave de firma ausente o inválida, sin incluir claves en el repositorio ni emitir tokens de respaldo.
- Limitar la autorización CORS de producción a la allowlist aprobada, sin alterar el payload de login.
- Una vez aprobada la regla de identificación, impedir tokens para las cuentas demo preexistentes identificadas y preservar el acceso de cuentas legítimas.
- Mantener las cuentas persistidas intactas y hacer reversible el cambio sin una migración destructiva.

**Non-Goals:**

- Definir o inferir la allowlist, los dominios permitidos o la regla de clasificación de cuentas.
- Borrar, desactivar o reescribir datos de cuentas existentes.
- Cambiar endpoints, formato de request/response, expiración o claims JWT, ni modificar `api/` o `web/`.
- Rediseñar la autenticación o la persistencia completa.

## Contratos protegidos

- `POST /auth/login` conserva campos requeridos `username` y `password`, `client` opcional y las respuestas documentadas en `docs/ENDPOINTS.md`: `200` al autenticar, `400` para campos inválidos, `401` para credenciales no válidas y `500` para el error interno caracterizado.
- La respuesta exitosa mantiene `token`, `expiresIn`, `user` y `debug`. El JWT mantiene firma RS256 y claims `sub`, `role` y `exp`.
- La ruta de clave continúa siendo externa. Si falta o no es válida, el runtime no queda disponible para emitir tokens, conforme al comportamiento actual documentado.
- La elegibilidad CORS es el único cambio deliberado en el acceso por origen: tras aprobar la allowlist, un origen de producción no incluido no recibe autorización. Métodos, headers y contrato HTTP de login quedan fuera de ese cambio.
- El rechazo de una cuenta demo identificada no emite token. Para no crear un formato de error nuevo, se reutiliza `401` y la forma existente de respuesta de credenciales inválidas; la regla de identificación debe aprobarse antes de fijar sus datos de prueba.

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

### Cuentas demo persistidas: decisión bloqueante

No se implementa una regla de clasificación hasta recibir aprobación explícita. Una vez aprobada, la elegibilidad se evalúa antes de emitir el JWT; una cuenta identificada como demo recibe el resultado `401` ya utilizado para credenciales no válidas, sin un nuevo payload que revele la causa. No se altera ni elimina ninguna fila persistida.

**Alternativas consideradas:** reconocer cuentas por username/password conocidos o por heurísticas sobre sus atributos; se descartan por riesgo de colisión con cuentas legítimas. Borrar o desactivar las cuentas se descarta porque la HU prohíbe esa acción antes de acordar la regla. La regla aprobada es prerequisito de implementación y de los tests que etiqueten una fixture como demo.

### CORS de producción

Después de recibir la allowlist aprobada, producción debe autorizar únicamente los orígenes de esa lista. El valor se suministra mediante configuración de despliegue específica del perfil, no mediante dominios inventados ni una lista productiva codificada en tests. Los perfiles de desarrollo y prueba conservan el comportamiento actual salvo que el Issue amplíe expresamente el alcance.

**Alternativas consideradas:** mantener `/.*/`, lo que no resuelve H-AUTH-05; aplicar la restricción globalmente, lo que cambia el flujo local fuera del alcance indicado; o codificar dominios sin aprobación, lo que inventaría una política. Se elige una restricción de producción ligada a la allowlist aprobada.

### Persistencia y reversibilidad

El cambio no necesita migrar, borrar ni reescribir registros para negar el login a una cuenta identificada. La base conserva sus filas y el control de acceso se aplica al flujo de autenticación. Esto reduce el riesgo de afectar a usuarios legítimos y permite revertir el comportamiento sin reconstruir datos.

## Estrategia incremental

1. Registrar las decisiones aprobadas sobre identificación de cuentas demo y allowlist CORS antes de implementar los escenarios que dependen de ellas. Sin ambas decisiones, no activar esos cambios de comportamiento ni cerrar los hallazgos correspondientes.
2. Añadir o conservar pruebas de caracterización y ejecutarlas en `auth/` antes de cambiar el comportamiento. Mantener el contrato de login y de firma como control de regresión.
3. Implementar cada política solo con sus datos aprobados, de forma aislada y revisable; no mezclar la decisión de cuentas con la de orígenes CORS.
4. Ejecutar `mvn verify` desde `auth/`, revisar JaCoCo para el código modificado y cumplir la meta del componente de al menos 85% de cobertura.
5. Actualizar únicamente la documentación de auth y los estados H-AUTH-05/H-AUTH-08 después de obtener resultados reales; no declarar un hallazgo resuelto antes de esa evidencia.

## Pruebas de caracterización

- **Firma y contrato:** conservar `issuesSignedTokenWithIdentityRoleAndExpiration` y los casos de login exitoso de `AuthResourceTest`; deben verificar firma RS256, claims, respuesta `200` y campos del DTO. `SigningKeyStartupCheckTest` ya cubre ubicación faltante, archivo inexistente y contenido inválido con fixtures temporales; mantener esos casos antes de alterar configuración.
- **Errores HTTP:** conservar los casos de campos requeridos, credenciales inválidas y error interno para detectar cambios accidentales en los códigos o cuerpos documentados.
- **CORS actual:** `allowsCurrentCorsConfigurationForLocalAndUnlistedOrigins` caracteriza que hoy se autoriza también un origen no listado. Mantener esa evidencia como baseline y reemplazar la expectativa del origen no incluido solo después de aprobar la allowlist; agregar casos de preflight permitido/no permitido con valores de prueba aislados.
- **Filas preexistentes:** `NoDemoSeedingTest` no cubre el login de cuentas existentes. Una vez aprobada la regla, usar una base temporal precreada con una fixture demo identificada por esa regla y una cuenta legítima de control. Caracterizar primero el login actual de ambas; después, exigir que la demo no obtenga token y que la cuenta legítima conserve `200` y un JWT válido. No usar ni modificar bases reales.

## Risks / Trade-offs

- **Regla de clasificación ambigua →** una falsa coincidencia puede bloquear a un usuario legítimo; mantener la decisión como gate y exigir una cuenta legítima de control en la misma prueba aislada.
- **Allowlist no provista →** no es posible validar qué origen real debe permitirse; usar fixtures sintéticas solo para probar la semántica y no cerrar H-AUTH-05 hasta validar la allowlist aprobada.
- **Clave inválida detiene el servicio →** protege contra tokens sin firma válida, pero reduce disponibilidad; conservar el fallo cerrado y verificar configuración antes del despliegue.
- **Rollback de CORS →** restaurar `/.*/` reabre el riesgo conocido; cualquier excepción debe ser temporal, aprobada y seguida de volver a la allowlist.
- **Cobertura insuficiente en ramas nuevas →** ejecutar JaCoCo en `auth/` y cumplir el mínimo de 85% del código modificado antes de integrar.

## Migration Plan

No se requiere migración de base de datos. Desplegar primero configuración y pruebas en un entorno aislado con la clave efímera; promover a producción solo con clave y allowlist aprobadas y con la regla de cuentas documentada. Si una verificación falla, revertir la versión/configuración de auth sin cambiar ni borrar filas de la base. El rollback de CORS a `/.*/` debe tratarse como una excepción de seguridad, no como estado estable. Nunca sustituir una clave de producción por una clave incluida en el repositorio.