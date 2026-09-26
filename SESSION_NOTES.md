# ai-challenge — выжимка по неделям (handoff для следующей сессии)

Обновлено: 26.09.2026. Всё закоммичено и запушено (master → github.com/krparen/ai-challenge).

**Общий стек**: Java 25, Maven (`mvnw.cmd`), Spring Boot 4.1.1, Spring AI 2.0.1 (BOM), модель `deepseek-v4-flash` (thinking disabled), PostgreSQL `localhost:5432` (postgres/postgres), Liquibase + `spring-boot-liquibase`, Thymeleaf, пакет `com.tutorial.ai_challenge`. Ключ — `${DEEPSEEK_API_KEY}` (User-scope Windows, плейсхолдер во всех yaml).

---

## week1 (дни 1–5)
- День 1: простой чат (GET `/` + POST `/chat`, PRG).
- День 2: `/compare` — сравнение «без ограничений» vs «с ограничениями»: system prompt, maxTokens, stopSequence (`\n` раскрывается), thinking, temperature, модель (влияет только на правую колонку).
- День 5: замеры времени/токенов/стоимости под ответом (usage из metadata), цены off-peak $0.22/$0.66 за 1M; модели подписки: `deepseek-v4-flash`, `deepseek-v4-flash-vision-exp`, `deepseek-v4-pro`.
- ⚠️ Ключ раньше лежал в yaml открытым текстом — исправлено на плейсхолдер, не возвращать.

## week2 (дни 6–10) — чат-агент с персистентностью и стратегиями
- День 6: чат-агент (`ChatAgent`, PRG-контроллер, `chat.html` с лоадером).
- День 7: контекст в PostgreSQL — `ChatMemory` на `JdbcChatMemoryRepository` (таблица `SPRING_AI_CHAT_MEMORY`), метаданные бесед через JPA (`Conversation`), миграции Liquibase; cid в cookie `conversationId` (7 дней, переживает рестарт); POST `/chat/new` закрывает беседу (`closed_at`).
- День 8: токены — `AgentReply` с usage, кумулятивные totals в `conversations.prompt/completion_tokens`, оценка истории `JTokkitTokenCountEstimator` (из `spring-ai-commons`!) + ≈$; лимит контекста v4-flash = **1 048 576 токенов** (HTTP 400 при превышении); `max-http-form-post-size: 100MB`; файл `week2/over_limit_request` (payload на проверку отказа).
- День 9: `SummaryChatMemory` — add() пишет полную историю в БД, get() = SystemMessage(сводка) + последние `chat.recent-messages`; сводка в `conversations.summary`, usage суммаризатора считается. Вывод: на коротких историях не экономит — выигрыш в ограничении роста промпта.
- День 10: ветвление copy-on-branch (`POST /chat/branch`, опц. `checkpoint` = сколько первых сообщений; `POST /chat/switch` переоткрывает закрытые; список бесед, «⑂ ветка отсюда») + стратегии `ChatStrategy` FULL/WINDOW/FACTS/SUMMARY в `conversations.strategy`, `StrategyChatMemory`, `POST /chat/strategy`, панель фактов, «+ факты» в stats/$; `chat.recent-messages: 2`, `compression-enabled` удалён.
- Осталось для видео: прогнать сценарий ТЗ (10–15 сообщ.) на каждой стратегии и сравнить.

