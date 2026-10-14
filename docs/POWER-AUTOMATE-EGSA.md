# Flujo "oyente" de Power Automate: manda la lista de EGSA a PartVision

Esto complementa `docs/PRECIOS-EGSA.md` (cómo funciona el endpoint). Acá está el flujo de nube **nuevo**, paso a paso.

## La decisión

**No se toca nada del flujo actual del cliente.** Se crea un flujo de nube **nuevo e independiente** que *escucha* la
carpeta de OneDrive donde el cliente ya deja el Excel todos los días, y cuando aparece la lista de hoy se la manda a
PartVision con una acción HTTP, igual que la planilla de compras.

- **Cero fricción:** no se toca la PC del cliente ni sus flujos (nube ni escritorio).
- **Fácil de mantener:** toda la comunicación con PartVision queda en un flujo chico y visible.
- **Escalable:** si el cliente cambia de máquina o de proceso de extracción, mientras el Excel siga llegando a esa
  carpeta, la integración sigue funcionando.

## Cómo está el flujo del cliente (queda intacto)

| # | Paso | Qué hace |
|---|---|---|
| 1 | Recurrence | Todos los días a las **7:10** (hora de Buenos Aires) |
| 2 | Ejecutar un flujo de escritorio | *Attended*. Abre EGSA CAT y exporta `EGSA_ListaPrecios_dd-mm-aaaa.xlsx`; según su script, **además la carga en PartVision por la pantalla** |
| 3 | Mostrar los archivos de la carpeta | OneDrive para la Empresa, `/Rectificaciones Barria/Catálogo EGSA` |
| 4 | Aplicar a cada uno + Condición | Si el archivo es `EGSA_ListaPrecios_<ayer>.xlsx`, lo elimina |
| 5 | Enviar correo | "Se han cargado los precios correctamente" |

La lista **termina en OneDrive** en esa carpeta, con el nombre `EGSA_ListaPrecios_dd-MM-aaaa.xlsx`. Eso es lo que escucha
el flujo nuevo.

## El flujo nuevo

```
Cuando se crea un archivo (OneDrive, /Rectificaciones Barria/Catálogo EGSA)   ← ya trae el contenido
  └─ Inicializar variable: PartVision_URL, EstadoCarga (= EN_CURSO), MensajeRespuesta
  └─ Condición: ¿el archivo se llama EGSA_ListaPrecios_<HOY>.xlsx?
       Verdadero:
         └─ HTTP POST a PartVision (cuerpo = el contenido del archivo)    ← con reintentos
         └─ Hasta que EstadoCarga ≠ EN_CURSO:  Retraso 10 s → HTTP GET → guardar resultado
         └─ Condición: ACTUALIZADA o SIN_CAMBIOS
               sí → correo "se cargaron los precios"
               no → correo "ALERTA: PartVision no aplicó la lista"
       Falso: nada (la ejecución queda en el historial y se ve qué archivo llegó)
  (si algo de lo anterior falla → correo "HA FALLADO EL FLUJO")
```

En el diseñador nuevo la **Vista de código** de cada acción es de solo lectura: los JSON de abajo sirven para
**comparar** con lo que quedó armado, no para pegarlos. Las expresiones (lo que tiene paréntesis) se cargan con el
botón **fx** (*Insertar expresión*) del campo; escritas como texto quedan como texto y no se calculan.

### Paso 1: el desencadenador

**OneDrive para la Empresa → Cuando se crea un archivo** (*When a file is created*, `OnNewFileV2`):

- **Carpeta:** `/Rectificaciones Barria/Catálogo EGSA`
- **Este, no "Cuando se crea un archivo (solo propiedades)"**: este ya trae el **contenido** del archivo, así que no
  hace falta pedirlo aparte. Tampoco "se crea o modifica": con "modifica" dispara de nuevo cada vez que OneDrive toca el
  archivo al sincronizarlo, y la lista se mandaría varias veces. El robot genera un archivo nuevo por día, así que
  "se crea" dispara **una vez por lista**.
- **Ojo con el nombre: viene en base64.** El dato dinámico *Nombre de archivo* es
  `triggerOutputs()?['headers/x-ms-file-name-encoded']`, y para `EGSA_ListaPrecios_14-10-2026.xlsx` trae
  `RUdTQV9MaXN0YVByZWNpb3NfMTQtMTAtMjAyNi54bHN4`. Para compararlo o mandarlo hay que decodificarlo:

  ```
  base64ToString(triggerOutputs()?['headers/x-ms-file-name-encoded'])
  ```

  Sin `base64ToString`, una condición como "empieza por `EGSA_ListaPrecios_`" da **siempre falso** y el flujo no manda
  nada nunca, sin ningún error.
- En `...` → **Configuración** del desencadenador: **Control de simultaneidad: Activado, grado de paralelismo 1**, para que
  dos disparos no corran a la vez.
