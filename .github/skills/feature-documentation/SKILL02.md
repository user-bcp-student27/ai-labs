---
name: feature-documentation
description: "Estandariza la documentación de endpoints y las tareas de Tech Lead, Arquitectura y Seguridad a partir de un diagrama, riesgos y dependencias conocidas."
---

# Skill: Feature Documentation

## 1) Metadata
- **Skill ID:** feature-documentation
- **Version:** Pendiente de definir.
- **Autor:** Pendiente de definir.
- **Aprobador:** Pendiente de definir.
- **Squad:** Pendiente de definir.
- **Tribu:** Pendiente de definir.
- **Fecha de aprobación:** Pendiente de definir.
- **Purpose:** Producir documentación trazable de endpoints y tareas sugeridas por rol, sin agregar requisitos no confirmados.
- **Audience (roles):** Tech Lead, Arquitecto, Seguridad.
- **When to use:** Al documentar endpoints a partir de información de arquitectura, riesgos de seguridad y dependencias.
- **Sensitive inputs:** No incluyas datos sensibles, secretos ni credenciales.
- **Expected output:** Un documento Markdown con las secciones definidas en Output.

## 2) Inputs
Proporciona la información disponible:

- **Metadata:** Autor, Aprobador, Squad, Tribu, Fecha de aprobación y Versión. Si no está disponible, marcar como pendiente.
- **Diagrama de arquitectura:** archivo o contenido disponible.
- **Known risks (security):** riesgos de seguridad conocidos y mitigaciones confirmadas, si existen.
- **Dependencies (arquitectura, security, Tech Lead):** dependencias y participantes conocidos, con su relación al flujo cuando esté especificada.

No completes información faltante mediante inferencias. Registra las ausencias como preguntas abiertas.

## 3) Output
Devuelve solo un documento Markdown que incluya:

1. **Documentación de endpoints**
   - Documenta cada endpoint confirmado por las entradas.
   - Incluye método, ruta y propósito solo si están especificados.
   - **Request:** formato y campos conocidos; distingue parámetros de ruta, query, headers y body únicamente si la fuente lo confirma.
   - **Parámetros:** nombre, ubicación, tipo, obligatoriedad y restricciones solo cuando estén documentados.
   - **Response:** estados, formato y campos solo cuando estén documentados.
   - Marca como pendiente cualquier detalle requerido que no esté disponible; no inventes endpoints, campos ni contratos.
2. **Riesgos y mitigaciones**
   - Registra riesgos de seguridad proporcionados y mitigaciones confirmadas, identificando la fuente cuando esté disponible.
3. **Criterios de aceptación**
   - Usa criterios verificables para comprobar que la documentación cubre los endpoints y datos confirmados.
   - Si no se puede definir un criterio sin información adicional, indícalo en Preguntas abiertas.
4. **Plan de validación**
   - Describe comprobaciones basadas en la información disponible, incluidas las de seguridad.
   - Incluye evidencia concreta, como un checklist, resultados de validación o el documento actualizado.
   - No prescribas herramientas no confirmadas como parte del stack permitido.
5. **Tareas sugeridas por rol**
   - **Tech Lead**
   - **Arquitecto**
   - **Seguridad**
6. **Preguntas abiertas**
   - Registra endpoints, contratos, parámetros, respuestas, riesgos, mitigaciones, dependencias o datos de metadata que no estén confirmados.

## 4) Constraints
- No inventes requisitos; marca como pregunta abierta toda decisión o dato no confirmado por las entradas.
- No propongas herramientas fuera del stack permitido.
- No incluyas datos sensibles, secretos ni credenciales.
- Los criterios de aceptación deben ser verificables; no uses objetivos vagos sin una métrica definida.
- El plan de validación debe incluir al menos un elemento de evidencia concreto, como salida de pruebas, un checklist o un archivo actualizado.
- Mantén la salida en un máximo de 1-2 páginas.
- Si falta información para satisfacer una regla de validación, decláralo como pendiente en Preguntas abiertas en vez de fabricar contenido.

## 5) Validation Rules
La salida es válida si:
- Incluye todas las secciones definidas en Output.
- Documenta request, parámetros y response por cada endpoint confirmado, o registra explícitamente los datos faltantes en Preguntas abiertas.
- Incluye al menos 2 riesgos con mitigaciones sustentados por las entradas; si no hay información para ello, marca este criterio como pendiente e identifica qué falta.
- Incluye criterios de aceptación verificables y un plan de validación con evidencia concreta, o identifica explícitamente qué información falta para definirlos.
- Incluye tareas sugeridas para Tech Lead, Arquitecto y Seguridad.
- No contiene datos sensibles, secretos, credenciales ni supuestos no declarados.

## 6) Evidence
Adjunta o referencia estos elementos en la documentación:
- `SKILL02.md` versionado.
- `examples/input01.sample.md`.
- `examples/output01.sample.md`.
- Tag de versión para el Skill, por ejemplo `skill-feature-documentation-v0.1.0`.
