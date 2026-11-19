$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root "logs"
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$mallJar = Join-Path $root "novacart-mall\target\novacart-mall-1.0.0.jar"
$deliveryJar = Join-Path $root "novacart-delivery\target\novacart-delivery-1.0.0.jar"
$adminJar = Join-Path $root "novacart-admin\target\novacart-admin-1.0.0.jar"

if (Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "NovaCart is already running: http://127.0.0.1:8090"
    exit 0
}

Push-Location $root
mvn -q -DskipTests package
Pop-Location

if (-not $env:OPENAI_API_KEY) {
    $lumenEnv = "E:\JianliProject\LumenPilot\.env.development"
    if (Test-Path $lumenEnv) {
        $text = [IO.File]::ReadAllText($lumenEnv, [Text.Encoding]::UTF8)
        $match = [regex]::Match($text, "(?m)^OPENAI_API_KEY=(.*)$")
        $env:OPENAI_API_KEY = $match.Groups[1].Value.Trim().Trim('"')
    }
}

if (-not $env:NOVACART_CHAT_MODEL) { $env:NOVACART_CHAT_MODEL = "qwen4-max" }
if (-not $env:NOVACART_EMBEDDING_MODEL) { $env:NOVACART_EMBEDDING_MODEL = "tongyi-embedding-vision-flash" }
if (-not $env:NOVACART_EMBEDDING_BASE_URL) { $env:NOVACART_EMBEDDING_BASE_URL = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding" }
if (-not $env:NOVACART_VECTOR_DIMENSIONS) { $env:NOVACART_VECTOR_DIMENSIONS = "768" }

$docker = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
$dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
& $docker info 2>$null | Out-Null
$dockerReady = $LASTEXITCODE -eq 0
if (-not $dockerReady -and (Test-Path $dockerDesktop)) {
    Start-Process $dockerDesktop
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 3
        & $docker info 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $dockerReady = $true
            break
        }
    }
}
$ErrorActionPreference = $previousErrorAction
if (-not $dockerReady) {
    throw "Docker Desktop did not become ready."
}
& $docker start lumenpilot-pg *> $null

Start-Process java -ArgumentList @("-jar", $deliveryJar) `
    -WorkingDirectory $root `
    -RedirectStandardOutput (Join-Path $logDir "delivery.log") `
    -RedirectStandardError (Join-Path $logDir "delivery.err.log") `
    -WindowStyle Hidden

for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    if (Get-NetTCPConnection -LocalPort 8091 -State Listen -ErrorAction SilentlyContinue) {
        break
    }
}

Start-Process java -ArgumentList @("-jar", $mallJar) `
    -WorkingDirectory $root `
    -RedirectStandardOutput (Join-Path $logDir "mall.log") `
    -RedirectStandardError (Join-Path $logDir "mall.err.log") `
    -WindowStyle Hidden

Start-Process java -ArgumentList @("-jar", $adminJar) `
    -WorkingDirectory $root `
    -RedirectStandardOutput (Join-Path $logDir "admin.log") `
    -RedirectStandardError (Join-Path $logDir "admin.err.log") `
    -WindowStyle Hidden

for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    if (Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue) {
        break
    }
}

Write-Host "NovaCart started: http://127.0.0.1:8090"
Write-Host "Developer console: http://127.0.0.1:8092"
Start-Process "http://127.0.0.1:8090"