- Limitaciones del desencadenador: ignora archivos de **más de 50 MB** (la lista pesa unos pocos) y archivos **movidos**
  dentro de OneDrive (un archivo subido o creado sí dispara).

### Paso 2: variables

Tres **Inicializar variable**, una debajo de la otra, antes de la condición (Power Automate no deja inicializar variables
adentro de una condición):

| Nombre | Tipo | Valor |
|---|---|---|
| `PartVision_URL` | Cadena | `https://190.106.132.98.nip.io/pv-api/v1` |
| `EstadoCarga` | Cadena | `EN_CURSO` |
| `MensajeRespuesta` | Cadena | (vacío) |

La URL va **tal cual, escrita a mano**: si se pega desde un chat o un documento puede quedar como enlace
(`[https://...](https://...)`) y todas las llamadas fallan. Cuando PartVision se mude de servidor, **solo cambia esta
variable**. En todas las acciones se usa como `variables('PartVision_URL')`.

### Paso 3: ¿es la lista de hoy?

**Condición** con una sola fila:

- Izquierda (fx): `base64ToString(triggerOutputs()?['headers/x-ms-file-name-encoded'])`
- Operador: **es igual a**
- Derecha (fx): `concat('EGSA_ListaPrecios_', convertFromUtc(utcNow(), 'Argentina Standard Time', 'dd-MM-yyyy'), '.xlsx')`

Pide que el archivo sea **el de hoy**, no solo que empiece con `EGSA_ListaPrecios_`. Es importante: PartVision no sabe
de qué día es una lista, así que si por cualquier motivo se disparara con un archivo **viejo** (alguien lo restaura o
lo vuelve a subir), se aplicarían **precios de hace días**.

Como **acción** (y no como "condición de desencadenador") tiene una ventaja: si el nombre no coincide, la ejecución igual
queda en el historial y se ve qué archivo llegó y por qué no se mandó. Todo lo que sigue va en la rama **Verdadero**; la
rama **Falso** queda vacía.

### Paso 4: mandar el archivo (acción HTTP)

En `...` → **Configuración**: **fragmentación (transferencia fragmentada) desactivada**, igual que en compras, y
**directiva de reintentos**: *Intervalo fijo*, recuento **6**, intervalo **PT1M**.

La directiva de reintentos importa por esto: **mientras el robot viejo siga cargando la lista por la pantalla**, a esa hora
PartVision puede estar ocupado con esa carga. En ese caso responde **429** ("ocupado"), y Power Automate **reintenta solo**
(reintenta 408, 429 y 5xx; un 409, por ejemplo, no lo reintentaría).

- **Método:** `POST`
- **URI:** `@{variables('PartVision_URL')}/precios/recepcion/egsa`
- **Encabezados:**
  - `X-API-Key` = la clave de recepción de EGSA (la da el administrador de PartVision; **no** es la de compras)
  - `Content-Type` = `application/octet-stream`
  - `X-Filename` = (fx) `base64ToString(triggerOutputs()?['headers/x-ms-file-name-encoded'])`
- **Cuerpo:** el dato dinámico **Contenido del archivo** del desencadenador.

La Vista de código tiene que quedar así:

```json
{
  "type": "Http",
  "inputs": {
    "uri": "@{variables('PartVision_URL')}/precios/recepcion/egsa",
    "method": "POST",
    "headers": {
      "X-API-Key": "LA_CLAVE_DE_EGSA",
      "Content-Type": "application/octet-stream",
      "X-Filename": "@{base64ToString(triggerOutputs()?['headers/x-ms-file-name-encoded'])}"
    },
    "body": "@triggerBody()",
    "retryPolicy": { "type": "fixed", "count": 6, "interval": "PT1M" }
  }
}
```

PartVision responde **202** con un número de constancia (`id`): "la recibí y la estoy procesando".

| Respuesta | Qué significa |
|---|---|
| 202 | Todo bien |
| 401 | La clave está mal copiada |
| 400 | No llegó un Excel: revisar que el **Cuerpo** sea el contenido del archivo y la fragmentación esté desactivada |
| 429 | PartVision está con otra actualización: Power Automate reintenta solo |
| 503 | La recepción no está activada en el servidor |

### Paso 5: esperar el resultado real

Con solo el 202 se sabe que PartVision **recibió** la lista, no que la **aplicó**. Para saberlo:

**Hasta que** (*Until*): `EstadoCarga` **no es igual a** `EN_CURSO`. Límite: **30** repeticiones. Adentro:

1. **Retraso** de **10 segundos**
2. **HTTP** `GET` `@{variables('PartVision_URL')}/precios/recepcion/@{body('HTTP')?['id']}` con el encabezado `X-API-Key`
3. **Establecer variable** `EstadoCarga` = (fx) `body('HTTP_2')?['resultado']`
4. **Establecer variable** `MensajeRespuesta` = (fx) `body('HTTP_2')?['mensaje']`

