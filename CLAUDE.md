# sh3d-mcp-plugin

Плагин для Sweet Home 3D — TCP-сервер внутри JVM приложения, принимающий JSON-команды для управления сценой. Часть системы **Sweet Home 3D MCP**, позволяющей Claude управлять Sweet Home 3D.

## Архитектура системы

```
Claude Desktop/Code CLI
        | stdin/stdout (JSON-RPC 2.0, MCP protocol)
        v
+---------------------------+
|  mcp-server/ (Java 21)   |  -- внешний процесс, fat JAR
|  MCP SDK 0.17.2 + Jackson |
+---------------------------+
        | TCP localhost:9877 (newline-delimited JSON)
        v
+---------------------------+
|  plugin/ (Java 11)  <-- ТЫ ЗДЕСЬ
|  Без внешних зависимостей |
+---------------------------+
        | SH3D Plugin API (EDT)
        v
   Sweet Home 3D
```

Плагин -- центральный компонент. MCP-сервер -- тонкий прокси, автоматически регистрирующий tools через auto-discovery. **Вся разработка новых фич происходит здесь.**

## MCP-сервер

**Расположение:** `../mcp-server/` (отдельный git-репо: `grimashevich/sh3d-mcp-server`)

MCP-сервер транслирует вызовы Claude в TCP-команды плагина. Благодаря auto-discovery он **не требует изменений** при добавлении новых команд -- при старте запрашивает `describe_commands` у плагина и динамически регистрирует MCP tools.

### Сборка MCP-сервера

```bash
cd ../mcp-server && mvn clean package -DskipTests -q
```

Артефакт: `../mcp-server/target/sh3d-mcp-server-0.1.0-SNAPSHOT.jar`

### Конфигурация MCP (Claude Code)

Файл `.mcp.json` в этой директории:
```json
{
  "mcpServers": {
    "sweethome3d": {
      "type": "stdio",
      "command": "cmd",
      "args": ["/c", "java", "-jar", "C:\\Users\\kgrim\\projects\\SH3D\\mcp-server\\target\\sh3d-mcp-server-0.1.0-SNAPSHOT.jar"]
    }
  }
}
```

### Проверка работы MCP-сервера

MCP-сервер запускается Claude Code автоматически через `.mcp.json`. Если MCP tools не работают:

1. Проверить, что SH3D запущена и плагин активен (TCP-порт 9877 отвечает)
2. Проверить, что MCP-сервер собран: `ls ../mcp-server/target/sh3d-mcp-server-*.jar`
3. Перезапустить Claude Code сессию (MCP-сервер перезапустится и получит дескрипторы)

## Стек

- **Java 11** (минимум — bytecode target 11, `source=11`, `target=11`)
- **Не совместим с Java 8.** Старые версии SH3D (например, 32-bit с bundled JRE 1.8) не загрузят плагин — JVM 8 не может прочитать class-файлы версии 55 (Java 11) и выбрасывает `UnsupportedClassVersionError`. Плагин молча не появляется в меню. Требуется SH3D с bundled JRE 11+.
- Без фреймворков, без внешних runtime-зависимостей
- Sweet Home 3D Plugin API (`com.eteks.sweethome3d.plugin.Plugin`) -- scope `system`
- JSON-парсер -- ручной, без библиотек
- JUnit 5 + Mockito 5.21 (тесты)

## Сборка и деплой

```bash
# Сборка
mvn clean package

# Быстрая сборка (без тестов)
mvn clean package -DskipTests -q

# Деплой (Windows)
copy target\sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p "%APPDATA%\eTeks\Sweet Home 3D\plugins\"

# Деплой (bash / Git Bash)
cp target/sh3d-mcp-plugin-0.1.0-SNAPSHOT.sh3p "$APPDATA/eTeks/Sweet Home 3D/plugins/"
```

Артефакт `.sh3p` -- ZIP-архив (переименованный JAR). SH3D обнаруживает плагин через `ApplicationPlugin.properties` (поле `class=`).

## Структура пакетов

Корневой пакет: `com.sh3d.mcp`

| Пакет | Назначение |
|-------|-----------|
| `plugin` | Точка входа (`SH3DMcpPlugin extends Plugin`), пункт меню (`ServerToggleAction`) |
| `server` | TCP-сервер: `TcpServer` (accept loop), `ClientHandler` (обработка соединения), `ServerState` (enum) |
| `protocol` | JSON-протокол: `JsonProtocol` (парсинг/форматирование), `Request`, `Response` (value objects) |
| `command` | Обработчики: `CommandHandler` (интерфейс), `CommandDescriptor` (auto-discovery), `CommandRegistry` (реестр), handler-классы |
| `bridge` | Мост к SH3D API: `HomeAccessor` -- потокобезопасная обёртка над `Home` через EDT |
| `config` | `PluginConfig` -- настройки (порт, autoStart и др.) |

