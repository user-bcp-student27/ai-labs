# Spec Delta

## Purpose

Define la elegibilidad en producción de las cuentas semilla `student` y `trainer`, identificadas por el seeder de `LegacyDatabase`, sin afectar registros persistidos ni el acceso de otras cuentas legítimas. Dev/test conserva el comportamiento actual para facilitar el taller.

## ADDED Requirements

### Requirement: Una cuenta demo persistida identificada no recibe un token
En producción, el sistema MUST impedir que las cuentas semilla `student` y `trainer` obtengan un JWT mediante `POST /auth/login`, incluso si las filas ya existían en la base. El sistema MUST conservar su login en dev/test.

#### Scenario: Login de cuenta demo identificada
- **WHEN** una cuenta preexistente con username `student` o `trainer` presenta credenciales válidas en producción
- **THEN** `POST /auth/login` no responde con un login exitoso ni emite un token para esa cuenta

### Requirement: Una cuenta legítima conserva el acceso
El sistema MUST preservar el login exitoso de cuentas legítimas que no satisfacen la regla aprobada para identificar cuentas demo.

#### Scenario: Cuenta legítima de control
- **WHEN** una cuenta legítima de control presenta credenciales válidas en la misma base de prueba
- **THEN** `POST /auth/login` responde `200` con el contrato exitoso documentado y emite un token válido para esa cuenta

### Requirement: Las filas persistidas no se modifican
El sistema MUST NOT borrar, desactivar ni modificar filas persistidas para bloquear el login de las cuentas demo.

#### Scenario: Cuentas demo existentes en producción
- **WHEN** se niega el login de una cuenta demo persistida
- **THEN** la fila y sus credenciales almacenadas permanecen sin cambios.