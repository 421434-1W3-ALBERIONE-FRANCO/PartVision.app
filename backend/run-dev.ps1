# Carga backend/.env en el entorno del proceso y arranca el backend en modo dev.
# Uso:  .\run-dev.ps1
# (Spring Boot NO lee .env por si solo; este script inyecta las variables antes de arrancar.)
$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $idx = $line.IndexOf('=')
            $k = $line.Substring(0, $idx).Trim()
            $v = $line.Substring($idx + 1).Trim()
            [Environment]::SetEnvironmentVariable($k, $v)
        }
    }
    $provider = [Environment]::GetEnvironmentVariable('AI_VISION_PROVIDER')
    Write-Host "Cargado $envFile (AI_VISION_PROVIDER=$provider)"
} else {
    Write-Host "No hay .env; se usan los defaults de application.yml (provider=stub)."
}
mvn spring-boot:run