## Реализованные команды

| Команда (TCP = MCP) | Описание |
|---------------------|----------|
| `ping` | Проверка связи (инфра, не видна Claude) |
| `add_dimension_line` | Размерная линия (аннотация измерения) на 2D-плане |
| `add_label` | Текстовая метка (аннотация) на 2D-плане |
| `add_level` | Создание нового уровня (этажа) с высотой и толщиной перекрытия |
| `apply_texture` | Применение текстуры из каталога к стене или комнате |
| `clear_scene` | Удаление всех объектов из сцены |
| `connect_walls` | Соединение двух стен по ID для корректного рендеринга углов |
| `create_room_polygon` | Создание комнаты по полигону точек |
| `create_wall` | Одиночная стена по двум точкам |
| `create_walls` | Прямоугольная комната из 4 стен |
| `delete_furniture` | Удаление мебели по ID |
| `delete_level` | Удаление уровня по ID с каскадным удалением всех объектов на нём |
| `delete_wall` | Удаление стены по ID |
| `modify_furniture` | Изменение свойств мебели по ID |
| `modify_room` | Изменение свойств комнаты по ID |
| `place_door_or_window` | Размещение двери/окна из каталога в стену (wallId + position) |
| `place_furniture` | Размещение мебели из каталога |
| `get_state` | Состояние сцены (стены, мебель, комнаты, камера) |
| `list_furniture_catalog` | Каталог мебели с фильтрацией |
| `list_levels` | Список всех уровней с указанием выбранного |
| `list_textures_catalog` | Каталог текстур с фильтрацией |
| `render_photo` | 3D-рендер сцены (Sunflow), опционально сохранение в файл (filePath) |
| `export_plan_image` | Быстрый экспорт 2D-плана в PNG |
| `export_svg` | Экспорт 2D-плана в SVG |
| `export_to_obj` | Экспорт 3D-сцены в Wavefront OBJ (ZIP: OBJ + MTL + текстуры) |
| `save_home` | Сохранение сцены в .sh3d файл |
| `set_camera` | Управление камерой (top/observer) |
| `set_environment` | Настройка окружения: земля, небо, свет, прозрачность стен, режим рисования |
| `set_selected_level` | Переключение активного уровня по ID |
| `describe_commands` | Auto-discovery: описания команд для MCP-сервера |

## Добавление новой команды

Один класс в plugin/ -- MCP-сервер подхватит автоматически:

1. Создать класс, реализующий `CommandHandler` + `CommandDescriptor`:
   ```java
   public class MyHandler implements CommandHandler, CommandDescriptor {
       @Override
       public Response execute(Request request, HomeAccessor accessor) { ... }

       @Override
       public String getDescription() {
           return "English description for Claude";
       }

       @Override
       public Map<String, Object> getSchema() {
           Map<String, Object> schema = new LinkedHashMap<>();
           schema.put("type", "object");
           Map<String, Object> props = new LinkedHashMap<>();
           // ... добавить properties
           schema.put("properties", props);
           schema.put("required", Arrays.asList("param1", "param2"));
           return schema;
       }

       // Опционально: если MCP-имя отличается от TCP-action
       @Override
       public String getToolName() { return "my_tool_name"; }
   }
   ```

2. Зарегистрировать в `SH3DMcpPlugin.createCommandRegistry()`:
   ```java
   registry.register("my_command", new MyHandler());
   ```

3. Пересобрать и задеплоить плагин, перезапустить SH3D и MCP-сервер.

## Критические правила

### EDT (Event Dispatch Thread)

Все мутации модели `Home` -- ТОЛЬКО через `HomeAccessor.runOnEDT()`. Используется `SwingUtilities.invokeAndWait()` для синхронного выполнения в EDT. Прямой вызов `home.addWall()` и т.п. из TCP-потоков запрещён.

```java
// Правильно:
Object result = accessor.runOnEDT(() -> {
    Home home = accessor.getHome();
    home.addWall(wall);
    return wall;
});

// НЕПРАВИЛЬНО -- race condition:
accessor.getHome().addWall(wall);
```

### Координатная система

- Единицы: **сантиметры** (500 = 5 метров)
- Ось X -- вправо, ось Y -- **вниз** (экранные координаты)

