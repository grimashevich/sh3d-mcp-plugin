# sh3d-mcp-plugin

Плагин для Sweet Home 3D, который поднимает TCP-сервер внутри JVM приложения и принимает JSON-команды для управления сценой (создание стен, размещение мебели, чтение состояния). Внешний MCP-сервер подключается к плагину по TCP и транслирует команды от Claude.

## Стек

- **Java 11** (совместимость с JVM Sweet Home 3D)
- Без фреймворков, без внешних runtime-зависимостей
- Sweet Home 3D Plugin API (`com.eteks.sweethome3d.plugin.Plugin`) -- scope `provided`/`system`
- JSON-парсер -- ручной, без библиотек

## Сборка

```bash
mvn clean package
```

Артефакт: `target/sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p` -- файл плагина SH3D.

## Деплой

Скопировать `.sh3p` в папку плагинов Sweet Home 3D (или установить двойным кликом):

```bash
# Windows
copy target\sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p "%APPDATA%\eTeks\Sweet Home 3D\plugins\"

# Linux / macOS
cp target/sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p ~/.eteks/sweethome3d/plugins/
```

После перезапуска SH3D плагин появляется в меню.

## Формат плагина

Файл `.sh3p` -- это ZIP-архив (переименованный JAR). SH3D обнаруживает плагин через `ApplicationPlugin.properties` (поле `class=`), а НЕ через `MANIFEST.MF`. Манифест стандартный, без специальных атрибутов.

## Структура пакетов

Корневой пакет: `com.sh3d.mcp`

| Пакет | Назначение |
|-------|-----------|
| `plugin` | Точка входа (`SH3DMcpPlugin extends Plugin`), пункт меню (`ServerToggleAction`) |
| `server` | TCP-сервер: `TcpServer` (accept loop), `ClientHandler` (обработка соединения), `ServerState` (enum) |
| `protocol` | JSON-протокол: `JsonProtocol` (парсинг/форматирование), `Request`, `Response` (value objects) |
| `command` | Обработчики команд: `CommandHandler` (интерфейс), `CommandRegistry` (реестр), `PingHandler`, `CreateWallsHandler`, `PlaceFurnitureHandler`, `GetStateHandler`, `ListFurnitureCatalogHandler` |
| `bridge` | Мост к SH3D API: `HomeAccessor` -- потокобезопасная обёртка над `Home` через EDT |
| `config` | `PluginConfig` -- настройки (порт, autoStart и др.) |

## Критические правила

### EDT (Event Dispatch Thread)

Все мутации модели `Home` -- ТОЛЬКО через `HomeAccessor.runOnEDT()`. Этот метод использует `SwingUtilities.invokeAndWait()` для синхронного выполнения в EDT. Прямой вызов `home.addWall()`, `home.addPieceOfFurniture()` и т.п. из TCP-потоков запрещён -- приведёт к race conditions и крашам UI.

```java
// Правильно:
Object result = accessor.runOnEDT(() -> {
    Home home = accessor.getHome();
    home.addWall(wall);
    return wall;
});

// НЕПРАВИЛЬНО -- вызов из TCP-потока без EDT:
accessor.getHome().addWall(wall); // Race condition!
```

### Координатная система

- Единицы измерения: **сантиметры** (500 = 5 метров)
- Ось X -- вправо, ось Y -- **вниз** (экранные координаты)
- `(x, y)` в `create_walls` -- верхний левый угол прямоугольника

### Нулевые внешние зависимости в runtime

SH3D JAR имеет scope `system` (provided) -- он уже в classpath при запуске. Итоговый JAR плагина не должен содержать никаких внешних библиотек. JSON парсится и форматируется вручную в `JsonProtocol`.

### JSON-парсер

Ручная реализация в `JsonProtocol`. Поддерживает: строки (с экранированием), числа, boolean, null, объекты, массивы. Не использовать Gson, Jackson, minimal-json и любые другие библиотеки.

### Добавление новой команды

Два шага:
1. Создать класс, реализующий `CommandHandler` (интерфейс с методом `Response execute(Request, HomeAccessor)`)
2. Зарегистрировать в `SH3DMcpPlugin.createCommandRegistry()`:
   ```java
   registry.register("my_command", new MyHandler());
   ```

### Протокол

TCP, построчный JSON (`\n` как разделитель). Запрос: `{"action": "...", "params": {...}}`. Ответ: `{"status": "ok", "data": {...}}` или `{"status": "error", "message": "..."}`. Порт по умолчанию: 9877.

## Тестирование

```bash
mvn test
```

- **JUnit 5** (`org.junit.jupiter:junit-jupiter:5.10.2`)
- **Mockito** (`org.mockito:mockito-core:5.11.0`)
- Тесты расположены в `src/test/java/com/sh3d/mcp/`
- Покрыто: `JsonProtocol`, `Request`, `Response`, `CommandRegistry`, `PingHandler`, `TcpServer` (интеграционный), `PluginConfig`

## Git

Conventional commits: `feat(scope): описание`, `fix(scope): ...`, `refactor(scope): ...`, `test(scope): ...`, `docs(scope): ...`

Работа только в feature-ветках (`feat/...`, `fix/...`), merge в main после ревью.

## Ссылки

- [Sweet Home 3D Javadoc](http://www.sweethome3d.com/javadoc/)
- [Plugin API](http://www.sweethome3d.com/javadoc/com/eteks/sweethome3d/plugin/Plugin.html)
- [PluginAction](http://www.sweethome3d.com/javadoc/com/eteks/sweethome3d/plugin/PluginAction.html)
- [ARCHITECTURE.md](ARCHITECTURE.md) -- детальная архитектура, диаграммы, ADR
- [sh3d-plugin-spec.md](sh3d-plugin-spec.md) -- спецификация (ТЗ)
