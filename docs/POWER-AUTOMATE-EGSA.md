# Flujo de Power Automate "Catalogo EGSA": qué cambiar para que mande la lista a PartVision

Esto complementa `docs/PRECIOS-EGSA.md` (cómo funciona el endpoint). Acá está el cambio **en el flujo
del cliente**, paso a paso.

## Cómo está el flujo hoy

Flujo de nube **"Catalogo EGSA"**:

| # | Paso | Qué hace |
|---|---|---|
| 1 | **Recurrence** | Todos los días a las **7:10** (hora de Buenos Aires) |
| 2 | **Ejecutar un flujo creado con Power Automate para escritorio** | Modo *attended* (la PC tiene que estar prendida y con la sesión abierta). Abre EGSA CAT y exporta `EGSA_ListaPrecios_dd-mm-aaaa.xlsx`. Según el script del cliente, **además abre Chrome y carga la lista a mano en PartVision** (inicia sesión con el usuario BOT, importa, elige columnas, aplica) |
| 3 | **Mostrar los archivos de la carpeta** | OneDrive para la Empresa, carpeta `/Rectificaciones Barria/Catálogo EGSA` |
| 4 | **Aplicar a cada uno** → **Condición** | Si el archivo se llama `EGSA_ListaPrecios_<ayer>.xlsx`, lo **elimina** (deja solo el de hoy) |
| 5 | **Enviar correo electrónico (V2)** | A exequiel.barria@mi.unc.edu.ar: "Carga precios EGSA – Se han cargado los precios correctamente." |

O sea: **la lista termina en OneDrive** (por eso el flujo la puede listar y borrar la de ayer), y **PartVision la
recibe porque el robot de escritorio la carga por la pantalla**. Eso es lo que se reemplaza.

## A dónde queremos llegar

Que **el propio flujo de nube** le mande el archivo a PartVision con una acción **HTTP**, igual que ya hace con la
planilla de compras. El robot de escritorio queda **solo exportando** la lista; ya no abre Chrome ni toca nuestra
pantalla.

```
Recurrence 7:10
  └─ 1. Ejecutar flujo de escritorio ............ SOLO exporta desde EGSA CAT (se le saca la parte de PartVision)
  └─ 2. Retraso 3 minutos ....................... para que OneDrive suba el archivo desde la PC
  └─ 3. OneDrive: Obtener contenido del archivo . el de hoy: EGSA_ListaPrecios_<hoy>.xlsx   ← NUEVO
  └─ 4. HTTP POST a PartVision .................. manda el archivo con la clave                ← NUEVO
  └─ 5. (opcional) Hasta que: consultar resultado  espera a que PartVision termine            ← NUEVO
  └─ 6. Mostrar archivos + Aplicar a cada uno ... lo que ya hay: borra la lista de ayer
  └─ 7. Enviar correo ........................... éxito, o aviso si PartVision no aplicó la lista
```

## Qué hay que hacer

### Paso 0: sacarle la parte de PartVision al flujo de escritorio

Abrir el flujo de escritorio (el que llama la acción 2) en **Power Automate para escritorio** y **borrar todo lo que
tenga que ver con PartVision**: abrir Chrome, ir a `190.106.132.98.nip.io/partvision`, escribir usuario y contraseña,
subir el archivo, elegir columnas y proveedor, "Vista previa", "Aplicar", cerrar sesión. **Dejar solamente** lo
de EGSA CAT (abrir el programa, "Descargar Lista para Excel", guardar el archivo).

Si no se borra, la lista se cargaría **dos veces** (por la pantalla y por el POST). No rompe nada, pero la carga
por pantalla es justamente la que queremos sacar.

> Si el flujo de escritorio no guarda el archivo en la carpeta de OneDrive `Catálogo EGSA`, hay que ajustarlo para que
> lo haga (o cambiar la ruta del paso 3). El paso "Mostrar los archivos de la carpeta" que ya existe indica que
> hoy sí cae ahí.

### Paso 1: variables de arriba (para no tocar todo cuando cambie el servidor)

Justo después de **Recurrence**, dos acciones **Redactar** (*Compose*):

| Nombre | Valor |
|---|---|
| `PartVision_URL` | `https://190.106.132.98.nip.io/pv-api/v1` |
| `Archivo_de_hoy` | `@{concat('EGSA_ListaPrecios_', formatDateTime(convertFromUtc(utcnow(), 'Argentina Standard Time'), 'dd-MM-yyyy'), '.xlsx')}` |

Cuando PartVision se mude a otro servidor, **solo cambia `PartVision_URL`**.

### Paso 2: esperar a que el archivo llegue a OneDrive

Después de la acción del flujo de escritorio: **Retraso** (*Delay*) de **3 minutos**. El robot guarda el archivo en la
PC y el cliente de OneDrive tarda un rato en subirlo. Si alguna vez no alcanza, se sube a 5.

### Paso 3: obtener el archivo de hoy

**OneDrive para la Empresa → Obtener contenido de archivo con la ruta de acceso** (*Get file content using path*):

- Ruta de acceso: `/Rectificaciones Barria/Catálogo EGSA/@{outputs('Archivo_de_hoy')}`
- Inferir tipo de contenido: **Sí**

Si el archivo todavía no está, esta acción **falla**, y eso es lo que queremos: el mail de "falló el flujo" avisa.

### Paso 4: mandar el archivo a PartVision (acción HTTP)

**HTTP**, con estos valores (en `...` → **Configuración** dejar la **transferencia fragmentada desactivada**, igual que en
el de compras):

