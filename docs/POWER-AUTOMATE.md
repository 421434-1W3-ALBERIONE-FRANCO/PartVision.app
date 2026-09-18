# Power Automate → PartVision (recepción de compras)

Cómo entran las facturas de compra del cliente en PartVision.

## El recorrido

1. La factura llega por mail al cliente.
2. Un flujo del cliente la vuelca en la tabla **"Ingreso stock"** de su planilla (Excel en
   SharePoint): una fila por producto, con el número de factura repetido.
3. Otro paso del flujo lee la tabla y la manda **entera** a PartVision.
4. PartVision agrupa las filas por factura, registra las nuevas y pone al día las que ya tenía.

El flujo puede releer la planilla cada vez que quiera: reenviar una factura que ya está no
duplica nada.

## El endpoint de la planilla

```
POST https://190.106.132.98.nip.io/pv-api/v1/compras/recepcion/filas
Content-Type: application/json
X-API-Key: <la clave; ver "La clave" más abajo>
```

El cuerpo es **lo que devuelve la acción "Enumerar las filas de una tabla", sin tocar**.
PartVision entiende los nombres de columna de la planilla y descarta los campos propios de
Power Automate (`@odata.etag`, `ItemInternalId`):

```json
{
  "value": [
    { "Factura": "900004110", "F. Factura": "46258", "Codigo": "808449(05)", "Cantidad": "1",
      "Descripcion": "AROS RECTIFICACION VOLKSWAGEN", "Estatus stock": "EN TRÁNSITO" },
    { "Factura": "900000482", "F. Factura": "46259", "Codigo": "", "Cantidad": "",
      "Descripcion": "CONTROLO:", "Estatus stock": "EN TRÁNSITO" }
  ]
}
```

| Columna | Qué se hace con ella |
|---|---|
| `Factura` | agrupa las filas. Se toma tal cual; es único en PartVision |
| `F. Factura` | acepta `24/08/2026`, `2026-08-24`, `2026-08-24T00:00:00Z` o el número de serie de Excel (`46258`). Power Automate manda este último por defecto |
| `Codigo` | se busca en el catálogo. **Vacío → `IMPORTADOS`** (ver abajo) |
| `Cantidad` | entero ≥ 1. **Una fila sin cantidad se saltea**: son anotaciones como "CONTROLO:" |
| `Descripcion` | informativa |
| `Estatus stock` | `EN TRÁNSITO` o `INGRESADA` (ver "Estados") |
| `Proveedor` | **todavía no existe en la planilla**; ver "El proveedor" |

También acepta los nombres propios (`factura`, `fechaFactura`, `codigo`, `cantidad`,
`descripcion`, `estatus`, `proveedor`) dentro de `filas` en lugar de `value`.

### La respuesta

Siempre `200` si la planilla se pudo leer, aunque alguna factura tenga problemas: las demás
se guardan igual (cada factura va en su propia transacción).

```json
{
  "filasRecibidas": 7, "filasIgnoradas": 1, "facturas": 4,
  "creadas": 3, "actualizadas": 1, "sinCambios": 0, "conflictos": 0, "errores": 0,
  "resultados": [
    { "factura": "900004110", "resultado": "CREADA", "estado": "EN_TRANSITO",
      "lineas": 3, "lineasMatcheadas": 3, "mensaje": "registrada" }
  ],
  "ignoradas": [
    { "posicion": 7, "factura": "900000482", "descripcion": "CONTROLO:",
      "motivo": "sin cantidad (o no es un entero positivo)" }
  ]
}
```

| `resultado` | Qué pasó |
|---|---|
| `CREADA` | no existía: se registró |
| `ACTUALIZADA` | ya estaba y la planilla cambió algo que manda ella: el estado, o el proveedor que antes no tenía |
| `SIN_CAMBIOS` | ya estaba igual |
| `CONFLICTO` | ya estaba **con otras líneas, fecha o proveedor**; no se toca. O la planilla la volvió a EN TRÁNSITO después de cargar su stock |
| `ERROR` | no se pudo armar: filas de la misma factura con fechas o proveedores distintos, o fecha ilegible |

`conflictos` y `errores` son lo que alguien tiene que mirar: conviene que el flujo mande un
mail cuando alguno sea mayor que cero.

