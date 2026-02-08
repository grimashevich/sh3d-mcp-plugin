<objective>
Сгенерировать полную структуру Maven-проекта для Sweet Home 3D MCP Plugin.
Создать все файлы: pom.xml, все Java-классы (заглушки с TODO), тесты, ресурсы, README.
Проект должен компилироваться командой `mvn compile` (без SweetHome3D.jar — он provided).
</objective>

<context>
Проект: TCP-плагин для Sweet Home 3D, принимающий JSON-команды для управления сценой.
Подробная архитектура описана в файле `ARCHITECTURE.md` — **обязательно прочитай его перед началом работы**.
Спецификация протокола в `sh3d-plugin-spec.md`.

Технологии: Java 11, Maven, JUnit 5. Без внешних зависимостей кроме SweetHome3D.jar (provided).
</context>

<requirements>

## 1. Maven POM (`pom.xml`)

Создай `pom.xml` в корне проекта (`./pom.xml`) со следующей конфигурацией:

- **groupId:** `com.sh3d.mcp`
- **artifactId:** `sh3d-mcp-plugin`
- **version:** `0.1.0-SNAPSHOT`
- **packaging:** `jar`

### Зависимости:
- `SweetHome3D.jar` — **scope: system**, systemPath: `${project.basedir}/lib/SweetHome3D.jar`
- `junit-jupiter` 5.10.2 — scope: test
- `mockito-core` 5.11.0 — scope: test

### Плагины:
- `maven-compiler-plugin` 3.13.0 — source/target: 11, encoding: UTF-8
- `maven-jar-plugin` 3.4.1 — MANIFEST.MF с атрибутом `Plugin-Class: com.sh3d.mcp.plugin.SH3DMcpPlugin`
- `maven-surefire-plugin` 3.2.5

### Properties:
- `maven.compiler.source` = 11
- `maven.compiler.target` = 11
- `project.build.sourceEncoding` = UTF-8

## 2. Java-классы (заглушки)

Все классы создаются как заглушки: правильная структура, сигнатуры методов, поля, импорты, Javadoc с TODO-комментариями. Тела методов — минимальные (return null / return new Object[0] / throw UnsupportedOperationException("TODO")).

**ВАЖНО:** Прочитай `ARCHITECTURE.md` для точных сигнатур, полей и контрактов каждого класса. Ниже — перечень классов и пакетов.

### Пакет `com.sh3d.mcp.plugin`

**`SH3DMcpPlugin.java`** — extends `com.eteks.sweethome3d.plugin.Plugin`
- Поля: `TcpServer tcpServer`, `PluginConfig config`
- Методы: `getActions(): PluginAction[]`, `destroy(): void`
- Приватный метод: `createCommandRegistry(): CommandRegistry`
- В `getActions()` — создание HomeAccessor, CommandRegistry, TcpServer, ServerToggleAction
- В `destroy()` — остановка tcpServer

**`ServerToggleAction.java`** — extends `com.eteks.sweethome3d.plugin.PluginAction`
- Поля: `TcpServer tcpServer`, `boolean running`
- Методы: `execute(): void` (toggle start/stop), приватный `updateMenuText(): void`
- Конструктор принимает `Plugin plugin, TcpServer tcpServer`
- Вызывает `putPropertyValue(Property.NAME, "MCP Server: Start")` в конструкторе

### Пакет `com.sh3d.mcp.server`

**`ServerState.java`** — enum: `STOPPED, STARTING, RUNNING, STOPPING`

**`TcpServer.java`**
- Поля: `ServerSocket serverSocket`, `Thread acceptThread`, `List<ClientHandler> activeClients` (synchronized), `AtomicReference<ServerState> state`, `CommandRegistry commandRegistry`, `int port`
- Методы: `start(): void`, `stop(): void`, `isRunning(): boolean`, `getState(): ServerState`
- Конструктор: `TcpServer(PluginConfig config, CommandRegistry registry, HomeAccessor accessor)`

**`ClientHandler.java`** — implements `Runnable`
- Поля: `Socket socket`, `CommandRegistry commandRegistry`, `HomeAccessor accessor`
- Методы: `run(): void`, `close(): void`, приватный `processLine(String line): String`

