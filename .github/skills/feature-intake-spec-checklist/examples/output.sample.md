# Feature Intake -> Spec Checklist

## 1. Summary
Agregar una confirmación antes de enviar una transferencia, con un resumen y validación de datos, para reducir 20% las quejas por transferencias duplicadas en un mes.

## 2. Scope
- **Dentro del alcance:** confirmación previa al envío, mensaje de resumen y validación de datos en el frontend web y la API REST existente; integración con Accounts service y Transfers service; logging estandarizado.
- **Fuera del alcance:** cambios de infraestructura y rediseño completo de la interfaz.

## 3. Supuestos
- La solicitud corresponde a transferencias iniciadas desde el frontend web existente.
- El objetivo de reducción se mide sobre quejas por transferencias duplicadas durante un mes; la línea base y el método de medición están pendientes.
- Los datos y las reglas de validación del resumen se acordarán antes de implementar.

## 4. Decisiones técnicas por tomar (pendientes o resueltas)
- **Resuelto:** conservar la API REST, el frontend web y el logging estandarizado existentes; depender de Accounts service y Transfers service.
- **Pendiente:** decidir qué datos incluye el resumen y cómo se confirma o cancela el envío.
- **Pendiente:** definir reglas, límites y mensajes de error de validación, incluida la validación del importe.
- **Pendiente:** acordar el contrato y el manejo de errores entre la aplicación y los servicios dependientes.
- **Pendiente:** establecer línea base, fuente de datos y responsable de medir la reducción de quejas.

## 5. Riesgos y mitigaciones
- **Exposición de datos sensibles en logs:** definir campos permitidos y revisar que valores sensibles no se registren; verificarlo con pruebas y revisión de logs.
- **Errores de validación del importe:** acordar reglas con producto y servicios dependientes; cubrir límites y errores con pruebas antes de habilitar el envío.
- **Inconsistencia entre el resumen confirmado y la transferencia enviada:** definir un contrato único para los datos y comprobar en pruebas que el resumen corresponde a la solicitud enviada.

## 6. Criterios de aceptación (medibles)
- El 100% de los intentos válidos presenta una confirmación antes de invocar el envío de la transferencia.
- El 100% de los intentos con datos inválidos según las reglas acordadas bloquea el envío y muestra un mensaje de validación.
- El resumen mostrado coincide con los datos usados en la solicitud de transferencia en el 100% de los casos cubiertos por pruebas funcionales.
- Los logs generados por el flujo no contienen valores sensibles de transferencias en las comprobaciones de seguridad acordadas.
- Las quejas por transferencias duplicadas disminuyen al menos 20% al completar un mes, comparadas con la línea base que se acuerde antes de implementar la medición.

## 7. Plan de validación
- **Pruebas unitarias:** cubrir confirmación previa, validaciones de datos e importe, errores de servicios y ausencia de envío cuando la validación falla.
- **Pruebas funcionales:** recorrer transferencia válida e inválida; comprobar resumen, confirmación y resultado de la integración con Accounts service y Transfers service.
- **Seguridad (comprobaciones mínimas):** inspeccionar los logs del flujo para confirmar que no contienen datos sensibles; verificar que los datos validados y enviados corresponden a la operación confirmada.
- **Evidencia requerida (qué adjuntar):** salida de pruebas unitarias y funcionales, checklist de revisión de logs sin datos sensibles, documentación breve actualizada y referencia a la fuente de medición del indicador.

## 8. Tareas sugeridas por rol
- **Chapter/PO:** precisar el flujo esperado de confirmación; acordar contenido del resumen y reglas de validación; definir línea base y método para medir la meta mensual.
- **Tech Lead:** definir contratos con Accounts service y Transfers service; acordar comportamiento ante errores y cancelación; revisar límites de logging y criterios de aceptación con el equipo.
- **Backend:** implementar o adaptar la validación en la API; integrar el envío con Transfers service y las consultas necesarias a Accounts service; evitar datos sensibles en logs y añadir pruebas unitarias.
- **Frontend:** implementar la confirmación previa; presentar el resumen acordado y mensajes de validación; cubrir los estados de éxito y error con pruebas.
- **QA:** derivar casos positivos, negativos y de límites de las reglas acordadas; ejecutar pruebas funcionales de extremo a extremo; adjuntar resultados y checklist de evidencia.
- **Cyber:** revisar los datos del flujo que pueden llegar a logs; comprobar que no se exponen datos sensibles; revisar las comprobaciones de seguridad del envío confirmado.

## 9. Preguntas abiertas
- ¿Qué campos debe incluir el resumen y cómo se presenta la acción de cancelar?
- ¿Cuáles son las reglas exactas de validación, especialmente para el importe?
- ¿Qué contratos, errores y disponibilidad ofrecen Accounts service y Transfers service para este flujo?
- ¿Cuál es la línea base, la fuente de datos y el período exacto para calcular la reducción de 20%?
- ¿Qué datos se consideran sensibles para el logging estandarizado?