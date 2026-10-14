<#
bajar-backup.ps1 - Baja a esta PC el backup nocturno mas nuevo de PartVision (cifrado).

Lo corre todos los dias la tarea programada "PartVision - bajar backup" (docs/BACKUPS.md).
Si la PC esta apagada a la hora de la tarea, Windows la corre apenas se prende. Si el
backup mas nuevo ya estaba, no baja nada. Conserva los ultimos -Conservar y anota cada
vuelta en bajadas.log, en la misma carpeta.

Los backups llegan cifrados: para abrirlos hace falta la llave privada "PartVision backups",
que esta solo en esta PC (y en el segundo lugar donde la guardes).

Este archivo esta escrito sin acentos a proposito: PowerShell 5.1 lee los scripts sin BOM
con la codificacion de Windows y los acentos se rompen.
#>
param(
  [string]$Destino = (Join-Path $env:USERPROFILE 'Desktop\trabajo\PartVision\respaldos\nocturnos'),
  [int]$Conservar = 7,
  [string]$Servidor = 'partvision',
  [string]$CarpetaRemota = 'pv-backups/nocturnos'
)
$ErrorActionPreference = 'Stop'
$ssh = Join-Path $env:WINDIR 'System32\OpenSSH\ssh.exe'
$scp = Join-Path $env:WINDIR 'System32\OpenSSH\scp.exe'

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$log = Join-Path $Destino 'bajadas.log'
function Anotar([string]$texto) {
  Add-Content -Path $log -Encoding UTF8 -Value ('{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $texto)
}

try {
  $remoto = & $ssh -o BatchMode=yes -o ConnectTimeout=20 $Servidor "ls -1 $CarpetaRemota/partvision_db-*.dump.gpg 2>/dev/null | tail -n 1"
  if ($LASTEXITCODE -ne 0 -or -not $remoto) { throw 'no se encontro ningun backup en el servidor' }
  $remoto = ([string]($remoto | Select-Object -Last 1)).Trim()
  $nombre = Split-Path $remoto -Leaf
  $local = Join-Path $Destino $nombre
  if (Test-Path $local) {
    Anotar "ya estaba: $nombre"
    exit 0
  }

  $tamanoRemoto = [long](& $ssh -o BatchMode=yes -o ConnectTimeout=20 $Servidor "stat -c %s '$remoto'")
  $parcial = "$local.parcial"
  & $scp -q -o BatchMode=yes -o ConnectTimeout=20 "${Servidor}:$remoto" $parcial
  if ($LASTEXITCODE -ne 0) { throw "fallo la copia (scp salio con $LASTEXITCODE)" }
  $tamanoLocal = (Get-Item $parcial).Length
  if ($tamanoLocal -ne $tamanoRemoto) {
    Remove-Item $parcial
    throw "llego incompleto ($tamanoLocal de $tamanoRemoto bytes)"
  }
  Move-Item -Force $parcial $local
  Anotar ('bajado: {0} ({1:N1} MB)' -f $nombre, ($tamanoLocal / 1MB))

  # Los nombres llevan la fecha: el orden alfabetico es el cronologico.
  Get-ChildItem -Path $Destino -Filter 'partvision_db-*.dump.gpg' | Sort-Object Name -Descending |
    Select-Object -Skip $Conservar | ForEach-Object {
      Remove-Item $_.FullName
      Anotar "borrado por viejo: $($_.Name)"
    }
} catch {
  Anotar "ERROR: $($_.Exception.Message)"
  exit 1
}
