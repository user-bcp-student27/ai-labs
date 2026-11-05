# Tasks

## 1. Línea base y caracterización de inyección

- [x] 1.1 Ejecutar `mvn test` desde `auth/` sin cambios y registrar que la suite pasa, para fijar la línea base.
- [x] 1.2 En `AuthResourceTest`, añadir `username` = `' OR '1'='1` con `password` no vacío y verificar `401` sin `token`; la prueba debe pasar con el código actual.
- [x] 1.3 En `AuthResourceTest`, añadir `username` = `student` con `password` = `' OR '1'='1` y verificar `401` sin `token`; la prueba debe pasar con el código actual.
- [x] 1.4 En `AuthResourceTest`, añadir `username` = `student'--` con `password` no vacío y verificar `401` sin `token`; la prueba debe pasar con el código actual.
- [x] 1.5 En las pruebas 1.2 a 1.4, verificar que el cuerpo de la respuesta no contiene el valor de `password`; extraer un método auxiliar privado solo si el cuerpo JSON se repite en tres o más pruebas.
- [x] 1.6 Ejecutar `mvn test` desde `auth/` y verificar que las pruebas nuevas y las existentes (`400`, `401`, `500`, JWT, `student`/`trainer` en producción) pasan sin modificar `src/main`.

## 2. Cambio funcional condicionado

- [x] 2.1 Si 1.2 a 1.4 pasan, confirmar en la revisión que no se modifica `auth/src/main` y dejar constancia en la descripción del PR; si alguna falla, detenerse e informar antes de continuar.
  - Constancia para el PR: 1.2 a 1.4 pasan con el código actual (`mvn test "-Dquarkus.http.test-port=18081"` desde `auth/`: 30 pruebas, 0 fallos) y `git diff --stat -- auth/src/main` está vacío; no hay defecto que corregir.
- [x] 2.2 Solo si 2.1 detectó un defecto real, corregir `LegacyDatabase` con un cambio mínimo y parametrizado en un commit separado, y verificar con `mvn test` desde `auth/` que 1.2 a 1.4 y la línea base pasan.
  - No aplica: 2.1 no detectó un defecto, por lo que `LegacyDatabase` no se modificó.

## 3. Evidencia y cierre

- [x] 3.1 Actualizar solo la fila H-AUTH-01 de `HALLAZGOS.md` con la evidencia de las pruebas 1.2 a 1.4, sin marcarla Resuelto sin revisión humana, y verificar con `git diff` que las filas de api y web no cambian.
- [x] 3.2 Ejecutar `mvn test` completo desde `auth/` y verificar que pasa; verificar con el informe JaCoCo que el código modificado alcanza 85% o más de cobertura.
- [x] 3.3 Ejecutar `openspec validate parameterize-auth-login-query --strict` desde la raíz y verificar que el cambio es válido.
- [x] 3.4 Comprobar con `git status` que los cambios se limitan a `auth/`, `HALLAZGOS.md` y `openspec/changes/parameterize-auth-login-query/`.
  - Verificado con `git status --short -- auth HALLAZGOS.md openspec/changes/parameterize-auth-login-query`: solo `HALLAZGOS.md`, `AuthResourceTest.java` y este directorio; `api/` y `web/` sin cambios. Otros cambios del árbol (`.agents/`, `.github/`, `.vscode/`, `docs/architecture/`) son ajenos a este issue y no deben incluirse en su commit.

## Workflow follow-up

- Revisar los requisitos de la spec `login-sql-injection-resistance` contra la evidencia de las pruebas.
- Archivar el cambio cuando se cumplan los requisitos de revisión del proyecto.
