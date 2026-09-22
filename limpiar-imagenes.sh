#!/usr/bin/env bash
# limpiar-imagenes.sh — Borra las imagenes de docker que quedaron huerfanas.
#
# Uso:
#   ./limpiar-imagenes.sh              # borra las huerfanas de mas de 72h
#   RETENCION_HORAS=24 ./limpiar-imagenes.sh
#   LIMPIAR_IMAGENES=0 ./limpiar-imagenes.sh   # no hace nada (para saltearlo en un deploy)
#
# Lo llama redeploy.sh al terminar: cada deploy construye una imagen nueva y deja la anterior
# sin etiqueta, ~900 MB por vez. El 2026-09-21 eso tenia el disco del VPS al 94%, y el disco
# lo comparte el proyecto NexVia: si se llena, Postgres deja de escribir y se caen los dos.
#
# Por que la edad se calcula aca y no con `docker image prune --filter until=`: el 2026-09-22,
# con docker 29.1.3, ese filtro dejo sin borrar una imagen huerfana de 4 dias (`until=72h` y
# hasta `until=1h` informaron 0B), mientras que `docker rmi` sobre esa misma imagen la borro
# sin problema. Una limpieza que a veces no limpia no sirve para lo que existe.
#
# Dos cuidados, a proposito:
#   - Solo se miran imagenes HUERFANAS (sin etiqueta). Las etiquetadas no se tocan nunca: ni
#     las que estan corriendo ni las base (maven, temurin, nginx, node, postgres), que sin
#     ellas cada deploy tendria que volver a bajarlas. Nunca se usa `prune -a`, que ademas se
#     llevaria imagenes de cualquier otro proyecto que algun dia use docker en este host.
#   - Se conservan las de las ultimas RETENCION_HORAS: la imagen del deploy anterior es la
#     vuelta atras rapida si el nuevo sale mal.
set -uo pipefail

RETENCION_HORAS="${RETENCION_HORAS:-72}"

if [ "${LIMPIAR_IMAGENES:-1}" != "1" ]; then
  echo "Limpieza de imagenes salteada (LIMPIAR_IMAGENES=${LIMPIAR_IMAGENES:-1})"
  exit 0
fi

huerfanas=$(docker images -f dangling=true -q 2>/dev/null)
if [ -z "$huerfanas" ]; then
  echo "Imagenes huerfanas: ninguna"
  exit 0
fi

limite=$(( $(date +%s) - RETENCION_HORAS * 3600 ))
borradas=0
conservadas=0

for id in $huerfanas; do
  creada_iso=$(docker inspect -f '{{.Created}}' "$id" 2>/dev/null)
  creada=$(date -d "$creada_iso" +%s 2>/dev/null)
  if [ -z "$creada" ]; then
    # Sin fecha legible no se arriesga: la proxima vuelta se vuelve a mirar.
    conservadas=$((conservadas + 1))
    continue
  fi
  if [ "$creada" -ge "$limite" ]; then
    conservadas=$((conservadas + 1))   # todavia sirve para volver atras
  elif docker rmi "$id" >/dev/null 2>&1; then
    borradas=$((borradas + 1))
  else
    # Puede ser padre de otra imagen: no es un problema, se borra cuando deje de serlo.
    conservadas=$((conservadas + 1))
  fi
done

echo "Imagenes huerfanas: ${borradas} borradas, ${conservadas} conservadas (menos de ${RETENCION_HORAS}h o en uso)"
