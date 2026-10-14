# Backups y mantenimiento nocturno

Hasta el 2026-10-14 no existía **ninguna** copia de la base de PartVision. Desde ese día una rutina la respalda todas
las noches, cifrada, en tres lugares.

## Qué corre y cuándo

`mantenimiento-nocturno.sh`, por cron en el server, **todos los días a las 4:30** (hora de Buenos Aires; en el crontab
dice `30 7` porque el server está en UTC). A esa hora no hay nadie usando el sistema y no choca con nada: la limpieza
del historial de precios corre los domingos a las 4:00, ADS a las 6:30, EGSA llega a eso de las 7:15 y compras a las 8:00.

1. **Backup** de la base (`pg_dump`), cifrado con la llave pública `backups-clave-publica.asc`. El backup en claro nunca
   se escribe en el disco del server, y el server **no puede abrir** lo que guarda. Antes de guardarlo se comprueba que
   se pueda leer y que traiga los productos (un backup de una base vacía sale "bien" y no sirve).
2. **Nube** (Backblaze B2), si está configurada: todas las noches a `diarios/` y los domingos también a `semanales/`.
3. **Revisión**: disco, memoria del backend y reinicios desde la noche anterior.
4. **Limpieza**: imágenes huérfanas de docker (`limpiar-imagenes.sh`), el log del watchdog si pasa de 1 MB y los
   `pv-redeploy-*.log` de más de 30 días.

El resultado de cada noche queda al final de `~/pv-mantenimiento.log` y en `~/.pv-mantenimiento/ultimo.txt`:

- `OK`
- `AVISO: ...` (algo para mirar: disco por encima del 85%, memoria del backend por encima del 90%, reinicios, nube sin
  configurar)
- `ERROR: ...` (algo roto: no hubo backup, no subió a la nube, disco por encima del 92%)

Cada paso sigue aunque el anterior falle. Los casos están probados en `mantenimiento-nocturno.test.sh`, que se corre en
el server y no toca la base, los contenedores ni la nube de verdad.

## Dónde quedan las copias

| Lugar | Cuántas | Cómo llegan |
|---|---|---|
| Server, `~/pv-backups/nocturnos/` | las últimas **3** noches | la rutina |
| Backblaze B2 | **7** diarias y **4** semanales | la rutina, si hay clave cargada |
| PC del administrador, `PartVision\respaldos\nocturnos\` | las últimas **7** | la tarea programada de Windows |

Un backup pesa unos **15 MB** (octubre 2026) y tarda unos segundos.

La copia del server sirve para deshacer un error rápido; no sobrevive a que se pierda el server. Por eso las otras dos.

## La llave

Los backups se cifran con la llave **"PartVision backups"**:

```
huella: 76FE 76C2 5434 93C3 D4E4  47BD 279F 33E5 2D00 AB12
```

- La **pública** está en el repo (`backups-clave-publica.asc`). Sirve solo para cifrar: no es un secreto.
- La **privada** está **solo en la PC del administrador** (en el llavero de gpg de Git Bash). Sin ella, **ningún backup
  se puede abrir**.

Dos cosas que hay que hacer una vez, desde Git Bash en esa PC:

1. Ponerle contraseña (se creó sin contraseña para no tener que escribirla en un chat):

   ```bash
   gpg --passwd "PartVision backups"
   ```

2. Guardar una copia en un **segundo lugar** (gestor de contraseñas, pendrive guardado). Si se rompe la PC y la llave
   estaba solo ahí, los backups no sirven:

   ```bash
   gpg --export-secret-keys --armor "PartVision backups" > /e/llave-backups-partvision.asc
   ```

   (cambiar `/e/` por la unidad del pendrive). El archivo queda protegido por la contraseña del paso 1.

El certificado para revocarla quedó en `~/.gnupg/openpgp-revocs.d/` de esa PC.

## Restaurar

Desde Git Bash en la PC que tiene la llave privada:

```bash
gpg --decrypt partvision_db-2026-10-14.dump.gpg > partvision_db.dump
```

Y en el servidor donde se quiera restaurar (primero **en una base de prueba**, nunca directo sobre la de producción):

```bash
sudo -u postgres createdb -O partvision partvision_prueba     # crear bases pide un usuario con ese permiso
pg_restore -h localhost -U partvision -d partvision_prueba --no-owner partvision_db.dump
```

Conviene probar una restauración completa **una vez por mes**: un backup que nunca se restauró es una suposición.

## Activar la nube (Backblaze B2)

Lo hace el administrador una sola vez. B2 tiene **10 GB gratis**; con 11 backups de 15 MB se usa menos de 200 MB.

1. Crear la cuenta en backblaze.com (B2 Cloud Storage).
2. **Buckets → Create a Bucket**: nombre único (por ejemplo `partvision-backups-` y algo), **Private**.
3. En el bucket, **Lifecycle Settings → Use custom lifecycle rules**, dos reglas:

   | File name prefix | Days till hide | Days till delete |
   |---|---|---|
   | `diarios/` | 7 | 1 |
   | `semanales/` | 29 | 1 |

4. **Application Keys → Add a New Application Key**:
   - Allow access to bucket: **solo el de los backups**.
   - Type of access: **Write Only**. Así, aunque alguien entre al server, no puede borrar ni leer los backups de la
     nube; los viejos los borra B2 con las reglas del paso 3.
5. B2 muestra el `keyID` y la `applicationKey` **una sola vez**. En el server:

   ```bash
   cd ~/Documents/Repos/PartVision.app && ./cargar-b2.sh
   ```

   Prueba la clave contra B2 antes de guardarla (en `~/.pv-backup-b2.env`, permisos 600). **No pegarla en ningún chat.**
6. Para probar sin esperar a la noche: `./mantenimiento-nocturno.sh; tail -n 4 ~/pv-mantenimiento.log`. Tiene que decir
   `Nube: subido a diarios/...` y `RESULTADO: OK`.

## La copia en la PC

`bajar-backup.ps1` baja el backup más nuevo por SSH (usa el alias `partvision` de `~/.ssh/config`), comprueba que llegue
completo y conserva los últimos 7. Lo corre la tarea programada **"PartVision - bajar backup"**, todos los días a las
12:00; si la PC está apagada a esa hora, Windows la corre apenas se prende. Lo que hace queda en
`respaldos\nocturnos\bajadas.log`.

Para crearla de nuevo (PowerShell, sin permisos de administrador):

```powershell
$accion = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "C:\Users\alber\Desktop\trabajo\PartVision\PartVision.app\bajar-backup.ps1"'
$cuando = New-ScheduledTaskTrigger -Daily -At 12:00
$opciones = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 30)
Register-ScheduledTask -TaskName 'PartVision - bajar backup' -Action $accion -Trigger $cuando -Settings $opciones
```

## En la mudanza

- Los scripts y la llave pública vienen con el repo.
- Instalar el cron: `30 7 * * * <repo>/mantenimiento-nocturno.sh` (o `30 4` si el server nuevo está en hora de Buenos
  Aires).
- Llevar `~/.pv-backup-b2.env` o cargar una clave nueva con `cargar-b2.sh`.
- En la PC, cambiar el alias `partvision` de `~/.ssh/config` para que apunte al server nuevo.
- Ver que la primera noche termine en `RESULTADO: OK`.
