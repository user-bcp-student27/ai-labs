# Spec Delta

## Purpose

Define los resultados observables del login con JWT y las condiciones mínimas de configuración de firma que impiden emitir tokens inválidos.

## ADDED Requirements

### Requirement: El login exitoso conserva el contrato del token
Cuando una cuenta legítima se autentica correctamente, el sistema MUST responder con el contrato exitoso documentado para `POST /auth/login` y emitir un JWT firmado con RS256 cuyos claims de identidad, rol y expiración correspondan a esa autenticación.

#### Scenario: Emisión de token para cuenta legítima
- **WHEN** una cuenta legítima presenta credenciales válidas y la clave RSA configurada es válida
- **THEN** `POST /auth/login` responde `200` con `token`, `expiresIn`, `user` y `debug`, y el JWT verifica como RS256 con `sub`, `role` y `exp` correspondientes al usuario autenticado

### Requirement: Una clave de firma ausente o inválida impide emitir tokens
El sistema MUST impedir la emisión de JWT cuando la configuración de firma requerida está ausente o no permite firmar con RS256.

#### Scenario: Falta la ubicación de la clave
- **WHEN** el servicio se inicia en un perfil que requiere firma y no tiene ubicación de clave configurada
- **THEN** el servicio no queda disponible para emitir tokens

#### Scenario: La clave configurada no es válida
- **WHEN** el servicio se inicia con una ubicación que no contiene una clave privada válida para RS256
- **THEN** el servicio no queda disponible para emitir tokens

### Requirement: El login conserva las respuestas de error documentadas
El sistema MUST conservar los códigos y cuerpos de error documentados para requests inválidos, credenciales incorrectas y errores internos caracterizados de `POST /auth/login`.

#### Scenario: Campos requeridos inválidos
- **WHEN** `username` o `password` falta, es nulo o contiene solo espacios
- **THEN** el endpoint responde `400 Bad Request` con el mensaje correspondiente documentado

#### Scenario: Credenciales inválidas
- **WHEN** las credenciales no corresponden a una cuenta autenticable
- **THEN** el endpoint responde `401 Unauthorized` sin emitir un token

#### Scenario: Error interno caracterizado
- **WHEN** el procesamiento del login produce el error interno documentado
- **THEN** el endpoint responde `500 Internal Server Error` con el cuerpo genérico documentado y sin datos internos adicionales