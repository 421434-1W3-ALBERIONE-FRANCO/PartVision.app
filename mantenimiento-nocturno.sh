#!/usr/bin/env bash
# mantenimiento-nocturno.sh — Backup cifrado de la base, revision del server y limpieza. Para cron.
#
# Instalacion (crontab -e), todos los dias a las 4:30 de Buenos Aires (el server esta en UTC):
#   30 7 * * * /home/c12742/Documents/Repos/PartVision.app/mantenimiento-nocturno.sh
#
# A esa hora no hay nadie usando el sistema y no choca con nada programado: la limpieza del
# historial de precios corre los domingos a las 4:00, ADS a las 6:30, la lista de EGSA llega
# a eso de las 7:15 y la planilla de compras a las 8:00.
#
# Que hace, en orden (cada paso sigue aunque el anterior falle):
#   1. Backup: pg_dump de la base, cifrado con la llave publica de backups-clave-publica.asc.
#      El backup en claro nunca toca el disco (va de pg_dump a gpg por un caño) y el server no
#      puede abrir lo que guarda: la llave privada la tiene solo el administrador. La base
#      tiene las claves del 2FA en claro, por eso nada sale de aca sin cifrar. Antes de
#      guardarlo se comprueba que el backup se pueda leer y que traiga los productos: un
#      pg_dump de una base vacia (por ejemplo, apuntando mal despues de la mudanza) sale bien
#      y no sirve para nada. En el server quedan las ultimas PV_BACKUP_CONSERVAR noches.
#   2. Nube: si esta configurada (cargar-b2.sh), sube la copia a Backblaze B2: todas las
#      noches a diarios/ y los domingos tambien a semanales/. La clave de B2 es de solo
#      escritura, asi que desde el server no se puede borrar ni leer un backup ya subido;
#      cuanto se guarda lo deciden las reglas del bucket (docs/BACKUPS.md).
#   3. Revision: disco, memoria del backend y reinicios desde la noche anterior.
#   4. Limpieza: imagenes de docker huerfanas (limpiar-imagenes.sh) y nuestros propios logs.
#
# El resultado de cada noche queda al final del log y en $ESTADO_DIR/ultimo.txt: OK, AVISO o
# ERROR, con el motivo. Sale con 0, 1 o 2 segun cual.
#
# Todo lo de abajo se puede pisar por variables de entorno; mantenimiento-nocturno.test.sh lo
# usa para correr el script entero con docker, pg_dump y Backblaze falsos.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PV_HOME="${PV_HOME:-$HOME}"
LOG="${PV_MANT_LOG:-$PV_HOME/pv-mantenimiento.log}"
ESTADO_DIR="${PV_MANT_DIR:-$PV_HOME/.pv-mantenimiento}"
BACKUP_DIR="${PV_BACKUP_DIR:-$PV_HOME/pv-backups/nocturnos}"
CONSERVAR="${PV_BACKUP_CONSERVAR:-3}"
CLAVE_PUBLICA="${PV_BACKUP_CLAVE:-$DIR/backups-clave-publica.asc}"
# El backup real pesa ~15 MB (2026-10). Uno de menos de 1 MB es una base vacia o un corte.
MIN_BYTES="${PV_BACKUP_MIN_BYTES:-1000000}"
B2_ENV="${PV_B2_ENV:-$PV_HOME/.pv-backup-b2.env}"
B2_API="${PV_B2_API:-https://api.backblazeb2.com}"
CONTENEDOR="${PV_MANT_CONTENEDOR:-partvision-backend}"
WATCHDOG_LOG="${PV_WATCHDOG_LOG:-$PV_HOME/pv-watchdog.log}"
DISCO_AVISO="${PV_DISCO_AVISO:-85}"
DISCO_ERROR="${PV_DISCO_ERROR:-92}"
MEMORIA_AVISO="${PV_MEMORIA_AVISO:-90}"
LOG_MAX_BYTES="${PV_LOG_MAX_BYTES:-1048576}"
LOG_LINEAS="${PV_LOG_LINEAS:-2000}"
REDEPLOY_LOG_DIAS="${PV_REDEPLOY_LOG_DIAS:-30}"
PG_RESTORE="${PV_PG_RESTORE:-pg_restore}"
# La fecha del nombre es la de Buenos Aires, la que ve el cliente.
HOY="${PV_HOY:-$(TZ=America/Argentina/Buenos_Aires date +%F)}"
DIA_SEMANA="${PV_DIA_SEMANA:-$(TZ=America/Argentina/Buenos_Aires date +%u)}"   # 7 = domingo

avisos=()
errores=()

