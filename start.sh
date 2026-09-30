#!/usr/bin/env bash
# Arranca el stack (db + app + túnel de Cloudflare) y abre la URL pública.
set -euo pipefail

cd "$(dirname "$0")"

echo "========================================================"
echo "  INICIANDO CLOUD STORAGE B2B (MODO AUTO-DESPLIEGUE)"
echo "========================================================"
echo ""

if [ ! -f .env ]; then
    echo "[ERROR] No existe el archivo .env. Copia .env.template a .env y rellénalo."
    exit 1
fi

# Rutas del host (con los mismos valores por defecto que docker-compose.yml)
HOST_STORAGE_PATH=$(grep -E "^HOST_STORAGE_PATH=" .env | cut -d'=' -f2- | tr -d '\r' || true)
HOST_DB_PATH=$(grep -E "^HOST_DB_PATH=" .env | cut -d'=' -f2- | tr -d '\r' || true)
HOST_STORAGE_PATH=${HOST_STORAGE_PATH:-./uploads}
HOST_DB_PATH=${HOST_DB_PATH:-./pgdata}

# Crear las carpetas como el usuario actual; si las creara Docker serían de root
# y la app (que corre con nuestro UID) no podría escribir en ellas.
mkdir -p "$HOST_STORAGE_PATH" "$HOST_DB_PATH"

# El contenedor de la app usa nuestro UID/GID (ver Dockerfile)
export APP_UID APP_GID
APP_UID=$(id -u)
APP_GID=$(id -g)

# La URL de un arranque anterior ya no es válida (el túnel genera una nueva cada vez)
rm -f "$HOST_STORAGE_PATH/public_url.txt"

echo "Levantando los servicios en segundo plano..."
docker compose up -d --build

echo ""
echo "Esperando a que el túnel seguro de Cloudflare se establezca..."
PUBLIC_URL=""
for _ in $(seq 1 30); do
    PUBLIC_URL=$(docker compose logs tunnel 2>/dev/null \
        | grep -oE "https://[a-zA-Z0-9.-]+\.trycloudflare\.com" | tail -n 1 || true)
    [ -n "$PUBLIC_URL" ] && break
    sleep 2
done

if [ -z "$PUBLIC_URL" ]; then
    echo "[ADVERTENCIA] No se pudo extraer la URL. Entra manualmente a http://localhost:8080"
    exit 0
fi

echo "[ÉXITO] Enlace seguro generado: $PUBLIC_URL"
# La API lo lee para mostrarlo en /api/info
echo "$PUBLIC_URL" > "$HOST_STORAGE_PATH/public_url.txt"

if command -v xdg-open > /dev/null; then
    xdg-open "$PUBLIC_URL" > /dev/null 2>&1 &
elif command -v open > /dev/null; then
    open "$PUBLIC_URL"
else
    echo "Abre manualmente la URL: $PUBLIC_URL"
fi
