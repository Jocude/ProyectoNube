---
name: levantar
description: Reconstruye y levanta el stack local (Postgres + app + túnel Cloudflare) con Docker Compose y comprueba que la app arranca.
disable-model-invocation: true
---

1. Comprobar que existe `.env` con `APP_LICENSE_KEY` no vacía (sin mostrar su valor ni ningún otro secreto). Si falta, avisar: la app hace `System.exit(1)` sin licencia válida.
2. `APP_UID=$(id -u) APP_GID=$(id -g) docker compose up -d --build $ARGUMENTS` (Compose v2). Sin argumentos levanta también el túnel público; para solo local, pasar `db app`. `./start.sh` hace lo mismo y además abre la URL pública.
3. Esperar a que la app responda: sondear `curl -fs http://localhost:8080/actuator/health` cada 5 s, máximo ~2 min.
4. Si no arranca: `docker compose logs --tail=80 app` y explicar el error (licencia, conexión a BD, bean que falla…).
5. Si arranca: dar http://localhost:8080 y la URL pública del túnel si aparece en `uploads/public_url.txt` o en `docker compose logs tunnel`.
