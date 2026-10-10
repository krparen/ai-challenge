# День 26: локальная LLM через Ollama — HTTP API (native /api/chat)
# Запуск: .\local-llm-demo.ps1   (требуется запущенный `ollama serve` и модель qwen2.5:3b)

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:11434'

function Ask([object[]]$messages) {
	$body = @{ model = 'qwen2.5:3b'; messages = $messages; stream = $false } | ConvertTo-Json -Depth 5
	$sw = [System.Diagnostics.Stopwatch]::StartNew()
	$r = Invoke-RestMethod -Uri "$base/api/chat" -Method Post -Body $body -ContentType 'application/json; charset=utf-8' -TimeoutSec 300
	$sw.Stop()
	$tps = [Math]::Round($r.eval_count / ($r.eval_duration / 1e9))
	[pscustomobject]@{
		Answer    = $r.message.content.Trim()
		Seconds   = [Math]::Round($sw.Elapsed.TotalSeconds, 1)
		PromptTok = $r.prompt_eval_count
		EvalTok   = $r.eval_count
		TokPerSec = $tps
	}
}

# 1. Простой запрос: знакомство
$a1 = Ask @(@{ role = 'user'; content = 'Привет! Как тебя зовут? Ответь одним предложением.' })

# 2. Диалог: второй ход с сохранением контекста (проверка памяти)
$dialogue = @(
	@{ role = 'user'; content = 'Привет! Меня зовут Алекс, а тебя?' },
	@{ role = 'assistant'; content = 'Привет, Алекс! Меня зовут Qwen. Чем помочь?' },
	@{ role = 'user'; content = 'Скажи, как меня зовут и как тебя? Ответь одним предложением.' }
)
$a2 = Ask $dialogue

# 3. Посложнее: логика + арифметика
$a3 = Ask @(@{ role = 'user'; content = 'У Маши было 12 конфет. Половину она отдала брату, а треть остатка — подруге. Сколько конфет осталось у Маши? Дай короткое решение и ответ.' })

# 4. Творческая: объяснение простыми словами
$a4 = Ask @(@{ role = 'user'; content = 'Объясни в двух предложениях, что такое кэширование, как для школьника.' })

$i = 0
foreach ($a in @($a1, $a2, $a3, $a4)) {
	$i++
	Write-Host "`n=== Запрос $i ===" -ForegroundColor Cyan
	Write-Host $a.Answer
	Write-Host ("[{0} с, промпт {1} + ответ {2} ток, {3} ток/с]" -f $a.Seconds, $a.PromptTok, $a.EvalTok, $a.TokPerSec) -ForegroundColor DarkGray
}
