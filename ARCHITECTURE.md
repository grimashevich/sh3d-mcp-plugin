# Sweet Home 3D MCP Plugin -- Архитектура (v0.1)

## Содержание

1. [Высокоуровневая архитектура](#1-высокоуровневая-архитектура)
2. [Структура пакетов и классов](#2-структура-пакетов-и-классов)
3. [TCP-сервер](#3-tcp-сервер)
4. [Система команд](#4-система-команд)
5. [Интеграция с Sweet Home 3D](#5-интеграция-с-sweet-home-3d)
6. [JSON-обработка](#6-json-обработка)
7. [Конфигурация](#7-конфигурация)
8. [Структура проекта (Maven)](#8-структура-проекта-maven)
9. [Диаграммы последовательности](#9-диаграммы-последовательности)
10. [Архитектурные решения (ADR)](#10-архитектурные-решения-adr)

---

## 1. Высокоуровневая архитектура

### 1.1 Контекстная диаграмма

```
+------------------+        +---------------------+        +----------------------------+
|                  |  MCP   |                     |  TCP   |                            |
|  Claude (LLM)   |------->|  MCP Server         |:9877-->|  SH3D MCP Plugin           |
|                  |  proto |  (отдельный процесс)|  JSON  |  (внутри Sweet Home 3D JVM)|
+------------------+        +---------------------+  line  +----------------------------+
                                                      |               |
                                                      |       +-------v-----------+
                                                      |       |  Sweet Home 3D    |
                                                      |       |  Java API         |
                                                      |       |  (Home, Wall,     |
                                                      |       |   Furniture, etc.) |
                                                      |       +-------------------+
```

**Граница ответственности этого проекта:** только блок "SH3D MCP Plugin". MCP Server --
отдельный проект, который подключается к плагину как TCP-клиент.

### 1.2 Диаграмма компонентов плагина

```
+============================================================================+
|                         SH3D MCP Plugin (JAR)                              |
|                                                                            |
|  +---------------------+     +------------------------------------------+  |
|  |   Plugin Lifecycle  |     |            TCP Server Layer               |  |
|  |                     |     |                                          |  |
|  |  SH3DMcpPlugin      |---->|  TcpServer         ClientHandler        |  |
|  |  (extends Plugin)   |     |  (ServerSocket)     (per-connection)     |  |
|  |                     |     |                                          |  |
|  |  ServerToggleAction |     +-------------------|----------------------+  |
|  |  (PluginAction)     |                         |                        |
|  +---------------------+                         | JSON request/response  |
|                                                  v                        |
|  +----------------------------------------------------------------------+ |
|  |                      Protocol Layer                                  | |
|  |                                                                      | |
|  |  JsonProtocol (parse request <-> format response)                    | |
|  |  Request / Response (value objects)                                  | |
|  +-------------------------------------|--------------------------------+ |
|                                        |                                  |
|                                        v                                  |
|  +----------------------------------------------------------------------+ |
|  |                      Command Layer                                   | |
|  |                                                                      | |
|  |  CommandRegistry -----> CommandHandler (interface)                    | |
|  |                         |-- PingHandler                              | |
|  |                         |-- CreateWallsHandler                       | |
|  |                         |-- PlaceFurnitureHandler                    | |
|  |                         |-- GetStateHandler                          | |
|  |                         |-- ListFurnitureCatalogHandler              | |
|  +-------------------------------------|--------------------------------+ |
|                                        |                                  |
|                                        v                                  |
|  +----------------------------------------------------------------------+ |
|  |                      SH3D Bridge Layer                               | |
|  |                                                                      | |
|  |  HomeAccessor (thread-safe доступ к Home через EDT)                  | |
|  +----------------------------------------------------------------------+ |
+============================================================================+
```

### 1.3 Описание компонентов

| Компонент | Ответственность |
|-----------|----------------|
| **Plugin Lifecycle** | Инициализация/уничтожение плагина, создание пункта меню, управление жизненным циклом TCP-сервера |
| **TCP Server Layer** | Прием TCP-соединений, чтение/запись строк, управление потоками ввода-вывода |
| **Protocol Layer** | Сериализация/десериализация JSON, валидация формата запросов, формирование ответов |
| **Command Layer** | Маршрутизация команд по имени, выполнение бизнес-логики, валидация параметров |
| **SH3D Bridge Layer** | Безопасный доступ к объектам Sweet Home 3D с гарантией выполнения в EDT |

### 1.4 Потоки данных

```
TCP клиент                   Плагин
    |                          |
    |--- JSON line ----------->|  1. ClientHandler.readLine()
    |                          |  2. JsonProtocol.parseRequest(line)
    |                          |  3. CommandRegistry.getHandler(action)
    |                          |  4. handler.execute(params, homeAccessor)
    |                          |     4a. homeAccessor.runOnEDT(() -> ...)
    |                          |     4b. SwingUtilities.invokeAndWait(...)
    |                          |  5. JsonProtocol.formatResponse(result)
    |<--- JSON line -----------|  6. ClientHandler.writeLine(response)
    |                          |
```

---

## 2. Структура пакетов и классов

### 2.1 Иерархия пакетов

```
com.eteks.sweethome3d.mcp/
|
|-- plugin/                         # Точка входа плагина и SH3D-интеграция
|   |-- SH3DMcpPlugin.java         # extends Plugin, точка входа
|   |-- ServerToggleAction.java     # PluginAction для Start/Stop через меню
|
|-- server/                         # TCP-сервер
|   |-- TcpServer.java             # ServerSocket, accept loop
|   |-- ClientHandler.java         # Обработка одного соединения
|   |-- ServerState.java           # Enum: STOPPED, STARTING, RUNNING, STOPPING
|
|-- protocol/                       # Протокол общения
|   |-- JsonProtocol.java          # Парсинг/форматирование JSON
|   |-- Request.java               # Value object: action + params
|   |-- Response.java              # Value object: status + data/message
|
|-- command/                        # Обработчики команд
|   |-- CommandHandler.java        # Интерфейс обработчика
|   |-- CommandRegistry.java       # Реестр action -> handler
|   |-- PingHandler.java
|   |-- CreateWallsHandler.java
|   |-- PlaceFurnitureHandler.java
|   |-- GetStateHandler.java
|   |-- ListFurnitureCatalogHandler.java
|
|-- bridge/                         # Мост к Sweet Home 3D API
|   |-- HomeAccessor.java          # Thread-safe обертка над Home
|
|-- config/                         # Конфигурация
|   |-- PluginConfig.java          # Настраиваемые параметры
```

### 2.2 Описание классов

#### Пакет `plugin`

**`SH3DMcpPlugin extends com.eteks.sweethome3d.plugin.Plugin`**
- Главный класс плагина, указывается в `ApplicationPlugin.properties`
- `getActions()` -- возвращает массив с одним `ServerToggleAction`
- `destroy()` -- останавливает TCP-сервер при закрытии Home

```
Поля:
  - tcpServer: TcpServer
  - config: PluginConfig

Методы:
  + getActions(): PluginAction[]
  + destroy(): void
```

**`ServerToggleAction extends PluginAction`**
- Пункт меню "MCP Server: Start / Stop"
- `execute()` -- переключает состояние сервера (запуск/остановка)
- Обновляет текст пункта меню в зависимости от состояния

```
Поля:
  - tcpServer: TcpServer
  - running: boolean

Методы:
  + execute(): void
  - updateMenuText(): void
```

#### Пакет `server`

**`TcpServer`**
- Управляет ServerSocket в daemon-потоке
- Принимает подключения и создает ClientHandler для каждого
- Поддерживает корректное завершение (graceful shutdown)

```
Поля:
  - serverSocket: ServerSocket
  - acceptThread: Thread
  - activeClients: List<ClientHandler>  (synchronized)
  - state: AtomicReference<ServerState>
  - commandRegistry: CommandRegistry
  - port: int

Методы:
  + start(): void
  + stop(): void
  + isRunning(): boolean
  + getState(): ServerState
```

**`ClientHandler implements Runnable`**
- Обрабатывает одно TCP-соединение
- Читает строки из InputStream, передает в JsonProtocol, вызывает CommandRegistry
- Пишет JSON-ответы в OutputStream

```
Поля:
  - socket: Socket
  - commandRegistry: CommandRegistry
  - protocol: JsonProtocol

Методы:
  + run(): void
  + close(): void
  - processLine(String line): String
```

**`ServerState (enum)`**
```
STOPPED, STARTING, RUNNING, STOPPING
```

#### Пакет `protocol`

**`JsonProtocol`**
- Статические методы для парсинга запросов и форматирования ответов
- Инкапсулирует всю работу с JSON

```
Методы:
  + parseRequest(String json): Request
  + formatSuccess(Map<String, Object> data): String
  + formatError(String message): String
  + formatResponse(Response response): String
```

**`Request`** (value object)
```
Поля:
  - action: String
  - params: Map<String, Object>

Методы:
  + getAction(): String
  + getParams(): Map<String, Object>
  + getString(String key): String
  + getFloat(String key): float
  + getFloat(String key, float defaultValue): float
```

**`Response`** (value object)
```
Поля:
  - status: String ("ok" | "error")
  - data: Map<String, Object>   (для success)
  - message: String             (для error)

Статические фабрики:
  + ok(Map<String, Object> data): Response
  + error(String message): Response
```

#### Пакет `command`

**`CommandHandler` (интерфейс)**
```java
public interface CommandHandler {
    /**
     * Выполняет команду с указанными параметрами.
     *
     * @param request  распарсенный запрос с параметрами
     * @param accessor потокобезопасный доступ к Home
     * @return ответ с результатом выполнения
     */
    Response execute(Request request, HomeAccessor accessor);
}
```

**`CommandRegistry`**
- Хранит отображение `action name -> CommandHandler`
- Метод `register(name, handler)` для добавления новых команд
- Метод `dispatch(request, accessor)` для маршрутизации

```
Поля:
  - handlers: Map<String, CommandHandler>  (unmodifiable после инициализации)

Методы:
  + register(String action, CommandHandler handler): void
  + dispatch(Request request, HomeAccessor accessor): Response
  + hasHandler(String action): boolean
```

**`PingHandler`** -- возвращает `{status: "ok", version: "0.1.0"}`

**`CreateWallsHandler`** -- создает 4 стены прямоугольником, соединяет углы

**`PlaceFurnitureHandler`** -- ищет мебель в каталоге, размещает в Home

**`GetStateHandler`** -- собирает и возвращает текущее состояние сцены

**`ListFurnitureCatalogHandler`** -- возвращает содержимое каталога мебели с фильтрацией

#### Пакет `bridge`

**`HomeAccessor`**
- Обертка над `Home` и `UserPreferences` (для доступа к каталогу)
- Гарантирует выполнение мутаций в EDT через `SwingUtilities.invokeAndWait`
- Предоставляет read-only доступ к состоянию Home (чтение можно вне EDT,
  но для консистентности также через EDT)

```
Поля:
  - home: Home
  - userPreferences: UserPreferences
  - undoableEditSupport: UndoableEditSupport

Методы:
  + getHome(): Home
  + getUserPreferences(): UserPreferences
  + runOnEDT(Callable<T> task): T        // invokeAndWait + возврат результата
  + getFurnitureCatalog(): FurnitureCatalog
```

---

## 3. TCP-сервер

### 3.1 Модель потоков (Threading Model)

```
+------------------------------------------------------------------+
|                        JVM Sweet Home 3D                         |
|                                                                  |
|  +----------------+                                              |
|  | EDT            | <--- SwingUtilities.invokeAndWait() ----+    |
|  | (Swing thread) |     (мутации Home, чтение состояния)    |    |
|  +----------------+                                         |    |
|                                                             |    |
|  +------------------+                                       |    |
|  | accept-thread    |  daemon=true                          |    |
|  | (TcpServer)      |  ServerSocket.accept() loop           |    |
|  +--------|---------+                                       |    |
|           |                                                 |    |
|           |  new connection                                 |    |
|           v                                                 |    |
|  +------------------+                                       |    |
|  | client-thread-N  |  daemon=true                          |    |
|  | (ClientHandler)  |  readline -> parse -> execute --------+    |
|  +------------------+  <- format <- respond                      |
|                                                                  |
+------------------------------------------------------------------+
```

**Потоки:**

| Поток | Тип | Ответственность |
|-------|-----|----------------|
| **EDT** (Swing) | Основной UI-поток SH3D | Все мутации модели Home, чтение состояния |
| **accept-thread** | daemon | Ожидание входящих TCP-соединений |
| **client-thread-N** | daemon | Обработка команд от конкретного клиента |

Все потоки TCP-сервера и клиентских обработчиков создаются как daemon-потоки. Это
гарантирует, что JVM сможет завершиться, даже если сервер не был явно остановлен
(хотя метод `destroy()` плагина выполняет корректную остановку).

### 3.2 Жизненный цикл соединения

```
                  TcpServer                    ClientHandler
                     |                              |
  start() ---------> |                              |
                     |  state = STARTING            |
                     |  new ServerSocket(port)       |
                     |  state = RUNNING              |
                     |                              |
                     |  accept() (блокирующий)       |
                     |  ............                 |
                     |  <-- new connection           |
                     |                              |
                     |  create ClientHandler ------> |
                     |  add to activeClients         |
                     |  start client-thread -------> | run()
                     |                              |
                     |  accept() (следующий)         | readline() (блокирующий)
                     |                              | <--- JSON request
                     |                              | parse -> dispatch -> execute
                     |                              | ---> JSON response
                     |                              | writeLine()
                     |                              |
                     |                              | readline() (блокирующий)
                     |                              | <--- null (клиент отключился)
                     |                              | close socket
                     |  <-- remove from active      | return (поток завершается)
                     |                              |
  stop() ----------> |                              |
                     |  state = STOPPING            |
                     |  close ServerSocket          |
                     |  for each active client:     |
                     |    client.close()             |
                     |  state = STOPPED             |
                     |                              |
```

### 3.3 Протокол

#### Транспортный уровень
- **Транспорт:** TCP
- **Кодировка:** UTF-8
- **Фрейминг:** построчный, `\n` как разделитель. Одна строка = один JSON-объект.
- **Направление:** запрос-ответ (синхронный). Клиент отправляет строку, ожидает строку-ответ.

#### Формат запроса
```json
{"action": "<command_name>", "params": {<параметры_команды>}}
```

Поле `params` опционально (для команд без параметров, например `ping`).

#### Формат ответа (успех)
```json
{"status": "ok", "data": {<данные_ответа>}}
```

#### Формат ответа (ошибка)
```json
{"status": "error", "message": "<описание_ошибки>"}
```

#### Коды ошибок и обработка

| Ситуация | Ответ |
|----------|-------|
| Невалидный JSON | `{"status": "error", "message": "Invalid JSON: <details>"}` |
| Отсутствует поле `action` | `{"status": "error", "message": "Missing 'action' field"}` |
| Неизвестная команда | `{"status": "error", "message": "Unknown action: <name>"}` |
| Отсутствует обязательный параметр | `{"status": "error", "message": "Missing required parameter: <name>"}` |
| Некорректный тип параметра | `{"status": "error", "message": "Invalid parameter '<name>': expected <type>"}` |
| Мебель не найдена | `{"status": "error", "message": "Furniture not found: <query>"}` |
| Ошибка EDT | `{"status": "error", "message": "Internal error: <details>"}` |
| Пустая строка | Игнорируется (не отправляется ответ) |

#### Ограничения
- Максимальная длина строки запроса: 64 KB (защита от OOM)
- Тайм-аут выполнения команды на EDT: 10 секунд (защита от deadlock)

### 3.4 Корректное завершение (Graceful Shutdown)

Остановка выполняется в следующем порядке:

1. Установить `state = STOPPING`
2. Закрыть `ServerSocket` -- это прерывает `accept()` с `SocketException`
3. Для каждого активного `ClientHandler`:
   - Закрыть его `Socket` -- это прерывает `readLine()` с `SocketException`
   - Обработчик ловит исключение и корректно завершает поток
4. Дождаться завершения `accept-thread` (join с тайм-аутом 5 секунд)
5. Установить `state = STOPPED`

Триггеры остановки:
- Пользователь нажал "Stop" в меню (ServerToggleAction.execute())
- Вызван `Plugin.destroy()` (закрытие Home)
- JVM завершается (daemon-потоки умирают автоматически)

---

## 4. Система команд

### 4.1 Паттерн: Command + Registry

Применяется паттерн **Command** в комбинации с **Registry** (реестр обработчиков).
Каждая команда инкапсулирована в отдельном классе, реализующем интерфейс `CommandHandler`.
Реестр `CommandRegistry` связывает имя команды (строку `action`) с соответствующим
обработчиком.

```
CommandRegistry
  |
  |-- "ping"                    --> PingHandler
  |-- "create_walls"            --> CreateWallsHandler
  |-- "place_furniture"         --> PlaceFurnitureHandler
  |-- "get_state"               --> GetStateHandler
  |-- "list_furniture_catalog"  --> ListFurnitureCatalogHandler
```

### 4.2 Интерфейс CommandHandler

```java
public interface CommandHandler {
    /**
     * Выполняет команду.
     *
     * Метод вызывается в потоке ClientHandler (не в EDT).
     * Для взаимодействия с Home используйте accessor.runOnEDT().
     *
     * @param request  распарсенный запрос (action + params)
     * @param accessor потокобезопасный мост к Sweet Home 3D
     * @return ответ (ok + data, либо error + message)
     */
    Response execute(Request request, HomeAccessor accessor);
}
```

### 4.3 Как добавить новую команду (расширяемость)

Добавление новой команды требует **ровно двух шагов**:

**Шаг 1.** Создать класс, реализующий `CommandHandler`:

```java
public class MyNewHandler implements CommandHandler {
    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Валидация параметров
        String param = request.getString("myParam");
        if (param == null) {
            return Response.error("Missing required parameter: myParam");
        }

        // Выполнение логики (если нужна мутация Home -- через EDT)
        Object result = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            // ... работа с моделью ...
            return someResult;
        });

        // Формирование ответа
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", result);
        return Response.ok(data);
    }
}
```

**Шаг 2.** Зарегистрировать в `CommandRegistry` (в конструкторе `SH3DMcpPlugin`):

```java
registry.register("my_new_command", new MyNewHandler());
```

Других изменений не требуется: протокол, TCP-сервер, JSON-сериализация --
всё работает автоматически.

### 4.4 Обработка ошибок и валидация

Каждый `CommandHandler` отвечает за валидацию своих параметров. При ошибке
обработчик возвращает `Response.error(...)`, а не бросает исключение. Исключения
перехватываются на уровне `ClientHandler.processLine()` как защитная мера.

**Трёхуровневая обработка ошибок:**

```
Уровень 1: Protocol Layer (JsonProtocol)
   - Невалидный JSON
   - Отсутствие поля "action"

Уровень 2: Command Registry (CommandRegistry.dispatch)
   - Неизвестная команда (action не зарегистрирован)

Уровень 3: Command Handler (CommandHandler.execute)
   - Невалидные параметры (тип, диапазон, обязательность)
   - Бизнес-ошибки (мебель не найдена, и т.д.)
   - Ошибки EDT (InterruptedException, исключения в Callable)
```

Каждый уровень возвращает корректный JSON-ответ с `"status": "error"`.
Непредвиденные исключения на любом уровне логируются и оборачиваются
в generic error response.

### 4.5 Спецификация обработчиков команд

#### PingHandler

```
Вход:   (нет параметров)
Выход:  {"status": "ok", "data": {"version": "0.1.0"}}

Логика: Мгновенный ответ, проверка жизнеспособности.
EDT:    Не требуется.
```

#### CreateWallsHandler

```
Вход:   x: float (обязательный)
        y: float (обязательный)
        width: float (обязательный, > 0)
        height: float (обязательный, > 0)
        thickness: float (опциональный, default 10.0)

Выход:  {"status": "ok", "data": {"wallsCreated": 4, "message": "Room 500x400 created"}}

Логика (в EDT):
  1. Рассчитать координаты 4 углов:
     A(x, y)  B(x+width, y)  C(x+width, y+height)  D(x, y+height)
  2. Создать 4 стены:
     w1: A -> B (верхняя)
     w2: B -> C (правая)
     w3: C -> D (нижняя)
     w4: D -> A (левая)
  3. Соединить стены (замкнутый контур):
     w1.setWallAtEnd(w2);  w2.setWallAtStart(w1);
     w2.setWallAtEnd(w3);  w3.setWallAtStart(w2);
     w3.setWallAtEnd(w4);  w4.setWallAtStart(w3);
     w4.setWallAtEnd(w1);  w1.setWallAtStart(w4);
  4. home.addWall(w1..w4)

Валидация: width > 0, height > 0, thickness > 0
```

#### PlaceFurnitureHandler

```
Вход:   name: string (обязательный)
        x: float (обязательный)
        y: float (обязательный)
        angle: float (опциональный, default 0, в градусах)

Выход:  {"status": "ok", "data": {"name": "...", "x": ..., "y": ...,
         "angle": ..., "width": ..., "depth": ..., "height": ...}}

Логика:
  1. Получить FurnitureCatalog из UserPreferences
  2. Итерировать по всем категориям и элементам каталога
  3. Найти первый элемент: name.toLowerCase().contains(query.toLowerCase())
  4. Если не найден --> Response.error("Furniture not found: <query>")
  5. В EDT:
     a. new HomePieceOfFurniture(catalogPiece)
     b. piece.setX(x)
     c. piece.setY(y)
     d. piece.setAngle(Math.toRadians(angle))
     e. home.addPieceOfFurniture(piece)
  6. Вернуть данные размещённой мебели

Валидация: name не пустой
```

#### GetStateHandler

```
Вход:   (нет параметров)
Выход:  {"status": "ok", "data": {
           "wallCount": 4,
           "furniture": [
             {"name": "...", "x": ..., "y": ..., "angle": ...,
              "width": ..., "depth": ..., "height": ...}
           ],
           "roomCount": 0,
           "boundingBox": {"minX": ..., "minY": ..., "maxX": ..., "maxY": ...}
         }}

Логика (в EDT):
  1. home.getWalls().size()
  2. Итерация по home.getFurniture(), сбор атрибутов
  3. home.getRooms().size()
  4. Расчёт bounding box по координатам всех стен и мебели

Валидация: нет (команда без параметров)
```

#### ListFurnitureCatalogHandler

```
Вход:   query: string (опциональный)
        category: string (опциональный)

Выход:  {"status": "ok", "data": {"furniture": [
           {"name": "...", "category": "...", "width": ...,
            "depth": ..., "height": ...}
         ]}}

Логика:
  1. Получить FurnitureCatalog из UserPreferences
  2. Итерировать по категориям/элементам
  3. Фильтрация:
     - Если указан query: name.toLowerCase().contains(query.toLowerCase())
     - Если указан category: category.getName().toLowerCase().contains(cat.toLowerCase())
  4. Собрать массив результатов

Валидация: нет обязательных параметров
EDT: Не требуется (каталог read-only, thread-safe)
```

---

## 5. Интеграция с Sweet Home 3D

### 5.1 Plugin API

Плагин использует стандартный Plugin API Sweet Home 3D:

```java
public class SH3DMcpPlugin extends Plugin {

    private TcpServer tcpServer;

    @Override
    public PluginAction[] getActions() {
        // Plugin предоставляет getHome(), getUserPreferences(),
        // getUndoableEditSupport() через свои final-методы
        HomeAccessor accessor = new HomeAccessor(
            getHome(),
            getUserPreferences(),
            getUndoableEditSupport()
        );

        CommandRegistry registry = createCommandRegistry();
        tcpServer = new TcpServer(PluginConfig.load(), registry, accessor);

        return new PluginAction[] {
            new ServerToggleAction(this, tcpServer)
        };
    }

    @Override
    public void destroy() {
        if (tcpServer != null && tcpServer.isRunning()) {
            tcpServer.stop();
        }
    }

    private CommandRegistry createCommandRegistry() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("ping",                    new PingHandler());
        registry.register("create_walls",            new CreateWallsHandler());
        registry.register("place_furniture",         new PlaceFurnitureHandler());
        registry.register("get_state",               new GetStateHandler());
        registry.register("list_furniture_catalog",  new ListFurnitureCatalogHandler());
        return registry;
    }
}
```

**Метаданные плагина** (`ApplicationPlugin.properties` в корне JAR):

```properties
name=SH3D MCP Plugin
class=com.eteks.sweethome3d.mcp.plugin.SH3DMcpPlugin
description=TCP server for Model Context Protocol integration
version=0.1.0
license=MIT
provider=SH3D MCP Project
applicationMinimumVersion=6.0
javaMinimumVersion=11
```

### 5.2 EDT -- безопасная модификация модели

Все объекты модели Sweet Home 3D (Home, Wall, HomePieceOfFurniture) привязаны к EDT.
Любая мутация извне EDT приведет к непредсказуемому поведению UI.

**HomeAccessor.runOnEDT()** -- центральный метод для безопасного доступа:

```java
public <T> T runOnEDT(Callable<T> task) throws CommandException {
    if (SwingUtilities.isEventDispatchThread()) {
        // Уже в EDT (теоретически не должно происходить из TCP-потока)
        try {
            return task.call();
        } catch (Exception e) {
            throw new CommandException("EDT execution failed: " + e.getMessage(), e);
        }
    }

    AtomicReference<T> resultRef = new AtomicReference<>();
    AtomicReference<Exception> errorRef = new AtomicReference<>();

    try {
        SwingUtilities.invokeAndWait(() -> {
            try {
                resultRef.set(task.call());
            } catch (Exception e) {
                errorRef.set(e);
            }
        });
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CommandException("EDT execution interrupted", e);
    } catch (InvocationTargetException e) {
        throw new CommandException("EDT invocation failed: " + e.getCause().getMessage(),
                                   e.getCause());
    }

    if (errorRef.get() != null) {
        throw new CommandException("Command failed: " + errorRef.get().getMessage(),
                                   errorRef.get());
    }

    return resultRef.get();
}
```

**Почему `invokeAndWait`, а не `invokeLater`:**
- Нужен синхронный ответ: TCP-клиент ждёт результат
- Нужно вернуть данные из EDT (например, ID созданных стен, состояние сцены)
- Нужно поймать исключения, возникшие в EDT, и передать их обратно в TCP-поток

**Защита от deadlock:**
- TCP-потоки всегда daemon; если EDT зависнет, JVM всё равно завершится
- Проверка `SwingUtilities.isEventDispatchThread()` перед вызовом (защита от nested invokeAndWait)
- В будущем можно добавить тайм-аут через `ExecutorService` + `Future.get(timeout)`

### 5.3 Доступ к объектам Sweet Home 3D

| Что нужно | Как получить | Где используется |
|-----------|-------------|-----------------|
| `Home` | `Plugin.getHome()` -> `HomeAccessor` | CreateWalls, PlaceFurniture, GetState |
| `FurnitureCatalog` | `Plugin.getUserPreferences().getFurnitureCatalog()` | PlaceFurniture, ListCatalog |
| `UndoableEditSupport` | `Plugin.getUndoableEditSupport()` | Пока не используется (MVP), но нужен для будущей поддержки Undo |
| `HomeController` | `Plugin.getHomeController()` (v3.5+) | Пока не используется (MVP) |
| Высота стен по умолчанию | `Home.getWallHeight()` | CreateWalls (для высоты стены) |

---

## 6. JSON-обработка

### 6.1 Выбор: встроенный ручной парсер

**Решение:** Минимальный ручной JSON-парсер/форматтер, реализованный в классе
`JsonProtocol`. Не используется ни minimal-json, ни другие внешние библиотеки.

**Обоснование:**

| Критерий | Ручной парсер | minimal-json | Gson/Jackson |
|----------|--------------|-------------|-------------|
| Внешние зависимости | 0 | 1 (~30KB) | 1 (>200KB) |
| Classpath-конфликты | Невозможны | Маловероятны | Возможны |
| Сложность нашего JSON | Очень низкая | - | - |
| Контроль над форматом | Полный | Достаточный | Избыточный |
| Размер JAR | Минимальный | +30KB | +200KB+ |

**Ключевой аргумент:** наш протокол оперирует плоским JSON с заранее известной
структурой. Нет вложенных объектов произвольной глубины, нет массивов объектов
произвольной формы. Всё предсказуемо:

Входящие запросы -- фиксированная структура `{"action": "...", "params": {...}}`,
где params содержит только скалярные значения (строки и числа).

Исходящие ответы -- формируются программно, мы контролируем формат полностью.

Самая сложная структура -- `get_state` и `list_furniture_catalog` с массивом объектов.
Это обрабатывается StringBuilder-ом с экранированием строк.

### 6.2 Реализация JsonProtocol

**Парсинг запросов (минимальный токенизатор):**

```
Поддерживаемые типы:
  - String:  "..."  (с экранированием \", \\, \n, \t, \uXXXX)
  - Number:  целые и дробные (int / float / double)
  - Boolean: true / false
  - Null:    null
  - Object:  { "key": value, ... }
  - Array:   [ value, ... ]  (только для исходящих ответов)
```

**Форматирование ответов (StringBuilder):**

Ответы формируются программно -- не нужен полноценный JSON-маршаллер. Каждый
CommandHandler создаёт `Map<String, Object>`, а `JsonProtocol.formatResponse()`
рекурсивно сериализует значения в JSON-строку.

### 6.3 Альтернативный вариант (fallback)

Если в процессе реализации окажется, что ручной парсер слишком трудоемок или
подвержен ошибкам, допустимо встроить (shade) библиотеку **minimal-json**
(com.eclipsesource.minimal-json, MIT License, ~30KB) в JAR через Maven Shade Plugin
с перепакетированием (relocation) в пространство `com.eteks.sweethome3d.mcp.internal.json`.
Это исключает конфликты с classpath SH3D.

---

## 7. Конфигурация

### 7.1 Настраиваемые параметры

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| `port` | int | 9877 | TCP-порт сервера |
| `maxLineLength` | int | 65536 | Максимальная длина строки запроса (байт) |
| `edtTimeout` | int | 10000 | Тайм-аут выполнения команды в EDT (мс) |
| `autoStart` | boolean | false | Автоматический запуск сервера при загрузке плагина |

### 7.2 Хранение настроек

Настройки хранятся в файле `sh3d-mcp.properties` в папке плагинов пользователя:
- Windows: `%APPDATA%\eTeks\Sweet Home 3D\plugins\sh3d-mcp.properties`
- Linux/macOS: `~/.eteks/sweethome3d/plugins/sh3d-mcp.properties`

**Формат файла:**
```properties
# SH3D MCP Plugin Configuration
port=9877
autoStart=false
```

**Поиск конфигурации (приоритет):**

1. Системные свойства Java: `-Dsh3d.mcp.port=9878` (наивысший приоритет)
2. Файл `sh3d-mcp.properties` рядом с JAR плагина
3. Значения по умолчанию (зашиты в `PluginConfig`)

```java
public class PluginConfig {
    private static final int DEFAULT_PORT = 9877;
    private static final int DEFAULT_MAX_LINE_LENGTH = 65536;
    private static final int DEFAULT_EDT_TIMEOUT = 10000;
    private static final boolean DEFAULT_AUTO_START = false;

    private final int port;
    private final int maxLineLength;
    private final int edtTimeout;
    private final boolean autoStart;

    public static PluginConfig load() {
        // 1. System properties
        // 2. Properties file
        // 3. Defaults
    }
}
```

---

## 8. Структура проекта (Maven)

### 8.1 Структура директорий

```
sh3d-mcp-plugin/
|
|-- pom.xml
|
|-- src/
|   |-- main/
|   |   |-- java/
|   |   |   |-- com/eteks/sweethome3d/mcp/
|   |   |       |-- plugin/
|   |   |       |   |-- SH3DMcpPlugin.java
|   |   |       |   |-- ServerToggleAction.java
|   |   |       |
|   |   |       |-- server/
|   |   |       |   |-- TcpServer.java
|   |   |       |   |-- ClientHandler.java
|   |   |       |   |-- ServerState.java
|   |   |       |
|   |   |       |-- protocol/
|   |   |       |   |-- JsonProtocol.java
|   |   |       |   |-- Request.java
|   |   |       |   |-- Response.java
|   |   |       |
|   |   |       |-- command/
|   |   |       |   |-- CommandHandler.java
|   |   |       |   |-- CommandRegistry.java
|   |   |       |   |-- CommandException.java
|   |   |       |   |-- PingHandler.java
|   |   |       |   |-- CreateWallsHandler.java
|   |   |       |   |-- PlaceFurnitureHandler.java
|   |   |       |   |-- GetStateHandler.java
|   |   |       |   |-- ListFurnitureCatalogHandler.java
|   |   |       |
|   |   |       |-- bridge/
|   |   |       |   |-- HomeAccessor.java
|   |   |       |
|   |   |       |-- config/
|   |   |           |-- PluginConfig.java
|   |   |
|   |   |-- resources/
|   |       |-- ApplicationPlugin.properties
|   |       |-- com/eteks/sweethome3d/mcp/plugin/
|   |           |-- ServerToggleAction.properties
|   |
|   |-- test/
|       |-- java/
|           |-- com/eteks/sweethome3d/mcp/
|               |-- protocol/
|               |   |-- JsonProtocolTest.java
|               |   |-- RequestTest.java
|               |   |-- ResponseTest.java
|               |
|               |-- command/
|               |   |-- CommandRegistryTest.java
|               |   |-- PingHandlerTest.java
|               |
|               |-- server/
|                   |-- TcpServerTest.java (интеграционный)
|
|-- ARCHITECTURE.md
|-- PRD.md
|-- sh3d-plugin-spec.md
```

### 8.2 Maven POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.eteks.sweethome3d</groupId>
    <artifactId>sh3d-mcp-plugin</artifactId>
    <version>0.1.0</version>
    <packaging>jar</packaging>

    <name>Sweet Home 3D MCP Plugin</name>
    <description>TCP server plugin for MCP integration with Sweet Home 3D</description>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <sh3d.version>7.5</sh3d.version>
    </properties>

    <dependencies>
        <!-- Sweet Home 3D (provided -- уже в classpath при запуске) -->
        <dependency>
            <groupId>com.eteks</groupId>
            <artifactId>sweethome3d</artifactId>
            <version>${sh3d.version}</version>
            <scope>provided</scope>
            <systemPath>${project.basedir}/lib/SweetHome3D.jar</systemPath>
            <type>jar</type>
        </dependency>

        <!-- Тесты -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <version>5.11.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Compiler: Java 11 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>11</source>
                    <target>11</target>
                </configuration>
            </plugin>

            <!-- JAR: clean MANIFEST.MF (SH3D discovers plugin via ApplicationPlugin.properties) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.1</version>
            </plugin>

            <!-- Copy .jar to .sh3p (Sweet Home 3D plugin format) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-antrun-plugin</artifactId>
                <version>3.1.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>run</goal></goals>
                        <configuration>
                            <target>
                                <copy file="${project.build.directory}/${project.build.finalName}.jar"
                                      tofile="${project.build.directory}/${project.build.finalName}.sh3p"/>
                            </target>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <!-- Тесты -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

**Примечание по зависимости SweetHome3D.jar:**

SH3D не публикуется в Maven Central. JAR нужно взять из установки SH3D и положить
в `lib/SweetHome3D.jar` проекта. Альтернатива -- установить в локальный Maven-репозиторий:

```bash
mvn install:install-file -Dfile=/path/to/SweetHome3D.jar \
    -DgroupId=com.eteks -DartifactId=sweethome3d \
    -Dversion=7.5 -Dpackaging=jar
```

После этого убрать `<systemPath>` из POM и использовать обычный `<scope>provided</scope>`.

### 8.3 Сборка и деплой

```bash
# Сборка
mvn clean package

# Результат
target/sh3d-mcp-plugin-0.1.0.jar

# Деплой (Windows)
copy target\sh3d-mcp-plugin-0.1.0.jar "%APPDATA%\eTeks\Sweet Home 3D\plugins\"

# Деплой (Linux/macOS)
cp target/sh3d-mcp-plugin-0.1.0.jar ~/.eteks/sweethome3d/plugins/
```

---

## 9. Диаграммы последовательности

### 9.1 Успешный сценарий: create_walls

```
TCP Client          ClientHandler       JsonProtocol     CommandRegistry   CreateWallsHandler    HomeAccessor          EDT
    |                     |                  |                |                   |                   |                 |
    |-- JSON request ---->|                  |                |                   |                   |                 |
    |  {"action":         |                  |                |                   |                   |                 |
    |   "create_walls",   |                  |                |                   |                   |                 |
    |   "params":{        |                  |                |                   |                   |                 |
    |    "x":0,"y":0,     |                  |                |                   |                   |                 |
    |    "width":500,     |                  |                |                   |                   |                 |
    |    "height":400}}   |                  |                |                   |                   |                 |
    |                     |                  |                |                   |                   |                 |
    |                     |-- parseRequest ->|                |                   |                   |                 |
    |                     |<- Request -------|                |                   |                   |                 |
    |                     |                  |                |                   |                   |                 |
    |                     |-- dispatch(req, accessor) ------->|                   |                   |                 |
    |                     |                  |                |-- execute(req, accessor) ------------>|                 |
    |                     |                  |                |                   |                   |                 |
    |                     |                  |                |                   |-- validate params |                 |
    |                     |                  |                |                   |   x=0, y=0,       |                 |
    |                     |                  |                |                   |   w=500, h=400,   |                 |
    |                     |                  |                |                   |   t=10 (default)  |                 |
    |                     |                  |                |                   |                   |                 |
    |                     |                  |                |                   |-- runOnEDT(() -> {                  |
    |                     |                  |                |                   |    create 4 walls  |                 |
    |                     |                  |                |                   |    connect walls   |                 |
    |                     |                  |                |                   |    home.addWall()  |                 |
    |                     |                  |                |                   |   }) ------------>|                 |
    |                     |                  |                |                   |                   |                 |
    |                     |                  |                |                   |                   |-- invokeAndWait -+
    |                     |                  |                |                   |                   |                 |
    |                     |                  |                |                   |                   |  Wall w1(0,0 -> 500,0)
    |                     |                  |                |                   |                   |  Wall w2(500,0 -> 500,400)
    |                     |                  |                |                   |                   |  Wall w3(500,400 -> 0,400)
    |                     |                  |                |                   |                   |  Wall w4(0,400 -> 0,0)
    |                     |                  |                |                   |                   |  connect w1<->w2<->w3<->w4<->w1
    |                     |                  |                |                   |                   |  home.addWall(w1..w4)
    |                     |                  |                |                   |                   |                 |
    |                     |                  |                |                   |                   |<- return -------+
    |                     |                  |                |                   |<- result ---------|                 |
    |                     |                  |                |                   |                   |                 |
    |                     |                  |                |<- Response.ok({wallsCreated:4, ...}) -|                 |
    |                     |                  |                |                   |                   |                 |
    |                     |<- Response ------|                |                   |                   |                 |
    |                     |                  |                |                   |                   |                 |
    |                     |-- formatResponse(resp) --------->|                   |                   |                 |
    |                     |<- JSON string ---|                |                   |                   |                 |
    |                     |                  |                |                   |                   |                 |
    |<- JSON response ----|                  |                |                   |                   |                 |
    |  {"status":"ok",    |                  |                |                   |                   |                 |
    |   "data":{          |                  |                |                   |                   |                 |
    |    "wallsCreated":4,|                  |                |                   |                   |                 |
    |    "message":       |                  |                |                   |                   |                 |
    |    "Room 500x400    |                  |                |                   |                   |                 |
    |     created"}}      |                  |                |                   |                   |                 |
    |                     |                  |                |                   |                   |                 |
```

### 9.2 Сценарий с ошибкой: place_furniture (мебель не найдена)

```
TCP Client          ClientHandler       JsonProtocol     CommandRegistry   PlaceFurnitureHandler   HomeAccessor
    |                     |                  |                |                   |                   |
    |-- JSON request ---->|                  |                |                   |                   |
    |  {"action":         |                  |                |                   |                   |
    |   "place_furniture",|                  |                |                   |                   |
    |   "params":{        |                  |                |                   |                   |
    |    "name":"xyz123", |                  |                |                   |                   |
    |    "x":100,"y":200}}|                  |                |                   |                   |
    |                     |                  |                |                   |                   |
    |                     |-- parseRequest ->|                |                   |                   |
    |                     |<- Request -------|                |                   |                   |
    |                     |                  |                |                   |                   |
    |                     |-- dispatch(req, accessor) ------->|                   |                   |
    |                     |                  |                |-- execute ------->|                   |
    |                     |                  |                |                   |                   |
    |                     |                  |                |                   |-- getCatalog() -->|
    |                     |                  |                |                   |<- catalog --------|
    |                     |                  |                |                   |                   |
    |                     |                  |                |                   |-- search "xyz123" |
    |                     |                  |                |                   |   iterate categories
    |                     |                  |                |                   |   iterate pieces  |
    |                     |                  |                |                   |   NO MATCH FOUND  |
    |                     |                  |                |                   |                   |
    |                     |                  |                |<- Response.error("Furniture not found: xyz123")
    |                     |                  |                |                   |                   |
    |                     |<- Response ------|                |                   |                   |
    |                     |-- formatResponse --------->       |                   |                   |
    |                     |<- JSON string ---|                |                   |                   |
    |                     |                  |                |                   |                   |
    |<- JSON response ----|                  |                |                   |                   |
    |  {"status":"error", |                  |                |                   |                   |
    |   "message":        |                  |                |                   |                   |
    |   "Furniture not    |                  |                |                   |                   |
    |    found: xyz123"}  |                  |                |                   |                   |
    |                     |                  |                |                   |                   |
```

### 9.3 Сценарий: невалидный JSON

```
TCP Client          ClientHandler       JsonProtocol
    |                     |                  |
    |-- "not a json" ---->|                  |
    |                     |-- parseRequest ->|
    |                     |   throws         |
    |                     |   ParseException |
    |                     |                  |
    |                     |-- formatError("Invalid JSON: ...") -->|
    |                     |<- JSON string ---|
    |                     |                  |
    |<- JSON response ----|
    |  {"status":"error", |
    |   "message":        |
    |   "Invalid JSON:    |
    |    Unexpected char   |
    |    at pos 0"}       |
    |                     |
```

### 9.4 Сценарий: подключение и отключение клиента

```
TCP Client             TcpServer              ClientHandler
    |                       |                       |
    |-- TCP connect ------->|                       |
    |                       |  accept()             |
    |                       |  create handler ----->|
    |                       |  add to active list   |
    |                       |  start thread ------->| run()
    |                       |                       |
    |-- "{"action":"ping"}" ----------------------->|
    |<-- "{"status":"ok"..}" ----------------------|
    |                       |                       |
    |-- TCP close (FIN) --->|                       |
    |                       |                       | readLine() -> null
    |                       |                       | close socket
    |                       |<- remove from active -|
    |                       |                       | thread exits
    |                       |                       |
```

---

## 10. Архитектурные решения (ADR)

### ADR-001: Построчный текстовый протокол поверх TCP

**Статус:** Принято

**Контекст:**
Плагин должен принимать команды от внешнего MCP-сервера. Необходимо выбрать
транспорт и формат фрейминга.

**Варианты:**
1. HTTP-сервер (REST API)
2. WebSocket
3. TCP с построчным JSON
4. TCP с length-prefix framing

**Решение:** TCP с построчным JSON (вариант 3)

**Обоснование:**
- HTTP-сервер (Jetty/Netty) потребовал бы массивную внешнюю зависимость (~2MB+),
  что прямо противоречит требованию "без внешних зависимостей". Реализация HTTP/1.1
  с нуля нецелесообразна.
- WebSocket имеет те же проблемы, что и HTTP, плюс дополнительная сложность протокола.
- Length-prefix framing надёжнее (нет проблем с переносами строк внутри данных), но
  сложнее в отладке (нельзя просто подключиться telnet/netcat для тестирования).
- Построчный JSON -- самый простой вариант, прекрасно отлаживается через netcat,
  достаточен для нашего протокола (JSON по спецификации не содержит сырых переносов
  строк -- они должны быть экранированы как `\n`).

**Последствия:**
- (+) Предельная простота реализации (~50 строк на сервер)
- (+) Лёгкая отладка через telnet/netcat
- (+) Нулевые внешние зависимости
- (-) Если в JSON попадёт неэкранированный перенос строки -- протокол сломается
  (митигация: наш JSON формируется программно, переносы всегда экранируются)
- (-) Нет встроенного механизма keepalive (митигация: можно использовать ping)

---

### ADR-002: Command Pattern с Registry для обработки команд

**Статус:** Принято

**Контекст:**
Нужна расширяемая архитектура для обработки разных типов команд. В MVP -- 5 команд,
но в будущем их количество может вырасти.

**Варианты:**
1. Один большой switch/case в ClientHandler
2. Command Pattern + Registry (Map<String, CommandHandler>)
3. Reflection-based dispatch (аннотации на методах)

**Решение:** Command Pattern + Registry (вариант 2)

**Обоснование:**
- switch/case плохо масштабируется: при 20+ командах один файл становится
  нечитаемым, нарушается SRP
- Reflection чрезмерно сложен для наших нужд и снижает предсказуемость поведения
- Registry + интерфейс -- проверенный паттерн, каждая команда изолирована
  в своём классе, добавление новых команд не требует модификации существующего кода
  (Open/Closed Principle)

**Последствия:**
- (+) Каждая команда в отдельном классе -- легко тестировать
- (+) Добавление новой команды = 1 новый класс + 1 строка регистрации
- (+) Легко вынести часть команд в отдельные модули/JAR при росте проекта
- (-) Чуть больше классов, чем при switch/case (незначительно для 5 команд)

---

### ADR-003: SwingUtilities.invokeAndWait для доступа к модели

**Статус:** Принято

**Контекст:**
TCP-сервер работает в отдельных потоках, а все объекты модели SH3D привязаны к EDT.
Нужно безопасно модифицировать и читать модель из TCP-потоков.

**Варианты:**
1. `SwingUtilities.invokeAndWait()` -- блокирующий вызов в EDT
2. `SwingUtilities.invokeLater()` -- асинхронный вызов в EDT
3. Очередь задач с пулом потоков и Future

**Решение:** `SwingUtilities.invokeAndWait()` (вариант 1)

**Обоснование:**
- `invokeLater` не подходит: нам нужен синхронный ответ для TCP-клиента, нужно
  вернуть данные из EDT и узнать, была ли ошибка
- Очередь задач с Future -- по сути то же самое, что invokeAndWait, но с
  дополнительной инфраструктурой, которая не оправдана для нашего случая
- invokeAndWait -- стандартный паттерн Swing для межпоточного взаимодействия

**Последствия:**
- (+) Гарантированная thread-safety
- (+) Синхронный результат -- идеально для request-response протокола
- (+) Стандартный Swing-паттерн, хорошо документирован
- (-) Потенциальный deadlock, если EDT сам вызовет invokeAndWait (митигация:
  проверка isEventDispatchThread)
- (-) Блокировка TCP-потока на время выполнения в EDT (приемлемо, т.к. операции
  быстрые, < 200 мс)

---

### ADR-004: Ручной JSON-парсер вместо внешней библиотеки

**Статус:** Принято

**Контекст:**
Нужно парсить входящие JSON-запросы и формировать JSON-ответы. По требованиям --
без внешних зависимостей (допустима minimal-json как fallback).

**Варианты:**
1. Встроить minimal-json (~30KB) через Maven Shade Plugin
2. Ручной минимальный парсер
3. Использовать javax.json (Java EE, нет в SE)

**Решение:** Ручной минимальный парсер (вариант 2) с fallback на minimal-json (вариант 1)

**Обоснование:**
- Наш JSON предельно прост: плоские объекты с примитивными значениями (строки, числа).
  Вложенность не превышает 2 уровней.
- Ручной парсер для такого JSON -- 200-300 строк кода, что вполне управляемо
- Нулевые внешние зависимости, нулевой риск classpath-конфликтов
- javax.json не доступен в стандартном Java SE 11

Если ручной парсер окажется ненадёжен на edge-case (Unicode, экранирование), будет
использован fallback -- minimal-json с shading + relocation.

**Последствия:**
- (+) Ноль зависимостей
- (+) Полный контроль над парсингом и форматированием
- (+) Минимальный размер JAR
- (-) Риск ошибок парсинга на нестандартном JSON (митигация: тесты + fallback)
- (-) Поддержка собственного парсера (митигация: JSON-формат фиксирован протоколом)

---

### ADR-005: Один поток на клиентское соединение (Thread-per-Connection)

**Статус:** Принято

**Контекст:**
Нужно определить модель обработки TCP-соединений.

**Варианты:**
1. Thread-per-connection (новый поток на каждое соединение)
2. NIO с Selector (один поток на все соединения)
3. Thread pool (ExecutorService)

**Решение:** Thread-per-connection (вариант 1)

**Обоснование:**
- MVP предполагает 1-2 одновременных клиента (MCP-сервер). Даже в перспективе
  маловероятно более 5 одновременных подключений.
- Thread-per-connection -- предельно прост в реализации и отладке
- NIO (Selector) -- оправдан при тысячах соединений, здесь -- чрезмерная сложность
- Thread pool (ExecutorService) -- хорошее промежуточное решение, но для 1-2
  соединений не оправдывает дополнительной сложности

**Последствия:**
- (+) Предельная простота кода
- (+) Каждый клиент полностью изолирован
- (+) Легко отлаживать (один поток = одна сессия)
- (-) Не масштабируется на сотни клиентов (неактуально для нашего сценария)
- (-) Потенциальная утечка потоков при некорректном закрытии (митигация: daemon-потоки
  + explicit close в finally-блоке)

---

### ADR-006: Пакет com.eteks.sweethome3d.mcp как корневой namespace

**Статус:** Принято

**Контекст:**
Нужно определить Java-пакет для классов плагина.

**Варианты:**
1. `com.eteks.sweethome3d.mcp` -- внутри namespace SH3D
2. `com.github.<user>.sh3dmcp` -- собственный namespace
3. `sh3dmcp` -- короткий, без domain prefix

**Решение:** `com.eteks.sweethome3d.mcp` (вариант 1)

**Обоснование:**
- Плагин является расширением SH3D и работает исключительно внутри его JVM
- Использование namespace `com.eteks.sweethome3d` сигнализирует, что это плагин
  для данного приложения, а не самостоятельная библиотека
- Упрощает доступ к package-private классам SH3D, если потребуется (хотя в MVP
  мы используем только public API)
- Вариант 3 нарушает Java naming conventions

**Последствия:**
- (+) Очевидная принадлежность к экосистеме SH3D
- (-) Формально это namespace проекта eTeks (не наш домен), но для плагинов
  это общепринятая практика

---

### ADR-007: ApplicationPlugin.properties как единственный механизм обнаружения

**Статус:** Обновлено (ранее: Принято)

**Контекст:**
Sweet Home 3D загружает плагины через файл `ApplicationPlugin.properties`,
который `PluginManager` находит сканированием ZIP-записей архива. Анализ
декомпилированного `PluginManager` из SH3D 7.5 и реального плагина
`PhotoVideoRendering.sh3p` подтвердил: `MANIFEST.MF` не используется для
обнаружения плагинов. Атрибут `Plugin-Class` в манифесте избыточен.

**Решение:** Использовать только `ApplicationPlugin.properties`. Не дублировать
`Plugin-Class` в MANIFEST.MF. Выходной файл -- `.sh3p` (стандартное расширение).

**Обоснование:**
- `PluginManager.loadPlugins()` сканирует ZIP-записи по `lastIndexOf("ApplicationPlugin.properties")`
- Реальные плагины SH3D (PhotoVideoRendering.sh3p) имеют чистый MANIFEST.MF без `Plugin-Class`
- Наш `applicationMinimumVersion=6.0` делает совместимость со старыми версиями неактуальной

**Последствия:**
- (+) Единственный источник истины для метаданных плагина
- (+) Соответствие реальным конвенциям экосистемы SH3D
- (+) Формат `.sh3p` поддерживает установку двойным кликом (SH3D 1.6+)

---

### ADR-008: Конфигурация через properties-файл и System properties

**Статус:** Принято

**Контекст:**
Плагин имеет настраиваемые параметры (как минимум порт TCP-сервера). Нужно
определить, где и как хранить конфигурацию.

**Варианты:**
1. Только hardcoded defaults (без конфигурации)
2. Properties-файл в папке плагинов
3. GUI-диалог настроек (Swing)
4. UserPreferences SH3D

**Решение:** Properties-файл + System properties (варианты 2 + override через JVM args)

**Обоснование:**
- Hardcoded defaults недостаточны: порт может быть занят
- GUI-диалог -- чрезмерно для MVP, где параметров всего 2-3
- UserPreferences SH3D -- нестандартное использование, может вызвать конфликты
- Properties-файл -- стандартный Java-подход, легко редактируется пользователем
- System properties позволяют переопределить конфигурацию без файла (удобно для
  автоматизированного тестирования и CI)

**Последствия:**
- (+) Простота: один файл `sh3d-mcp.properties`
- (+) Гибкость: override через `-Dsh3d.mcp.port=XXXX`
- (+) Не требует GUI
- (-) Пользователь должен вручную создать/отредактировать файл (митигация:
  в будущем можно добавить GUI-диалог; defaults работают без файла)

---

## Приложение A: Координатная система

```
     X axis (вправо) -->
   0 +----------------------------->
     |
     |   (x, y) - верхний левый угол
     |   +------- width -------+
     |   |                     |
     | h |                     |
     | e |      Комната        |
     | i |                     |
     | g |                     |
     | h |                     |
     | t +---------------------+
     |
     v
   Y axis (вниз)

   Единицы: сантиметры (500 = 5 метров)
```

## Приложение B: Пример сессии (telnet)

```
$ telnet localhost 9877

> {"action": "ping"}
< {"status": "ok", "data": {"version": "0.1.0"}}

> {"action": "create_walls", "params": {"x": 0, "y": 0, "width": 500, "height": 400}}
< {"status": "ok", "data": {"wallsCreated": 4, "message": "Room 500x400 created"}}

> {"action": "list_furniture_catalog", "params": {"query": "table"}}
< {"status": "ok", "data": {"furniture": [{"name": "Table", "category": "Living room", "width": 120.0, "depth": 80.0, "height": 75.0}, ...]}}

> {"action": "place_furniture", "params": {"name": "Table", "x": 250, "y": 200, "angle": 45}}
< {"status": "ok", "data": {"name": "Table", "x": 250.0, "y": 200.0, "angle": 45.0, "width": 120.0, "depth": 80.0, "height": 75.0}}

> {"action": "get_state"}
< {"status": "ok", "data": {"wallCount": 4, "furniture": [{"name": "Table", "x": 250.0, "y": 200.0, "angle": 45.0, "width": 120.0, "depth": 80.0, "height": 75.0}], "roomCount": 0, "boundingBox": {"minX": 0.0, "minY": 0.0, "maxX": 500.0, "maxY": 400.0}}}

> {"action": "unknown_command"}
< {"status": "error", "message": "Unknown action: unknown_command"}

> not a json
< {"status": "error", "message": "Invalid JSON: Unexpected character 'n' at position 0"}
```

## Приложение C: Порядок реализации

Рекомендуемый порядок реализации классов (от фундамента к фичам):

```
Фаза 1: Инфраструктура
  1. PluginConfig              -- конфигурация
  2. Request, Response         -- value objects протокола
  3. JsonProtocol              -- парсинг/форматирование JSON
  4. CommandHandler            -- интерфейс
  5. CommandRegistry           -- реестр команд
  6. CommandException          -- исключение команд
  7. PingHandler               -- первая команда

Фаза 2: TCP-сервер
  8. ServerState               -- enum состояний
  9. ClientHandler             -- обработка соединения
  10. TcpServer                -- accept loop

Фаза 3: Интеграция с SH3D
  11. HomeAccessor             -- мост к Home через EDT
  12. SH3DMcpPlugin            -- точка входа плагина
  13. ServerToggleAction       -- пункт меню
  14. ApplicationPlugin.properties -- метаданные

Фаза 4: Команды
  15. CreateWallsHandler       -- создание стен
  16. GetStateHandler          -- получение состояния
  17. PlaceFurnitureHandler    -- размещение мебели
  18. ListFurnitureCatalogHandler -- каталог мебели

Фаза 5: Тесты и сборка
  19. Unit-тесты (JsonProtocol, Registry, Handlers)
  20. Интеграционный тест (TcpServer + real socket)
  21. Maven POM + сборка JAR
  22. Ручное тестирование в SH3D
```