registrar(){ printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOG"; }
aviso(){ avisos+=("$1"); registrar "AVISO: $1"; }
error(){ errores+=("$1"); registrar "ERROR: $1"; }
juntar(){ local sep="" x; for x in "$@"; do printf '%s%s' "$sep" "$x"; sep="; "; done; }

mkdir -p "$ESTADO_DIR" "$BACKUP_DIR"
chmod 700 "$ESTADO_DIR" "$BACKUP_DIR"

# Una sola corrida a la vez: si alguien la lanza a mano mientras corre la de cron, la segunda
# no hace nada en vez de pisar el backup a medio escribir.
exec 9> "$ESTADO_DIR/lock"
if ! flock -n 9; then
  registrar "Ya hay un mantenimiento corriendo; esta vuelta no hace nada."
  exit 0
fi

registrar "--- Mantenimiento nocturno ($HOY)"

# --- 1. Backup ---

# La base y su clave salen del contenedor del backend: una sola fuente de verdad. Si algun
# dia se cambia la clave de la base, no hay que acordarse de cambiarla tambien aca. Las
# variables PG* de siempre, si vienen puestas, mandan (las usan los tests).
datos_base(){
  local env url hostport
  env=$(docker inspect "$CONTENEDOR" --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null)
  url=$(printf '%s\n' "$env" | sed -n 's/^DB_URL=//p')       # jdbc:postgresql://localhost:5432/partvision_db
  url="${url#jdbc:postgresql://}"
  hostport="${url%%/*}"
  url="${url#*/}"
  : "${PGHOST:=${hostport%%:*}}"
  [ "$hostport" != "${hostport#*:}" ] && : "${PGPORT:=${hostport##*:}}"
  : "${PGDATABASE:=${url%%\?*}}"
  : "${PGUSER:=$(printf '%s\n' "$env" | sed -n 's/^DB_USERNAME=//p')}"
  : "${PGPASSWORD:=$(printf '%s\n' "$env" | sed -n 's/^DB_PASSWORD=//p')}"
  export PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
  [ -n "$PGHOST" ] && [ -n "$PGDATABASE" ] && [ -n "$PGUSER" ]
}

hacer_backup(){
  local nombre="partvision_db-${HOY}.dump.gpg"
  local final="$BACKUP_DIR/$nombre" tmp="$BACKUP_DIR/$nombre.parcial"
  local fifo="$ESTADO_DIR/dump.fifo" lista="$ESTADO_DIR/backup-contenido.txt" err="$ESTADO_DIR/backup-errores.txt"
  local gpghome valida_pid valida_st tamano st

  if [ ! -s "$CLAVE_PUBLICA" ]; then
    error "falta la llave publica ($CLAVE_PUBLICA): esta noche no hubo backup"
    return 1
  fi
  if ! datos_base; then
    error "no se pudo leer la base del contenedor $CONTENEDOR: esta noche no hubo backup"
    return 1
  fi

  # gpg necesita un directorio propio aunque no guarde nada: asi no toca el ~/.gnupg de la
  # cuenta, que en este server es compartida.
  rm -f "$fifo" "$tmp"
  # Sin la fifo, tee crearia un archivo comun con ese nombre y escribiria el backup EN CLARO.
  if ! mkfifo -m 600 "$fifo"; then
    error "no se pudo preparar el backup (mkfifo): esta noche no hubo backup"
    return 1
  fi
  gpghome=$(mktemp -d)
  # La copia que va por la fifo es solo para comprobar el backup. pg_restore --list lee el
  # indice del principio y se va; el `cat` vacia el resto, porque si nadie lee la fifo, tee se
  # corta a mitad de camino y el backup cifrado queda trunco.
  { "$PG_RESTORE" --list > "$lista" 2>/dev/null; echo $? > "$lista.st"; cat > /dev/null; } < "$fifo" &
  valida_pid=$!

  # --lock-wait-timeout: si una tabla esta bloqueada (el VACUUM FULL de los domingos a las
  # 4:00, por ejemplo), falla a los 10 minutos en vez de quedarse esperando para siempre con
  # el candado de esta rutina tomado.
  ( umask 077
    pg_dump -Fc --lock-wait-timeout=10min 2> "$err" \
      | tee "$fifo" \
      | gpg --homedir "$gpghome" --batch --yes --quiet --trust-model always \
            --recipient-file "$CLAVE_PUBLICA" --output "$tmp" --encrypt )
  st=$?
  wait "$valida_pid"
  valida_st=$(cat "$lista.st" 2>/dev/null || echo 1)
  rm -rf "$gpghome" "$fifo" "$lista.st"

  if [ "$st" -ne 0 ]; then
    error "el backup fallo ($(head -c 200 "$err" | tr '\n' ' '))"
    rm -f "$tmp"
    return 1
  fi
  if [ "$valida_st" != "0" ] || ! grep -q 'TABLE DATA public productos ' "$lista"; then
    error "el backup salio pero no se puede leer o no trae los productos: no se guardo"
    rm -f "$tmp"
    return 1
  fi
  tamano=$(stat -c %s "$tmp")
  if [ "$tamano" -lt "$MIN_BYTES" ]; then
    error "el backup pesa solo $tamano bytes (se esperan mas de $MIN_BYTES): no se guardo"
    rm -f "$tmp"
    return 1
  fi

  mv -f "$tmp" "$final"
  chmod 600 "$final"
  registrar "Backup: $nombre ($(numfmt --to=iec "$tamano"), $(grep -c 'TABLE DATA' "$lista") tablas con datos)"

  # Quedan las ultimas noches; los nombres llevan la fecha, asi que el orden alfabetico es el
  # cronologico. Lo que este a medias de una corrida cortada tampoco sirve.
  ls -1 "$BACKUP_DIR"/partvision_db-*.dump.gpg 2>/dev/null | sort | head -n -"$CONSERVAR" | xargs -r rm -f
  find "$BACKUP_DIR" -maxdepth 1 -name '*.parcial' -delete
  BACKUP_DE_HOY="$final"
}

# --- 2. Nube ---

subir_nube(){  # $1=archivo
  local archivo="$1" key_id app_key bucket nombre salida
  if [ ! -s "$B2_ENV" ]; then
    aviso "la copia en la nube no esta configurada (cargar-b2.sh): el backup de hoy quedo solo en el server"
    return 0
  fi
  key_id=$(sed -n 's/^B2_KEY_ID=//p' "$B2_ENV")
  app_key=$(sed -n 's/^B2_APP_KEY=//p' "$B2_ENV")
  bucket=$(sed -n 's/^B2_BUCKET_ID=//p' "$B2_ENV")
  nombre="$(basename "$archivo")"
  local destinos=("diarios/$nombre")
  [ "$DIA_SEMANA" = "7" ] && destinos+=("semanales/$nombre")

  # Las claves van por el entorno del proceso, no por la linea de comandos: lo que esta en la
  # linea de comandos lo ve cualquiera con `ps`.
  if salida=$(B2_API="$B2_API" B2_KEY_ID="$key_id" B2_APP_KEY="$app_key" B2_BUCKET_ID="$bucket" \
              python3 - "$archivo" "${destinos[@]}" 2>&1 <<'PY'
import base64, hashlib, json, os, sys, urllib.error, urllib.parse, urllib.request

archivo, destinos = sys.argv[1], sys.argv[2:]
api = os.environ["B2_API"].rstrip("/")

def pedir(url, datos=None, cabeceras=None):
    req = urllib.request.Request(url, data=datos, headers=cabeceras or {},
                                 method="POST" if datos is not None else "GET")
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        try:
            cuerpo = json.load(e)
            motivo = f"{cuerpo.get('code')}: {cuerpo.get('message')}"
        except Exception:
            motivo = e.reason
        sys.exit(f"B2 respondio {e.code} ({motivo})")
    except urllib.error.URLError as e:
        sys.exit(f"no se pudo conectar con B2 ({e.reason})")

credencial = base64.b64encode(f"{os.environ['B2_KEY_ID']}:{os.environ['B2_APP_KEY']}".encode()).decode()
auth = pedir(api + "/b2api/v2/b2_authorize_account", cabeceras={"Authorization": "Basic " + credencial})
bucket = os.environ.get("B2_BUCKET_ID") or (auth.get("allowed") or {}).get("bucketId")
if not bucket:
    sys.exit("la clave de B2 no esta atada a un bucket y falta B2_BUCKET_ID")

with open(archivo, "rb") as f:
    contenido = f.read()
sha1 = hashlib.sha1(contenido).hexdigest()
for destino in destinos:
    subida = pedir(auth["apiUrl"] + "/b2api/v2/b2_get_upload_url",
                   json.dumps({"bucketId": bucket}).encode(),
                   {"Authorization": auth["authorizationToken"], "Content-Type": "application/json"})
    r = pedir(subida["uploadUrl"], contenido, {
        "Authorization": subida["authorizationToken"],
        "X-Bz-File-Name": urllib.parse.quote(destino, safe="/"),
        "Content-Type": "application/octet-stream",
        "X-Bz-Content-Sha1": sha1,
    })
    if r.get("contentSha1") != sha1:
        sys.exit(f"{destino}: B2 guardo un contenido distinto al enviado")
    print(destino)
PY
  ); then
    registrar "Nube: subido a $(printf '%s' "$salida" | tr '\n' ' ')"
  else
    error "no se pudo subir el backup a la nube: $(printf '%s' "$salida" | tail -1)"
  fi
}

# --- 3. Revision ---

revisar_disco(){
  local uso
  uso=$(df -P / | awk 'NR==2 {gsub("%", "", $5); print $5}')
  if [ -z "$uso" ]; then
    aviso "no se pudo leer el uso del disco"
  elif [ "$uso" -ge "$DISCO_ERROR" ]; then
    error "disco al ${uso}%: si se llena, Postgres deja de escribir y se caen PartVision y NexVia"
  elif [ "$uso" -ge "$DISCO_AVISO" ]; then
    aviso "disco al ${uso}%"
  else
    registrar "Disco: ${uso}%"
  fi
}

revisar_memoria(){
  local uso
  uso=$(docker stats --no-stream --format '{{.MemPerc}}' "$CONTENEDOR" 2>/dev/null | tr -d '%' | cut -d. -f1)
  if [ -z "$uso" ]; then
    aviso "no se pudo leer la memoria del backend"
  elif [ "$uso" -ge "$MEMORIA_AVISO" ]; then
    aviso "el backend usa el ${uso}% de su memoria: cerca del limite en el que el kernel lo mata"
  else
    registrar "Memoria del backend: ${uso}% de su limite"
  fi
}

# Dos fuentes, porque son reinicios distintos: los de Docker (el proceso murio, por ejemplo
# el kernel lo mato por memoria, como a las 6:30 del 10 al 12/10/2026) y los del watchdog (el
# proceso seguia vivo pero no respondia).
revisar_reinicios(){
  local id cuenta antes_id="" antes_cuenta=0 muertes desde watchdog
  if ! read -r id cuenta < <(docker inspect -f '{{.Id}} {{.RestartCount}}' "$CONTENEDOR" 2>/dev/null); then
    aviso "no se pudo leer el contenedor $CONTENEDOR"
    return
  fi
  [ -f "$ESTADO_DIR/reinicios" ] && read -r antes_id antes_cuenta < "$ESTADO_DIR/reinicios"
  echo "$id $cuenta" > "$ESTADO_DIR/reinicios"
  # Si cambio el id, hubo un deploy: el contenedor es otro y su cuenta arranca en cero.
  if [ "$id" = "$antes_id" ] && [ "${cuenta:-0}" -gt "${antes_cuenta:-0}" ]; then
    muertes=$(docker logs --since 24h "$CONTENEDOR" 2>&1 | grep -c '^Killed$')
    if [ "${muertes:-0}" -gt 0 ]; then
      aviso "el backend se cayo y se reinicio solo $((cuenta - antes_cuenta)) vez/veces desde la noche anterior; el kernel lo mato por falta de memoria"
    else
      aviso "el backend se cayo y se reinicio solo $((cuenta - antes_cuenta)) vez/veces desde la noche anterior"
    fi
  fi
  if [ -f "$WATCHDOG_LOG" ]; then
    desde=$(date -d '-24 hours' '+%Y-%m-%d %H:%M:%S')
    watchdog=$(awk -v d="$desde" 'substr($0, 1, 19) >= d && /Reiniciando/' "$WATCHDOG_LOG" | grep -c .)
    [ "${watchdog:-0}" -gt 0 ] && aviso "el watchdog tuvo que reiniciar $watchdog vez/veces un contenedor que no respondia"
  fi
}

# --- 4. Limpieza ---

recortar_log(){  # $1=archivo — se queda con las ultimas lineas si pasa del tope
  local f="$1"
  [ -f "$f" ] || return 0
  if [ "$(stat -c %s "$f")" -gt "$LOG_MAX_BYTES" ]; then
    tail -n "$LOG_LINEAS" "$f" > "$f.recorte" && cat "$f.recorte" > "$f" && rm -f "$f.recorte"
  fi
}

limpiar(){
  if [ -x "$DIR/limpiar-imagenes.sh" ]; then
    registrar "$("$DIR/limpiar-imagenes.sh" 2>&1 | tail -1)"
  fi
  recortar_log "$WATCHDOG_LOG"
  find "$PV_HOME" -maxdepth 1 -name 'pv-redeploy-*.log' -mtime +"$REDEPLOY_LOG_DIAS" -delete 2>/dev/null
}

BACKUP_DE_HOY=""
hacer_backup && subir_nube "$BACKUP_DE_HOY"
revisar_disco
revisar_memoria
revisar_reinicios
limpiar

if [ "${#errores[@]}" -gt 0 ]; then
  resultado="ERROR: $(juntar "${errores[@]}")"
  [ "${#avisos[@]}" -gt 0 ] && resultado+=" | AVISO: $(juntar "${avisos[@]}")"
  salida=2
elif [ "${#avisos[@]}" -gt 0 ]; then
  resultado="AVISO: $(juntar "${avisos[@]}")"
  salida=1
else
  resultado="OK"
  salida=0
fi
recortar_log "$LOG"
registrar "RESULTADO: $resultado"
printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M')" "$resultado" > "$ESTADO_DIR/ultimo.txt"
exit "$salida"
