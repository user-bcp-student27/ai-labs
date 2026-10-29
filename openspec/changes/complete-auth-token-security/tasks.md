# Tasks

## 1. Caracterización y prerequisitos

- [x] 1.1 Ejecutar `mvn verify` desde `auth/` y registrar el baseline de firma/claims JWT, validación de clave, códigos de login y preflight CORS; confirmar el resultado real de cada suite.
- [x] 1.2 Completar, si falta cobertura, la prueba de configuración que demuestra que una ubicación ausente o una clave inválida impide iniciar el servicio y emitir tokens, usando una clave RSA efímera; verificar con `mvn verify` desde `auth/`.
- [ ] 1.3 Registrar la aprobación de la regla para identificar cuentas demo persistidas y de la allowlist de producción; verificar que ambas decisiones están documentadas antes de comenzar los cambios de comportamiento dependientes.
- [ ] 1.4 Con la regla de identificación aprobada, añadir una prueba de caracterización sobre una base SQLite temporal con una cuenta demo candidata y una cuenta legítima de control; antes del filtro, confirmar y registrar el resultado actual de login de ambas, sin tocar datos reales.
- [ ] 1.5 Con la allowlist aprobada, añadir una prueba de preflight de producción con un origen incluido y otro excluido, usando valores sintéticos de test; verificar que ambos casos reproducen el comportamiento actual antes del cambio de configuración.

## 2. CORS de producción (H-AUTH-05)

- [ ] 2.1 Restringir la autorización de origen en producción a la allowlist aprobada, sin cambiar métodos, headers, payloads de login ni el comportamiento de dev/test; verificar que pasan los preflight permitido y excluido.
- [ ] 2.2 Actualizar únicamente la sección auth de `docs/ENDPOINTS.md` y H-AUTH-05 en `HALLAZGOS.md` con la política aprobada y resultados reales; verificar que las secciones de `api` y `web` permanecen intactas y no marcar el hallazgo resuelto sin evidencia.

## 3. Cuentas demo persistidas (H-AUTH-08)

- [ ] 3.1 Aplicar en el login la regla aprobada antes de emitir el JWT, sin borrar, desactivar ni modificar filas; verificar en la base temporal que la demo identificada no obtiene token y que la cuenta legítima conserva `200` y un JWT válido.
- [ ] 3.2 Actualizar únicamente H-AUTH-08 en `HALLAZGOS.md` con la decisión aprobada y resultados reales de la prueba aislada; verificar que no se afirma una clasificación ni una resolución sin evidencia y que las secciones de otros componentes no cambian.

## 4. Integración y cobertura de auth

- [ ] 4.1 Ejecutar `mvn verify` desde `auth/` con ambas políticas aprobadas; verificar los escenarios de las tres specs, revisar JaCoCo para el código modificado y cumplir la meta mínima de 85% de cobertura de `auth`.
- [ ] 4.2 Revisar el diff final de `auth/`, sus pruebas y las secciones documentales tocadas; verificar que no hay cambios en `api/`, `web/`, datos persistidos, secretos ni contratos fuera del alcance del Issue #27.