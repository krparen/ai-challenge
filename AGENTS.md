# ai-challenge — контекст проекта (для восстановления сессии)

Учебный курс по LLM-разработке. Репозиторий содержит домашки по неделям/дням.

## Стек и структура

- Java 25, Maven (`mvnw.cmd`), Spring Boot **4.1.1** (стартеры нового именования: `spring-boot-starter-webmvc`, `-thymeleaf`)
- Spring AI **2.0.1** (BOM `spring-ai-bom`), стартер `spring-ai-starter-model-deepseek`
- Thymeleaf для страниц
- Проект домашки: `week1/`, пакет `com.tutorial.ai_challenge`

### Файлы week1

- `ChatController.java` — все эндпоинты:
  - `GET /` + `POST /chat` — простой чат (день 1)
  - `GET /compare` + `POST /compare` — сравнение «без ограничений» vs «с ограничениями» (день 2): message + поля system prompt, maxTokens (деф. 60), stopSequence (запись `\n` раскрывается в перенос, пусто = без стопа), thinking (select вкл/выкл), temperature (0–2, деф. 1.0), model (select из MODELS, влияет только на правую колонку; слева всегда deepseek-v4-flash)
  - замеры (день 5): под каждым ответом hint со временем, токенами (вход+генерация) и оценкой стоимости (цены зашиты в `pricePerMillion`, off-peak, cache-miss); usage берётся из `call().chatResponse().getMetadata().getUsage()`
- `templates/chat.html`, `templates/compare.html` — страницы с формами, hint под правой колонкой показывает применённые параметры

## Конфигурация (`week1/src/main/resources/application.yaml`)

- `spring.ai.deepseek.api-key` — из env `DEEPSEEK_API_KEY`. Переменная задана в User-scope Windows. **Внимание:** пользователь иногда вписывает ключ прямо в yaml (файл гит-отслеживаемый!) — напоминать вернуть `${DEEPSEEK_API_KEY}` до коммита.
- Модель: `deepseek-v4-flash`. Актуальные ID моделей смотреть в доках DeepSeek (api-docs.deepseek.com) — `deepseek-chat` и `deepseek-reasoner` устарели; у v4-flash thinking **включён по умолчанию**.
- Отключение thinking в yaml — только вложенной формой (важно!):
  ```yaml
  thinking:
    type: disabled
  ```
  Скалярная `thinking: disabled` валит контекст: в Spring AI 2.0.1 у рекорда `Thinking` нет конвертера из строки (проверено ошибкой биндинга).

## Грабли Spring AI 2.0 (отличия от 1.x)

- `ChatClient...options(...)` и `Builder.defaultOptions(...)` принимают **билдер**, а не готовый объект: `.options(DeepSeekChatOptions.builder().maxTokens(60)...)` — без `.build()`. Сигнатуры проверять `javap -classpath <jar> 'класс$Вложенный'` (в PowerShell имя с `$` — в одинарных кавычках!).
- Включённый thinking **съедает бюджет maxTokens** — на сам ответ может не хватить.

## Как я проверяю изменения (рабочий процесс)

1. `.\mvnw.cmd -q package` (тесты + jar)
2. Запуск: `Start-Process java -ArgumentList @('-jar',$jar) -RedirectStandardOutput ... -PassThru -WindowStyle Hidden` с `$env:DEEPSEEK_API_KEY` в той же сессии (User-scope переменная в мои процессы не наследуется!)
3. `Get-NetTCPConnection -LocalPort 8080` → POST через `Invoke-WebRequest`, парсинг ответа по `<div class="answer">` / `<div class="hint">`
4. Останов: `Stop-Process -Id <pid>`
- Обёртка shell-инструмента иногда убивает команду с `Start-Process` (ошибка `ChildProcess.kill`) — java-процесс при этом обычно выживает; проверять порт и лог.

## ЧАСТЫЙ СЛУЧАЙ: забытый запущенный экземпляр в IDE

Пользователь часто оставляет приложение запущенным из IDE (старый код) на порту 8080. Симптомы:
- мой экземпляр падает с `Port 8080 was already in use` (смотреть app-out.log),
- `Get-NetTCPConnection` показывает владельца, не совпадающего с моим PID,
- POST возвращает 200, но со **старым** поведением/шаблоном — выглядит как «изменения не применились».

Порядок действий:
1. Сверять PID владельца порта с PID моего `Start-Process` (`$conn.OwningProcess -ne $p.Id` → конфликт).
2. IDE-процесс видно как `java`/`javaw` — это инстанс пользователя, **самовольно не убивать**.
3. Либо попросить пользователя остановить Run в IDE, либо проверяться на другом порту: `-Dserver.port=8081`.
4. После правок пользователю всегда напоминать перезапустить его IDE-инстанс.

## Статус

- День 1 (чат) и день 2 (сравнение с ограничениями) — сделаны и проверены живьём.
- День 5 (переключатель моделей + замеры времени/токенов/стоимости) — сделан. Модели подписки (проверено `GET api.deepseek.com/models`): `deepseek-v4-flash`, `deepseek-v4-flash-vision-exp`, `deepseek-v4-pro`; цены off-peak за 1M (cache-miss, вход/генерация): flash и vision-exp $0.22/$0.66, pro $0.66/$1.98.
- Новые файлы по просьбе пользователя добавлять в git-индекс (`git add`); коммиты — только по явной просьбе.
