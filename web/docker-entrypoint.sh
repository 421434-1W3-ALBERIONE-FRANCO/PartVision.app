#!/bin/sh
set -e

# 1) URL base del frontend en runtime. Relativa por defecto (mismo origen via el proxy /api).
API="${API_BASE_URL:-/api/v1}"
echo "window.__env = { apiBaseUrl: '${API}' };" > /usr/share/nginx/html/env.js

# 2) Reverse-proxy al backend: reemplaza los placeholders de nginx con BACKEND_URL.
#    En prod (Render) definir BACKEND_URL con la URL publica del backend (ej:
#    https://partvision-app.onrender.com). Local: default al backend en :8080.
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
BACKEND_HOST="$(echo "$BACKEND_URL" | sed -E 's#^https?://##; s#/.*$##')"
sed -i "s#__BACKEND_URL__#${BACKEND_URL}#g; s#__BACKEND_HOST__#${BACKEND_HOST}#g" /etc/nginx/conf.d/default.conf

# 3) Escucha en el puerto que exige la plataforma (Render/Railway inyectan $PORT).
#    BIND_ADDR acota la interfaz: en el VPS compartido se corre con --network host y
#    BIND_ADDR=127.0.0.1 para que solo entre por el nginx del host (TLS + limites) y no
#    quede la web publicada aparte en el puerto 8084. Vacio = todas las interfaces.
PORT="${PORT:-80}"
BIND="${BIND_ADDR:+${BIND_ADDR}:}"
sed -i "s/listen [0-9.:]\+;/listen ${BIND}${PORT};/" /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
