# ============================================
# Etapa 1: Compilación con Maven
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copiar primero el pom.xml para cachear las dependencias
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests

# ============================================
# Etapa 2: Imagen de ejecución ligera
# ============================================
FROM eclipse-temurin:21-jre-alpine

# Crear usuario no-root para seguridad.
# Usa el mismo UID/GID que el usuario del host para poder escribir en la carpeta
# de subidas montada (bind mount) sin recurrir a chmod 777.
ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -S -g "$APP_GID" appgroup && adduser -S -u "$APP_UID" -G appgroup appuser

WORKDIR /app

# Copiar el JAR compilado desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

# Crear directorio de uploads y asignar permisos al usuario
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app/uploads

EXPOSE 8080

# Ejecutar como usuario no-root
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
