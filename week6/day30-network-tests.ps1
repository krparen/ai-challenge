# День 30: проверка приватного LLM-сервиса по сети
# Запуск: .\day30-network-tests.ps1 [-IpAddress 192.168.0.125]

param([string]$IpAddress = '192.168.0.125')

$base = "http://${IpAddress}:11434"

function Chat([string]$prompt, [object]$options, [int]$timeoutSec = 300) {
	$body = @{ model = 'qwen2.5:3b'; messages = @(@{ role = 'user'; content = $prompt }); stream = $false }
	if ($options) { $body.options = $options }
	$json = $body | ConvertTo-Json -Depth 5
	$sw = [System.Diagnostics.Stopwatch]::StartNew()
	try {
		$r = Invoke-RestMethod -Uri "$base/api/chat" -Method Post -Body $json -ContentType 'application/json; charset=utf-8' -TimeoutSec $timeoutSec
		$sw.Stop()
		[pscustomobject]@{ Ok = $true; Text = $r.message.content.Trim(); Seconds = [Math]::Round($sw.Elapsed.TotalSeconds, 1) }
	}
	catch {
		$sw.Stop()
		$resp = $_.Exception.Response
		$detail = $_.Exception.Message
		if ($resp) {
			try {
				$sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
				$detail = $sr.ReadToEnd()
			} catch {}
		}
		[pscustomobject]@{ Ok = $false; Text = $detail; Seconds = [Math]::Round($sw.Elapsed.TotalSeconds, 1) }
	}
}

Write-Host "=== 1. Доступ по сети (не localhost): $base ===" -ForegroundColor Cyan
Write-Host ("версия: " + (Invoke-RestMethod -Uri "$base/api/version" -TimeoutSec 15).version)
Write-Host ("корень: " + (Invoke-WebRequest -Uri "$base/" -UseBasicParsing -TimeoutSec 15).Content)
Write-Host ("список моделей: " + (((Invoke-RestMethod -Uri "$base/api/tags" -TimeoutSec 15).models | ForEach-Object { $_.name }) -join ', '))

Write-Host "`n=== 2. Один запрос через сеть ===" -ForegroundColor Cyan
$warm = Chat 'Ответь одним словом: работает?' $null
Write-Host ("[{0} с, {1}]" -f $warm.Seconds, ('запрос ' + $(if ($warm.Ok) { 'успешен' } else { 'ОШИБКА' })))
Write-Host $warm.Text

Write-Host "`n=== 3. Три параллельных запроса ===" -ForegroundColor Cyan
$jobs = 1..3 | ForEach-Object {
	$n = $_
	Start-Job -ScriptBlock {
		param($b, $n)
		$body = @{ model = 'qwen2.5:3b'; messages = @(@{ role = 'user'; content = "Скажи одним словом, что число $n чётное." }); stream = $false } | ConvertTo-Json -Depth 5
		$sw = [System.Diagnostics.Stopwatch]::StartNew()
		$r = Invoke-RestMethod -Uri "$b/api/chat" -Method Post -Body $body -ContentType 'application/json; charset=utf-8' -TimeoutSec 600
		$sw.Stop()
		"запрос ${n}: {0} с — {1}" -f [Math]::Round($sw.Elapsed.TotalSeconds, 1), $r.message.content.Trim()
	} -ArgumentList $base, $n
}
$total = [System.Diagnostics.Stopwatch]::StartNew()
$jobs | Wait-Job -Timeout 900 | Out-Null
$total.Stop()
$jobs | ForEach-Object { Receive-Job $_ -ErrorAction SilentlyContinue }
$jobs | Remove-Job -Force
Write-Host ("общее время трёх параллельных запросов: {0} с" -f [Math]::Round($total.Elapsed.TotalSeconds, 1))

Write-Host "`n=== 4. Ограничение контекста: num_ctx (Ollama молча ОБРЕЗАЕТ лишний промпт) ===" -ForegroundColor Cyan
$prompt = 'Кодовое слово: ЦИРКУЛЬ. Запомни его. ' + ('наполнитель ' * 600) + ' Повтори кодовое слово одним словом.'
$bigCtx = Chat $prompt @{ num_ctx = 4096 } 300
Write-Host ("num_ctx=4096 ({0} с): кодовое слово -> {1}" -f $bigCtx.Seconds, $bigCtx.Text)
$smallCtx = Chat $prompt @{ num_ctx = 256 } 300
Write-Host ("num_ctx=256  ({0} с): кодовое слово -> {1}" -f $smallCtx.Seconds, $smallCtx.Text)

Write-Host "`n=== 5. Ограничение: num_predict (макс. токенов ответа) ===" -ForegroundColor Cyan
$cap = Chat 'Расскажи подробно, что такое заклинание.' @{ num_predict = 5 } 300
Write-Host ("[{0} с]" -f $cap.Seconds)
Write-Host ("обрезанный ответ: " + $cap.Text)

Write-Host "`n=== 6. Rate limit отсутствует: 10 запросов подряд не отбрасываются ===" -ForegroundColor Cyan
$ok = 0
for ($i = 1; $i -le 10; $i++) {
	$r = Chat "Скажи просто число $i." @{ num_predict = 3 } 120
	if ($r.Ok) { $ok++ }
}
Write-Host ("принято запросов: $ok из 10 (все без rate limit)")