(Los nombres `HTTP` y `HTTP_2` son los que Power Automate le pone a las acciones; ajustar si se llaman distinto.)

Resultados posibles: `ACTUALIZADA`, `SIN_CAMBIOS`, `RETENIDA` (llegó rara: PartVision **no la aplicó** y espera que un
administrador la revise) y `ERROR` (no se pudo leer el archivo).

### Paso 6: el correo

**Condición** sobre `EstadoCarga`: es `ACTUALIZADA` **o** `SIN_CAMBIOS`.

- **Sí** → correo "Carga precios EGSA": `Se cargaron los precios en PartVision. @{variables('MensajeRespuesta')}`
- **No** → correo con asunto **"ALERTA: PartVision no aplicó la lista de EGSA"** y cuerpo
  `@{variables('MensajeRespuesta')}` (el motivo viene ahí; el detalle completo está en la pantalla Precios).

Un correo de **"HA FALLADO EL FLUJO"** con *Configurar ejecución posterior* → *Ha fallado* y *Se agotó el tiempo de espera*
para el HTTP. Un 401 o una lista que no llegó **no se ven** si nadie lo mira.

## Cosas a tener en cuenta

- **El flujo nuevo tiene que crearse con la cuenta dueña de esa carpeta de OneDrive** (la misma con la que está el flujo
  actual). Los desencadenadores de OneDrive solo ven el OneDrive de la cuenta con la que se conectan; una carpeta de otra
  persona, aunque esté compartida, no la ven.
- **La acción HTTP es una acción premium**: esa cuenta necesita la licencia (el flujo de compras ya la usa).
- **Mientras el flujo de escritorio siga cargando por la pantalla**, PartVision recibe la lista **dos veces** (la que
  carga el robot y la que manda este flujo). No pasa nada: la segunda da `SIN_CAMBIOS`, y si coinciden en el tiempo, el
  429 y el reintento lo resuelven. Pero la carga por pantalla es la parte más pesada para el servidor; cuando el
  cliente quiera, **se le saca esa parte al flujo de escritorio** (borrar lo que abre Chrome y va a PartVision) y queda solo
  este flujo.
- Si el robot **reemplaza** el archivo del mismo día (lo borra y lo vuelve a crear), el desencadenador dispara de nuevo: se
  manda otra vez y da `SIN_CAMBIOS`.
- Si PartVision no recibe ninguna lista en **2 días**, aparece un aviso arriba de todas las pantallas para los
  administradores, aunque este flujo no esté funcionando.

## Cómo probarlo

**Nunca probar con una lista vieja renombrada con la fecha de hoy**: PartVision la aplicaría y volverían precios de
hace días. Hay dos formas seguras:

1. **Esperar la lista de mañana**: el robot la deja a las 7:10 y el flujo se dispara solo unos minutos después.
2. **Con la lista de HOY** que ya está en la carpeta: bajarla, borrarla de la carpeta y volver a subirla (moverla dentro de
   OneDrive no dispara el flujo; subirla sí).

En el historial de ejecuciones:

- La **Condición** tiene que dar **Verdadero**. Si da Falso, abrir el desencadenador → *Mostrar salidas sin procesar* y
  mirar `x-ms-file-name-encoded`: decodificado tiene que ser exactamente `EGSA_ListaPrecios_dd-MM-aaaa.xlsx` con la fecha
  de hoy.
- El **HTTP** tiene que dar **202** y el "Hasta que" terminar en `ACTUALIZADA` o `SIN_CAMBIOS`.
- En PartVision, pantalla **Precios**: el panel "Lista recibida · EGSA" muestra la fecha, "la mandó el robot" y los
  números. Si sale **RETENIDA**, ahí aparece el motivo y el botón **Aplicar igual**.

La primera lista va a mandar a revisión unos ~100 precios: son los productos con códigos repetidos que hoy tienen el
precio de **otra marca**; el servidor ya calcula el correcto y espera que alguien lo apruebe.

## Cuando PartVision se mude de servidor

1. Cambiar la variable `PartVision_URL` (Paso 2).
2. La clave de recepción vive en el servidor: se copia al nuevo (o se genera una nueva y se cambia en el encabezado
   `X-API-Key`).
3. Probar copiando un archivo de hoy a la carpeta.

## Alternativas (no recomendadas)

- **Modificar el flujo actual del cliente** para que haga el POST después del flujo de escritorio: obliga a tocar un flujo que
  hoy funciona.
- **Que lo mande el robot de escritorio** con `docs/egsa_partvision.py` o el bloque de PowerShell de
  `docs/PRECIOS-EGSA.md`: deja una pieza más en la PC del cliente y hay que instalar Python.
