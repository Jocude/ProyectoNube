#!/usr/bin/env bash
# =========================================================================
#  Script de Despliegue en Un Clic para Linux (B2B Self-Hosted)
# =========================================================================

set -e

echo "========================================================================="
echo " Iniciando instalador de Cloud Storage Server..."
echo "========================================================================="
echo ""

# 1. Comprobar requisitos
echo "[1/4] Verificando dependencias del sistema..."
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker no está instalado en este servidor."
    echo "Por favor, instálalo antes de continuar: https://docs.docker.com/engine/install/"
    exit 1
fi

DOCKER_CMD="docker compose"
if ! docker compose version &> /dev/null; then
    if command -v docker-compose &> /dev/null; then
        DOCKER_CMD="docker-compose"
    else
        echo "[ERROR] No se encontró 'docker compose' ni 'docker-compose'."
        echo "Asegúrate de instalar el plugin de Docker Compose."
        exit 1
    fi
fi
echo "Requisitos verificados con éxito."
echo ""

# 2. Comprobar archivo de configuración (.env)
echo "[2/4] Verificando archivo de configuración (.env)..."
if [ ! -f .env ]; then
    echo "[ADVERTENCIA] No se encontró el archivo '.env'."
    if [ -f .env.template ]; then
        echo "Copiando plantilla '.env.template' a '.env'..."
        cp .env.template .env
        echo "[IMPORTANTE] Se ha creado el archivo '.env'."
        echo "Por favor, edítalo e ingresa tus valores locales,"
        echo "incluyendo las rutas físicas del host y tu CLAVE DE LICENCIA."
        echo "Una vez guardados los cambios, vuelve a ejecutar este instalador."
    else
        echo "[ERROR] No se encontró la plantilla '.env.template'."
        exit 1
    fi
    exit 1
fi
echo "Configuración .env detectada."
echo ""

# 3. Leer rutas del .env y crear carpetas locales
echo "[3/4] Inicializando directorios locales en el servidor..."
# Extraer rutas del archivo .env y limpiar retorno de carro si fue editado en Windows
HOST_STORAGE_PATH=$(grep -E "^HOST_STORAGE_PATH=" .env | cut -d'=' -f2- | tr -d '\r')
HOST_DB_PATH=$(grep -E "^HOST_DB_PATH=" .env | cut -d'=' -f2- | tr -d '\r')

# Fallbacks por defecto si las variables están vacías
HOST_STORAGE_PATH=${HOST_STORAGE_PATH:-./uploads}
HOST_DB_PATH=${HOST_DB_PATH:-./pgdata}

echo "Creando carpeta de subidas: $HOST_STORAGE_PATH"
mkdir -p "$HOST_STORAGE_PATH"

echo "Creando carpeta de base de datos: $HOST_DB_PATH"
mkdir -p "$HOST_DB_PATH"

# Asegurar permisos correctos para que Docker pueda escribir en ellas
chmod 777 "$HOST_STORAGE_PATH"
chmod 777 "$HOST_DB_PATH"

echo "Directorios inicializados correctamente."
echo ""

# 4. Levantar la aplicación con Docker Compose
echo "[4/4] Levantando contenedores de la aplicación..."
$DOCKER_CMD up -d --build

echo ""
echo "========================================================================="
echo " ¡INSTALACIÓN COMPLETADA EXITOSAMENTE!"
echo "========================================================================="
echo " Tu nube personal ya está levantada en segundo plano."
echo " - Acceso Local: http://localhost:8080"
echo " - Los archivos cifrados se guardarán en: $HOST_STORAGE_PATH"
echo " - Para ver el estado de los contenedores escribe: docker ps"
echo "========================================================================="
echo ""
