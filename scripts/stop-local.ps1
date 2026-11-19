$ports = @(8090, 8091, 8092)
foreach ($port in $ports) {
    $connection = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($connection) {
        Stop-Process -Id $connection.OwningProcess -Force
    }
}
Write-Host "NovaCart services stopped"
