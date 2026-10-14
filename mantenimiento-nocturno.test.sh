#!/usr/bin/env bash
# mantenimiento-nocturno.test.sh — Prueba mantenimiento-nocturno.sh de punta a punta sin tocar
# la base, los contenedores ni la nube de verdad.
#
# Uso (en el server, donde la rutina corre de verdad):  ./mantenimiento-nocturno.test.sh
#
# Pone primero en el PATH un docker, un pg_dump y un df falsos, levanta un Backblaze falso en
# un puerto local y usa gpg de verdad con una llave descartable, asi se comprueba que el backup
# cifrado se abre y sale identico al original. Todo va a un directorio temporal.
# Requiere bash, GNU coreutils, flock, gpg y python3.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T="$(mktemp -d)"
B2_PID=""
trap '[ -n "$B2_PID" ] && kill "$B2_PID" 2>/dev/null; gpgconf --homedir "$T/gnupg" --kill gpg-agent 2>/dev/null; rm -rf "$T"' EXIT

mkdir -p "$T/bin" "$T/gnupg"
chmod 700 "$T/gnupg"

# --- docker falso: anota lo que le piden y contesta segun las variables FAKE_* ---
cat > "$T/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_DOCKER_CALLS"
case "$1" in
  inspect)
    if [[ "$*" == *RestartCount* ]]; then echo "${FAKE_ID:-contenedor-a} ${FAKE_REINICIOS:-0}"
    else printf 'DB_URL=jdbc:postgresql://localhost:5432/partvision_db\nDB_USERNAME=partvision\nDB_PASSWORD=x\n'; fi ;;
  stats)  echo "${FAKE_MEMORIA:-40.5}%" ;;
  logs)   printf '%s\n' "${FAKE_LOGS:-}" ;;
  images) exit 0 ;;
esac
EOF

# --- pg_dump falso: devuelve siempre el mismo "backup" (o falla) ---
cat > "$T/bin/pg_dump" <<'EOF'
#!/usr/bin/env bash
if [ "${FAKE_PGDUMP_FALLA:-0}" = "1" ]; then echo "pg_dump: error: conexion rechazada" >&2; exit 1; fi
cat "$FAKE_DUMP"
EOF

# --- pg_restore falso: como el de verdad, lee solo el principio y se va ---
cat > "$T/bin/pg_restore-falso" <<'EOF'
#!/usr/bin/env bash
head -c 100 > /dev/null
if [ "${FAKE_SIN_PRODUCTOS:-0}" = "1" ]; then echo "; Archive vacio"; exit 0; fi
printf '%s\n' "; Archive created" "4460; 0 16390 TABLE DATA public productos partvision" \
  "4461; 0 16391 TABLE DATA public compras partvision"
EOF

# --- df falso ---
cat > "$T/bin/df" <<'EOF'
#!/usr/bin/env bash
printf 'Filesystem 1024-blocks Used Available Capacity Mounted on\n/dev/vda2 41000000 1 1 %s%% /\n' "${FAKE_DISCO:-50}"
EOF
chmod +x "$T/bin/"*

# 200 KB: mas que lo que entra en una fifo (64 KB). Si nadie vaciara la copia que va a
# pg_restore, tee se cortaria y el backup cifrado quedaria trunco: el test lo veria.
head -c 200000 /dev/urandom > "$T/dump-original"

# --- llave descartable: la publica para la rutina, la privada para abrir lo que cifro ---
gpg --homedir "$T/gnupg" --batch --pinentry-mode loopback --passphrase '' \
    --quick-gen-key "prueba backups" future-default default never 2>/dev/null
gpg --homedir "$T/gnupg" --armor --export "prueba backups" > "$T/clave.asc"

