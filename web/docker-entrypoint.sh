#!/bin/sh
set -e

# 1) Inyecta la URL del backend en runtime (misma imagen, distinto entorno).
API="${API_BASE_URL:-http://localhost:8080/api/v1}"
echo "window.__env = { apiBaseUrl: '${API}' };" > /usr/share/nginx/html/env.js

# 2) Escucha en el puerto que exige la plataforma (Render/Railway inyectan $PORT).
PORT="${PORT:-80}"
sed -i "s/listen [0-9]\+;/listen ${PORT};/" /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
