r"""
Actualización de precios EGSA -> PartVision

Paso 1 - EGSA CAT (app de escritorio, vía pywinauto):
    abre EGSA CAT, exporta la lista de precios a Excel en
    Escritorio\Catalogo Egsa\EGSA_ListaPrecios_dd-mm-aaaa.xlsx
    y borra las listas de días anteriores.                (IGUAL QUE ANTES)

Paso 2 - PartVision:
    manda el Excel DIRECTO al servidor (una sola llamada HTTP) y espera el resultado.
    Ya NO abre Chrome, ni inicia sesión, ni toca la pantalla de Precios: lo demás
    (columnas, proveedor, altas de códigos nuevos, frenos si la lista viene rara) lo
    hace el servidor.

Requisitos (Windows):
    pip install pywinauto            (el paso 2 usa solo la librería estándar de Python)

Variables de entorno:
    PARTVISION_EGSA_KEY   obligatoria: la clave de recepción que da el administrador de PartVision.
    PARTVISION_API        opcional: solo para pruebas (por defecto, el servidor de producción).

Para probar solo el paso 2 con un archivo que ya tenés (sin abrir EGSA CAT):
    python egsa_partvision.py "C:\ruta\EGSA_ListaPrecios_05-10-2026.xlsx"

Códigos de salida: 0 = los precios quedaron al día; 1 = algo falló o la lista quedó retenida
(en ese caso el flujo tiene que avisar: el mensaje de cada caso queda en el log).
"""

import ctypes
import json
import logging
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

try:
    from pywinauto import Application
    from pywinauto.findwindows import ElementNotFoundError
    from pywinauto.timings import TimeoutError as PwaTimeoutError
except ImportError:  # solo para poder probar el paso 2 fuera del equipo que tiene EGSA CAT
    Application = ElementNotFoundError = PwaTimeoutError = None

# ----------------------------------------------------------------------------
# Configuración
# ----------------------------------------------------------------------------
EGSA_EXE = r"C:\Users\Public\EgsaCat\EgsaCat.exe"
EGSA_DIR = r"C:\Users\Public\EgsaCat"
EGSA_TITULO = r"^EGSA CAT "               # ventana principal, sirve para cualquier versión
CARPETA_DESTINO = "Catalogo Egsa"          # subcarpeta dentro del Escritorio
PREFIJO_ARCHIVO = "EGSA_ListaPrecios_"
TIMEOUT_EXPORTACION = 120                  # segundos hasta que aparece el cartel "Información"

PV_API = os.environ.get("PARTVISION_API", "https://190.106.132.98.nip.io/pv-api/v1")
PV_KEY = os.environ.get("PARTVISION_EGSA_KEY")

REINTENTOS_OCUPADO = 6                     # el servidor puede estar actualizando otra lista (ADS a las 6:30)
ESPERA_OCUPADO = 60                        # segundos entre reintentos
ESPERA_RESULTADO = 15 * 60                 # segundos máximos esperando que termine de procesar
SONDEO = 5                                 # segundos entre consultas del resultado

AQUI = Path(__file__).resolve().parent
log = logging.getLogger("egsa_partvision")


# ----------------------------------------------------------------------------
# Paso 1: exportar la lista desde EGSA CAT
# ----------------------------------------------------------------------------
def carpeta_escritorio() -> Path:
    """Ruta real del Escritorio (respeta redirecciones, p. ej. OneDrive)."""
    buf = ctypes.create_unicode_buffer(260)
    ctypes.windll.shell32.SHGetFolderPathW(None, 0x10, None, 0, buf)  # CSIDL_DESKTOPDIRECTORY
    return Path(buf.value)


