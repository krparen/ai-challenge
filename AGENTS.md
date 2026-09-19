# ai-challenge — контекст проекта (для восстановления сессии)

Учебный курс по LLM-разработке. Репозиторий содержит домашки по неделям/дням.

## Стек и структура

- Java 25, Maven (`mvnw.cmd`), Spring Boot **4.1.1** (стартеры нового именования: `spring-boot-starter-webmvc`, `-thymeleaf`)
- Spring AI **2.0.1** (BOM `spring-ai-bom`), стартер `spring-ai-starter-model-deepseek`
- Thymeleaf для страниц
- Проекты-домашки: `week1/` (дни 1–5, см. Статус), `week2/` (дни 6–10, см. Статус) и `week3/` (заготовка: тот же pom, что в week2, класс `AiChallengeApplicationWeek3`, контроллер с GET /hello → «Hi!», пустой Liquibase master.yaml; БД `challenge_week3` создана 19.09.2026 через `& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U postgres -h localhost` с `PGPASSWORD=postgres` — psql не в PATH, лежит в bin каталога PostgreSQL 18, сервер как служба `postgresql-x64-18`; в week2 ниже по дням: день 6: чат-агент; день 7: контекст в PostgreSQL — Spring AI `ChatMemory` на `JdbcChatMemoryRepository` (таблица `SPRING_AI_CHAT_MEMORY`), беседы-метаданные через JPA (`Conversation`), миграции Liquibase; cid в cookie `conversationId`, а не в сессии — переживает рестарт; POST /chat/new закрывает беседу: `chatMemory.clear` + `closed_at`; день 8: токены — `ChatAgent.AgentReply` с usage (prompt/completion) каждого вызова, кумулятивные totals в колонках `conversations.prompt_tokens/completion_tokens`, оценка истории `JTokkitTokenCountEstimator` (из `spring-ai-commons`! не из model) на странице + cost off-peak; переполнение контекста проверено: лимит v4-flash = **1 048 576 токенов**, при превышении API даёт HTTP 400 → error-flash в UI, память беседы не портится; день 9: `SummaryChatMemory` (наш @Bean вместо MessageWindowChatMemory) — add() пишет ПОЛНУЮ историю в БД (jdbc saveAll = delete+insert всего списка, проверено javap), get() = SystemMessage(сводка) + последние `chat.recent-messages` (дефолт 10, настраивается), сводка хранится в `conversations.summary/summary_message_count` (миграция 004), пересобирается LLM-ом только когда за окном новые сообщения; UI/статистика читают полную историю из `ChatMemoryRepository`; флаг `chat.compression-enabled`; замер на 13 коротких ходах: сводка сохраняет факты за окном («Зелёный, Бобик» с 26 сообщ.), но на короткой истории токенов НЕ экономит (2818 vs 2408) — выигрыш только на длинных ходах/историях, зато промпт перестаёт расти; usage суммаризатора тоже считается: колонки `conversations.summary_prompt_tokens/summary_completion_tokens` (миграция 005), показываются в stats-строке и входят в $-оценку; окно 3 (эксперимент пользователя): суммаризатор сжёг 1331 ток. за 7 ходов против 695 у агента — слишком малое окно дороже полной истории); день 10 (ветвление): copy-on-branch — форк = новая беседа с копией истории (`POST /chat/branch`, опц. `checkpoint` = сколько первых сообщений скопировать; `ChatAgent.branchConversation` копирует через `ChatMemoryRepository.saveAll`, миграция 006 `conversations.branch_of/branch_point`, entity `forkFrom`); переключение бесед — `POST /chat/switch` (перезаписывает cookie, закрытую беседу переоткрывает `closed_at=null`); UI: список бесед («Беседы», текущая подсвечена, у веток «↳ родитель + (первые N сообщ.)/(вся история)»), «⑂ Ветка (копия диалога)» и «⑂ ветка отсюда» под каждым сообщением пользователя; кука общая для вкладок браузера, для параллельных вкладок нужен cid параметром (не реализовано); день 10, часть 2 (стратегии): `ChatStrategy` enum FULL/WINDOW/FACTS/SUMMARY в колонке `conversations.strategy` (миграция 007: strategy/facts/facts_prompt_tokens/facts_completion_tokens), `SummaryChatMemory` заменён на `StrategyChatMemory` — get() режет промпт по стратегии беседы (FULL=всё, WINDOW=последние `chat.recent-messages`, FACTS=SystemMessage(факты)+окно, SUMMARY=сводка+окно из дня 9), add() обновляет факты LLM-вызовом только в FACTS (хранитель: «ключ: значение», копятся с нуля после включения); `POST /chat/strategy` + ряд кнопок на странице (текущая подсвечена .stron), факты видны в `<details>`, usage фактов в stats («+ факты X+Y») и в $; `chat.compression-enabled` из yaml УДАЛЁН (дефолтная стратегия FULL, старые беседы миграцией переведены на FULL); живой тест (окно 2, 14 сообщ.): WINDOW-промпт = ровно 2 USER-маркера, запрос 419 ток против ~6850 полной истории; «Алекс» в window-промпте — эхо ассистента, не баг; внимание: проверка страницы с чужой/пустой кукой молча создаёт НОВУЮ беседу (ensureConversation) — при отладке смотреть cid на странице), пакет одинаков: `com.tutorial.ai_challenge`; класс приложения в week2 пользователь переименовал в `AiChallengeApplicationWeek2`
- Ключ в обоих yaml — через `${DEEPSEEK_API_KEY}` (week1 исправлен 14.09.2026; ключ задаётся User-scope переменной Windows)

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
- День 5 (перключатель моделей + замеры времени/токенов/стоимости) — сделан. Модели подписки (проверено `GET api.deepseek.com/models`): `deepseek-v4-flash`, `deepseek-v4-flash-vision-exp`, `deepseek-v4-pro`; цены off-peak за 1M (cache-miss, вход/генерация): flash и vision-exp $0.22/$0.66, pro $0.66/$1.98.
- День 10, часть 1 (Branching, copy-on-branch + список бесед) — сделано и проверено живьём на 8081: полный форк копирует всю историю, форк с checkpoint=2 — первые 2 сообщения, после игры в ветке исходный диалог фактов не видит (независимость подтверждена: «фиолетовый» в оригинале, «зелёный» остался в ветке).
- День 10, часть 2 (стратегии WINDOW/FACTS + переключатель, SUMMARY как бонус) — сделано и проверено живьём на 8081: факты извлекаются («заказчик: Алекс, платформа: Android…»), окно режет промпт (2 USER-маркера, 419 ток vs 6850 полной истории), usage фактов считается (+616+87 за 3 хода). Осталось для видео: прогнать сценарий ТЗ (10–15 сообщ.) на каждой стратегии и сравнить качество/стабильность/токены/удобство.
- week3 — заготовка создана и проверена живьём 19.09.2026 (GET /hello → «Hi!» на 8081, `mvnw -q package` зелёный): pom 1:1 как в week2 (webmvc, thymeleaf, deepseek, chat-memory-jdbc, data-jpa, postgresql, liquibase + spring-boot-liquibase, lombok), класс `AiChallengeApplicationWeek3`, `HelloController`, пустой master.yaml (Liquibase создал только databasechangelog), yaml → БД `challenge_week3`; jar `ai_challenge_week3-0.0.1-SNAPSHOT.jar`.
- Новые файлы по просьбе пользователя добавлять в git-индекс (`git add`); коммиты — только по явной просьбе.