# --- Backblaze falso ---
cat > "$T/b2.py" <<'EOF'
import base64, hashlib, json, os, sys
from http.server import BaseHTTPRequestHandler, HTTPServer
T, puerto = sys.argv[1], int(sys.argv[2])
CREDENCIAL = "Basic " + base64.b64encode(b"0051a2b3c4d5e6f7000000001:K005abcdefghijklmnopqrstuvwxyz12").decode()
BASE = f"http://127.0.0.1:{puerto}"
class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def responder(self, codigo, cuerpo):
        datos = json.dumps(cuerpo).encode()
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(datos)))
        self.end_headers()
        self.wfile.write(datos)
    def do_GET(self):
        if self.path != "/b2api/v2/b2_authorize_account":
            return self.responder(404, {"code": "not_found", "message": self.path})
        if self.headers.get("Authorization") != CREDENCIAL:
            return self.responder(401, {"code": "unauthorized", "message": "clave invalida"})
        self.responder(200, {"apiUrl": BASE, "authorizationToken": "tok", "allowed": {
            "bucketId": "bkt", "bucketName": "pv-backups", "capabilities": ["listBuckets", "writeFiles"]}})
    def do_POST(self):
        cuerpo = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if self.path == "/b2api/v2/b2_get_upload_url":
            if self.headers.get("Authorization") != "tok" or json.loads(cuerpo).get("bucketId") != "bkt":
                return self.responder(401, {"code": "bad_auth_token", "message": "token"})
            return self.responder(200, {"uploadUrl": BASE + "/subir", "authorizationToken": "up"})
        if self.path == "/subir":
            if self.headers.get("Authorization") != "up":
                return self.responder(401, {"code": "bad_auth_token", "message": "token de subida"})
            sha1 = hashlib.sha1(cuerpo).hexdigest()
            if sha1 != self.headers.get("X-Bz-Content-Sha1"):
                return self.responder(400, {"code": "bad_request", "message": "sha1 no coincide"})
            modo_f = os.path.join(T, "b2-modo")
            modo = open(modo_f).read().strip() if os.path.exists(modo_f) else "ok"
            with open(os.path.join(T, "b2-subidas"), "a") as f:
                f.write(f"{self.headers.get('X-Bz-File-Name')} {sha1}\n")
            return self.responder(200, {"fileName": self.headers.get("X-Bz-File-Name"),
                                        "contentSha1": sha1 if modo == "ok" else "0" * 40})
        self.responder(404, {"code": "not_found", "message": self.path})
HTTPServer(("127.0.0.1", puerto), H).serve_forever()
EOF
PUERTO=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
python3 "$T/b2.py" "$T" "$PUERTO" &
B2_PID=$!
for _ in $(seq 1 50); do
  python3 -c "import socket; socket.create_connection(('127.0.0.1', $PUERTO), 1)" 2>/dev/null && break
  sleep 0.1
done

fallas=0
ok(){ printf '  ok    %s\n' "$1"; }
mal(){ printf '  FALLA %s\n' "$1"; fallas=$((fallas + 1)); }

H="$T/home"
BK="$H/backups"
HOY_BK="$BK/partvision_db-2026-10-14.dump.gpg"

# Arranca de cero (salvo que CONSERVAR_ESTADO=1) y corre la rutina real. Lo que anotan los
# falsos (subidas a la nube, llamadas a docker) es de cada corrida: se borra siempre.
correr(){
  if [ "${CONSERVAR_ESTADO:-0}" != "1" ]; then
    rm -rf "$H" "$T/b2-modo"
    mkdir -p "$H"
  fi
  rm -f "$T/b2-subidas"
  : > "$T/llamadas"
  PATH="$T/bin:$PATH" FAKE_DOCKER_CALLS="$T/llamadas" FAKE_DUMP="$T/dump-original" \
    PV_HOME="$H" PV_MANT_DIR="$H/.estado" PV_BACKUP_DIR="$BK" \
    PV_BACKUP_CLAVE="${CLAVE:-$T/clave.asc}" PV_BACKUP_MIN_BYTES="${MIN_BYTES:-1000}" \
    PV_B2_ENV="$H/b2.env" PV_B2_API="http://127.0.0.1:$PUERTO" PV_PG_RESTORE="$T/bin/pg_restore-falso" \
    PV_HOY=2026-10-14 PV_DIA_SEMANA="${DIA:-2}" PV_LOG_MAX_BYTES=2000 PV_LOG_LINEAS=10 \
    PGHOST=localhost PGDATABASE=partvision_db PGUSER=partvision PGPASSWORD=x \
    bash "$DIR/mantenimiento-nocturno.sh"
  echo $? > "$T/salida"
}

