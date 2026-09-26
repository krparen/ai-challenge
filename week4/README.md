# Неделя 4

## Задание 1

Как проверить:

1. Открыть коммит 55a099e8b82087b3283a7db9ce50a4488540f56d
2. Запустить demo_application
3. Перейти на http://localhost:8080/mcp
4. Нажать на кнопку, увидеть доступные методы

## Задание 2

Как проверить:

1. Открыть коммит 54ed0f77e81ea404bf5fe99d0a523992836ecc21
2. Запустить service2 (порт 8081) — это mock REST API + MCP-сервер над ним
3. Опционально: проверить REST напрямую — http://localhost:8081/mock/getColor и http://localhost:8081/mock/getNumberFromZeroToNine (случайные цвет и число)
4. Запустить demo_application (порт 8080) — это MCP-клиент
5. Перейти на http://localhost:8080/mcp
6. «Запросить список инструментов» — увидеть DeepWiki (3 инструмента) и mock-api (getColor, getNumber)
7. Нажать «Вызвать getColor и getNumber» — увидеть результаты вызова через MCP и составленную из них фразу

Примечание: если service2 перезапускали при работающем demo_application, первый вызов вернёт ошибку «MCP session with server terminated» — нажмите кнопку ещё раз, клиент переподключится.