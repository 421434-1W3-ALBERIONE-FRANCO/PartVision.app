#!/usr/bin/env bash
# limpiar-imagenes.sh — Borra las imagenes de docker que quedaron huerfanas.
#
# Uso:
#   ./limpiar-imagenes.sh          # borra las huerfanas de mas de 72h
#   RETENCION=24h ./limpiar-imagenes.sh
#   LIMPIAR_IMAGENES=0 ./limpiar-imagenes.sh   # no hace nada (para saltearlo en un deploy)
#
# Lo llama redeploy.sh al terminar: cada deploy construye una imagen nueva y deja la anterior
# sin etiqueta, ~900 MB por vez. El 2026-09-21 eso tenia el disco del VPS al 94%, y el disco
# lo comparte el proyecto NexVia: si se llena, Postgres deja de escribir y se caen los dos.
#
# Dos cuidados, a proposito:
#   - NUNCA se usa `prune -a`. Solo se borra lo que no tiene etiqueta NI contenedor: las
#     imagenes base (maven, temurin, nginx, node, postgres) quedan, y sin ellas cada deploy
#     tendria que volver a bajarlas. `-a` ademas borraria imagenes de cualquier otro proyecto
#     que algun dia use docker en este host.
#   - Se conservan las de las ultimas RETENCION horas: la imagen del deploy anterior es la
#     vuelta atras rapida si el nuevo sale mal.
set -uo pipefail

RETENCION="${RETENCION:-72h}"

if [ "${LIMPIAR_IMAGENES:-1}" != "1" ]; then
  echo "Limpieza de imagenes salteada (LIMPIAR_IMAGENES=${LIMPIAR_IMAGENES:-1})"
  exit 0
fi

salida=$(docker image prune -f --filter "until=${RETENCION}" 2>&1)
estado=$?

if [ "$estado" -ne 0 ]; then
  # Que falle la limpieza no puede dar por fallido un deploy que ya funciono.
  echo "AVISO: no se pudieron limpiar las imagenes viejas: $salida" >&2
  exit 0
fi

recuperado=$(printf '%s' "$salida" | grep -i 'reclaimed space' | head -1)
echo "Imagenes huerfanas de mas de ${RETENCION}: ${recuperado:-nada que borrar}"
