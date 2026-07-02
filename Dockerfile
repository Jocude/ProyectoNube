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

# Crear usuario no-root para seguridad
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copiar el JAR compilado desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

# Crear directorio de uploads y asignar permisos al usuario
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app/uploads

EXPOSE 8080

# Ejecutar como usuario no-root
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
