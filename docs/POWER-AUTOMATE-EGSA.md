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
Cuando se crea un archivo (OneDrive, /Rectificaciones Barria/Catálogo EGSA)
   solo si se llama EGSA_ListaPrecios_<HOY>.xlsx         ← condición de desencadenador
  └─ Redactar: PartVision_URL
  └─ Inicializar variables: resultado, mensaje
  └─ Retraso 1 minuto                                    ← que el archivo termine de subirse
  └─ OneDrive: Obtener contenido del archivo  (por Id)
  └─ HTTP POST a PartVision                              ← con reintentos
  └─ Hasta que resultado ≠ EN_CURSO:  Retraso 10 s → HTTP GET → guardar resultado
  └─ Condición: ACTUALIZADA o SIN_CAMBIOS
        sí → correo "se cargaron los precios"
        no → correo "ALERTA: PartVision no aplicó la lista"
  (si algo de lo anterior falla → correo "HA FALLADO EL FLUJO")
```

### Paso 1: el desencadenador

**OneDrive para la Empresa → Cuando se crea un archivo** (*When a file is created*):

- **Carpeta:** `/Rectificaciones Barria/Catálogo EGSA`
- Preferir **"se crea un archivo"** a "se crea o modifica un archivo (solo propiedades)". El robot genera un archivo nuevo con
  la fecha de cada día, así que "se crea" dispara **una vez por lista**. Con "o modifica" dispara de nuevo cada vez que
  OneDrive toca el archivo al sincronizarlo, y la lista se mandaría varias veces.
- En `...` → **Configuración** del desencadenador:
  - **Control de simultaneidad: Activado, grado de paralelismo 1**, para que dos disparos no corran a la vez.
  - **Condiciones de desencadenador** (esto reemplaza al "verificar que el nombre empiece con EGSA_ListaPrecios_" y es
    mejor, porque ni siquiera arranca una ejecución para archivos ajenos). Pegar:

    ```
    @equals(triggerOutputs()?['body/Name'], concat('EGSA_ListaPrecios_', formatDateTime(convertFromUtc(utcnow(), 'Argentina Standard Time'), 'dd-MM-yyyy'), '.xlsx'))
    ```

    Pide que el archivo sea **el de hoy**, no solo que empiece con `EGSA_ListaPrecios_`. Es importante: PartVision no sabe
    de qué día es una lista, así que si por cualquier motivo se disparara con un archivo **viejo** (alguien lo restaura o
    lo vuelve a guardar), se aplicarían **precios de hace días**.

### Paso 2: variables

- **Redactar** `PartVision_URL` = `https://190.106.132.98.nip.io/pv-api/v1` (cuando PartVision se mude de servidor,
  **solo cambia esto**).
- **Inicializar variable** `resultado` (Cadena) = `EN_CURSO` y `mensaje` (Cadena) = vacío.

### Paso 3: esperar y obtener el archivo

1. **Retraso** de **1 minuto**: el disparo puede llegar cuando OneDrive todavía está terminando de subir el archivo.
2. **OneDrive para la Empresa → Obtener contenido de archivo** (por **Id**, no por ruta):
   - Archivo: `@{triggerOutputs()?['body/Id']}`
   - Inferir tipo de contenido: **Sí**

El desencadenador solo trae las *propiedades* del archivo (nombre, Id); **el contenido hay que pedirlo** con este paso.

### Paso 4: mandar el archivo (acción HTTP)

En `...` → **Configuración**: **transferencia fragmentada desactivada** (igual que en compras) y **directiva de reintentos**:
*Intervalo fijo*, recuento **6**, intervalo **PT1M**.

La directiva de reintentos importa por esto: **mientras el robot viejo siga cargando la lista por la pantalla**, a esa hora
PartVision puede estar ocupado con esa carga. En ese caso responde **429** ("ocupado"), y Power Automate **reintenta solo**
(reintenta 408, 429 y 5xx; un 409, por ejemplo, no lo reintentaría).

- **Método:** `POST`
- **URI:** `@{outputs('PartVision_URL')}/precios/recepcion/egsa`
- **Encabezados:**
  - `X-API-Key` = la clave de recepción de EGSA (la da el administrador de PartVision; **no** es la de compras)
  - `Content-Type` = `application/octet-stream`
  - `X-Filename` = `@{triggerOutputs()?['body/Name']}`