salio(){ [ "$(cat "$T/salida")" = "$1" ]; }
en_log(){ grep -q -- "$1" "$H/pv-mantenimiento.log"; }
resultado(){ grep -q -- "$1" "$H/.estado/ultimo.txt"; }
abre_igual(){  # $1=archivo cifrado
  gpg --homedir "$T/gnupg" --batch --quiet --decrypt "$1" 2>/dev/null > "$T/descifrado" \
    && cmp -s "$T/descifrado" "$T/dump-original"
}
BUENA=K005abcdefghijklmnopqrstuvwxyz12
MALA=K005esta-no-es-la-clave-buena-000
con_nube(){ printf 'B2_KEY_ID=0051a2b3c4d5e6f7000000001\nB2_APP_KEY=%s\n' "${1:-$BUENA}" > "$H/b2.env"; }

echo "mantenimiento-nocturno.sh"

# --- backup ---
correr
[ -f "$HOY_BK" ] && abre_igual "$HOY_BK" \
  && ok "backup: cifrado, y al abrirlo sale identico al original" \
  || mal "backup: cifrado, y al abrirlo sale identico al original"
! head -c 200000 "$HOY_BK" | cmp -s - "$T/dump-original" \
  && ok "backup: lo guardado no es el backup en claro" || mal "backup: lo guardado no es el backup en claro"
[ "$(stat -c %a "$HOY_BK")" = "600" ] && [ -z "$(find "$BK" "$H/.estado" -name '*.parcial' -o -name '*.fifo')" ] \
  && ok "backup: solo lo lee el dueño y no quedan restos" || mal "backup: solo lo lee el dueño y no quedan restos"
salio 1 && resultado 'AVISO: la copia en la nube no esta configurada' \
  && ok "sin nube configurada: AVISO, no OK" || mal "sin nube configurada: AVISO, no OK"

rm -rf "$H"; mkdir -p "$BK"
for d in 10 11 12 13; do echo viejo > "$BK/partvision_db-2026-10-$d.dump.gpg"; done
CONSERVAR_ESTADO=1 correr
[ "$(ls "$BK" | tr '\n' ' ')" = "partvision_db-2026-10-12.dump.gpg partvision_db-2026-10-13.dump.gpg partvision_db-2026-10-14.dump.gpg " ] \
  && ok "conserva las ultimas 3 noches" || mal "conserva las ultimas 3 noches"

rm -rf "$H"; mkdir -p "$BK"; echo anterior > "$BK/partvision_db-2026-10-13.dump.gpg"
FAKE_PGDUMP_FALLA=1 CONSERVAR_ESTADO=1 correr
[ ! -f "$HOY_BK" ] && [ "$(cat "$BK/partvision_db-2026-10-13.dump.gpg")" = "anterior" ] && salio 2 \
  && resultado 'ERROR: el backup fallo (pg_dump: error: conexion rechazada' \
  && ok "pg_dump falla: ERROR, no guarda nada y no toca lo anterior" \
  || mal "pg_dump falla: ERROR, no guarda nada y no toca lo anterior"

FAKE_SIN_PRODUCTOS=1 correr
[ ! -f "$HOY_BK" ] && salio 2 && resultado 'no trae los productos' \
  && ok "backup sin productos (base vacia): ERROR y no se guarda" \
  || mal "backup sin productos (base vacia): ERROR y no se guarda"

MIN_BYTES=999999999 correr
[ ! -f "$HOY_BK" ] && resultado 'pesa solo' \
  && ok "backup demasiado chico: ERROR y no se guarda" || mal "backup demasiado chico: ERROR y no se guarda"

CLAVE="$T/no-existe.asc" correr
[ ! -f "$HOY_BK" ] && resultado 'falta la llave publica' \
  && ok "sin llave publica: ERROR, nunca guarda en claro" || mal "sin llave publica: ERROR, nunca guarda en claro"

