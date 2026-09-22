#!/usr/bin/env bash
# limpiar-imagenes.test.sh — Prueba limpiar-imagenes.sh con un docker falso.
#
# Uso:  ./limpiar-imagenes.test.sh
#
# Lo que importa verificar es que NUNCA borre de mas: solo huerfanas, solo viejas, nada de
# `-a`, y que una falla de la limpieza no de por fallido el deploy.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

VIEJA=aaaa11112222
NUEVA=bbbb33334444

mkdir -p "$T/bin"
cat > "$T/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_DOCKER_CALLS"
[ "${FAKE_DOCKER_RC:-0}" != "0" ] && { echo "Cannot connect to the Docker daemon" >&2; exit 1; }
case "$1 $2" in
  "images -f")   printf '%s\n%s\n' "$FAKE_VIEJA" "$FAKE_NUEVA" ;;
  "inspect -f")  # $4 es el id
                 case "$4" in
                   "$FAKE_VIEJA") date -u -d '-10 days' +%Y-%m-%dT%H:%M:%S.000000000Z ;;
                   "$FAKE_NUEVA") date -u -d '-2 hours'  +%Y-%m-%dT%H:%M:%S.000000000Z ;;
                 esac ;;
  "rmi "*)       [ "${FAKE_RMI_RC:-0}" = "0" ] || exit 1 ;;
esac
EOF
chmod +x "$T/bin/docker"

fallas=0
ok(){ printf '  ok    %s\n' "$1"; }
mal(){ printf '  FALLA %s\n' "$1"; fallas=$((fallas + 1)); }

correr(){
  rm -f "$T/llamadas"; touch "$T/llamadas"
  PATH="$T/bin:$PATH" FAKE_DOCKER_CALLS="$T/llamadas" FAKE_VIEJA="$VIEJA" FAKE_NUEVA="$NUEVA" \
    "$@" bash "$DIR/limpiar-imagenes.sh" 2>&1
}

echo "limpiar-imagenes.sh"

salida=$(correr env)
grep -q "^rmi $VIEJA$" "$T/llamadas" \
  && ok "borra la huerfana de 10 dias" || mal "borra la huerfana de 10 dias"
grep -q "^rmi $NUEVA$" "$T/llamadas" \
  && mal "conserva la de hace 2h (vuelta atras)" || ok "conserva la de hace 2h (vuelta atras)"
echo "$salida" | grep -q '1 borradas, 1 conservadas' \
  && ok "informa que hizo" || mal "informa que hizo ($salida)"

grep -q 'dangling=true' "$T/llamadas" \
  && ok "solo mira huerfanas" || mal "solo mira huerfanas"
grep -qE '(^| )-a( |$)' "$T/llamadas" \
  && mal "nunca usa -a" || ok "nunca usa -a"

salida=$(correr env RETENCION_HORAS=1)
grep -q "^rmi $NUEVA$" "$T/llamadas" \
  && ok "con retencion de 1h tambien borra la de 2h" || mal "con retencion de 1h tambien borra la de 2h"

salida=$(correr env LIMPIAR_IMAGENES=0)
[ ! -s "$T/llamadas" ] && echo "$salida" | grep -qi 'salteada' \
  && ok "con LIMPIAR_IMAGENES=0 no toca docker" || mal "con LIMPIAR_IMAGENES=0 no toca docker"

salida=$(correr env FAKE_DOCKER_RC=1); estado=$?
[ "$estado" -eq 0 ] && echo "$salida" | grep -qi 'ninguna' \
  && ok "si docker falla, no rompe el deploy" || mal "si docker falla, no rompe el deploy ($estado)"

salida=$(correr env FAKE_RMI_RC=1); estado=$?
[ "$estado" -eq 0 ] && echo "$salida" | grep -q '0 borradas, 2 conservadas' \
  && ok "si una imagen no se puede borrar, sigue" || mal "si una imagen no se puede borrar, sigue ($salida)"

echo
if [ "$fallas" -eq 0 ]; then echo "todo ok"; else echo "$fallas falla(s)"; fi
exit "$fallas"
