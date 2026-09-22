#!/usr/bin/env bash
# limpiar-imagenes.test.sh — Prueba limpiar-imagenes.sh con un docker falso.
#
# Uso:  ./limpiar-imagenes.test.sh
#
# Lo que importa verificar es que NUNCA borre de mas: nada de `-a`, y que una falla de la
# limpieza no de por fallido el deploy.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

mkdir -p "$T/bin"
cat > "$T/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_DOCKER_CALLS"
[ "${FAKE_DOCKER_RC:-0}" != "0" ] && { echo "Cannot connect to the Docker daemon" >&2; exit 1; }
echo "Deleted Images:"
echo "Total reclaimed space: 333.9MB"
EOF
chmod +x "$T/bin/docker"

fallas=0
ok(){ printf '  ok    %s\n' "$1"; }
mal(){ printf '  FALLA %s\n' "$1"; fallas=$((fallas + 1)); }

correr(){  # imprime la salida; deja las llamadas en $T/llamadas
  rm -f "$T/llamadas"
  touch "$T/llamadas"
  PATH="$T/bin:$PATH" FAKE_DOCKER_CALLS="$T/llamadas" "$@" bash "$DIR/limpiar-imagenes.sh" 2>&1
}

echo "limpiar-imagenes.sh"

salida=$(correr env)
grep -q -- 'image prune -f --filter until=72h' "$T/llamadas" \
  && ok "borra huerfanas de mas de 72h" || mal "borra huerfanas de mas de 72h"

grep -q -- ' -a' "$T/llamadas" \
  && mal "nunca usa prune -a" || ok "nunca usa prune -a"

echo "$salida" | grep -q '333.9MB' \
  && ok "informa cuanto recupero" || mal "informa cuanto recupero"

salida=$(correr env RETENCION=24h)
grep -q -- 'until=24h' "$T/llamadas" \
  && ok "respeta la retencion configurada" || mal "respeta la retencion configurada"

salida=$(correr env LIMPIAR_IMAGENES=0)
[ ! -s "$T/llamadas" ] && echo "$salida" | grep -qi 'salteada' \
  && ok "con LIMPIAR_IMAGENES=0 no toca docker" || mal "con LIMPIAR_IMAGENES=0 no toca docker"

salida=$(correr env FAKE_DOCKER_RC=1); estado=$?
[ "$estado" -eq 0 ] && echo "$salida" | grep -qi 'AVISO' \
  && ok "si docker falla, avisa pero no rompe el deploy" || mal "si docker falla, avisa pero no rompe el deploy"

echo
if [ "$fallas" -eq 0 ]; then echo "todo ok"; else echo "$fallas falla(s)"; fi
exit "$fallas"
