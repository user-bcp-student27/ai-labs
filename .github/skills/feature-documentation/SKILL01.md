---
name: feature-documentation
description: "Genera documentación trazable de endpoints REST y tareas por rol a partir de un diagrama de arquitectura, riesgos de seguridad y dependencias de arquitectura y seguridad."
---

# Skill: Feature Documentation

## 1) Metadata
- **Skill ID:** feature-documentation
- **Version:** 0.1.0
- **Autor:** Pendiente de definir.
- **Aprobadores:** Pendiente de definir.
- **Squad:** Pendiente de definir.
- **Tribu:** Pendiente de definir.
- **Fecha de aprobación:** Pendiente de definir.
- **Purpose:** Estandarizar la documentación verificable de endpoints y las tareas necesarias para completarla.
- **Audience (roles):** Tech Lead, Backend, Arquitecto, Seguridad.
- **When to use:** Al documentar endpoints con información de arquitectura y seguridad disponible.
- **When NOT to use:** No definido en la entrada; no inferir restricciones de uso.
- **Sensitive inputs:** No incluyas datos sensibles, secretos ni credenciales.
- **Expected output:** Un documento Markdown estructurado mediante la sección Output.

## 2) Inputs
Proporciona la información disponible en este formato:

- **Diagrama de arquitectura:** archivo, referencia o contenido disponible.
- **Known risks (security):** riesgos de seguridad conocidos y su evidencia, si está disponible.
- **Dependencies (arquitectura, security):** dependencias arquitectónicas y de seguridad conocidas.

No completes información faltante mediante inferencias. Registra las ausencias como preguntas abiertas.

## 3) Output
Devuelve solo un documento Markdown que incluya:

1. **Documentación de endpoints**
   - Una entrada por cada endpoint confirmado por las entradas.
   - Método, ruta y propósito, solo si están especificados.
   - Request: formato y campos conocidos; distingue parámetros de ruta, query, headers y body cuando la fuente lo confirme.
   - Parámetros: nombre, ubicación, tipo, obligatoriedad y restricciones únicamente cuando estén documentados.
   - Response: estados, formato y campos únicamente cuando estén documentados.
   - Señala como pendiente cualquier detalle requerido que no esté disponible; no inventes endpoints, campos ni contratos.
2. **Riesgos y mitigaciones**
   - Para cada riesgo conocido, incluye su mitigación y la evidencia o decisión que la sustenta, si están disponibles.
3. **Criterios de aceptación**
   - Criterios verificables y trazables al diagrama o a los riesgos y dependencias proporcionados.
   - Si la entrada no permite definir un criterio verificable, registra la información faltante en Preguntas abiertas.
4. **Plan de validación**
   - Comprobaciones derivadas de la documentación disponible, incluidas las comprobaciones de seguridad pertinentes.
   - Evidencia concreta que se debe adjuntar, como resultados de pruebas, un checklist o el documento actualizado.
   - No prescribas herramientas que no estén confirmadas por el stack permitido.
5. **Tareas sugeridas por rol**
   - **Tech Lead**
   - **Backend**
   - **Arquitecto**
   - **Seguridad**
6. **Preguntas abiertas**
   - Decisiones, contratos, parámetros, respuestas, riesgos, mitigaciones o dependencias que la entrada no confirme.

## 4) Constraints
- No inventes requisitos; marca como pregunta abierta toda decisión que las entradas no confirmen.
- No propongas herramientas fuera del stack permitido.
- No incluyas datos sensibles, secretos ni credenciales.
- Los criterios de aceptación deben ser verificables; no uses objetivos vagos sin una métrica definida.
- El plan de validación debe incluir al menos un elemento de evidencia concreto, como salida de pruebas, un checklist o un archivo actualizado.
- Mantén la salida en un máximo de 1-2 páginas.
- Si falta información para satisfacer una regla de validación, decláralo en Preguntas abiertas en vez de fabricar contenido.

## 5) Validation Rules
La salida es válida si:
- Incluye todas las secciones definidas en Output.
- Documenta request, parámetros y response por cada endpoint confirmado, o registra los datos faltantes como preguntas abiertas.
- Incluye al menos 2 riesgos con mitigaciones sustentados por las entradas; si no hay información suficiente, marca la validación como pendiente y pregunta por los datos faltantes.
- Incluye criterios de aceptación verificables y un plan de validación con evidencia concreta, o identifica explícitamente qué información falta para definirlos.
- Incluye tareas para Tech Lead, Backend, Arquitecto y Seguridad.
- No contiene datos sensibles, secretos, credenciales ni supuestos no declarados.

## 6) Evidence
Adjunta o referencia estos elementos en la documentación:
- `SKILL01.md` versionado.
- `examples/input01.sample.md`.
- `examples/output01.sample.md`.
- Tag de versión para el Skill, por ejemplo `skill-feature-documentation-v0.1.0`.