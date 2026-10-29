# Spec Delta

## Purpose

Define qué origen de navegador puede obtener autorización CORS en producción. La allowlist aprobada contiene únicamente `https://www.viabcp.com`. El path `/cas/*` no forma parte del origen CORS; el navegador envía esquema, host y puerto en `Origin`.

## ADDED Requirements

### Requirement: CORS en producción autoriza solo orígenes aprobados
En producción, el sistema MUST autorizar solicitudes CORS únicamente desde `https://www.viabcp.com`, sin cambiar el contrato de request o response de `POST /auth/login`. Los perfiles dev/test conservan la configuración actual.

#### Scenario: Preflight desde origen incluido
- **WHEN** un navegador envía un preflight desde el origen `https://www.viabcp.com` en producción
- **THEN** la respuesta autoriza ese origen mediante `Access-Control-Allow-Origin`

#### Scenario: Preflight desde origen no incluido
- **WHEN** un navegador envía un preflight desde cualquier otro origen en producción
- **THEN** la respuesta no autoriza ese origen mediante `Access-Control-Allow-Origin`