#!/usr/bin/env bash
# redeploy.sh — Reconstruye y recrea los contenedores de PartVision en el server self-hosted.
#
# Uso:
#   ./redeploy.sh              # rebuild de imagenes + recrea backend y web
#   ./redeploy.sh --no-build   # recrea SIN reconstruir (reusa las imagenes existentes)
#   ./redeploy.sh backend      # opera solo sobre el backend  (tambien acepta: web | all)
#   ./redeploy.sh --no-build web
#
# Contexto: server 190.106.132.98. Postgres es NATIVO del host en 127.0.0.1:5432
# (no lo maneja este script ni docker-compose). nginx del host hace de reverse-proxy + TLS.
# docker corre sin sudo (usuario en el grupo 'docker').
#
# Secretos: NUNCA se escriben en la linea de comando. Se extraen del contenedor VIVO
# a un env-file temporal (permisos 600) que se borra al terminar. Asi la Gemini key,
# el JWT_SECRET y el DB_PASSWORD se preservan sin re-tipearlos ni exponerlos.
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD=1
TARGET="all"

for arg in "$@"; do
  case "$arg" in
    --no-build)      BUILD=0 ;;
    backend|web|all) TARGET="$arg" ;;
    -h|--help)       awk 'NR==1{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "$0"; exit 0 ;;
    *) echo "Argumento desconocido: $arg (usa -h para ayuda)" >&2; exit 2 ;;
  esac
done

