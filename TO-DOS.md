# TO-DOS

---

## 2026-02-08 — JsonProtocol.parseRequest() [BLOCKER]

- [x] Реализовать минимальный JSON-парсер (DONE: recursive descent parser, 13 тестов)

**Problem:** `JsonProtocol.parseRequest()` бросает `UnsupportedOperationException`. Это блокирует **весь pipeline**: ClientHandler вызывает parseRequest для каждой входящей строки. Без парсера ни одна команда не может быть обработана через TCP.

**Files:**
- `src/main/java/com/sh3d/mcp/protocol/JsonProtocol.java:27-35`

**Solution:**
Реализовать минимальный токенизатор для плоского JSON. Поддержать типы: String (с экранированием `\"`, `\\`, `\n`, `\t`, `\uXXXX`), Number (int/float), Boolean, Null, Object (вложенность 1-2 уровня). Парсинг: найти `{` → читать пары `"key": value` → извлечь `"action"` (обязательное) и `"params"` (опциональный вложенный объект) → вернуть `new Request(action, paramsMap)`. При невалидном JSON бросать `IllegalArgumentException`. Без внешних библиотек.

---

## 2026-02-08 — PluginConfig: кроссплатформенные пути

- [x] Добавить поддержку Linux/macOS путей для файла конфигурации (DONE)

**Problem:** `loadPropertiesFile()` ищет конфиг только через `%APPDATA%` (Windows). На Linux/macOS `APPDATA` не существует — конфиг никогда не найдётся.

**Files:**
- `src/main/java/com/sh3d/mcp/config/PluginConfig.java:62-75`

**Solution:**
Добавить fallback: если `APPDATA` == null, проверить `~/.eteks/sweethome3d/plugins/sh3d-mcp.properties` (стандартный путь SH3D на Unix). Определять ОС через `System.getProperty("os.name")`.

---

## 2026-02-08 — CreateWallsHandler

- [x] Реализовать команду create_walls (DONE: 9 тестов)

**Problem:** Обработчик — заглушка с `UnsupportedOperationException`. Это ключевая команда MVP (P1) — создание прямоугольной комнаты из 4 стен.

**Files:**
- `src/main/java/com/sh3d/mcp/command/CreateWallsHandler.java:28-46`

**Solution:**
1. Валидация параметров: `x`, `y` (обязательные), `width > 0`, `height > 0`, `thickness` (default 10.0)
2. В `accessor.runOnEDT()`: создать 4 объекта `Wall(xStart, yStart, xEnd, yEnd)`, установить thickness
3. Соединить стены замкнутым контуром: `w1.setWallAtEnd(w2); w2.setWallAtStart(w1)` и т.д. по кругу
4. `home.addWall()` для каждой стены
5. Вернуть `Response.ok(Map.of("wallsCreated", 4, "message", "Room WxH created"))`

Зависит от: **JsonProtocol.parseRequest()** (без парсера запрос не дойдёт до обработчика)

---

## 2026-02-08 — GetStateHandler

- [x] Реализовать команду get_state (DONE: 7 тестов)

**Problem:** Заглушка. Команда нужна для верификации результатов и обратной связи для Claude (P1).

**Files:**
- `src/main/java/com/sh3d/mcp/command/GetStateHandler.java:24-50`

**Solution:**
В `accessor.runOnEDT()`:
1. `home.getWalls().size()` → wallCount
2. Итерация по `home.getFurniture()` → массив `{name, x, y, angle (в градусах), width, depth, height}`
3. `home.getRooms().size()` → roomCount
4. Расчёт bounding box по координатам всех стен (min/max по xStart, xEnd, yStart, yEnd)
5. Вернуть `Response.ok(data)`

Зависит от: **JsonProtocol.parseRequest()**

---

## 2026-02-08 — PlaceFurnitureHandler

- [x] Реализовать команду place_furniture (DONE: 14 тестов)

**Problem:** Заглушка. Вторая ключевая команда MVP (P2) — размещение мебели из каталога.

**Files:**
- `src/main/java/com/sh3d/mcp/command/PlaceFurnitureHandler.java:28-45`

**Solution:**
1. Валидация: `name` (обязательный, не пустой), `x`, `y` (обязательные), `angle` (default 0)
2. Поиск в каталоге: `accessor.getFurnitureCatalog()` → итерация по категориям/элементам → `piece.getName().toLowerCase().contains(query.toLowerCase())`
3. Если не найден → `Response.error("Furniture not found: " + name)`
4. В EDT: `new HomePieceOfFurniture(catalogPiece)`, setX/Y, `setAngle((float) Math.toRadians(angle))`, `home.addPieceOfFurniture()`
5. Вернуть данные размещённой мебели

