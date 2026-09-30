---
name: verificar
description: Compila, formatea (Spotless), analiza con SpotBugs y ejecuta los tests del proyecto con Maven dentro de Docker. Usar tras cambiar código Java para verificar que todo sigue funcionando, o cuando el usuario pida "verificar", "pasar los tests" o "compilar".
---

No hay `mvn`/`java` en el host: todo va en el contenedor `maven:3.9-eclipse-temurin-21` con el volumen `m2-cache` para las dependencias.

Base del comando (desde la raíz del repo):

```bash
MVN='docker run --rm -v "$PWD":/app -v m2-cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q -B'
```

Pasos:

1. Ficheros Java cambiados: `git status --porcelain -- '*.java'`.
2. Formato solo de esos ficheros (el resto del proyecto aún no está formateado, no hacer `spotless:check` global):
   `eval "$MVN spotless:apply -DspotlessFiles='<regex con las rutas, separadas por coma>'"`
3. Tests: `eval "$MVN test"` (o `-Dtest=Clase` / `-Dtest=Clase#metodo` si $ARGUMENTS indica uno concreto). La primera ejecución descarga dependencias (~1 min).
4. Análisis estático: `eval "$MVN compile spotbugs:check"` (~35 s). El código ya tenía 11 avisos (sobre todo `EI_EXPOSE_REP`, más `DM_EXIT` en LicenseValidatorService, `SE_BAD_FIELD` en User y `DMI_HARDCODED_ABSOLUTE_FILENAME` en B2bAdminService); informar solo de los NUEVOS en ficheros tocados.
5. Si falla, leer `target/surefire-reports/*.txt` del test afectado y explicar la causa al usuario en español, incluyendo el concepto de Spring/Mockito/JUnit implicado.
6. Informar: ficheros formateados, tests ejecutados/fallidos y si la compilación pasó. No ocultar fallos previos ya existentes; distinguirlos de los introducidos por el cambio.