- **Cuerpo:** el contenido del archivo del paso anterior (*Contenido del archivo*).

Para pegar en la **Vista de código** de la acción (ajustar el nombre de la acción anterior si se llama distinto):

```json
{
  "type": "Http",
  "inputs": {
    "uri": "@{outputs('PartVision_URL')}/precios/recepcion/egsa",
    "method": "POST",
    "headers": {
      "X-API-Key": "PEGAR_ACA_LA_CLAVE",
      "Content-Type": "application/octet-stream",
      "X-Filename": "@{triggerOutputs()?['body/Name']}"
    },
    "body": "@body('Obtener_contenido_de_archivo')",
    "retryPolicy": { "type": "fixed", "count": 6, "interval": "PT1M" }
  },
  "runAfter": { "Obtener_contenido_de_archivo": ["Succeeded"] }
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

**Hasta que** (*Until*): `resultado` **no es igual a** `EN_CURSO`. Límite: **30** repeticiones. Adentro:

1. **Retraso** de **10 segundos**
2. **HTTP** `GET` `@{outputs('PartVision_URL')}/precios/recepcion/@{body('HTTP')?['id']}` con el encabezado `X-API-Key`
3. **Establecer variable** `resultado` = `@{body('HTTP_2')?['resultado']}`
4. **Establecer variable** `mensaje` = `@{body('HTTP_2')?['mensaje']}`

(Los nombres `HTTP` y `HTTP_2` son los que Power Automate le pone a las acciones; ajustar si se llaman distinto.)

Resultados posibles: `ACTUALIZADA`, `SIN_CAMBIOS`, `RETENIDA` (llegó rara: PartVision **no la aplicó** y espera que un
administrador la revise) y `ERROR` (no se pudo leer el archivo).

### Paso 6: el correo

**Condición** sobre `resultado`: es `ACTUALIZADA` **o** `SIN_CAMBIOS`.

- **Sí** → correo "Carga precios EGSA": `Se cargaron los precios en PartVision. @{variables('mensaje')}`
- **No** → correo con asunto **"ALERTA: PartVision no aplicó la lista de EGSA"** y cuerpo `@{variables('mensaje')}` (el motivo
  viene ahí; el detalle completo está en la pantalla Precios).

Un correo de **"HA FALLADO EL FLUJO"** con *Configurar ejecución posterior* → *Ha fallado* y *Se agotó el tiempo de espera*
para el HTTP y para "Obtener contenido de archivo". Un 401 o una lista que no llegó **no se ven** si nadie lo mira.

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

1. Con la clave ya cargada, guardar el flujo y esperar a la próxima lista, **o** probarlo ya: copiar a mano un archivo
   `EGSA_ListaPrecios_<hoy>.xlsx` a la carpeta (el nombre con la fecha de hoy, o la condición lo ignora).
2. En el historial de ejecuciones: el HTTP tiene que dar **202** y el "Hasta que" terminar en `ACTUALIZADA` o `SIN_CAMBIOS`.
3. En PartVision, pantalla **Precios**: el panel "Lista recibida · EGSA" muestra la fecha, "la mandó el robot" y los números.
   Si sale **RETENIDA**, ahí aparece el motivo y el botón **Aplicar igual**.

La primera lista va a mandar a revisión unos ~100 precios: son los productos con códigos repetidos que hoy tienen el
precio de **otra marca**; el servidor ya calcula el correcto y espera que alguien lo apruebe.

## Cuando PartVision se mude de servidor

1. Cambiar `PartVision_URL` (Paso 2).
2. La clave de recepción vive en el servidor: se copia al nuevo (o se genera una nueva y se cambia en el encabezado
   `X-API-Key`).
3. Probar copiando un archivo de hoy a la carpeta.

## Alternativas (no recomendadas)

- **Modificar el flujo actual del cliente** para que haga el POST después del flujo de escritorio: obliga a tocar un flujo que
  hoy funciona.
- **Que lo mande el robot de escritorio** con `docs/egsa_partvision.py` o el bloque de PowerShell de
  `docs/PRECIOS-EGSA.md`: deja una pieza más en la PC del cliente y hay que instalar Python.