# --- nube ---
rm -rf "$H"; mkdir -p "$H"; con_nube
CONSERVAR_ESTADO=1 correr
salio 0 && resultado 'OK' && [ "$(cut -d' ' -f1 "$T/b2-subidas")" = "diarios/partvision_db-2026-10-14.dump.gpg" ] \
  && [ "$(cut -d' ' -f2 "$T/b2-subidas")" = "$(sha1sum "$HOY_BK" | cut -d' ' -f1)" ] \
  && ok "nube: sube a diarios/ el mismo archivo que quedo en el server, y da OK" \
  || mal "nube: sube a diarios/ el mismo archivo que quedo en el server, y da OK"

rm -rf "$H"; mkdir -p "$H"; con_nube
DIA=7 CONSERVAR_ESTADO=1 correr
[ "$(cut -d' ' -f1 "$T/b2-subidas" | tr '\n' ' ')" = "diarios/partvision_db-2026-10-14.dump.gpg semanales/partvision_db-2026-10-14.dump.gpg " ] \
  && ok "nube: los domingos tambien a semanales/" || mal "nube: los domingos tambien a semanales/"

rm -rf "$H"; mkdir -p "$H"; con_nube "$MALA"
CONSERVAR_ESTADO=1 correr
salio 2 && resultado 'no se pudo subir el backup a la nube: B2 respondio 401' && [ -f "$HOY_BK" ] \
  && ! grep -q -- "$MALA" "$H/pv-mantenimiento.log" \
  && ok "nube con clave mala: ERROR, el backup local queda y la clave no va al log" \
  || mal "nube con clave mala: ERROR, el backup local queda y la clave no va al log"

rm -rf "$H"; mkdir -p "$H"; con_nube; echo sha-mal > "$T/b2-modo"
CONSERVAR_ESTADO=1 correr
salio 2 && resultado 'guardo un contenido distinto' \
  && ok "nube que guarda otra cosa: ERROR" || mal "nube que guarda otra cosa: ERROR"
rm -f "$T/b2-modo"

# --- revision ---
FAKE_DISCO=86 correr
resultado 'AVISO:.*disco al 86%' && ok "disco al 86%: AVISO" || mal "disco al 86%: AVISO"
FAKE_DISCO=93 correr
salio 2 && resultado 'ERROR: disco al 93%' && ok "disco al 93%: ERROR" || mal "disco al 93%: ERROR"
FAKE_MEMORIA=95.2 correr
resultado 'usa el 95% de su memoria' && ok "memoria del backend al 95%: AVISO" || mal "memoria del backend al 95%: AVISO"

rm -rf "$H"; mkdir -p "$H/.estado"; echo "contenedor-a 0" > "$H/.estado/reinicios"
FAKE_REINICIOS=2 FAKE_LOGS=$'algo\nKilled\nalgo mas' CONSERVAR_ESTADO=1 correr
resultado 'se reinicio solo 2 vez/veces.*falta de memoria' \
  && ok "reinicios desde anoche: AVISO, y dice si fue por memoria" \
  || mal "reinicios desde anoche: AVISO, y dice si fue por memoria"

rm -rf "$H"; mkdir -p "$H/.estado"; echo "contenedor-a 4" > "$H/.estado/reinicios"
FAKE_ID=contenedor-b FAKE_REINICIOS=0 CONSERVAR_ESTADO=1 correr
! resultado 'reinicio' && ok "contenedor nuevo (deploy): no lo cuenta como caida" \
  || mal "contenedor nuevo (deploy): no lo cuenta como caida"

rm -rf "$H"; mkdir -p "$H"
printf '%s  backend no responde. Reiniciando partvision-backend.\n' "$(date -d '-2 days' '+%Y-%m-%d %H:%M:%S')" > "$H/pv-watchdog.log"
CONSERVAR_ESTADO=1 correr
! resultado 'watchdog' && ok "watchdog: un reinicio de hace 2 dias no avisa" \
  || mal "watchdog: un reinicio de hace 2 dias no avisa"