- **Método:** `POST`
- **URI:** `@{outputs('PartVision_URL')}/precios/recepcion/egsa`
- **Encabezados:**
  - `X-API-Key` = la clave de recepción de EGSA (la da el administrador de PartVision; **no** es la de compras)
  - `Content-Type` = `application/octet-stream`
  - `X-Filename` = `@{outputs('Archivo_de_hoy')}`
- **Cuerpo:** el **contenido del archivo** del paso 3 (en el selector de contenido dinámico: *Contenido de archivo*).
  En la vista de código tiene que quedar `@body('Obtener_contenido_de_archivo_con_la_ruta_de_acceso')`.

Para pegar en **Vista de código** de la acción (ajustar el nombre de la acción anterior si en el flujo se llama
distinto):

```json
{
  "type": "Http",
  "inputs": {
    "uri": "@{outputs('PartVision_URL')}/precios/recepcion/egsa",
    "method": "POST",
    "headers": {
      "X-API-Key": "PEGAR_ACA_LA_CLAVE",
      "Content-Type": "application/octet-stream",
      "X-Filename": "@{outputs('Archivo_de_hoy')}"
    },
    "body": "@body('Obtener_contenido_de_archivo_con_la_ruta_de_acceso')"
  },
  "runAfter": {
    "Obtener_contenido_de_archivo_con_la_ruta_de_acceso": ["Succeeded"]
  }
}
```

PartVision responde **202** con un número de constancia (`id`): significa "la recibí y la estoy procesando".

| Respuesta | Qué hacer |
|---|---|
| 202 | Todo bien |
| 401 | La clave está mal copiada |
| 400 | No llegó un Excel: revisar que el **Cuerpo** sea el contenido del archivo y la fragmentación esté desactivada |
| 409 | PartVision está con otra actualización: reintentar en 1-2 minutos (a las 7:10 no debería pasar) |
| 503 | La recepción no está activada en el servidor |

### Paso 5 (recomendado): esperar el resultado real

Con solo el 202 el flujo sabe que PartVision **recibió** la lista, no que la **aplicó**. Para saberlo:

1. Antes de todo, **Inicializar variable** `resultado` (Cadena) = `EN_CURSO` y `mensaje` (Cadena) = vacío.
2. Después del HTTP, **Hasta que** (*Until*): `resultado` **no es igual a** `EN_CURSO`. Límite: 30 repeticiones.
   Adentro:
   - **Retraso** 10 segundos
   - **HTTP** `GET` `@{outputs('PartVision_URL')}/precios/recepcion/@{body('HTTP')?['id']}` con el encabezado `X-API-Key`
   - **Establecer variable** `resultado` = `@{body('HTTP_2')?['resultado']}`
   - **Establecer variable** `mensaje` = `@{body('HTTP_2')?['mensaje']}`
3. **Condición** sobre `resultado`: es `ACTUALIZADA` **o** `SIN_CAMBIOS`.
   - **Sí** → el correo que ya existe, con el texto: `Se han cargado los precios correctamente. @{variables('mensaje')}`
   - **No** → otro correo, asunto **"ALERTA: PartVision no aplicó la lista de EGSA"**, cuerpo
     `@{variables('mensaje')}` (el motivo viene ahí; el detalle completo está en la pantalla Precios).

Resultados posibles: `ACTUALIZADA`, `SIN_CAMBIOS`, `RETENIDA` (llegó rara: PartVision **no la aplicó** y espera que un
administrador la revise) y `ERROR` (no se pudo leer).

### Paso 6: el aviso de falla

El correo "HA FALLADO EL FLUJO" que ya tiene el flujo debe correr si falla **cualquiera** de las acciones nuevas: en
`...` → **Configurar ejecución posterior**, marcar *Ha fallado* y *Se agotó el tiempo de espera* también para el
HTTP y para "Obtener contenido de archivo". Un 401 o un archivo que no llegó **no se ven** si nadie lo mira.

### Lo que queda igual

**Mostrar los archivos de la carpeta** y el **Aplicar a cada uno** que borra la lista de ayer: se dejan donde están
(después del HTTP o del Hasta que).

## Cómo probarlo

1. Con la clave ya cargada, abrir el flujo y tocar **Probar → Manualmente → Guardar y probar**. Sirve cualquier día:
   mientras el archivo de hoy esté en la carpeta de OneDrive.
2. Mirar en la ejecución: el HTTP tiene que dar **202**; el "Hasta que" tiene que terminar con `ACTUALIZADA` o
   `SIN_CAMBIOS`.
3. En PartVision, pantalla **Precios**: el panel "Lista recibida · EGSA" muestra la última lista, con la fecha, "la mandó
   el robot" y los números. Si sale **RETENIDA**, ahí aparece el motivo y el botón **Aplicar igual**.

La primera lista que llegue va a mandar a revisión unos ~100 precios: son los productos con códigos repetidos que hoy
tienen el precio de **otra marca**; el servidor ya calcula el correcto y espera que alguien lo apruebe.

## Alternativa: que lo mande el robot de escritorio

Si preferís no tocar el flujo de nube, el robot de escritorio puede mandar el archivo con el script
`docs/egsa_partvision.py` o con el bloque de PowerShell de `docs/PRECIOS-EGSA.md`. Funciona igual, pero deja **una
pieza más en la PC del cliente** (y hay que instalar Python), mientras que con HTTP en el flujo de nube queda todo
visible en un solo lugar. Por eso se recomienda lo de arriba.

## Cuando PartVision se mude de servidor

1. Cambiar `PartVision_URL` (Paso 1).
2. La clave de recepción vive en el servidor: se copia al nuevo (o se genera una nueva y se cambia en el encabezado
   `X-API-Key`).
3. Probar con **Probar → Manualmente**.
