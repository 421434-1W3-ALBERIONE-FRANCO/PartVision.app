# Actualización automática de precios de Autopartes del Sur (ADS)

PartVision baja sola la **lista de precios del portal de ADS**
(`catalogo.autopartesdelsur.com.ar`) y la aplica. Es el mismo Excel del botón **"Lista de
precios"** del portal ("Catalogo Autopartes del Sur - DD-MM-YYYY.xlsx", con Código,
Descripción y Precio de Lista). Corre **todos los días a las 7 y a las 13 h** (hora de Buenos
Aires) y cuando alguien toca **«Actualizar ahora»** en la pantalla Precios.

La cuenta es la misma que la de la importación manual:
**costo = Precio de Lista × (1 + ajuste lista)** y **venta = costo × (1 + margen)**, con los
porcentajes de ADS cargados en Precios (hoy, ajuste 0 y margen 22,5%).

La **importación manual de Excel sigue exactamente igual**: es el respaldo para cuando el portal
no responde.

## Lo que se probó con la cuenta del cliente (01/10/2026)

- El Excel llega **idéntico con o sin sesión**: no trae precios propios de la cuenta. Igual se
  pide con la sesión del cliente, por si ADS algún día lo personaliza.
- Los precios de la cuenta están en la **búsqueda de artículos** del portal, no en el Excel. En el
  producto 150010, por ejemplo:

  | Columna | Valor |
  |---|---|
  | Precio lista | $1.206,66 |
  | Precio costo s/IVA | $690,21 |
  | Precio costo c/IVA | $835,15 |
  | Precio venta | $1.478,16 (lista + 22,5% del perfil) |

  **Decisión del usuario:** la base es el **Precio de Lista** del Excel.
- El archivo "Catálogo al DD-MM-YYYY.xlsx" que se importó a mano el 30/09 traía 2.096 productos
  por debajo de la lista (juntas −15%, bulones −10%, juntas de tapa de válvulas −20%, tapas de
  cilindro −2%). Con la lista como base, esos productos suben a precio de lista.

## Cómo se activa

Hace falta el usuario (CUIT) y la contraseña del portal de ADS **del cliente**. Van solo en el
entorno del servidor, en `~/.partvision-backend.env` (chmod 600), igual que la clave de compras.
Nunca van en el repo ni en un chat.

Lo más simple es correr en el servidor `bash ~/pv-cargar-ads.sh`, escrito a mano. El script pide
el CUIT y la contraseña, limpia lo que mete el pegado desde Windows, controla que el CUIT tenga 11
números y reinicia el backend. A mano es así:

En el servidor (`ssh partvision`):

```bash
umask 077; grep -v -E '^ADS_(USUARIO|PASSWORD)=' ~/.partvision-backend.env > ~/.pv-env.tmp
read -rp 'Usuario (CUIT) del portal de ADS: ' U; read -rp 'Contraseña del portal de ADS: ' P
printf 'ADS_USUARIO=%s\nADS_PASSWORD=%s\n' "$U" "$P" >> ~/.pv-env.tmp && mv ~/.pv-env.tmp ~/.partvision-backend.env; unset U P; clear
cd ~/Documents/Repos/PartVision.app && ./redeploy.sh --no-build backend
```

El prompt es visible a propósito: pegar en un `read -s` desde una terminal de Windows puede meter
caracteres de más sin que se vea. Por eso el `clear` al final.

Después, en Precios, tocar **«Actualizar ahora»** para la primera corrida y mirar el resultado.

Sin esas dos variables la función queda **apagada**: el panel dice "Sin activar", el botón no
se puede tocar y no se llama nunca al portal.

Otras variables (opcionales):

| Variable | Default | Para qué |
|---|---|---|
| `ADS_CRON` | `0 0 7,13 * * *` | Cuándo corre sola (hora de Buenos Aires). `-` la apaga y deja solo el botón. Para reactivarla hay que **escribir el horario**, no alcanza con borrar la línea: `redeploy.sh` conserva las `ADS_` del contenedor anterior. |
| `PARTVISION_PRECIOS_ADS_URL` | `https://catalogo.autopartesdelsur.com.ar/` | Solo para pruebas. |

## Qué hace cada corrida

