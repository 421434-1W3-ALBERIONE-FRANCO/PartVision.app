#!/usr/bin/env bash
# limpiar-imagenes.test.sh — Prueba limpiar-imagenes.sh con un docker falso.
#
# Uso:  ./limpiar-imagenes.test.sh
#
# Lo que importa verificar es que NUNCA borre de mas: solo huerfanas, solo viejas, nada de
# `-a`, y que una falla de la limpieza no de por fallido el deploy. Y que no se quede corto:
# borrar una imagen puede dejar a la vista a su padre, que tambien hay que borrar.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

VIEJA=aaaa11112222
MEDIA=cccc55556666
NUEVA=bbbb33334444
PADRE=dddd77778888

# El docker falso recuerda que huerfanas quedan (en $FAKE_LISTA): `rmi` saca una de la lista y,
# si es la VIEJA y hay FAKE_PADRE, deja a la vista a su padre, como hace el builder de verdad.
mkdir -p "$T/bin"
cat > "$T/bin/docker" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_DOCKER_CALLS"
[ "${FAKE_DOCKER_RC:-0}" != "0" ] && { echo "Cannot connect to the Docker daemon" >&2; exit 1; }
case "$1 $2" in
  "images -f")   cat "$FAKE_LISTA" ;;
  "inspect -f")  # $4 es el id
                 case "$4" in
                   "$FAKE_VIEJA"|"$FAKE_PADRE") date -u -d '-10 days' +%Y-%m-%dT%H:%M:%S.000000000Z ;;
                   "$FAKE_MEDIA") date -u -d '-30 hours' +%Y-%m-%dT%H:%M:%S.000000000Z ;;
                   "$FAKE_NUEVA") date -u -d '-2 hours'  +%Y-%m-%dT%H:%M:%S.000000000Z ;;
                 esac ;;
  "rmi "*)       [ "${FAKE_RMI_RC:-0}" = "0" ] || exit 1
                 grep -vx "$2" "$FAKE_LISTA" > "$FAKE_LISTA.tmp"; mv "$FAKE_LISTA.tmp" "$FAKE_LISTA"
                 if [ "$2" = "$FAKE_VIEJA" ] && [ -n "${FAKE_PADRE:-}" ]; then
                   echo "$FAKE_PADRE" >> "$FAKE_LISTA"
                 fi ;;
esac
EOF
chmod +x "$T/bin/docker"

fallas=0
ok(){ printf '  ok    %s\n' "$1"; }
mal(){ printf '  FALLA %s\n' "$1"; fallas=$((fallas + 1)); }

correr(){
  rm -f "$T/llamadas"; touch "$T/llamadas"
  printf '%s\n%s\n%s\n' "$VIEJA" "$MEDIA" "$NUEVA" > "$T/lista"
  PATH="$T/bin:$PATH" FAKE_DOCKER_CALLS="$T/llamadas" FAKE_LISTA="$T/lista" \
    FAKE_VIEJA="$VIEJA" FAKE_MEDIA="$MEDIA" FAKE_NUEVA="$NUEVA" \
    "$@" bash "$DIR/limpiar-imagenes.sh" 2>&1
}

echo "limpiar-imagenes.sh"

salida=$(correr env FAKE_PADRE=)
grep -q "^rmi $VIEJA$" "$T/llamadas" \
  && ok "borra la huerfana de 10 dias" || mal "borra la huerfana de 10 dias"
grep -q "^rmi $NUEVA$" "$T/llamadas" \
  && mal "conserva la de hace 2h (vuelta atras)" || ok "conserva la de hace 2h (vuelta atras)"
grep -q "^rmi $MEDIA$" "$T/llamadas" \
  && ok "retencion por defecto de 24h: borra la de 30h" || mal "retencion por defecto de 24h: borra la de 30h"
echo "$salida" | grep -q '2 borradas, 1 conservadas (menos de 24h' \
  && ok "informa que hizo" || mal "informa que hizo ($salida)"
[ "$(grep -c "^rmi $VIEJA$" "$T/llamadas")" -eq 1 ] \
  && ok "no intenta borrar dos veces la misma" || mal "no intenta borrar dos veces la misma"

grep -q 'dangling=true' "$T/llamadas" \
  && ok "solo mira huerfanas" || mal "solo mira huerfanas"
grep -qE '(^| )-a( |$)' "$T/llamadas" \
  && mal "nunca usa -a" || ok "nunca usa -a"

# El caso del 2026-09-30: una pasada borraba la punta de la cadena y dejaba al padre, que era
# el que tenia los bytes, para el deploy siguiente.
salida=$(correr env FAKE_PADRE="$PADRE")
grep -q "^rmi $PADRE$" "$T/llamadas" \
  && ok "borra tambien al padre que queda a la vista" || mal "borra tambien al padre que queda a la vista"
echo "$salida" | grep -q '3 borradas, 1 conservadas' \
  && ok "y lo cuenta" || mal "y lo cuenta ($salida)"

salida=$(correr env FAKE_PADRE= RETENCION_HORAS=1)
grep -q "^rmi $NUEVA$" "$T/llamadas" \
  && ok "con retencion de 1h tambien borra la de 2h" || mal "con retencion de 1h tambien borra la de 2h"

salida=$(correr env LIMPIAR_IMAGENES=0)
[ ! -s "$T/llamadas" ] && echo "$salida" | grep -qi 'salteada' \
  && ok "con LIMPIAR_IMAGENES=0 no toca docker" || mal "con LIMPIAR_IMAGENES=0 no toca docker"

salida=$(correr env FAKE_DOCKER_RC=1); estado=$?
[ "$estado" -eq 0 ] && echo "$salida" | grep -qi 'ninguna' \
  && ok "si docker falla, no rompe el deploy" || mal "si docker falla, no rompe el deploy ($estado)"

salida=$(correr env FAKE_PADRE= FAKE_RMI_RC=1); estado=$?
[ "$estado" -eq 0 ] && echo "$salida" | grep -q '0 borradas, 3 conservadas' \
  && ok "si una imagen no se puede borrar, sigue" || mal "si una imagen no se puede borrar, sigue ($salida)"
[ "$(grep -c '^images -f' "$T/llamadas")" -eq 1 ] \
  && ok "si una pasada no borra nada, no repite" || mal "si una pasada no borra nada, no repite"

echo
if [ "$fallas" -eq 0 ]; then echo "todo ok"; else echo "$fallas falla(s)"; fi
exit "$fallas"
