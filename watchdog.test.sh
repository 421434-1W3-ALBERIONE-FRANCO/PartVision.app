#!/usr/bin/env bash
# watchdog.test.sh — Prueba watchdog.sh de punta a punta sin tocar los contenedores reales.
#
# Uso (en el server, donde el watchdog corre de verdad):  ./watchdog.test.sh
#
# Pone un `docker` falso primero en el PATH (anota lo que le piden y nunca ejecuta nada),
# un servidor HTTP local como servicio "sano" y un puerto sin nada escuchando como servicio
# "caido". Estado y log van a un directorio temporal. Requiere bash, GNU date, curl y python3.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T="$(mktemp -d)"
SERVIDOR=""
trap '[ -n "$SERVIDOR" ] && kill "$SERVIDOR" 2>/dev/null; rm -rf "$T"' EXIT

# --- docker falso ---
mkdir -p "$T/bin"
cat > "$T/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_DOCKER_CALLS"
case "$1" in
  inspect) [ -n "${FAKE_STARTED_AT:-}" ] && echo "$FAKE_STARTED_AT" || exit 1 ;;
  restart) exit 0 ;;
esac
EOF
chmod +x "$T/bin/docker"

# --- servicio sano: un servidor HTTP que responde 200 ---
PUERTO=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
python3 -m http.server "$PUERTO" --bind 127.0.0.1 --directory "$T" >/dev/null 2>&1 &
SERVIDOR=$!
for _ in $(seq 1 50); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PUERTO/")" = "200" ] && break
  sleep 0.1
done
SANO="http://127.0.0.1:$PUERTO/"
CAIDO="http://127.0.0.1:9/"   # puerto discard: nada escucha

fallas=0
ok(){ printf '  ok    %s\n' "$1"; }
mal(){ printf '  FALLA %s\n' "$1"; fallas=$((fallas + 1)); }

hace(){ date -u -d "$1" +%Y-%m-%dT%H:%M:%S.000000000Z; }

# Corre el watchdog real con el backend en $1 (url) y el contenedor arrancado en $2.
correr(){
  rm -rf "$T/estado" "$T/log" "$T/llamadas"
  mkdir -p "$T/estado"
  [ -n "${PREVIOS:-}" ] && printf '%s\n' $PREVIOS > "$T/estado/backend.intentos"
  PATH="$T/bin:$PATH" FAKE_DOCKER_CALLS="$T/llamadas" FAKE_STARTED_AT="$2" \
    PV_WATCHDOG_BACKEND_URL="$1" PV_WATCHDOG_WEB_URL="$SANO" \
    PV_WATCHDOG_LOG="$T/log" PV_WATCHDOG_DIR="$T/estado" \
    PV_WATCHDOG_PAUSA=0 PV_WATCHDOG_ESPERA=0 PV_WATCHDOG_GRACIA=180 \
    bash "$DIR/watchdog.sh"
  touch "$T/log" "$T/llamadas"
}

reinicio(){ grep -q '^restart partvision-backend$' "$T/llamadas"; }
en_log(){ grep -q "$1" "$T/log"; }

echo "watchdog.sh"

# El caso que el 2026-09-16 no funciono: primera caida, sin reinicios previos.
PREVIOS="" correr "$CAIDO" "$(hace '-1 hour')"
reinicio && en_log 'no responde. Reiniciando' \
  && ok "primera caida: reinicia" || mal "primera caida: reinicia"

PREVIOS="" correr "$CAIDO" "$(hace '-30 seconds')"
! reinicio && en_log 'arranco hace menos de 180s' \
  && ok "recien arrancado: espera, no reinicia" || mal "recien arrancado: espera, no reinicia"

PREVIOS="$(date -d '-10 min' +%s) $(date -d '-8 min' +%s) $(date -d '-5 min' +%s)" \
  correr "$CAIDO" "$(hace '-1 hour')"
! reinicio && en_log 'ya se reinicio 3 veces' \
  && ok "3 reinicios en la ultima hora: se frena" || mal "3 reinicios en la ultima hora: se frena"

PREVIOS="$(date -d '-3 hours' +%s) $(date -d '-2 hours' +%s) $(date -d '-2 hours' +%s)" \
  correr "$CAIDO" "$(hace '-1 hour')"
reinicio && ok "reinicios viejos: vencen y vuelve a reiniciar" \
  || mal "reinicios viejos: vencen y vuelve a reiniciar"

PREVIOS="" correr "$CAIDO" ""
reinicio && ok "sin poder leer el arranque: igual reinicia" \
  || mal "sin poder leer el arranque: igual reinicia"

PREVIOS="" correr "$SANO" "$(hace '-1 hour')"
! grep -q '^restart' "$T/llamadas" && [ ! -s "$T/log" ] \
  && ok "todo sano: no toca nada ni escribe el log" || mal "todo sano: no toca nada ni escribe el log"

PREVIOS="" correr "$CAIDO" "$(hace '-1 hour')"
[ "$(grep -c . "$T/estado/backend.intentos")" = "1" ] \
  && ok "anota el reinicio para la ventana" || mal "anota el reinicio para la ventana"

echo
if [ "$fallas" -eq 0 ]; then echo "todo ok"; else echo "$fallas falla(s)"; fi
exit "$fallas"