1. Inicia sesión en el portal (`POST auth/login/`). El token se reusa entre corridas y se pide
   otro solo si el portal lo rechaza. **Nunca llama a `auth/logoff/`**, porque el portal guarda
   ahí el carrito del cliente.
2. Baja la lista (`GET api/catalogo/generarXLS/`, el botón "Lista de precios" del portal).
3. Compara cada precio con el catálogo y aplica **solo lo que cambió**. Si nada cambió, no
   escribe nada.
4. Deja la **constancia** de la corrida: fecha, resultado, cuántos se actualizaron y los
   problemas encontrados. Las últimas 15 se ven en el panel.

Usa la misma fórmula que la importación manual (`ajuste lista` y `margen` del proveedor) y el
mismo historial, así que cada actualización **se puede revertir** desde "Historial de
Actualizaciones" (aparece con la etiqueta API).

## Lo que no hace, a propósito

- **No da de alta productos.** Los códigos nuevos de ADS se listan y se dan de alta con la
  importación manual, como siempre.
- **Solo toca productos cargados como "Autopartes del Sur".** La importación manual acepta un
  candidato único aunque sea de otro proveedor. La automática no, porque nadie la está mirando.
- **No corre a la vez que una importación manual.** Comparten el mismo candado.

## Filas malas: se saltean y se informan

| Qué viene mal | Qué hace |
|---|---|
| Precio en cero, vacío, ilegible o absurdo | Deja el precio como estaba y lo informa. Hoy la lista de ADS trae 50 así. |
| Código repetido con dos precios distintos | No toca ese código |
| Código que no está en el catálogo | Lo lista con ejemplos. Hoy son 30. |
| Precio que **sube más de 60% o baja más de 35%** de una vez | No lo aplica: queda **para revisar** |

Los que quedan para revisar aparecen en el panel con el costo de hoy, el de ADS y el cambio.
Se eligen y se **aplican** (con los márgenes de ese momento, con historial para revertir) o se
**descartan** (quedan como estaban). Una corrida nueva reemplaza lo que había quedado pendiente.

## Frenos: la lista entera no se aplica

Si la lista llega con señales de estar mal armada, **no se aplica nada**, queda **Frenada** y
aparece el botón **«Aplicar igual»** para cuando alguien la revisó:

- Trae menos de 1.000 productos con precio, o menos del 80% de los que traía la última buena.
- Más del 5% de las filas tienen el precio en cero o ilegible (cambio de formato).
- Menos del 50% de los códigos existen en el catálogo (columnas corridas).
- Más del 10% de los precios saltan más de lo normal a la vez (y son 20 o más).

Con «Aplicar igual» se vuelve a bajar la lista y se aplica, pero los saltos individuales
**igual** van a revisión.

## Errores y avisos

Si el portal no responde, rechaza el usuario, devuelve una página en vez del Excel, o el Excel
cambió de columnas, la corrida queda como **Error** con el motivo en palabras simples.

Los administradores ven un **aviso arriba de todas las pantallas** (rojo si es un error o los
precios tienen más de 3 días sin actualizarse; amarillo si hay algo para revisar), que lleva a
Precios. No hay aviso por mail porque el servidor no tiene el correo configurado.

Si el servidor se reinicia a mitad de una corrida, al volver la marca como cortada.

## Probado

- Tests unitarios del analizador, del cliente del portal (contra un portal falso local) y del
  servicio.
- De punta a punta con la app real, PostgreSQL 16, los **68.437 productos y precios reales** de
  producción y un portal falso que servía la lista real del 30/09 en distintos escenarios:
  - Lista real: 67.202 sin cambios en 7 s.
  - Suba del 8%: 67.199 precios aplicados en 61 s con 768 MB de heap, el mismo tope del servidor.
  - Errores mezclados: 56 inválidos, 3 repetidos y 30 saltos a revisión. Aprobar, descartar y
    revertir funcionaron.
  - Lista cortada y columnas corridas: frenadas. Página de mantenimiento: error.

## Lo que no se sabe del portal

- Si iniciar sesión desde el servidor cierra la sesión que el cliente tiene abierta en su
  navegador. Muchos sistemas así reusan el token y no pasa nada. Si el cliente nota que el
  portal lo saca todos los días a las 7 o a las 13, avisar: se puede bajar a una corrida por día
  o reusar el token por más tiempo.
- Cuánto dura el token. Da igual: si lo rechaza, se pide otro.
