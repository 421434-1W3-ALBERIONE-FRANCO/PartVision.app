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
#
# Solo toca los contenedores partvision-*: nunca nginx ni NexVia, que comparten el host.
#
# Todo lo de abajo se puede pisar por variables de entorno; watchdog.test.sh lo usa para
# ejercitar el script entero con un docker falso, sin tocar los contenedores reales.
set -uo pipefail

BACKEND_HEALTH="${PV_WATCHDOG_BACKEND_URL:-http://localhost:8088/actuator/health}"
WEB_HEALTH="${PV_WATCHDOG_WEB_URL:-http://localhost:8084/}"
LOG="${PV_WATCHDOG_LOG:-${HOME}/pv-watchdog.log}"
ESTADO_DIR="${PV_WATCHDOG_DIR:-${HOME}/.pv-watchdog}"
MAX_REINICIOS=3                                # por ventana
VENTANA_SEG=3600                               # 1 hora
# Un contenedor recien arrancado todavia no atiende: el backend tarda hasta ~80s en
# levantar Spring. Sin esta gracia, despues de un reboot del host el watchdog lo
# encontraria "caido" y lo reiniciaria en pleno arranque.
GRACIA_SEG="${PV_WATCHDOG_GRACIA:-180}"
PAUSA_SEG="${PV_WATCHDOG_PAUSA:-5}"            # entre los dos intentos de salud
ESPERA_REINICIO="${PV_WATCHDOG_ESPERA:-90}"    # tras reiniciar, antes de volver a mirar

mkdir -p "$ESTADO_DIR"

registrar(){ printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOG"; }

sano(){  # $1=url — dos intentos, para no reiniciar por un hipo puntual
  local codigo
  for _ in 1 2; do
    # curl ya imprime 000 cuando no conecta; no hace falta (ni conviene) un "|| echo".
    codigo=$(curl -s -o /dev/null -m 10 -w '%{http_code}' "$1" 2>/dev/null)
    [ "$codigo" = "200" ] && return 0
    sleep "$PAUSA_SEG"
  done
  return 1
}

# 0 si el contenedor arranco hace menos de GRACIA_SEG. Si no se puede saber (el
# contenedor no existe, fecha ilegible) se sigue como antes: el reinicio lo intenta y,
# si falla, queda registrado. Callarse ante la duda dejaria al watchdog mudo para siempre.
recien_arrancado(){  # $1=contenedor
  local inicio desde
  inicio=$(docker inspect -f '{{.State.StartedAt}}' "$1" 2>/dev/null) || return 1
  desde=$(date -d "$inicio" +%s 2>/dev/null) || return 1
  [ $(( $(date +%s) - desde )) -lt "$GRACIA_SEG" ]
}

# Cuenta reinicios recientes; devuelve 1 si ya se supero el tope de la ventana.
puede_reiniciar(){  # $1=servicio
  local f="$ESTADO_DIR/$1.intentos" ahora recientes n
  ahora=$(date +%s)
  recientes=""
  if [ -f "$f" ]; then
    while read -r t; do
      [ -n "$t" ] && [ $((ahora - t)) -lt "$VENTANA_SEG" ] && recientes+="$t"$'\n'
    done < "$f"
  fi
  printf '%s' "$recientes" > "$f"
  # grep -c imprime 0 Y sale con 1 cuando no hay lineas. Un "|| echo 0" agregaba un
  # segundo 0, el test de abajo fallaba y el script creia que ya habia reiniciado 3 veces:
  # nunca pudo reiniciar nada (reboot del host del 2026-09-16).
  n=$(grep -c . "$f" 2>/dev/null)
  [ "${n:-0}" -lt "$MAX_REINICIOS" ]
}

anotar_reinicio(){ date +%s >> "$ESTADO_DIR/$1.intentos"; }

vigilar(){  # $1=servicio  $2=contenedor  $3=url
  sano "$3" && return 0

  if recien_arrancado "$2"; then
    registrar "$1 no responde pero arranco hace menos de ${GRACIA_SEG}s; se revisa en la proxima vuelta."
    return 0
  fi

  if ! puede_reiniciar "$1"; then
    registrar "$1 CAIDO pero ya se reinicio $MAX_REINICIOS veces en la ultima hora; no se toca. Revisar a mano."
    return 1
  fi

  registrar "$1 no responde. Reiniciando $2."
  anotar_reinicio "$1"
  docker restart "$2" >/dev/null 2>&1 || { registrar "$1 ERROR: fallo docker restart"; return 1; }

  sleep "$ESPERA_REINICIO"
  if sano "$3"; then
    registrar "$1 recuperado."
  else
    registrar "$1 SIGUE CAIDO despues del reinicio. Ver: docker logs --tail 100 $2"
  fi
}

vigilar backend partvision-backend "$BACKEND_HEALTH"
vigilar web     partvision-web     "$WEB_HEALTH"
