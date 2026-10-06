# Recepción de la lista de precios de EGSA

EGSA no tiene portal ni API: es un programa de escritorio (EGSA CAT) con usuario y contraseña, en la PC
del cliente. Por eso **el que lleva la lista a PartVision es el robot de esa PC**. Hasta ahora lo hacía
manejando nuestra pantalla de Precios como una persona (abría Chrome, iniciaba sesión, subía el Excel,
elegía columnas y apretaba «Aplicar»). Ahora puede **mandar el archivo directo al servidor**, con una
sola llamada, igual que Power Automate manda la planilla de compras.

```
PC del cliente                                         PartVision (servidor)
──────────────                                         ─────────────────────
7:00  Power Automate dispara el flujo de escritorio
      1) EGSA CAT exporta EGSA_ListaPrecios_dd-mm-aaaa.xlsx        (igual que antes)
      2) POST /api/v1/precios/recepcion/egsa  ───────►  la acepta (202) y la procesa en segundo plano
         GET  /api/v1/precios/recepcion/{id}  ◄───────  el robot consulta cómo terminó
      3) si salió mal, el flujo avisa (como hoy por mail)
```

La **importación manual de Excel sigue igual**, y el robot viejo (el que maneja la pantalla) sigue
andando hasta que el cliente cambie su flujo: las dos vías conviven.

## Por qué conviene el cambio

| Robot por pantalla (hoy) | Mandando el archivo |
|---|---|
| Se rompe si cambia un botón o un texto de la pantalla, sin avisar | Una llamada HTTP: no depende de cómo se ve nada |
| Se pisa con ADS: si el servidor está actualizando, el día de EGSA se pierde | Si está ocupado responde 429 (Power Automate lo reintenta solo) y el script también |
| Aplica lo que venga, aunque la lista llegue cortada o corrida | Frenos: una lista rara **no se aplica** y avisa |
| Reescribe 67 mil productos todos los días | Escribe solo lo que cambió |
| Un código repetido de EGSA le deja el precio de **otra marca** | Toma el precio de la marca de cada producto |
| Hay que dar de alta los códigos nuevos a mano (el robot los daba de alta clickeando) | Los da de alta solo, con tope |

## Cómo se activa

Hace falta una **clave de recepción** (`EGSA_API_KEY`). Va solo en el entorno del servidor, en
`~/.partvision-backend.env` (chmod 600), igual que la de compras. Nunca va en el repo ni en un chat.
Sin ella la recepción queda **apagada** (el endpoint responde 503) y el panel de Precios lo dice.

En el servidor (`ssh partvision`), para crearla sin verla:

```bash
umask 077; grep -v '^EGSA_API_KEY=' ~/.partvision-backend.env > ~/.pv-env.tmp
printf 'EGSA_API_KEY=%s\n' "$(head -c 32 /dev/urandom | base64 | tr -d '=+/' | cut -c1-40)" >> ~/.pv-env.tmp && mv ~/.pv-env.tmp ~/.partvision-backend.env
cd ~/Documents/Repos/PartVision.app && ./redeploy.sh --no-build backend
```

Para dársela al cliente (por un canal privado, **no por un chat compartido**):

```bash
ssh partvision "grep EGSA_API_KEY ~/.partvision-backend.env"
```

Para cambiarla, repetir el primer bloque y pasarle la nueva al cliente.

## Lo que tiene que cambiar el cliente

Solo el **paso 2** de su flujo. El paso 1 (exportar desde EGSA CAT) queda exactamente igual.

### Opción A: el script de Python (`docs/egsa_partvision.py`)

Es su mismo script con el paso 2 reemplazado. Ya no usa Playwright ni Chrome (usa solo la librería
estándar). Necesita una variable de entorno nueva, en lugar de `PARTVISION_USER` y `PARTVISION_PASS`:

```
PARTVISION_EGSA_KEY = <la clave de recepción>
```

Para probarlo sin abrir EGSA CAT, con una lista que ya tenga:

```
python egsa_partvision.py "C:\ruta\EGSA_ListaPrecios_05-10-2026.xlsx"
```

Sale con código **0** si los precios quedaron al día y con **1** (y el motivo en el log) si algo falló
o la lista quedó retenida, para que el flujo pueda avisar.

### Opción B: PowerShell (si el flujo de escritorio no usa Python)

```powershell
$api     = 'https://190.106.132.98.nip.io/pv-api/v1'
$clave   = $env:PARTVISION_EGSA_KEY
$archivo = "$([Environment]::GetFolderPath('Desktop'))\Catalogo Egsa\EGSA_ListaPrecios_$(Get-Date -Format 'dd-MM-yyyy').xlsx"

$r = Invoke-RestMethod -Uri "$api/precios/recepcion/egsa" -Method Post -InFile $archivo `
       -ContentType 'application/octet-stream' -TimeoutSec 300 `
       -Headers @{ 'X-API-Key' = $clave; 'X-Filename' = (Split-Path $archivo -Leaf) }
do {
  Start-Sleep -Seconds 5
  $c = Invoke-RestMethod -Uri "$api/precios/recepcion/$($r.id)" -Headers @{ 'X-API-Key' = $clave }
} while ($c.resultado -eq 'EN_CURSO')

"$($c.resultado): $($c.mensaje)"
if ($c.resultado -in 'RETENIDA', 'ERROR') { exit 1 }
```

### El pedido

