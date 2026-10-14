#!/usr/bin/env bash
# cargar-b2.sh — Guarda en el server la clave de Backblaze B2 para la copia nocturna en la nube.
#
# Uso (en el server; lo corre el administrador, a mano):
#   ./cargar-b2.sh
#
# Pide el keyID y la applicationKey de una "Application Key" de B2 (como crearla: docs/BACKUPS.md).
# Lo que se escribe se ve en pantalla a proposito: pegar en un prompt oculto desde una terminal de
# Windows a veces mete caracteres de mas o llega vacio sin que se note, y la clave quedaria mal.
#
# Antes de guardarla la prueba contra B2 (solo pide autorizacion: no sube ni borra nada) y exige
# que este limitada a un solo bucket y pueda escribir. Si ademas puede borrar, avisa: conviene una
# de solo escritura, asi quien entre al server no puede borrar los backups de la nube.
#
# Queda en ~/.pv-backup-b2.env con permisos 600. Estas claves no se pegan nunca en un chat.
set -euo pipefail

DESTINO="${PV_B2_ENV:-$HOME/.pv-backup-b2.env}"
API="${PV_B2_API:-https://api.backblazeb2.com}"

limpio(){ printf '%s' "$1" | tr -d '[:space:]'; }

read -rp "keyID: " id
read -rp "applicationKey: " clave
id=$(limpio "$id")
clave=$(limpio "$clave")

if ! [[ "$id" =~ ^[0-9a-f]{12,40}$ ]]; then
  echo "El keyID tiene que ser hexadecimal (numeros y letras de la a a la f). Revisalo y volve a correr el script." >&2
  exit 1
fi
if [ "${#clave}" -lt 30 ]; then
  echo "La applicationKey parece incompleta: tiene ${#clave} caracteres y suelen ser 31." >&2
  exit 1
fi

echo "Probando la clave contra Backblaze..."
# Las claves van por el entorno del proceso, no por la linea de comandos (que se ve con `ps`).
if ! info=$(B2_KEY_ID="$id" B2_APP_KEY="$clave" B2_API="$API" python3 - <<'PY'
import base64, json, os, sys, urllib.error, urllib.request

credencial = base64.b64encode(f"{os.environ['B2_KEY_ID']}:{os.environ['B2_APP_KEY']}".encode()).decode()
req = urllib.request.Request(os.environ["B2_API"].rstrip("/") + "/b2api/v2/b2_authorize_account",
                             headers={"Authorization": "Basic " + credencial})
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        auth = json.load(r)
except urllib.error.HTTPError as e:
    sys.exit(f"Backblaze rechazo la clave (HTTP {e.code}): revisa que el keyID y la applicationKey sean de la misma clave.")
except urllib.error.URLError as e:
    sys.exit(f"No se pudo conectar con Backblaze ({e.reason}).")

permitido = auth.get("allowed") or {}
permisos = permitido.get("capabilities") or []
if not permitido.get("bucketId"):
    sys.exit("La clave no esta limitada a un bucket: crea otra eligiendo el bucket de los backups.")
if "writeFiles" not in permisos:
    sys.exit("La clave no puede escribir archivos: crea otra con acceso de escritura (Write Only).")
print(f"bucket: {permitido.get('bucketName')}")
print(f"permisos: {', '.join(permisos)}")
if "deleteFiles" in permisos:
    print("OJO: esta clave tambien puede BORRAR archivos. Conviene una de solo escritura (Write Only).")
PY
); then
  exit 1
fi

printf '%s\n' "$info" | sed 's/^/  /'
( umask 077; printf 'B2_KEY_ID=%s\nB2_APP_KEY=%s\n' "$id" "$clave" > "$DESTINO" )
chmod 600 "$DESTINO"
echo "Listo: la clave quedo guardada en $DESTINO."
echo "El primer backup sube esta noche a las 4:30. Para probarlo ahora:"
echo "  $(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mantenimiento-nocturno.sh; tail -n 4 ~/pv-mantenimiento.log"