## Estados

La planilla decide el estado. PartVision solo decide el último paso, que es lo único que la
planilla no sabe: en qué ubicación queda cada cosa.

| Planilla | PartVision | Qué se puede hacer en el panel |
|---|---|---|
| `EN TRÁNSITO` | **En tránsito** | nada: la mercadería no llegó |
| `INGRESADA` | **Por ubicar** | asignar una ubicación a cada línea e ingresar al stock |
| — | **Ingresada** | nada: el stock ya se cargó |

- Recién cuando la planilla dice INGRESADA se puede cargar el stock. El panel no se adelanta.
- La ubicación la elige una persona: el panel precarga la sugerida cuando el producto ya
  tiene stock en algún lado. Solo ~400 de los 135.000 productos tienen stock cargado, así
  que para la mayoría no hay cómo adivinarla.
- Si las filas de una factura no dicen todas lo mismo, queda **en tránsito** hasta que todas
  digan INGRESADA, y la respuesta lo avisa.
- Si la planilla la vuelve a EN TRÁNSITO (una corrección), la compra la sigue, salvo que su
  stock ya se haya cargado: eso vuelve como `CONFLICTO`.

## Qué pasa con cada línea

- **El código se busca en el catálogo.** Los 16 códigos de la muestra de la planilla existen;
  12 existen **para los dos proveedores**.
- **Código vacío → `IMPORTADOS`.** Son pedidos puntuales de clientes. La línea queda
  registrada en la compra pero no tiene producto, así que no carga stock al ingresarla,
  salvo que se la sume al catálogo (ver "Importados").
- **Una línea sin producto** (código que no está, o repetido entre proveedores sin saber de
  cuál es) se ve en el panel con "—" y la compra muestra `3/5` en ámbar.

### El proveedor

Hay unos **10.300 SKU cargados dos veces**, uno por proveedor. El proveedor de la factura es
lo único que dice a cuál de los dos productos va el stock.

- **La planilla todavía no tiene columna de proveedor.** Hasta que la tenga, las líneas con
  código repetido quedan sin producto: en la muestra, 12 de 16. No conviene encender el flujo
  antes.
- Cuando la columna aparezca, las facturas que ya estaban **se completan solas** en el
  siguiente envío (no chocan), y sus líneas repetidas se resuelven, salvo que el stock ya se
  haya cargado.
- `ADS` se traduce a `Autopartes del Sur`. Otros alias se configuran en el server con
  `PARTVISION_COMPRAS_PROVEEDOR_ALIAS=ADS=Autopartes del Sur;OTRO=Nombre del catálogo`
  (en `~/.partvision-backend.env`). Sin alias, se compara sin mayúsculas ni tildes.
- Un código que existe para un solo proveedor se asigna igual, sea cual sea el de la factura.

## Armar el flujo

1. **Disparador**: una recurrencia (por ejemplo cada 15 minutos) o el mismo flujo que carga
   la planilla, al terminar.
2. **Enumerar las filas de una tabla** (Excel Online Business) sobre "Ingreso stock". El
   formato de fecha puede quedar en el valor por defecto.
3. **HTTP**:
   - Método: `POST`
   - URI: la de arriba
   - Encabezados: `Content-Type: application/json` y `X-API-Key: <clave>`
   - Cuerpo: `body('Enumerar_las_filas_de_una_tabla')`, la salida completa del paso anterior
4. **Aviso**: una condición sobre `conflictos` o `errores` mayor que cero que mande un mail.
   También `Configurar ejecución posterior` para avisar si el HTTP falla (un 401 después de
   rotar la clave es silencioso si nadie lo mira).

Guardá la clave como **variable de entorno de Power Platform**, no escrita en la acción.

### Qué filas mandar

- **No mandes facturas anteriores a la puesta en marcha.** Su stock seguramente ya se cargó a
  mano; si alguien las ingresara de nuevo desde el panel, quedaría contado dos veces.
- Si la planilla nunca se vacía, cada envío crece. El tope es **5.000 filas** por envío: borrar
  las filas de facturas ya ingresadas la mantiene chica.

### Límites

