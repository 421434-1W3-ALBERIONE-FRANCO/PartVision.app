<#
bajar-backup.ps1 - Baja a esta PC el backup nocturno mas nuevo de PartVision (cifrado).

Lo corre la tarea programada "PartVision - bajar backup" cada 2 horas y al iniciar sesion
(docs/BACKUPS.md). Si el backup mas nuevo ya estaba, no baja nada, asi que intentar seguido
no cuesta: lo que importa es que llegue aunque la PC haya estado apagada o suspendida a la
hora fija. Conserva los ultimos -Conservar y anota cada vuelta en bajadas.log, en la misma
carpeta.

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
$opcionesSsh = @('-o', 'BatchMode=yes', '-o', 'ConnectTimeout=20', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4')

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$log = Join-Path $Destino 'bajadas.log'
function Anotar([string]$texto) {
  Add-Content -Path $log -Encoding UTF8 -Value ('{0}  {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $texto)
}

# Corre ssh/scp con la entrada cerrada y un tope de tiempo. Dentro de la tarea programada la
# consola es oculta y ssh.exe se quedaba esperando en ella para siempre (2026-10-15: colgado
# en el primer `ls`, mientras el mismo comando tardaba 1,5 s desde una terminal).
function Ejecutar([string]$programa, [string[]]$argumentos, [int]$segundos) {
  $entrada = [IO.Path]::GetTempFileName()
  $salida = [IO.Path]::GetTempFileName()
  $errores = [IO.Path]::GetTempFileName()
  try {
    $p = Start-Process -FilePath $programa -ArgumentList $argumentos -NoNewWindow -PassThru `
      -RedirectStandardInput $entrada -RedirectStandardOutput $salida -RedirectStandardError $errores
    # Sin tomar el handle ya, PowerShell 5.1 deja ExitCode vacio cuando el proceso termina.
    $null = $p.Handle
    if (-not $p.WaitForExit($segundos * 1000)) {
      $p.Kill()
      throw ('{0} no termino en {1} s' -f (Split-Path $programa -Leaf), $segundos)
    }
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) {
      $detalle = ([string](Get-Content $errores -Raw)) -replace '\s+', ' '
      throw ('{0} salio con {1}: {2}' -f (Split-Path $programa -Leaf), $p.ExitCode, $detalle.Trim())
    }
    # scp -q no imprime nada: en PowerShell 5.1 un archivo vacio da $null, no ''.
    $texto = Get-Content $salida -Raw
    if ($null -eq $texto) { return '' }
    return $texto.Trim()
  } finally {
    Remove-Item $entrada, $salida, $errores -ErrorAction SilentlyContinue
  }
}

try {
  $remoto = Ejecutar $ssh ($opcionesSsh + @($Servidor, "`"ls -1 $CarpetaRemota/partvision_db-*.dump.gpg 2>/dev/null | tail -n 1`"")) 90
  if (-not $remoto) { throw 'no se encontro ningun backup en el servidor' }
  $nombre = Split-Path $remoto -Leaf
  $local = Join-Path $Destino $nombre
  if (Test-Path $local) {
    Anotar "ya estaba: $nombre"
    exit 0
  }

  $tamanoRemoto = [long](Ejecutar $ssh ($opcionesSsh + @($Servidor, "`"stat -c %s '$remoto'`"")) 90)
  $parcial = "$local.parcial"
  Ejecutar $scp (@('-q') + $opcionesSsh + @("${Servidor}:$remoto", "`"$parcial`"")) 900 | Out-Null
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
  Anotar ('ERROR: {0} (linea {1})' -f $_.Exception.Message, $_.InvocationInfo.ScriptLineNumber)
  Get-ChildItem -Path $Destino -Filter '*.parcial' -ErrorAction SilentlyContinue | Remove-Item -ErrorAction SilentlyContinue
  exit 1
}
