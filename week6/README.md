# Неделя 6

## Задание 1 (день 26): локальная LLM

Модель: **qwen2.5:3b** (1.9 ГБ, q4) через **Ollama** — CPU-инференс ~17–20 ток/с на Intel Ultra 5 125H.
(Пробовала сначала qwen2.5:1.5b — на диалоге с памятью и арифметике врёт, 3b решает оба пункта и остаётся крошечной.)

Как проверить (снять на видео):

1. Установить Ollama: `winget install --id Ollama.Ollama` (уже установлено)
2. Запустить сервер: `ollama serve` (порт 11434) — либо просто открыть приложение Ollama
3. Скачать модель: `ollama pull qwen2.5:3b`
4. CLI-доступ: `ollama run qwen2.5:3b "Скажи одним предложением: локальная модель работает?"` → осмысленный ответ
5. HTTP API + 4 запроса разной сложности: `.\local-llm-demo.ps1`
   - запрос 1 (простой): «Как тебя зовут?» → «Меня зовут Qwen»
   - запрос 2 (диалог, 3 сообщения): модель помнит «Меня зовут Алекс» → «Ты Алекс, а я Qwen»
   - запрос 3 (посложнее, арифметика): 12 конфет → половина брату, треть остатка подруге → верный ответ 4 с пошаговым решением
   - запрос 4 (объяснение): «что такое кэширование, как для школьника» → понятная аналогия
   - под каждым ответом: время, промпт/ответ-токены, ток/с
6. HTTP API можно дёргать и напрямую:
   ```powershell
   Invoke-RestMethod -Uri http://localhost:11434/api/chat -Method Post `
     -ContentType 'application/json; charset=utf-8' `
     -Body '{"model":"qwen2.5:3b","messages":[{"role":"user","content":"Привет!"}],"stream":false}'
   ```
   (есть и OpenAI-совместимый эндпоинт `http://localhost:11434/v1/chat/completions`)

### Проверка через Postman

Всё уже установлено (Ollama + qwen2.5:3b), нужно только поднять сервер: `ollama serve` (если не запущен — окно сразу не закрывается, сервер работает, пока оно открыто).

1. Новый запрос → **POST** `http://localhost:11434/api/chat`
2. **Body → raw → JSON**, вставить (Content-Type Postman выставит сам):
   ```json
   {
     "model": "qwen2.5:3b",
     "messages": [
       { "role": "user", "content": "Привет! Как тебя зовут?" }
     ],
     "stream": false
   }
   ```
3. **Send** → в ответе поле `message.content` (плюс статистика: `prompt_eval_count`, `eval_count`, `eval_duration`, `total_duration`)
4. Диалог из двух ходов — просто дописать в `messages` ответ ассистента и следующий вопрос:
   ```json
   {
     "model": "qwen2.5:3b",
     "messages": [
       { "role": "user", "content": "Меня зовут Алекс." },
       { "role": "assistant", "content": "Привет, Алекс!" },
       { "role": "user", "content": "Как меня зовут?" }
     ],
     "stream": false
   }
   ```
5. Вариант с OpenAI-совместимым API (если захочется показать совместимость): **POST** `http://localhost:11434/v1/chat/completions`, тело того же вида, ответ — в `choices[0].message.content`

Кириллица в Postman естественна — он шлёт UTF-8, никаких charset-танцев, в отличие от PowerShell.