def conectar_egsa():
    """Se conecta a EGSA CAT si ya está abierto; si no, lo inicia.

    EgsaCat.exe es un lanzador (el proceso real se llama EgsaCat_1.183), por eso
    se busca la ventana por título en lugar de usar el proceso que se inició.
    """
    try:
        return Application(backend="uia").connect(title_re=EGSA_TITULO, timeout=2)
    except (ElementNotFoundError, PwaTimeoutError):
        log.info("Iniciando EGSA CAT...")
        subprocess.Popen([EGSA_EXE], cwd=EGSA_DIR)
        return Application(backend="uia").connect(title_re=EGSA_TITULO, timeout=120)


def esperar_archivo(ruta: Path, timeout: int = 60) -> None:
    limite = time.monotonic() + timeout
    while time.monotonic() < limite:
        if ruta.exists() and ruta.stat().st_size > 0:
            return
        time.sleep(1)
    raise FileNotFoundError(f"EGSA CAT no generó {ruta}")


def borrar_listas_anteriores(carpeta: Path, nombre_actual: str) -> None:
    for f in carpeta.glob(PREFIJO_ARCHIVO + "*"):
        if f.stem != nombre_actual:
            f.unlink()
            log.info("Borrada lista anterior: %s", f.name)


def exportar_lista_egsa() -> Path:
    if Application is None:
        raise RuntimeError("Falta instalar pywinauto (pip install pywinauto) en este equipo.")
    nombre = PREFIJO_ARCHIVO + datetime.now().strftime("%d-%m-%Y")
    carpeta = carpeta_escritorio() / CARPETA_DESTINO
    carpeta.mkdir(exist_ok=True)
    archivo = carpeta / f"{nombre}.xlsx"
    if archivo.exists():  # evita el cartel "¿Desea reemplazarlo?" si se corre dos veces el mismo día
        archivo.unlink()

    app = conectar_egsa()
    principal = app.window(title_re=EGSA_TITULO)
    principal.wait("visible ready", timeout=60)
    principal.set_focus()

    # Abrir "Configuración". En la grabación original es un clic por coordenadas:
    # 133 px a la izquierda y 64 px arriba de la esquina inferior derecha del panel.
    panel = (principal.child_window(class_name="TPanel", depth=1, found_index=0)
                      .child_window(class_name="TPanel", depth=1, found_index=0))
    r = panel.rectangle()
    panel.click_input(coords=(r.width() - 133, r.height() - 64))

    config = app.window(title="Configuración")
    config.wait("visible ready", timeout=30)
    config.child_window(title="Lista de Precios", control_type="TabItem").click_input()
    config.child_window(title="Descargar Lista para Excel", control_type="Button").click_input()

    # Diálogo "Guardar como": se escribe la ruta completa en vez de navegar carpetas
    guardar = app.window(title="Guardar como")
    guardar.wait("visible ready", timeout=30)
    guardar.child_window(auto_id="1148", control_type="Edit").set_edit_text(str(carpeta / nombre))
    guardar.child_window(title="Guardar", control_type="Button").click_input()

    info = app.window(title="Información")
    info.wait("visible ready", timeout=TIMEOUT_EXPORTACION)
    info.child_window(class_name="TButton").click_input()  # OK
    config.child_window(title="Cancelar", control_type="Button").click_input()

    esperar_archivo(archivo)
    borrar_listas_anteriores(carpeta, nombre)
    return archivo