### Пакет `com.sh3d.mcp.protocol`

**`Request.java`** — value object
- Поля: `String action`, `Map<String, Object> params`
- Методы: `getAction(): String`, `getParams(): Map`, `getString(String key): String`, `getFloat(String key): float`, `getFloat(String key, float defaultValue): float`
- Конструктор: `Request(String action, Map<String, Object> params)`

**`Response.java`** — value object
- Поля: `String status`, `Map<String, Object> data`, `String message`
- Статические фабрики: `ok(Map<String, Object> data): Response`, `error(String message): Response`
- Приватный конструктор

**`JsonProtocol.java`** — утилитный класс со статическими методами
- Методы: `parseRequest(String json): Request`, `formatResponse(Response response): String`, `formatSuccess(Map<String, Object> data): String`, `formatError(String message): String`
- Приватный конструктор (утилитный класс)

### Пакет `com.sh3d.mcp.command`

**`CommandHandler.java`** — интерфейс
```java
public interface CommandHandler {
    Response execute(Request request, HomeAccessor accessor);
}
```

**`CommandException.java`** — extends RuntimeException
- Конструкторы: `CommandException(String message)`, `CommandException(String message, Throwable cause)`

**`CommandRegistry.java`**
- Поле: `Map<String, CommandHandler> handlers`
- Методы: `register(String action, CommandHandler handler): void`, `dispatch(Request request, HomeAccessor accessor): Response`, `hasHandler(String action): boolean`

**`PingHandler.java`** — implements CommandHandler
- execute возвращает `Response.ok(Map.of("version", "0.1.0"))`

**`CreateWallsHandler.java`** — implements CommandHandler
- Логика из ARCHITECTURE.md: валидация params (x, y, width, height, thickness), создание 4 Wall через EDT

**`PlaceFurnitureHandler.java`** — implements CommandHandler
- Поиск в каталоге, создание HomePieceOfFurniture через EDT

**`GetStateHandler.java`** — implements CommandHandler
- Сбор состояния: walls count, furniture list, rooms count, bounding box

**`ListFurnitureCatalogHandler.java`** — implements CommandHandler
- Фильтрация каталога по query и category

### Пакет `com.sh3d.mcp.bridge`

**`HomeAccessor.java`**
- Поля: `Home home`, `UserPreferences userPreferences`
- Методы: `getHome(): Home`, `getUserPreferences(): UserPreferences`, `runOnEDT(Callable<T> task): T`, `getFurnitureCatalog(): FurnitureCatalog`
- `runOnEDT` использует `SwingUtilities.invokeAndWait()` — реализуй полностью по ARCHITECTURE.md (с AtomicReference, обработкой InterruptedException, InvocationTargetException)

### Пакет `com.sh3d.mcp.config`

**`PluginConfig.java`**
- Константы: `DEFAULT_PORT = 9877`, `DEFAULT_MAX_LINE_LENGTH = 65536`, `DEFAULT_EDT_TIMEOUT = 10000`, `DEFAULT_AUTO_START = false`
- Поля: `int port`, `int maxLineLength`, `int edtTimeout`, `boolean autoStart`
- Метод: `static load(): PluginConfig` (чтение System properties → properties file → defaults)
- Геттеры для всех полей

## 3. Ресурсы

**`src/main/resources/ApplicationPlugin.properties`:**
```properties
name=SH3D MCP Plugin
class=com.sh3d.mcp.plugin.SH3DMcpPlugin
description=TCP server for Model Context Protocol integration
version=0.1.0
license=MIT
provider=SH3D MCP Project
applicationMinimumVersion=6.0
javaMinimumVersion=11
```

**`src/main/resources/com/sh3d/mcp/plugin/ServerToggleAction.properties`:**
```properties
Name=MCP Server: Start
```

## 4. Тесты (заглушки с TODO)

Создай следующие тестовые классы в `src/test/java/com/sh3d/mcp/`:

