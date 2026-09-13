# ai-challenge — контекст проекта (для восстановления сессии)

Учебный курс по LLM-разработке. Репозиторий содержит домашки по неделям/дням.

## Стек и структура

- Java 25, Maven (`mvnw.cmd`), Spring Boot **4.1.1** (стартеры нового именования: `spring-boot-starter-webmvc`, `-thymeleaf`)
- Spring AI **2.0.1** (BOM `spring-ai-bom`), стартер `spring-ai-starter-model-deepseek`
- Thymeleaf для страниц
- Проекты-домашки: `week1/` (дни 1–5, см. Статус) и `week2/` (день 6: чат-агент; день 7: контекст в PostgreSQL — Spring AI `ChatMemory` на `JdbcChatMemoryRepository` (таблица `SPRING_AI_CHAT_MEMORY`), беседы-метаданные через JPA (`Conversation`), миграции Liquibase; cid в cookie `conversationId`, а не в сессии — переживает рестарт; POST /chat/new закрывает беседу: `chatMemory.clear` + `closed_at`; день 8: токены — `ChatAgent.AgentReply` с usage (prompt/completion) каждого вызова, кумулятивные totals в колонках `conversations.prompt_tokens/completion_tokens`, оценка истории `JTokkitTokenCountEstimator` (из `spring-ai-commons`! не из model) на странице + cost off-peak; переполнение контекста проверено: лимит v4-flash = **1 048 576 токенов**, при превышении API даёт HTTP 400 → error-flash в UI, память беседы не портится), пакет одинаков: `com.tutorial.ai_challenge`; класс приложения в week2 пользователь переименовал в `AiChallengeApplicationWeek2`
- Ключ в yaml week2 — через `${DEEPSEEK_API_KEY}`; в week1 пользователь вписал ключ открытым текстом (вернуть плейсхолдер до коммита!)

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
- Токенайзер Spring AI — `org.springframework.ai.tokenizer.TokenCountEstimator`/`JTokkitTokenCountEstimator` живёт в **`spring-ai-commons`** (в `spring-ai-model` его нет — проверено листингом jar). На английском jtokkit (o200k) завышает против DeepSeek-токенайзера ~в 2 раза: 4.4M chars → jtokkit ~640k vs реальных 720k... точнее реальные были меньше; для кириллицы не проверен. Не автоконфигурируется — `new JTokkitTokenCountEstimator()`.
- Большие POST в week2: `server.tomcat.max-http-form-post-size: 100MB` (дефолт 2MB даёт 413; кириллица в form-urlencoded раздувается ×3, ASCII почти нет). Контекст deepseek-v4-flash = 1 048 576 токенов (6.1 chars/token на этом английском тексте); превышение → HTTP 400 `invalid_request_error` с точной цифрой.

## Грабли Spring AI 2.0 (отличия от 1.x)

- `ChatClient...options(...)` и `Builder.defaultOptions(...)` принимают **билдер**, а не готовый объект: `.options(DeepSeekChatOptions.builder().maxTokens(60)...)` — без `.build()`. Сигнатуры проверять `javap -classpath <jar> 'класс$Вложенный'` (в PowerShell имя с `$` — в одинарных кавычках!).
- Включённый thinking **съедает бюджет maxTokens** — на сам ответ может не хватить.
- Chat memory 2.0.1 (проверено javap): артефакт `spring-ai-starter-model-chat-memory-repository-jdbc`; советник — `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor` (в `chat.client.advisor`, НЕ `chat.memory.advisor`), `builder(chatMemory)`; id беседы — advisor-параметр `ChatMemory.CONVERSATION_ID`; таблица 2.0 — `SPRING_AI_CHAT_MEMORY` (conversation_id/content/type/"timestamp"/sequence_id, без PK) — DDL брать из jar репозитория, а не из доков 1.0; `spring.ai.chat.memory.repository.jdbc.initialize-schema` — режим (`never`/`always`, НЕ boolean).
- Spring Boot 4: автоконфигурация Liquibase вынесена в отдельный модуль **`spring-boot-liquibase`** — `liquibase-core` сам её не подтягивает, без него миграции молча не стартуют (симптом: `Schema validation: missing table` в validate-режиме JPA).

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