log(){ printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

# Extrae las env vars de app de un contenedor vivo a un archivo (600).
reuse_env(){  # $1=contenedor  $2=regex vars  $3=archivo destino
  local name="$1" re="$2" out="$3"
  if ! docker inspect "$name" >/dev/null 2>&1; then
    echo "ERROR: el contenedor '$name' no existe; no puedo reusar su env." >&2
    echo "       Es un primer despliegue: crealo una vez a mano con todas las -e" >&2
    echo "       (ver README/DEPLOY) y despues usa este script para los redeploys." >&2
    exit 1
  fi
  docker inspect "$name" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E "$re" > "$out"
  chmod 600 "$out"
}

# Espera hasta que una URL devuelva 200 (o se agoten los intentos).
health_wait(){  # $1=url  $2=intentos(def 18, ~90s)
  local url="$1" n="${2:-18}" code
  for i in $(seq 1 "$n"); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$url" || echo 000)
    printf '   intento %s: HTTP %s\n' "$i" "$code"
    [ "$code" = "200" ] && return 0
    sleep 5
  done
  return 1
}

deploy_backend(){
  [ "$BUILD" = 1 ] && { log "Build imagen backend"; docker build -t partvision-backend "$REPO_DIR/backend"; }
  log "Reusando env del backend actual (secretos preservados)"
  local envf; envf="$(mktemp)"; trap 'rm -f "$envf"' RETURN
  # COMPRAS_API_KEY va incluida a proposito: es lo unico que protege
  # POST /api/v1/compras/recepcion (endpoint publico, sin JWT). Si se pierde en un
  # redeploy, el controller la da por vacia y deja de validar, dejando el endpoint abierto.
  reuse_env partvision-backend '^(SPRING_|DB_|JWT_|CORS_|AI_|GEMINI_|COMPRAS_|MAIL_|APP_|PORT|MANAGEMENT_)' "$envf"
  # Overlay opcional para AGREGAR o ROTAR secretos sin recrear el contenedor a mano:
  # un VAR=valor por linea en ~/.partvision-backend.env (chmod 600). Va despues del
  # env reusado, asi pisa lo que ya estaba. Si no existe, no pasa nada.
  local overlay="$HOME/.partvision-backend.env"
  if [ -f "$overlay" ]; then
    log "Aplicando overlay $overlay"
    grep -E '^[A-Z_][A-Z0-9_]*=' "$overlay" >> "$envf" || true
  fi
  log "Recreando contenedor backend"
  docker stop partvision-backend >/dev/null 2>&1 || true
  docker rm   partvision-backend >/dev/null 2>&1 || true
  # Limite de memoria DURO: sin esto, con --network host y sin cgroup limit,
  # -XX:MaxRAMPercentage=75.0 de la imagen calcula el heap contra la RAM TOTAL
  # del host (3.8G) en vez de un cupo propio: una carga pesada puede tirar
  # abajo NexVia/Postgres, que comparten este mismo VPS. Con el limite puesto,
  # UseContainerSupport lo detecta solo y calcula el heap contra ESTO.
  # Ademas del techo de memoria: tope de CPU y de procesos. El VPS tiene 2 nucleos y los
  # comparte con NexVia; una importacion de 67k filas puede tomarlos enteros y dejar al otro
  # proyecto sin atender. --cpus es un freno proporcional, no mata nada: la importacion tarda
  # un poco mas y el host sigue respondiendo. --pids-limit acota un fork-bomb accidental.
  docker run -d --name partvision-backend --restart=always \
    --memory="${BACKEND_MEMORY:-1g}" --memory-swap="${BACKEND_MEMORY:-1g}" \
    --cpus="${BACKEND_CPUS:-1.5}" --pids-limit="${BACKEND_PIDS:-1024}" --network host \
    --env-file "$envf" partvision-backend >/dev/null
  log "Esperando health del backend (localhost:8088)"
  if health_wait http://localhost:8088/actuator/health; then
    echo "   backend UP"
  else
    echo "   AVISO: el backend no respondio 200 a tiempo. Revisa: docker logs partvision-backend"
  fi
}

deploy_web(){
  [ "$BUILD" = 1 ] && { log "Build imagen web"; docker build -t partvision-web "$REPO_DIR/web"; }
  log "Recreando contenedor web"
  local envf; envf="$(mktemp)"; trap 'rm -f "$envf"' RETURN
  # La web no tiene secretos: reusa su env si el contenedor existe, si no aplica defaults.
  if docker inspect partvision-web >/dev/null 2>&1; then
    docker inspect partvision-web --format '{{range .Config.Env}}{{println .}}{{end}}' \
      | grep -E '^(BACKEND_URL|API_BASE_URL|PORT|BIND_ADDR)=' > "$envf" || true
  fi
  [ -s "$envf" ] || printf 'BACKEND_URL=http://localhost:8088\nAPI_BASE_URL=/pv-api/v1\nPORT=8084\n' > "$envf"
  # La web corre con --network host: sin BIND_ADDR su nginx escucha en 0.0.0.0:8084 y el
  # panel queda publicado aparte, por HTTP plano, esquivando el nginx del host (TLS y
  # cabeceras). Se fuerza aunque el contenedor viejo no lo tuviera.
  grep -q '^BIND_ADDR=' "$envf" || printf 'BIND_ADDR=%s\n' "${WEB_BIND_ADDR:-127.0.0.1}" >> "$envf"
  chmod 600 "$envf"
  docker stop partvision-web >/dev/null 2>&1 || true
  docker rm   partvision-web >/dev/null 2>&1 || true
  # nginx sirviendo estaticos no necesita mas que esto; los topes existen para que un
  # problema aca no le saque recursos a NexVia, que comparte el VPS.
  docker run -d --name partvision-web --restart=always --network host \
    --memory="${WEB_MEMORY:-128m}" --memory-swap="${WEB_MEMORY:-128m}" \
    --cpus="${WEB_CPUS:-0.5}" --pids-limit="${WEB_PIDS:-128}" \
    --env-file "$envf" partvision-web >/dev/null
  log "Verificando web (localhost:8084)"
  health_wait http://localhost:8084/ 6 || echo "   AVISO: la web no respondio 200 a tiempo."
}

case "$TARGET" in
  backend) deploy_backend ;;
  web)     deploy_web ;;
  all)     deploy_backend; deploy_web ;;
esac

log "Estado final"
docker ps --filter name=partvision --format 'table {{.Names}}\t{{.Status}}'
