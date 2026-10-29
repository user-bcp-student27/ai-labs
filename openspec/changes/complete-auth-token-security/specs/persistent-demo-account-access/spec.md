# Spec Delta

## Purpose

Define la elegibilidad de cuentas demo ya persistidas para autenticarse sin afectar los registros ni el acceso de cuentas legítimas.

## ADDED Requirements

### Requirement: Una cuenta demo persistida identificada no recibe un token
Cuando exista una regla aprobada para distinguir cuentas demo persistidas de cuentas legítimas, el sistema MUST impedir que una cuenta identificada por esa regla obtenga un JWT mediante `POST /auth/login`.

#### Scenario: Login de cuenta demo identificada
- **WHEN** una cuenta preexistente satisface la regla aprobada y presenta credenciales que de otro modo serían válidas
- **THEN** `POST /auth/login` no responde con un login exitoso ni emite un token para esa cuenta

### Requirement: Una cuenta legítima conserva el acceso
El sistema MUST preservar el login exitoso de cuentas legítimas que no satisfacen la regla aprobada para identificar cuentas demo.

#### Scenario: Cuenta legítima de control
- **WHEN** una cuenta legítima de control presenta credenciales válidas en la misma base de prueba
- **THEN** `POST /auth/login` responde `200` con el contrato exitoso documentado y emite un token válido para esa cuenta

### Requirement: Sin regla aprobada no se alteran cuentas persistidas
Mientras no exista una regla aprobada para identificar cuentas demo, el sistema MUST NOT borrar, desactivar ni modificar cuentas persistidas como parte de este cambio.

#### Scenario: Regla de identificación pendiente
- **WHEN** se ejecuta la verificación con cuentas preexistentes y no hay una regla de identificación aprobada
- **THEN** sus registros permanecen sin cambios por este cambio y no se presume cuál cuenta es demo o legítima