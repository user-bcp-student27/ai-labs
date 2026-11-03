## api python

### Contrato para agentes

1. Analiza el ciclo de vida de la aplicación y los middlewares antes de modificar rutas.
2. Conserva la compatibilidad con los esquemas de Pydantic existentes en el frontend.
3. Crea pruebas de caracterización con `pytest` y `httpx.AsyncClient` (si es asíncrono) antes de refactorizar.
4. No uses `# noqa` ni silencies alertas de Ruff, SonarQube o Bandit.
5. No expongas secretos; usa `pydantic-settings` o `os.environ` con valores por defecto seguros para desarrollo.
6. Separa las rutas (routers), la lógica de negocio (services), el acceso a datos (repositories) y los esquemas (schemas).
7. Meta de cobertura con `pytest-cov` en el código modificado: 85% o superior.

#### Clean code

8. Cada función hace una sola cosa; si una función de ruta (`@router`) valida tokens, consulta la base de datos y formatea el JSON a la vez, sepárala en capas independientes.
9. Usa anotaciones de tipo (`Type Hints`) explícitas en todos los argumentos y retornos; evita el uso de `Any`.
10. No uses objetos mutables (como `[]` o `{}`) como argumentos por defecto en las funciones; usa `None` y configúralos dentro.
11. Evita bloques `try-except` genéricos (`except Exception:`); captura excepciones específicas y usa manejadores de excepciones globales para las respuestas HTTP.
12. Extrae las configuraciones y variables de entorno a una clase base de configuración; no uses strings "mágicos" para las llaves de diccionarios o rutas de archivos.