# ----------------------------------------------------------------------------
# Paso 2: mandar la lista a PartVision
# ----------------------------------------------------------------------------
def _pedir(metodo: str, url: str, cuerpo: bytes | None = None, nombre: str | None = None):
    """Un pedido HTTP a PartVision. Devuelve (codigo, json); no lanza por 4xx/5xx."""
    headers = {"X-API-Key": PV_KEY}
    if cuerpo is not None:
        headers["Content-Type"] = "application/octet-stream"
    if nombre:
        headers["X-Filename"] = nombre
    req = urllib.request.Request(url, data=cuerpo, method=metodo, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            texto = resp.read().decode("utf-8", "replace")
            return resp.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as e:
        texto = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(texto)
        except ValueError:
            return e.code, {"message": texto[:300]}


def enviar_a_partvision(archivo: Path) -> dict:
    """Manda el Excel, espera a que PartVision termine de procesarlo y devuelve el resultado."""
    datos = archivo.read_bytes()
    log.info("Mandando %s (%.1f MB) a PartVision...", archivo.name, len(datos) / 1048576)

    constancia = None
    for intento in range(1, REINTENTOS_OCUPADO + 1):
        codigo, cuerpo = _pedir("POST", f"{PV_API}/precios/recepcion/egsa", datos, archivo.name)
        if codigo == 202:
            constancia = cuerpo
            break
        if codigo == 409:  # el servidor está con otra actualización: se espera y se reintenta
            log.warning("PartVision está ocupado (intento %d de %d): reintento en %d s",
                        intento, REINTENTOS_OCUPADO, ESPERA_OCUPADO)
            time.sleep(ESPERA_OCUPADO)
            continue
        mensaje = (cuerpo or {}).get("message", "")
        if codigo == 401:
            raise RuntimeError("PartVision rechazó la clave (PARTVISION_EGSA_KEY). Pedile la clave vigente al administrador.")
        if codigo == 503:
            raise RuntimeError("La recepción de listas todavía no está activada en el servidor de PartVision.")
        raise RuntimeError(f"PartVision respondió HTTP {codigo}: {mensaje}")
    if constancia is None:
        raise RuntimeError("PartVision estuvo ocupado todo el tiempo: no se pudo mandar la lista. Se reintenta mañana.")

    id_ = constancia["id"]
    log.info("Lista recibida por PartVision (constancia %s). Esperando el resultado...", id_)
    limite = time.monotonic() + ESPERA_RESULTADO
    while time.monotonic() < limite:
        time.sleep(SONDEO)
        codigo, r = _pedir("GET", f"{PV_API}/precios/recepcion/{id_}")
        if codigo != 200:
            raise RuntimeError(f"No se pudo consultar el resultado (HTTP {codigo}): {(r or {}).get('message', '')}")
        if r["resultado"] != "EN_CURSO":
            return r
    raise RuntimeError(f"PartVision no terminó de procesar la lista en {ESPERA_RESULTADO // 60} minutos (constancia {id_}).")


def informar(r: dict) -> None:
    """Deja en el log lo que paso. Falla (y por eso el flujo avisa) si los precios NO quedaron al dia."""
    log.info("Resultado: %s - %s", r["resultado"], r.get("mensaje"))
    log.info("   actualizados=%s  sin cambio=%s  para revisar=%s  códigos nuevos sin alta=%s  precios en cero=%s",
             r["actualizados"], r["sinCambio"], r["enRevision"], r["noEncontrados"], r["filasInvalidas"])
    for problema in r.get("problemas", []):
        log.info("   - %s", problema)
    if r["resultado"] == "RETENIDA":
        raise RuntimeError("PartVision NO aplicó la lista porque llegó con datos raros. Un administrador tiene que "
                           "revisarla en la pantalla Precios (hay un aviso arriba).")
    if r["resultado"] == "ERROR":
        raise RuntimeError(f"PartVision no pudo procesar la lista: {r.get('mensaje')}")


# ----------------------------------------------------------------------------
def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s  %(levelname)s  %(message)s",
        handlers=[
            logging.StreamHandler(),
            logging.FileHandler(AQUI / "egsa_partvision.log", encoding="utf-8"),
        ],
    )
    if not PV_KEY:
        sys.exit("Falta la variable de entorno PARTVISION_EGSA_KEY con la clave de recepción de PartVision.")

    try:
        if len(sys.argv) > 1:  # prueba: solo el paso 2, con un archivo que ya existe
            archivo = Path(sys.argv[1])
            if not archivo.is_file():
                sys.exit(f"No existe el archivo {archivo}")
        else:
            archivo = exportar_lista_egsa()
            log.info("Lista exportada: %s", archivo)
        informar(enviar_a_partvision(archivo))
    except Exception as e:  # noqa: BLE001 - se informa y se sale con error para que el flujo avise
        log.error("%s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()
