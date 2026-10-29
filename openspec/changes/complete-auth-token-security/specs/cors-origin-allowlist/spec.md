# Spec Delta

## Purpose

Define qué orígenes de navegador pueden obtener autorización CORS en producción una vez aprobada la allowlist correspondiente.

## ADDED Requirements

### Requirement: CORS en producción autoriza solo orígenes aprobados
En producción, el sistema MUST autorizar solicitudes CORS únicamente desde los orígenes incluidos en la allowlist aprobada, sin cambiar el contrato de request o response de `POST /auth/login`.

#### Scenario: Preflight desde origen incluido
- **WHEN** un navegador envía un preflight desde un origen incluido en la allowlist aprobada para producción
- **THEN** la respuesta autoriza ese origen mediante `Access-Control-Allow-Origin`

#### Scenario: Preflight desde origen no incluido
- **WHEN** un navegador envía un preflight desde un origen que no está incluido en la allowlist aprobada para producción
- **THEN** la respuesta no autoriza ese origen mediante `Access-Control-Allow-Origin`