Зависит от: **JsonProtocol.parseRequest()**, **ListFurnitureCatalogHandler** (общая логика поиска)

---

## 2026-02-08 — ListFurnitureCatalogHandler

- [ ] Реализовать команду list_furniture_catalog

**Problem:** Заглушка. Вспомогательная команда (P2) — нужна чтобы Claude знал, какую мебель можно разместить.

**Files:**
- `src/main/java/com/sh3d/mcp/command/ListFurnitureCatalogHandler.java:25-47`

**Solution:**
1. Опциональные параметры: `query`, `category`
2. `accessor.getFurnitureCatalog()` → итерация по категориям
3. Фильтр по category: `cat.getName().toLowerCase().contains(category.toLowerCase())`
4. Фильтр по query: `piece.getName().toLowerCase().contains(query.toLowerCase())`
5. Собрать массив `{name, category, width, depth, height}`
6. EDT не требуется (каталог read-only, thread-safe)

Зависит от: **JsonProtocol.parseRequest()**

---

## 2026-02-08 — Тесты: JsonProtocolTest (parse-методы)

- [x] Реализовать тесты парсинга JSON (DONE: 13 тестов в JsonProtocolTest)

**Problem:** 3 теста закомментированы — `testParseValidRequest`, `testParseRequestWithoutParams`, `testParseInvalidJson`. Нет покрытия парсера.

**Files:**
- `src/test/java/com/sh3d/mcp/protocol/JsonProtocolTest.java:13-33`

**Solution:**
Раскомментировать и реализовать после готовности `JsonProtocol.parseRequest()`. Проверить: парсинг с params, без params, невалидный JSON (throws IAE), пограничные случаи (пустая строка, null, Unicode).

Зависит от: **JsonProtocol.parseRequest()**

---

## 2026-02-08 — Тесты: CommandRegistryTest (dispatch-методы)

- [ ] Реализовать тесты dispatch с mock HomeAccessor

**Problem:** 2 теста закомментированы — `testRegisterAndDispatch`, `testDispatchUnknownAction`. Dispatch не тестируется.

**Files:**
- `src/test/java/com/sh3d/mcp/command/CommandRegistryTest.java:23-36`

**Solution:**
Создать mock HomeAccessor (Mockito) → вызвать `registry.dispatch(new Request("ping", ...), mockAccessor)` → проверить `resp.isOk()`. Для unknown: проверить `resp.isError()` и сообщение "Unknown action".

Зависит от: **JsonProtocol.parseRequest()** (dispatch сам по себе не зависит, но полноценное тестирование через TCP — да)

---

## 2026-02-08 — Тесты: PluginConfigTest (System property override)

- [x] Реализовать тест переопределения через System property (DONE)

**Problem:** `testSystemPropertyOverride` закомментирован. Приоритет конфигурации не протестирован.

**Files:**
- `src/test/java/com/sh3d/mcp/config/PluginConfigTest.java:19-26`

**Solution:**
Раскомментировать: `System.setProperty("sh3d.mcp.port", "9999")` → `PluginConfig.load()` → assert port == 9999 → `System.clearProperty()` в finally.

Зависит от: ничего (можно реализовать сразу)

---

## 2026-02-08 — Тесты: TcpServerTest (интеграционные)

- [ ] Реализовать интеграционные тесты TCP-сервера

**Problem:** 2 теста — заглушки. Нет проверки start/stop и end-to-end ping через сокет.

**Files:**
- `src/test/java/com/sh3d/mcp/server/TcpServerTest.java:10-31`

**Solution:**
`testStartAndStop`: создать TcpServer с mock accessor → start → assertTrue(isRunning) → stop → assertFalse.
`testPingOverSocket`: start server → `new Socket("localhost", port)` → отправить `{"action":"ping"}` → прочитать ответ → проверить `"status":"ok"` и version → stop.

Зависит от: **JsonProtocol.parseRequest()** (для testPingOverSocket)

---

## Граф зависимостей

```
PluginConfigTest.testSystemPropertyOverride  (независимый, можно сразу)
                    |
                    v
          JsonProtocol.parseRequest()  <-- BLOCKER, реализовать первым
           /        |        |       \
          v         v        v        v
  CreateWalls  GetState  PlaceFurn  ListCatalog
                                       |
                                       v
                                  PlaceFurniture (общая логика поиска)

          JsonProtocolTest (parse)
          CommandRegistryTest (dispatch)
          TcpServerTest.testPingOverSocket
```