printf '%s  backend no responde. Reiniciando partvision-backend.\n' "$(date -d '-1 hour' '+%Y-%m-%d %H:%M:%S')" >> "$H/pv-watchdog.log"
CONSERVAR_ESTADO=1 correr
resultado 'el watchdog tuvo que reiniciar 1 vez' && ok "watchdog: un reinicio de la ultima hora avisa" \
  || mal "watchdog: un reinicio de la ultima hora avisa"

# --- limpieza ---
rm -rf "$H"; mkdir -p "$H"
seq 1 1000 | sed 's/^/2026-10-01 00:00:00  linea /' > "$H/pv-watchdog.log"
touch -d '40 days ago' "$H/pv-redeploy-20260901-1200.log"
touch "$H/pv-redeploy-20261013-2351.log"
CONSERVAR_ESTADO=1 correr
[ "$(grep -c . "$H/pv-watchdog.log")" = "10" ] && [ ! -f "$H/pv-redeploy-20260901-1200.log" ] \
  && [ -f "$H/pv-redeploy-20261013-2351.log" ] \
  && ok "limpieza: recorta el log del watchdog y borra logs de deploy viejos, no los nuevos" \
  || mal "limpieza: recorta el log del watchdog y borra logs de deploy viejos, no los nuevos"
grep -q '^images -f dangling=true -q$' "$T/llamadas" && en_log 'Imagenes huerfanas: ninguna' \
  && ok "limpieza: corre limpiar-imagenes.sh" || mal "limpieza: corre limpiar-imagenes.sh"

# --- una corrida a la vez ---
rm -rf "$H"; mkdir -p "$H/.estado"
flock "$H/.estado/lock" sleep 3 &
bloqueo=$!
sleep 0.5
CONSERVAR_ESTADO=1 correr
# Solo ese: un `wait` a secas esperaria tambien al Backblaze falso, que no termina nunca.
wait "$bloqueo"
salio 0 && [ ! -f "$HOY_BK" ] && en_log 'Ya hay un mantenimiento corriendo' \
  && ok "si ya hay una corrida, la segunda no hace nada" || mal "si ya hay una corrida, la segunda no hace nada"

# --- cargar-b2.sh ---
cargar(){  # $1=keyID $2=applicationKey, tal como alguien los pegaria
  printf '%s\n%s\n' "$1" "$2" \
    | PV_B2_ENV="$H/b2.env" PV_B2_API="http://127.0.0.1:$PUERTO" bash "$DIR/cargar-b2.sh" > "$T/cargar.out" 2>&1
}
rm -rf "$H"; mkdir -p "$H"
cargar " 0051a2b3c4d5e6f7000000001 " "$BUENA "
[ "$(stat -c %a "$H/b2.env" 2>/dev/null)" = "600" ] && grep -qx 'B2_KEY_ID=0051a2b3c4d5e6f7000000001' "$H/b2.env" \
  && grep -qx "B2_APP_KEY=$BUENA" "$H/b2.env" && grep -q 'bucket: pv-backups' "$T/cargar.out" \
  && ok "cargar-b2: prueba la clave contra B2 y la guarda sin espacios, con permisos 600" \
  || mal "cargar-b2: prueba la clave contra B2 y la guarda sin espacios, con permisos 600"
rm -f "$H/b2.env"
cargar "0051a2b3c4d5e6f7000000001" "$MALA"
[ ! -f "$H/b2.env" ] && grep -q 'rechazo la clave' "$T/cargar.out" \
  && ok "cargar-b2: clave que B2 rechaza: no guarda nada" || mal "cargar-b2: clave que B2 rechaza: no guarda nada"
cargar "esto-no-es-un-keyid" "$BUENA"
[ ! -f "$H/b2.env" ] && grep -q 'hexadecimal' "$T/cargar.out" \
  && ok "cargar-b2: keyID con formato raro: no guarda nada" || mal "cargar-b2: keyID con formato raro: no guarda nada"

echo
if [ "$fallas" -eq 0 ]; then echo "todo ok"; else echo "$fallas falla(s)"; fi
exit "$fallas"