- **5.000 filas** y **2 MB** por envío. El tope existe porque la ruta es pública y el servidor
  lee el cuerpo entero antes de mirar la clave.
- **30 envíos por minuto por IP.** Con la planilla entera en un solo envío, sobra.

## El endpoint de una factura

Sigue disponible para quien arme el JSON de una factura con sus líneas. Usa las mismas reglas
de estado, proveedor y reenvíos que el de la planilla, pero responde con códigos HTTP.

```
POST https://190.106.132.98.nip.io/pv-api/v1/compras/recepcion
```

```json
{
  "factura": "900004110",
  "fechaFactura": "24/08/2026",
  "proveedor": "EGSA",
  "estatus": "EN TRÁNSITO",
  "lineas": [ { "codigo": "808449(05)", "descripcion": "AROS RECTIFICACION", "cantidad": 1 } ]
}
```

| Código | Qué pasó |
|---|---|
| 201 | registrada, actualizada o ya estaba igual |
| 400 | falta un campo obligatorio o `cantidad < 1` |
| 401 | `X-API-Key` ausente o incorrecta |
| 409 | ese número ya existe con otro contenido |
| 413 | el cuerpo supera 2 MB |
| 422 | fecha ilegible |
| 429 | demasiadas solicitudes desde la misma IP |
| 503 | el server no tiene `COMPRAS_API_KEY` configurada: el endpoint está apagado |

## La clave

Vive **solo** en `~/.partvision-backend.env` del server (chmod 600); `redeploy.sh` la aplica al
recrear el contenedor. Para leerla al configurar el flujo:

```bash
ssh partvision "grep COMPRAS_API_KEY ~/.partvision-backend.env"
```

Para rotarla (reemplaza solo esa línea; el archivo puede tener otras variables):

```bash
ssh partvision
umask 077 && grep -v '^COMPRAS_API_KEY=' ~/.partvision-backend.env > ~/.pv-env.tmp; printf 'COMPRAS_API_KEY=%s\n' "$(head -c 32 /dev/urandom | base64 | tr -d '=+/' | cut -c1-40)" >> ~/.pv-env.tmp && mv ~/.pv-env.tmp ~/.partvision-backend.env
cd ~/Documents/Repos/PartVision.app && ./redeploy.sh --no-build backend
```

Después actualizá la variable en Power Platform. Entre los dos pasos el flujo recibe 401.

## Importados

En la pantalla **Compras**, el botón **Importados** muestra cuántas líneas `IMPORTADOS`
están sin producto y las lista, las facturas más nuevas primero. Para cada una hay dos
opciones:

- **Crear producto nuevo.** Propone un SKU `IMP-00001`, `IMP-00002`… (el siguiente libre).
  Se puede cambiar, pero tiene que ser **único en todo el catálogo**, de cualquier marca y
  proveedor: si ya existe, rebota y sugiere el siguiente libre. El producto queda con la
  descripción de la línea (editable) y el proveedor de la compra.
- **Ya está en el catálogo.** Busca un producto y asocia la línea. Es lo que corresponde
  cuando **la misma pieza se vuelve a pedir**: como llega otra vez sin código, entra otra vez
  como `IMPORTADOS`, y crearla de nuevo la duplicaría con otro SKU.

Qué pasa con el stock depende de la compra:

| La compra está… | Al resolver la línea |
|---|---|
| en tránsito o por ubicar | solo se asocia el producto. El stock entra al ingresar la compra, con la ubicación que se elija ahí |
| ingresada | hay que elegir la ubicación, y el stock de esa línea se carga en el momento: había quedado afuera del ingreso por no tener producto |

Una línea ya resuelta desaparece de la lista y no se puede resolver dos veces (cargaría el
stock dos veces). Si después la planilla trae el proveedor de esa factura, el producto que se
asoció a mano se respeta.

## Lo que todavía no existe

- **Reconocer solo un importado que se repite.** La segunda vez que llega la misma pieza sin
  código, hay que asociarla a mano con "Ya está en el catálogo": la planilla no trae nada que
  la identifique de forma confiable.
- **Precios desde Power Automate.** Hoy entran solo por la importación manual de Excel. Si se
  pide, hay que reusar el cálculo de costo y margen de `PrecioImportService`.
