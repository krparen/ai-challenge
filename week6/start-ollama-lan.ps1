# День 30: запуск Ollama как приватного LAN-сервиса
# Перезапускает ollama serve на всех интерфейсах (0.0.0.0:11434),
# печатает адреса для доступа с других устройств того же Wi-Fi.
# Запуск: .\start-ollama-lan.ps1 [-IpAddress 192.168.0.125]

param([string]$IpAddress = '')

$ErrorActionPreference = 'Stop'

if (-not $IpAddress) {
	$IpAddress = (Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'Беспроводная сеть*' -ErrorAction SilentlyContinue |
		Where-Object { $_.IPAddress -like '192.168.*' } | Select-Object -First 1).IPAddress
}
if (-not $IpAddress) { throw 'Wi-Fi адаптер с 192.168.* адресом не найден — укажите -IpAddress вручную' }

Get-Process ollama -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

$env:OLLAMA_HOST = '0.0.0.0'
Start-Process "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" -ArgumentList 'serve' -WindowStyle Hidden
Start-Sleep -Seconds 4

$bind = (Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1).LocalAddress
Write-Host ("Ollama слушает: {0}:11434" -f $bind)
Write-Host ("Проверка из сети: " + (Invoke-RestMethod -Uri "http://${IpAddress}:11434/api/version" -TimeoutSec 15).version)
Write-Host ''
Write-Host "Адреса для других устройств того же Wi-Fi:"
Write-Host ("  Ollama API:  http://{0}:11434" -f $IpAddress)
Write-Host ("  Чат week6:   http://{0}:8080/chat  (если приложение week6 запущено)" -f $IpAddress)

$rules = Get-NetFirewallRule -DisplayName 'Ollama API 11434', 'Week6 Chat 8080' -ErrorAction SilentlyContinue
if (($rules | Measure-Object).Count -lt 2) {
	Write-Host ''
	Write-Host 'ВНИМАНИЕ: не хватает firewall-правил. Выполните в админ-консоли:'
	Write-Host '  New-NetFirewallRule -DisplayName "Ollama API 11434" -Direction Inbound -Protocol TCP -LocalPort 11434 -Action Allow'
	Write-Host '  New-NetFirewallRule -DisplayName "Week6 Chat 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow'
}