## week3 (дни 11–14) — агент со слоями памяти
- 4 хранилища раздельно (миграции 001–008): краткосрочная `SPRING_AI_CHAT_MEMORY` (окно `memory.short-term.window`), рабочая `working_tasks` (общий пул задач всех профилей, одна глобально ACTIVE, «снять активность»), память агента `agent_memory` (ТОЛЬКО руками, ✕-удаление), профиль `profiles` (style/format/restrictions руками) + `profile_attributes`; беседы `conversations` (usage + usage извлечения).
- `MemoryService` — фасад слоёв; `LayeredChatMemory`: get() = System-блоки + окно, add() = полная история + извлекатель (usage в `extraction_*_tokens`).
- Промпт: безусловные блоки `=== ИНВАРИАНТЫ ===` (первыми) → `=== ПРОФИЛЬ ===` → `=== ПАМЯТЬ АГЕНТА ===` → `=== РАБОЧАЯ ПАМЯТЬ ===` → `=== КРАТКОСРОЧНАЯ ПАМЯТЬ ===`, пустые с «(пока пусто)».
- День 11: слои памяти (переключение профилей = перелинковка беседы). День 12 фактически покрыт (профиль+предпочтения в каждом запросе).
- День 13: FSM задачи — `TaskStage` PLANNING→EXECUTION→VALIDATION→DONE (+откаты VALIDATION→EXECUTION, EXECUTION→PLANNING; self разрешён; DONE авто-finish), миграции 007–008 (`stage/current_step/expected_action`, `task_state_history`); гибрид: извлекатель возвращает STAGE/STEP/ACTION/NOTES → `applyTransition` валидирует, нелегальные логируются «ОТКЛОНЕНО FSM» (driver LLM/MANUAL); `POST /task/stage`; пауза/возобновление из нового диалога работают.
- День 14: инварианты — миграция 009 `invariants` (category: архитектура/стек/техрешение/бизнес-правило + text; глобальные, ТОЛЬКО руками, панель с И1/И2 и ✕, `POST /invariant/add|delete`); при конфликте — отказ с цитированием номера и текста инварианта + альтернатива (проверено живьём: «MongoDB + платный API» → отказ по И1/И2).
- UX: `/` → `/chat`; чат-textarea (Enter — отправить, Shift+Enter — перенос, авто рост); редактируемые заметки задачи (`POST /task/state`); выбор активной задачи селектом; смена профиля селектом onchange; ✕-удаление подтверждённых фактов.

## week4 (заготовка, 26.09.2026)
- **Два отдельных mvn-проекта**: `week4/demo_application` (порт 8080) и `week4/service2` (8081) — порты прописаны в `application.yaml`, аргументы запуска не нужны.
- pom 1:1 с week2/3; классы `AiChallengeApplicationDemo`/`AiChallengeApplicationService2`; `HelloController` GET `/hello` → «Hi! (demo application)/(service 2)»; БД `challenge_week4_s1` / `challenge_week4_s2` (созданы).
- Проверено: оба стартуют одновременно, `/hello` на обоих портах → 200.
- Старт обоих: `C:\Users\Admin\AppData\Local\Temp\opencode\start-week4-both.ps1`.

## Грабли/инструменты
- psql: `& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U postgres -h localhost`, `PGPASSWORD=postgres` (не в PATH; служба `postgresql-x64-18`).
- PowerShell: IWR нужен `-UseBasicParsing`; кириллическим .ps1 — BOM; `Start-Process` — одной строкой аргументы; `ChildProcess.kill` обёртки — java выживает; при живом инстансе `mvn package` падает (jar залочен — сначала Stop-Process).
- `api.deepseek.com` требует VPN (без него 443 закрыт); клиентский таймаут ≠ серверный провал — «зависший» ход может завершиться серверно, смотреть БД.
- IDE-инстанс пользователя на 8080 — не убивать, просить перезапустить; тестовые инстансы — на 8081.
- Thymeleaf: `'И' + (st.index + 1)` рендерит литеральный текст — оборачивать в `${st.index + 1}`.
- GET/POST `/chat` с чужой/пустой кукой молча создаёт новую беседу.
- Забытый геттер entity = 500 с оборванным соединением (SpEL EL1008E).
- git: новые файлы добавлять в индекс; коммиты — по явной просьбе; `amplicode.xml` в корне — не решено.

## Текущее состояние и что дальше
- Рабочее дерево чистое, всё в origin/master.
- Дальше: задания недели 4 (не начаты); опционально для видео: week2 — сравнение стратегий на сценарии ТЗ, week3 — demo слоёв памяти и сравнение профилей (день 12).
