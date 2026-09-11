#!/usr/bin/env bash
# watchdog.sh — Reinicia PartVision si deja de responder. Pensado para cron.
#
# Instalacion (crontab -e), cada 5 minutos:
#   */5 * * * * /home/c12742/Documents/Repos/PartVision.app/watchdog.sh
#
# Por que hace falta si los contenedores tienen --restart=always: esa politica cubre
# el proceso que MUERE, pero no el que sigue vivo sin poder atender (heap agotado,
# deadlock, thread pool tomado). Ahi Docker ve el contenedor "running" y no hace nada.
#
# Para no entrar en un loop de reinicios cuando la falla es de fondo (por ejemplo la
# base caida), se frena tras varios intentos seguidos dentro de una misma ventana.
set -uo pipefail

BACKEND_HEALTH="http://localhost:8088/actuator/health"
WEB_HEALTH="http://localhost:8084/"
LOG="${HOME}/pv-watchdog.log"
ESTADO_DIR="${HOME}/.pv-watchdog"
MAX_REINICIOS=3          # por ventana
VENTANA_SEG=3600         # 1 hora

mkdir -p "$ESTADO_DIR"

registrar(){ printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOG"; }

sano(){  # $1=url — dos intentos, para no reiniciar por un hipo puntual
  for _ in 1 2; do
    [ "$(curl -s -o /dev/null -m 10 -w '%{http_code}' "$1" 2>/dev/null || echo 000)" = "200" ] && return 0
    sleep 5
  done
  return 1
}

# Cuenta reinicios recientes; devuelve 1 si ya se supero el tope de la ventana.
puede_reiniciar(){  # $1=servicio
  local f="$ESTADO_DIR/$1.intentos" ahora recientes
  ahora=$(date +%s)
  recientes=""
  if [ -f "$f" ]; then
    while read -r t; do
      [ -n "$t" ] && [ $((ahora - t)) -lt "$VENTANA_SEG" ] && recientes+="$t"$'\n'
    done < "$f"
  fi
  printf '%s' "$recientes" > "$f"
  [ "$(grep -c . "$f" 2>/dev/null || echo 0)" -lt "$MAX_REINICIOS" ]
}

anotar_reinicio(){ date +%s >> "$ESTADO_DIR/$1.intentos"; }

vigilar(){  # $1=servicio  $2=contenedor  $3=url
  sano "$3" && return 0

  if ! puede_reiniciar "$1"; then
    registrar "$1 CAIDO pero ya se reinicio $MAX_REINICIOS veces en la ultima hora; no se toca. Revisar a mano."
    return 1
  fi

  registrar "$1 no responde. Reiniciando $2."
  anotar_reinicio "$1"
  docker restart "$2" >/dev/null 2>&1 || { registrar "$1 ERROR: fallo docker restart"; return 1; }

  sleep 45
  if sano "$3"; then
    registrar "$1 recuperado."
  else
    registrar "$1 SIGUE CAIDO despues del reinicio. Ver: docker logs --tail 100 $2"
  fi
}

vigilar backend partvision-backend "$BACKEND_HEALTH"
vigilar web     partvision-web     "$WEB_HEALTH"
