@echo off
:: =========================================================================
::  Script de Despliegue en Un Clic para Windows (B2B Self-Hosted)
:: =========================================================================
chcp 65001 >nul
echo =========================================================================
echo  Iniciando instalador de Cloud Storage Server...
echo =========================================================================
echo.

:: 1. Comprobar requisitos
echo [1/5] Verificando dependencias del sistema...
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Docker no esta instalado o no se encuentra en el PATH.
    echo Por favor, instala Docker Desktop antes de continuar: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

:: Comprobar si docker compose v2 o v1 esta disponible
set DOCKER_CMD=docker compose
docker compose version >nul 2>nul
if %errorlevel% neq 0 (
    where docker-compose >nul 2>nul
    if %errorlevel% neq 0 (
        echo [ERROR] No se encontro el comando 'docker compose' ni 'docker-compose'.
        echo Asegurate de tener habilitado Docker Compose en Docker Desktop.
        pause
        exit /b 1
    ) else (
        set DOCKER_CMD=docker-compose
    )
)
echo Requisitos verificados con exito.
echo.

:: 2. Comprobar archivo de configuracion (.env)
echo [2/5] Verificando archivo de configuracion (.env)...
if not exist .env (
    echo [ADVERTENCIA] No se encontro el archivo '.env'.
    if exist .env.template (
        echo Copiando plantilla '.env.template' a '.env'...
        copy .env.template .env >nul
        echo [IMPORTANTE] Se ha creado el archivo '.env'.
        echo Abre el archivo '.env' en un editor de texto y configura tus valores locales,
        echo especialmente las rutas fisicas del host y tu CLAVE DE LICENCIA.
        echo Una vez configurado, vuelve a ejecutar este instalador.
    ) else (
        echo [ERROR] No se encontro la plantilla '.env.template'. Reinstala el paquete.
    )
    pause
    exit /b 1
)
echo Configuracion .env detectada.
echo.

:: 3. Leer rutas del .env y crear carpetas locales
echo [3/5] Inicializando directorios locales en el servidor...
set HOST_STORAGE_PATH=
set HOST_DB_PATH=

for /f "usebackq tokens=1,2 delims==" %%i in (".env") do (
    if "%%i"=="HOST_STORAGE_PATH" set HOST_STORAGE_PATH=%%j
    if "%%i"=="HOST_DB_PATH" set HOST_DB_PATH=%%j
)

if "%HOST_STORAGE_PATH%"=="" set HOST_STORAGE_PATH=./uploads
if "%HOST_DB_PATH%"=="" set HOST_DB_PATH=./pgdata

echo Creando carpeta de subidas: %HOST_STORAGE_PATH%
if not exist "%HOST_STORAGE_PATH%" mkdir "%HOST_STORAGE_PATH%"

echo Creando carpeta de base de datos: %HOST_DB_PATH%
if not exist "%HOST_DB_PATH%" mkdir "%HOST_DB_PATH%"
echo Directorios inicializados correctamente.
echo.

:: 4. Levantar la aplicacion con Docker Compose
echo [4/5] Levantando contenedores de la aplicacion...
%DOCKER_CMD% up -d --build
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Ocurrio un error al arrancar los contenedores de Docker.
    echo Comprueba que los puertos 8080 y 5432 no esten ocupados y que Docker este activo.
    pause
    exit /b 1
)

:: 5. Esperar la URL publica del tunel Cloudflare
echo.
echo [5/5] Esperando URL publica del tunel Cloudflare...
set PUBLIC_URL=
set PUBLIC_URL_FILE=%HOST_STORAGE_PATH%\public_url.txt

:: Intentar leer la URL durante hasta 60 segundos
set /a ATTEMPTS=0
:WAIT_LOOP
set /a ATTEMPTS+=1
if %ATTEMPTS% gtr 30 goto TUNNEL_TIMEOUT

if exist "%PUBLIC_URL_FILE%" (
    set /p PUBLIC_URL=<"%PUBLIC_URL_FILE%"
    if not "%PUBLIC_URL%"=="" goto TUNNEL_OK
)
timeout /t 2 /nobreak >nul
goto WAIT_LOOP

:TUNNEL_TIMEOUT
echo [AVISO] No se pudo obtener la URL publica del tunel automaticamente.
echo Puedes consultarla manualmente con: docker logs cloudstorage-tunnel
echo.
goto FINAL

:TUNNEL_OK
echo.
echo =========================================================================
echo  URL PUBLICA DEL TUNEL: %PUBLIC_URL%
echo =========================================================================
echo.

:FINAL
echo =========================================================================
echo  INSTALACION COMPLETADA EXITOSAMENTE!
echo =========================================================================
echo  Tu nube personal ya esta levantada en segundo plano.
echo  - Acceso Local:    http://localhost:8080
if not "%PUBLIC_URL%"=="" (
echo  - Acceso Publico:  %PUBLIC_URL%
echo  - Swagger UI:      %PUBLIC_URL%/swagger-ui.html
echo  - Estado API:      %PUBLIC_URL%/api/info
echo  - Health Check:    %PUBLIC_URL%/actuator/health
)
echo  - Archivos cifrados en: %HOST_STORAGE_PATH%
echo  - Para ver contenedores: docker ps
echo =========================================================================
echo.
pause
