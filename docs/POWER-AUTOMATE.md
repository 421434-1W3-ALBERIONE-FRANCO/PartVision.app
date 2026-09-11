# Power Automate → PartVision (recepción de compras)

Cómo conectar un flujo de Power Automate del cliente para que las facturas de compra
entren solas en PartVision.

## El endpoint

```
POST https://190.106.132.98.nip.io/pv-api/v1/compras/recepcion
Content-Type: application/json
X-API-Key: <la clave; está en ~/.partvision-backend.env del server>
```

```json
{
  "factura": "A-0001-00012345",
  "fechaFactura": "2026-09-11",
  "proveedor": "EGSA",
  "estatus": "PENDIENTE",
  "lineas": [
    { "codigo": "0082-20-00", "descripcion": "PISTONES FIAT 600", "cantidad": 4 }
  ]
}
```

Responde `201` con la compra creada (incluye su `id`).

### Reglas de los campos

| Campo | Obligatorio | Notas |
|---|---|---|
| `factura` | sí | máx. 50 caracteres |
| `fechaFactura` | sí | `YYYY-MM-DD` |
| `proveedor` | no | texto libre |
| `estatus` | sí | si **contiene** "INGRESADA" → `INGRESADA`; **cualquier otra cosa** → `EN_TRANSITO` |
| `lineas` | sí, al menos una | |
| `lineas[].codigo` | no | máx. 100; es lo que se intenta matchear contra el catálogo |
| `lineas[].descripcion` | no | máx. 500 |
| `lineas[].cantidad` | sí | entero ≥ 1 |

Ojo con `estatus`: no valida, cae a `EN_TRANSITO` por defecto. Un typo no da error,
entra con el estado equivocado.

Las líneas cuyo `codigo` no matchea un producto entran igual, con `productoId: null`.
No es un error: quedan para resolver a mano desde la pantalla de compras.

### Respuestas

| Código | Qué pasó |
|---|---|
| 201 | creada |
| 400 | falta un campo obligatorio o `cantidad < 1` |
| 401 | `X-API-Key` ausente o incorrecta |
| 409 | ese número de factura ya existe **con otro contenido** (ver abajo) |
| 413 | el cuerpo supera 2 MB |
| 429 | demasiadas solicitudes desde la misma IP (30 por minuto) |
| 503 | el server no tiene `COMPRAS_API_KEY` configurada — el endpoint está apagado |

### Reenvíos y facturas repetidas

Reenviar **la misma factura con el mismo contenido** devuelve `201` con la compra que ya
estaba: el reintento de Power Automate ante un timeout no duplica nada.

Reenviar el mismo número **con contenido distinto** devuelve `409`. Antes devolvía `201` y
descartaba los datos nuevos en silencio: si el cliente corregía una factura y el flujo la
reenviaba, la corrección no entraba nunca y nadie se enteraba. Si el reenvío es a propósito,
hay que corregir la compra desde el panel.

### Límites

- **2 MB** de cuerpo y **5.000 líneas** por factura. Una factura real no se acerca; el tope
  está porque la ruta es pública y el servidor parsea el JSON entero antes de mirar la clave.
- **30 requests por minuto por IP**. Un flujo normal manda una factura cada varios segundos.

## Armar el flujo

1. **Trigger**: el que use el cliente (mail con adjunto, carpeta de SharePoint, botón).
2. **Parsear la factura** hasta tener el array de líneas.
3. **Acción HTTP**:
   - Method: `POST`
   - URI: la de arriba
   - Headers: `Content-Type: application/json` y `X-API-Key: <clave>`
   - Body: el JSON

Guardá la clave como **variable de entorno de Power Platform**, no escrita en la
acción. Así rotarla no obliga a editar el flujo.

4. **Manejo de error**: configurá `Configure run after` en fallo para que avise.
   Un 401 después de una rotación es silencioso si nadie lo mira.

## La clave

Vive en `~/.partvision-backend.env` del server (chmod 600) y `redeploy.sh` la aplica
como overlay al recrear el contenedor, así que sobrevive los deploys.

Para rotarla:

```bash
ssh partvision
umask 077 && printf 'COMPRAS_API_KEY=%s\n' "$(head -c 24 /dev/urandom | base64 | tr -d '=+/' | cut -c1-32)" > ~/.partvision-backend.env
cd ~/Documents/Repos/PartVision.app && ./redeploy.sh --no-build backend
```

Después actualizá la variable de entorno en Power Platform. Entre los dos pasos el
flujo recibe 401.

## Lo que NO existe

**Precios.** No hay endpoint para empujar precios desde Power Automate; hoy entran
solo por la importación manual de Excel. Si el cliente lo pide, hay que diseñarlo
reusando el cálculo de costo/margen de `PrecioImportService` en vez de rehacerlo.