**`protocol/JsonProtocolTest.java`** — тесты парсинга и форматирования JSON:
- `testParseValidRequest()`
- `testParseRequestWithoutParams()`
- `testParseInvalidJson()`
- `testFormatSuccessResponse()`
- `testFormatErrorResponse()`

**`protocol/RequestTest.java`** — тесты value object:
- `testGetString()`
- `testGetFloat()`
- `testGetFloatWithDefault()`

**`protocol/ResponseTest.java`** — тесты фабричных методов:
- `testOkResponse()`
- `testErrorResponse()`

**`command/CommandRegistryTest.java`** — тесты реестра команд:
- `testRegisterAndDispatch()`
- `testDispatchUnknownAction()`
- `testHasHandler()`

**`command/PingHandlerTest.java`** — тест ping-команды:
- `testPingReturnsVersion()`

**`server/TcpServerTest.java`** — интеграционный тест:
- `testStartAndStop()`
- `testPingOverSocket()`

**`config/PluginConfigTest.java`** — тесты конфигурации:
- `testDefaultValues()`
- `testSystemPropertyOverride()`

Каждый тест: аннотация `@Test`, пустое тело с `// TODO: implement`.

## 5. Дополнительные файлы

**`lib/.gitkeep`** — пустой файл, чтобы git отслеживал директорию для SweetHome3D.jar

**`.gitignore`:**
```
target/
*.class
*.jar
!lib/*.jar
.idea/
*.iml
.settings/
.classpath
.project
```

</requirements>

<constraints>
- Java 11 — НЕ используй var, записи (records), sealed classes и другие фичи Java 14+
- Все импорты SH3D классов: `com.eteks.sweethome3d.model.*`, `com.eteks.sweethome3d.plugin.*`
- НЕ создавай реализацию JSON-парсера — только заглушку с TODO
- HomeAccessor.runOnEDT() — единственный метод, который нужно реализовать ПОЛНОСТЬЮ (это критический код для thread-safety)
- PingHandler — реализуй полностью (это тривиально: return Response.ok(...))
- Все остальные CommandHandler — заглушки с TODO
- Максимум 300 строк на файл. Если файл длиннее — разбей логически
- Кодировка всех файлов: UTF-8
</constraints>

<implementation>
Порядок создания файлов:

1. Структура директорий (mkdir -p)
2. `pom.xml`
3. `.gitignore`
4. `lib/.gitkeep`
5. Resources: `ApplicationPlugin.properties`, `ServerToggleAction.properties`
6. Java-классы в порядке зависимостей:
   - config/PluginConfig
   - protocol/Request, Response, JsonProtocol
   - command/CommandException, CommandHandler, CommandRegistry
   - command/PingHandler (полная реализация)
   - command/CreateWallsHandler, PlaceFurnitureHandler, GetStateHandler, ListFurnitureCatalogHandler (заглушки)
   - bridge/HomeAccessor (runOnEDT — полная реализация)
   - server/ServerState, ClientHandler, TcpServer
   - plugin/ServerToggleAction, SH3DMcpPlugin
7. Тестовые классы
</implementation>

<verification>
После создания всех файлов выполни проверку:

1. Запусти `mvn compile -f ./pom.xml` — убедись, что проект компилируется (допустимы ошибки из-за отсутствия SweetHome3D.jar)
2. Проверь, что все Java-файлы имеют правильную структуру пакетов
3. Проверь, что `ApplicationPlugin.properties` указывает на правильный класс
4. Убедись, что MANIFEST.MF будет содержать Plugin-Class
</verification>

<success_criteria>
- [ ] pom.xml с правильными зависимостями и плагинами
- [ ] 16 Java-классов в правильных пакетах
- [ ] 7 тестовых классов
- [ ] ApplicationPlugin.properties в resources
- [ ] ServerToggleAction.properties в resources
- [ ] .gitignore
- [ ] lib/.gitkeep
- [ ] HomeAccessor.runOnEDT() реализован полностью
- [ ] PingHandler реализован полностью
- [ ] Все остальные классы — корректные заглушки с TODO
- [ ] Проект структурно корректен для Maven
</success_criteria>
