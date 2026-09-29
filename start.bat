@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo   INICIANDO CLOUD STORAGE B2B (MODO AUTO-DESPLIEGUE)
echo ========================================================
echo.
echo Levantando los servicios en segundo plano...
docker compose up -d --build

echo.
echo Esperando a que el tunel seguro de Cloudflare se establezca...
timeout /t 10 /nobreak > nul

for /f "delims=" %%a in ('powershell -Command "$log = docker compose logs tunnel; if ($log -match 'https://[a-zA-Z0-9.-]+\.trycloudflare\.com') { $matches[0] }"') do set PUBLIC_URL=%%a

if "!PUBLIC_URL!"=="" (
    echo [ADVERTENCIA] No se pudo extraer la URL. Abriendo localhost...
    start http://localhost:8080
) else (
    echo [EXITO] Enlace seguro generado: !PUBLIC_URL!
    echo !PUBLIC_URL!> uploads\public_url.txt
    echo Abriendo el navegador automaticamente...
    start "" "!PUBLIC_URL!"
)
