#!/bin/bash

echo "========================================================"
echo "  INICIANDO CLOUD STORAGE B2B (MODO AUTO-DESPLIEGUE)"
echo "========================================================"
echo ""
echo "Levantando los servicios en segundo plano..."
docker-compose up -d

echo ""
echo "Esperando a que el tunel seguro de Cloudflare se establezca..."
sleep 10

PUBLIC_URL=$(docker-compose logs tunnel | grep -oE "https://[a-zA-Z0-9.-]+\.trycloudflare\.com" | tail -n 1)

if [ -z "$PUBLIC_URL" ]; then
    echo "[ADVERTENCIA] No se pudo extraer la URL. Entra manualmente a http://localhost:8080"
else
    echo "[EXITO] Enlace seguro generado: $PUBLIC_URL"
    echo "$PUBLIC_URL" > "uploads/public_url.txt"
    echo "Abriendo el navegador automaticamente..."
    if command -v xdg-open > /dev/null; then
        xdg-open "$PUBLIC_URL"
    elif command -v open > /dev/null; then
        open "$PUBLIC_URL"
    else
        echo "Abre manualmente la URL: $PUBLIC_URL"
    fi
fi
