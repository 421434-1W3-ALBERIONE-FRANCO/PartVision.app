#!/usr/bin/env bash
# restart.sh — Reinicia los contenedores de PartVision sin reconstruir nada.
#
# Uso:
#   ./restart.sh            # reinicia backend y web
#   ./restart.sh backend    # solo uno (tambien acepta: web | all)
#   ./restart.sh status     # solo informa, no toca nada
#
# A diferencia de redeploy.sh (que rebuildea las imagenes y tarda minutos), esto
# solo reinicia los contenedores ya construidos: sirve para levantar la app cuando
# se colgo, no para publicar codigo nuevo. El env y los secretos se conservan
# porque el contenedor es el mismo, no se recrea.
#
# Pensado para ejecutarse desde el celular via SSH, asi que imprime poco y claro.
set -uo pipefail

BACKEND_HEALTH="http://localhost:8088/actuator/health"
WEB_HEALTH="http://localhost:8084/"

TARGET="${1:-all}"

log(){ printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

# Codigo HTTP de una URL, o 000 si no hubo respuesta.
codigo(){ curl -s -o /dev/null -m 5 -w '%{http_code}' "$1" 2>/dev/null || echo 000; }

estado_contenedor(){  # $1=nombre
  docker inspect -f '{{.State.Status}}' "$1" 2>/dev/null || echo "no existe"
}

mostrar_estado(){
  printf '  %-22s %-12s %s\n' "CONTENEDOR" "ESTADO" "HTTP"
  printf '  %-22s %-12s %s\n' "partvision-backend" "$(estado_contenedor partvision-backend)" "$(codigo "$BACKEND_HEALTH")"
  printf '  %-22s %-12s %s\n' "partvision-web" "$(estado_contenedor partvision-web)" "$(codigo "$WEB_HEALTH")"
}

# Espera a que la URL devuelva 200. El backend arranca lento (JVM + Flyway).
esperar(){  # $1=url  $2=intentos
  local url="$1" n="$2" c
  for i in $(seq 1 "$n"); do
    c=$(codigo "$url")
    [ "$c" = "200" ] && { echo "   OK (HTTP 200)"; return 0; }
    printf '   esperando... (%s/%s, HTTP %s)\n' "$i" "$n" "$c"
    sleep 5
  done
  return 1
}

reiniciar(){  # $1=contenedor  $2=url  $3=intentos
  local nombre="$1"
  if [ "$(estado_contenedor "$nombre")" = "no existe" ]; then
    echo "   ERROR: el contenedor '$nombre' no existe. Hace falta un deploy: ./redeploy.sh"
    return 1
  fi
  log "Reiniciando $nombre"
  docker restart "$nombre" >/dev/null || { echo "   ERROR: no se pudo reiniciar"; return 1; }
  esperar "$2" "$3" || { echo "   AVISO: no respondio a tiempo. Ver: docker logs --tail 50 $nombre"; return 1; }
}

if [ "$TARGET" = "status" ]; then
  log "Estado actual"
  mostrar_estado
  exit 0
fi

case "$TARGET" in
  backend) reiniciar partvision-backend "$BACKEND_HEALTH" 24 ;;
  web)     reiniciar partvision-web "$WEB_HEALTH" 6 ;;
  all)     reiniciar partvision-backend "$BACKEND_HEALTH" 24
           reiniciar partvision-web "$WEB_HEALTH" 6 ;;
  *)       echo "Argumento desconocido: $TARGET (usa: backend | web | all | status)" >&2; exit 2 ;;
esac

log "Estado final"
mostrar_estado