- `POST /api/v1/precios/recepcion/egsa` con el Excel **crudo** en el cuerpo
  (`Content-Type: application/octet-stream`) y la clave en `X-API-Key`. `X-Filename` es opcional (solo
  para el log). Pesa hasta 25 MB.
- `GET /api/v1/precios/recepcion/{id}` con la misma clave: devuelve cómo terminó.

| Respuesta | Qué significa |
|---|---|
| 202 | Aceptada; se procesa en segundo plano. El cuerpo trae `id` |
| 400 | No es un Excel (.xlsx) |
| 401 | Clave incorrecta o ausente |
| 429 | El servidor está con otra actualización (ADS, una importación): reintentar en uno o dos minutos. Power Automate lo reintenta solo; el 409 **no**, por eso se usa 429 |
| 413 | Pesa más de 25 MB |
| 503 | La recepción no está activada en el servidor |

Resultado final: `ACTUALIZADA` (se aplicaron precios), `SIN_CAMBIOS`, `RETENIDA` (llegó rara: no se
aplicó nada) o `ERROR` (no se pudo leer).

## Qué hace el servidor con la lista

Usa el **mismo motor que ADS** (`docs/PRECIOS-ADS.md`): costo = `PrecioLista` × (1 + ajuste), venta =
costo × (1 + margen), con los porcentajes de EGSA de Precios (hoy ajuste 0 y margen 20,032%).

- **Solo escribe lo que cambió**, con historial para revertir el lote.
- **Solo toca productos de EGSA.** Antes, un código que también existía en ADS terminaba con precio de
  EGSA en el producto de ADS (eran unos 25).
- **Precio en cero o vacío:** se saltea y se informa. Es normal: **el 4% de la lista de EGSA viene así**
  (importados, Sintermetal, Packson). Por eso el límite de filas ilegibles que frena la lista es 20%
  y no 5%.
- **Códigos repetidos con marcas distintas** (el 106 es un árbol de levas BH y una cadena RUL-REP):
  cada producto toma el precio de la fila que trae **su marca**. Si no hay forma de saberlo (dos filas de
  la misma marca con precios distintos, o el producto no tiene marca), no se toca y se informa.
- **Códigos nuevos:** se dan de alta solos (con su marca y descripción) y quedan con precio en la misma
  recepción. Si llegan **más de 300 de una vez**, no se crea ninguno y se avisa.
- **Saltos grandes** (suben más de 60% o bajan más de 35%) no se aplican: quedan en el panel de
  Precios para aprobarlos o descartarlos uno por uno.
- **Frenos** (la lista entera no se aplica y queda `RETENIDA`): trae menos de 1.000 productos o menos
  del 80% de la última buena; más del 20% de filas ilegibles; menos del 50% de los códigos existe en el
  catálogo (columnas corridas); más del 10% de los precios salta a la vez.
- **«Aplicar igual»:** EGSA no tiene portal para volver a bajar la lista, así que la retenida **se guarda
  en el servidor** (una por proveedor). Un administrador la revisa en Precios y, si está bien, toca
  «Aplicar igual»: se aplica ese mismo archivo. Los saltos grandes igual van a revisión.
- **Avisos:** si no llega ninguna lista en **2 días**, o llega y falla, aparece un aviso arriba de todas
  las pantallas para los administradores (cuenta también la carga por pantalla del robot viejo).

## Probado

Con la app real, PostgreSQL 16, los **136 mil productos y precios reales** de producción y la lista real de
EGSA (67.671 filas, 170 códigos repetidos), con el mismo tope de memoria que el servidor (768 MB, SerialGC):

| Caso | Resultado |
|---|---|
| Lista de hoy | 15 s. 2.885 precios, 14 productos nuevos con precio, 129 repetidos resueltos por marca, 111 saltos a revisión (los que tenían el precio de otra marca) |
| La misma lista otra vez | `SIN_CAMBIOS` en 7 s |
| Suba del 8% en 64 mil precios (peor caso) | 45 s, sin problemas de memoria |
| Lista cortada (3.000 filas) | `RETENIDA`; «Aplicar igual» la aplicó con el archivo guardado |
| Columnas corridas | `RETENIDA`; aun forzada, no crea productos (son más de 300) |
| Dos envíos casi juntos | el segundo recibe 429 |
| Sin clave, clave mala, no es un Excel | 401, 401, 400; sin clave configurada en el servidor, 503 |
| Script de Python y PowerShell | los dos contra el servidor de pruebas; salida 0 en lo bueno y 1 con motivo en lo malo |

## Lo que todavía no resuelve

- **Productos que faltan por códigos repetidos.** PartVision guarda un solo producto por proveedor y
  código, así que de cada código repetido de EGSA solo existe uno (por ejemplo el árbol de levas BH del
  106 existe, pero la cadena RUL-REP del mismo código no). Para tenerlos todos, EGSA tendría que
  identificarse por **código + marca**, lo que cambia una regla de la base y el cruce de otras pantallas.
- **Memoria del servidor.** Cargar 67 mil productos para compararlos llega a ~650-690 MB de los 768 MB
  de heap del backend, tanto en la importación por pantalla como en esta recepción y en ADS. Hay poco
  margen si coincide con otra cosa. La recomendación es subir `BACKEND_MEMORY` de 1 GB a 1,5 GB
  (el servidor tiene 3,9 GB y la comparte con NexVia, por eso es decisión del dueño).