### Нулевые внешние зависимости

Итоговый JAR не должен содержать внешних библиотек. JSON парсится вручную в `JsonProtocol`. **Не использовать** Gson, Jackson и любые другие JSON-библиотеки.

### TCP-протокол

Построчный JSON (`\n` как разделитель). Порт: **9877**.

- Запрос: `{"action": "...", "params": {...}}\n`
- Ответ OK: `{"status": "ok", "data": {...}}\n`
- Ответ error: `{"status": "error", "message": "..."}\n`

## Визуальный контроль и самотестирование

Агент может самостоятельно запускать SH3D, выполнять команды и видеть результат.

### Запуск и остановка SH3D

```bash
# Запустить (фоновый процесс)
"/c/Program Files/Sweet Home 3D/SweetHome3D.exe" &
sleep 3

# Остановить
cmd //c "taskkill /IM javaw.exe /F"
```

Плагин стартует TCP-сервер автоматически (`autoStart=true`).

### Проверка TCP-порта плагина

```bash
py -c "import socket; s=socket.socket(); s.connect(('localhost',9877)); s.sendall(b'{\"action\":\"ping\"}\n'); print(s.recv(4096).decode()); s.close()"
```

### Скриншот окна SH3D

```python
import ctypes
ctypes.windll.user32.SetProcessDPIAware()

import win32gui
from PIL import ImageGrab

def find_sh3d():
    result = []
    def callback(hwnd, _):
        title = win32gui.GetWindowText(hwnd)
        if 'Sweet Home 3D' in title and win32gui.IsWindowVisible(hwnd):
            result.append((hwnd, title))
    win32gui.EnumWindows(callback, None)
    return result

windows = find_sh3d()
hwnd = windows[0][0]
rect = win32gui.GetWindowRect(hwnd)
img = ImageGrab.grab(bbox=rect)
img.save('screenshot.png')
```

Зависимости: `py -m pip install Pillow pywin32`

### Цикл самотестирования новых фич

1. Собрать плагин: `mvn clean package -DskipTests -q`
2. Закрыть SH3D: `cmd //c "taskkill /IM javaw.exe /F"`
3. Задеплоить: `cp target/*.sh3p "$APPDATA/eTeks/Sweet Home 3D/plugins/"`
4. Запустить SH3D: `"/c/Program Files/Sweet Home 3D/SweetHome3D.exe" &` + `sleep 3`
5. Проверить TCP: ping по TCP (см. выше)
6. Проверить MCP: убедиться, что MCP-сервер собран и Claude Code сессия перезапущена
7. Выполнить тестовые команды через MCP tools
8. Визуально проверить результат (скриншот / get_state)
9. Закрыть SH3D

## Тестирование

```bash
mvn test
```

- JUnit 5 + Mockito 5.21.0
- JDK 24: surefire argLine включает `EnableDynamicAgentLoading` + `--add-opens`
- Тесты: `src/test/java/com/sh3d/mcp/`

## Git

- **Репозиторий:** `grimashevich/sh3d-mcp-plugin`
- Conventional commits: `feat(scope):`, `fix(scope):`, `refactor(scope):`, `docs(scope):`
- Feature-ветки, merge в main
- После merge в main — удалять feature-ветку (локально и на remote)
- **Ревью не проводится.** Вместо code review изменения проверяются вживую: собираем плагин, деплоим в Sweet Home 3D, выполняем команды через MCP/TCP и оцениваем результат визуально (скриншот) и программно (get_state). Работоспособность подтверждается реальным поведением приложения, а не ревью кода. После подтверждения -- merge в main

## Документы

| Файл | Описание |
|------|----------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Архитектура плагина (5 слоёв, ADR, диаграммы) |
| [PRD.md](PRD.md) | Бизнес-требования |
| [sh3d-plugin-spec.md](sh3d-plugin-spec.md) | Спецификация плагина |
| [TO-DOS.md](TO-DOS.md) | TODO-лист и roadmap |
| `../mcp-server/CLAUDE.md` | Описание MCP-сервера |
| `../mcp-server/sh3d-mcp-server-spec.md` | Спецификация MCP-сервера |
| `../RESEARCH.md` | Исследование API SH3D, полный список фич |

## Ссылки

- [Sweet Home 3D Javadoc](http://www.sweethome3d.com/javadoc/)
- [Plugin API](http://www.sweethome3d.com/javadoc/com/eteks/sweethome3d/plugin/Plugin.html)
- [PluginAction](http://www.sweethome3d.com/javadoc/com/eteks/sweethome3d/plugin/PluginAction.html)
