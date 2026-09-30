# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build y tests (sin Maven/JDK local)
No hay `mvn` ni `java` en el host; Maven se ejecuta dentro de Docker con caché de dependencias en el volumen `m2-cache`:

```bash
docker run --rm -v "$PWD":/app -v m2-cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q test
docker run --rm -v "$PWD":/app -v m2-cache:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -q spotless:apply
# Un solo test: ... mvn -q test -Dtest=AuthServiceTest
# Análisis estático: ... mvn -q compile spotbugs:check
# Todo junto (lo mismo que la CI): ... mvn -B verify
```

- Formato: Spotless con google-java-format estilo **AOSP** (4 espacios) + eliminar imports no usados. Todo el código está formateado; un hook lo aplica tras cada edición y `mvn spotless:check` debe pasar.

## Ejecutar la app
- Stack completo: `./start.sh` (db Postgres 16 + app en :8080 + túnel Cloudflare público). Solo existe Compose v2 (`docker compose`). Sin túnel: `APP_UID=$(id -u) APP_GID=$(id -g) docker compose up -d --build db app`.
- El contenedor de la app corre con el UID/GID del host (build args `APP_UID`/`APP_GID`, por defecto 1000) para poder escribir en `uploads/`. Postgres no publica puertos: acceder con `docker exec cloudstorage-db psql -U cloudstorage`.
- `JWT_SECRET`, `ENCRYPTION_KEY` (32 bytes Base64), `DB_PASSWORD` y `APP_LICENSE_KEY` son obligatorias en `.env`: sin ellas Compose o la app se niegan a arrancar. `DB_PASSWORD` solo se aplica al crear `pgdata/`.
- La app hace `System.exit(1)` al arrancar si `APP_LICENSE_KEY` falta o no es un JWT RS256 válido firmado con la clave de `LicenseValidatorService`. `util/LicenseGenerator` genera pares de claves/licencias.
- Esquema de BD: lo gestiona **Flyway** (`src/main/resources/db/migration/V<n>__descripcion.sql`, SQL compatible con Postgres y H2). Hibernate está en `ddl-auto: validate`: al cambiar una entidad JPA hay que añadir una migración nueva; nunca editar una ya aplicada.

## Tests
- Mockito va en modo estricto: preparar (`when`) una llamada que el código no hace falla con `UnnecessaryStubbing`.
- Unitarios con Mockito (`@ExtendWith(MockitoExtension.class)`) e integración en `ApiIntegrationTest` (`@SpringBootTest` + MockMvc). Este último usa `@MockBean LicenseValidatorService` (para no depender de la caducidad de la licencia), un `.env` temporal para el panel B2B y una IP distinta por test (el rate limit cuenta por IP).
- Tests con contexto Spring (`@SpringBootTest`) deben usar `@ActiveProfiles("test")` para cargar `src/test/resources/application-test.yml` (H2 en modo PostgreSQL + Flyway); sin él intentan conectar a Postgres. La licencia de ese fichero caduca el 2027-07-02: mejor mockear `LicenseValidatorService`.
- SpotBugs: los falsos positivos aceptados están en `spotbugs-exclude.xml` con su justificación; no añadir exclusiones genéricas.

## Errores HTTP
- `GlobalExceptionHandler` (hereda de `ResponseEntityExceptionHandler`) traduce excepciones a códigos: usar las del paquete `exception` (`ConflictException` 409, `QuotaExceededException` 507, `ShareLinkExpiredException` 410, `RegistrationDisabledException` 403), `EntityNotFoundException` 404, `IllegalArgumentException` 400 y `BadCredentialsException` 401. Nunca `RuntimeException` genérica (sale como 500).
- El cuerpo de error es `{"error": "...", "timestamp": ...}`; el frontend lee `error`.

## Seguridad
- Rutas públicas definidas en `config/SecurityConfig.java` (`permitAll`); todo lo demás exige JWT vía `JwtAuthenticationFilter`. Al añadir endpoints, decidir explícitamente si van ahí.
- No commitear `.env`, `pgdata/`, `uploads/` ni `target/`; los secretos van en `.env` (plantillas: `.env.template`, `.env.example`).

## Git
- Commits directamente en `main`.
- Mensajes: `PREFIJO: descripción` en español, prefijo en mayúsculas (`ADD`, `FIX`, `UPDATE`, `REMOVE`…).
