# Spec Delta

## Purpose

Define que `POST /auth/login` trata `username` y `password` como datos y no como SQL, de modo que una entrada con sintaxis SQL no permita autenticarse ni emita un token, y que el contrato documentado del login se conserve.

## ADDED Requirements

### Requirement: Las credenciales no alteran la consulta del login
El sistema MUST tratar `username` y `password` recibidos en `POST /auth/login` como valores de datos. El sistema MUST NOT construir la consulta de autenticación concatenando esos valores en el texto SQL.

#### Scenario: Inyección en username
- **WHEN** se envía `POST /auth/login` con `username` igual a `' OR '1'='1` y cualquier `password` no vacío
- **THEN** la respuesta es `401` y el cuerpo no contiene `token`

#### Scenario: Inyección en password
- **WHEN** se envía `POST /auth/login` con un `username` existente y `password` igual a `' OR '1'='1`
- **THEN** la respuesta es `401` y el cuerpo no contiene `token`

#### Scenario: Inyección que intenta comentar el resto de la consulta
- **WHEN** se envía `POST /auth/login` con `username` igual a `student'--` y cualquier `password` no vacío
- **THEN** la respuesta es `401` y el cuerpo no contiene `token`

### Requirement: El login legítimo conserva el contrato documentado
El sistema MUST conservar el contrato de `POST /auth/login` de `docs/ENDPOINTS.md`: con credenciales válidas de una cuenta elegible responde `200` con `token`, `expiresIn`, `user.username`, `user.displayName`, `user.role` y `debug`.

#### Scenario: Credenciales válidas en dev/test
- **WHEN** en dev/test se envía `POST /auth/login` con `username` `student` y `password` `Workshop123!`
- **THEN** la respuesta es `200`, `user.username` es `student` y `token` es un JWT RS256

#### Scenario: Cuenta demo en producción
- **WHEN** en producción se envía `POST /auth/login` con `username` `student` o `trainer`
- **THEN** la respuesta sigue siendo `401` según `docs/ENDPOINTS.md`

#### Scenario: Campos requeridos inválidos
- **WHEN** se envía `POST /auth/login` con `username` o `password` vacío o solo espacios
- **THEN** la respuesta sigue siendo `400` con el mensaje documentado, sin consultar credenciales

### Requirement: Las respuestas de error no revelan la contraseña
El sistema MUST NOT incluir el valor de `password` en ninguna respuesta de `POST /auth/login`, incluido un intento de inyección.

#### Scenario: Mensaje de credenciales inválidas
- **WHEN** un intento de inyección recibe `401`
- **THEN** el mensaje puede incluir el `username` recibido, según el contrato actual, y no incluye el valor de `password`

### Requirement: La resistencia a inyección queda cubierta por pruebas
El componente `auth/` MUST incluir en `AuthResourceTest` pruebas de los intentos de inyección anteriores, y `mvn test` MUST pasar desde `auth/`.

#### Scenario: Prueba de regresión
- **WHEN** se ejecuta `mvn test` desde `auth/`
- **THEN** `AuthResourceTest` ejecuta los intentos de inyección, verifica `401` sin `token` y la suite pasa
