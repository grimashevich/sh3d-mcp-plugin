# sh3d-mcp-plugin

Плагин для Sweet Home 3D — встроенный HTTP MCP-сервер (Streamable HTTP) внутри JVM приложения. Реализует MCP протокол напрямую, позволяя Claude управлять Sweet Home 3D без внешних прокси.

## Архитектура системы

```
Claude Desktop/Code CLI
        | HTTP (Streamable HTTP, JSON-RPC 2.0)
        v
+-----------------------------------+
|  SH3D MCP Plugin (Java 11)       |
|  Встроенный HTTP MCP-сервер       |
|  http://127.0.0.1:9877/mcp       |
|  Без внешних зависимостей         |
+-----------------------------------+
        | SH3D Plugin API (EDT)
        v
   Sweet Home 3D
```

Плагин — единый компонент, реализующий MCP (Model Context Protocol) версии `2025-03-26` через Streamable HTTP напрямую. Внешний MCP-сервер-прокси не нужен — Claude подключается к HTTP endpoint `/mcp` в плагине.

## Конфигурация MCP

Файл `.mcp.json` в этой директории:
```json
{
  "mcpServers": {
    "sweethome3d": {
      "type": "http",
      "url": "http://localhost:9877/mcp"
    }
  }
}
```

### Проверка работы

Если MCP tools не работают:

1. Проверить, что SH3D запущена и плагин активен (HTTP-порт 9877 отвечает)
2. Перезапустить Claude Code сессию

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
| `plugin` | Точка входа (`SH3DMcpPlugin extends Plugin`), пункт меню (`McpSettingsAction`) |
| `http` | HTTP MCP-сервер: `HttpMcpServer`, `McpRequestHandler`, `JsonRpcProtocol`, `McpSession`, `SessionManager` |
| `server` | Состояние сервера: `ServerState` (enum), `ServerStateListener` |
| `protocol` | JSON-утилиты: `JsonUtil` (парсинг/сериализация), `Request`, `Response` (value objects) |
| `command` | Обработчики: `CommandHandler` (интерфейс), `CommandDescriptor` (auto-discovery), `CommandRegistry` (реестр), handler-классы |
| `bridge` | Мост к SH3D API: `HomeAccessor` -- потокобезопасная обёртка над `Home` через EDT, `CheckpointManager` -- undo/redo таймлайн (in-memory снимки через `Home.clone()`), `ObjectResolver` -- линейный поиск по UUID для стен/мебели/комнат/меток и др. |
| `config` | `PluginConfig` -- настройки (порт, autoStart и др.), `ClaudeDesktopConfigurator` -- кросс-платформенный merge конфигурации Claude Desktop |

## Реализованные команды

| Команда | Описание |
|---------|----------|
| `add_dimension_line` | Размерная линия (аннотация измерения) на 2D-плане |
| `add_label` | Текстовая метка (аннотация) на 2D-плане |
| `add_level` | Создание нового уровня (этажа) с высотой и толщиной перекрытия |
| `apply_texture` | Применение текстуры из каталога к стене или комнате |
| `batch_commands` | Пакетное выполнение нескольких команд за один вызов |
| `checkpoint` | In-memory снимок сцены (undo-точка). Опциональный `description` |
| `clear_scene` | Удаление всех объектов из сцены |
| `connect_walls` | Соединение двух стен по ID для корректного рендеринга углов |
| `create_room_polygon` | Создание комнаты по полигону точек |
| `create_wall` | Одиночная стена по двум точкам |
| `create_walls` | Прямоугольная комната из 4 стен |
| `delete_furniture` | Удаление мебели по ID |
| `delete_level` | Удаление уровня по ID с каскадным удалением всех объектов на нём |
| `delete_room` | Удаление комнаты по ID |
| `delete_wall` | Удаление стены по ID |
| `export_plan_image` | Быстрый экспорт 2D-плана в PNG |
| `export_svg` | Экспорт 2D-плана в SVG |
| `export_to_obj` | Экспорт 3D-сцены в Wavefront OBJ (ZIP: OBJ + MTL + текстуры) |
| `generate_shape` | Произвольные 3D-фигуры: extrude (полигон + высота) и mesh (вершины + треугольники) |
| `get_cameras` | Получение списка сохранённых камер |
| `get_state` | Состояние сцены (стены, мебель, комнаты, камера) |
| `list_categories` | Список категорий мебельного каталога с количеством предметов в каждой |
| `list_checkpoints` | Список всех чекпоинтов с текущей позицией курсора (undo/redo таймлайн) |
| `list_furniture_catalog` | Каталог мебели с фильтрацией |
| `list_levels` | Список всех уровней с указанием выбранного |
| `list_textures_catalog` | Каталог текстур с фильтрацией |
| `load_home` | Загрузка сцены из .sh3d файла |
| `modify_furniture` | Изменение свойств мебели по ID |
| `modify_room` | Изменение свойств комнаты по ID |
| `modify_wall` | Изменение свойств стены по ID (высота, толщина, цвет, дуга) |
| `place_door_or_window` | Размещение двери/окна из каталога в стену (wallId + position) |
| `place_furniture` | Размещение мебели из каталога |
| `render_photo` | 3D-рендер сцены (Sunflow), опционально сохранение в файл (filePath) |
| `restore_checkpoint` | Восстановление сцены из чекпоинта (undo/redo). Опциональный `id` |
| `save_home` | Сохранение сцены в .sh3d файл |
| `set_camera` | Управление камерой (top/observer) |
| `set_environment` | Настройка окружения: земля, небо, свет, прозрачность стен, режим рисования |
| `set_selected_level` | Переключение активного уровня по ID |
| `store_camera` | Сохранение именованной точки обзора |

## Добавление новой команды

Один класс -- плагин подхватит автоматически:

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

       // Опционально: если MCP-имя отличается от action
       @Override
       public String getToolName() { return "my_tool_name"; }
   }
   ```

2. Зарегистрировать в `SH3DMcpPlugin.createCommandRegistry()`:
   ```java
   registry.register("my_command", new MyHandler());
   ```

3. Пересобрать и задеплоить плагин, перезапустить SH3D.

## Критические правила

### EDT (Event Dispatch Thread)

Все мутации модели `Home` -- ТОЛЬКО через `HomeAccessor.runOnEDT()`. Используется `SwingUtilities.invokeAndWait()` для синхронного выполнения в EDT. Прямой вызов `home.addWall()` и т.п. из HTTP-потоков запрещён.

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

Итоговый JAR не должен содержать внешних библиотек. JSON парсится вручную в `JsonUtil`. **Не использовать** Gson, Jackson и любые другие JSON-библиотеки.

### MCP-протокол

Streamable HTTP (JSON-RPC 2.0). Endpoint: `http://127.0.0.1:9877/mcp`. Версия протокола: `2025-03-26`.

- **POST /mcp** -- JSON-RPC 2.0 запросы (`initialize`, `tools/list`, `tools/call`, `ping`)
- **GET /mcp** -- SSE (не реализовано, 405)
- **DELETE /mcp** -- завершение сессии

Сессии управляются через заголовок `Mcp-Session-Id`.

## Визуальный контроль и самотестирование

Агент может самостоятельно запускать SH3D, выполнять команды и видеть результат.

### Запуск и остановка SH3D

```bash
# Запустить (фоновый процесс)
"/c/Program Files/Sweet Home 3D/SweetHome3D.exe" &
sleep 3
```

Плагин стартует HTTP MCP-сервер автоматически (`autoStart=true`).

**Остановка SH3D — ВАЖНО:**

Агент **НЕ должен** закрывать SH3D самостоятельно. Вместо этого нужно попросить пользователя закрыть приложение вручную (кнопка X или File → Exit).

Причина: `taskkill /F` (force kill) убивает JVM без cleanup — Java3D/JOGL не освобождают GPU-контексты, файловые блокировки остаются. После нескольких force kill SH3D перестаёт запускаться до перезагрузки системы.

Если пользователь явно разрешил автоматическое закрытие, использовать **мягкий** вариант:
```bash
# Мягкое закрытие (WM_CLOSE — эквивалент нажатия X)
cmd //c "taskkill /IM javaw.exe"
sleep 3
```
**Без флага `/F`!** Это отправляет WM_CLOSE, SH3D завершается штатно. Но если есть несохранённые изменения, появится диалог "Сохранить?", который заблокирует закрытие.

**НИКОГДА** не использовать `taskkill /F` для javaw.exe — это приводит к утечке системных ресурсов.

### Проверка HTTP-порта плагина

```bash
curl -s -X POST http://localhost:9877/mcp -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"ping"}'
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
2. **Попросить пользователя закрыть SH3D** (или `cmd //c "taskkill /IM javaw.exe"` без /F, если разрешено)
3. Задеплоить: `cp target/*.sh3p "$APPDATA/eTeks/Sweet Home 3D/plugins/"`
4. Запустить SH3D: `"/c/Program Files/Sweet Home 3D/SweetHome3D.exe" &` + `sleep 3`
5. Проверить HTTP: ping по HTTP (см. выше)
6. Выполнить тестовые команды через MCP tools
7. Визуально проверить результат (скриншот / get_state)
8. **Попросить пользователя закрыть SH3D** (не убивать процесс!)

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
- **Ревью не проводится.** Вместо code review изменения проверяются вживую: собираем плагин, деплоим в Sweet Home 3D, выполняем команды через MCP и оцениваем результат визуально (скриншот) и программно (get_state). Работоспособность подтверждается реальным поведением приложения, а не ревью кода. После подтверждения -- merge в main

## Документы

| Файл | Описание |
|------|----------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Архитектура плагина (6 слоёв, ADR, диаграммы) |
| [PRD.md](PRD.md) | Бизнес-требования |
| [sh3d-plugin-spec.md](sh3d-plugin-spec.md) | Спецификация плагина |
| [TO-DOS.md](TO-DOS.md) | TODO-лист и roadmap |
| [RESEARCH.md](RESEARCH.md) | Исследование API SH3D, полный список фич |

## Ссылки

- [Sweet Home 3D Javadoc](http://www.sweethome3d.com/javadoc/)
- [Plugin API](http://www.sweethome3d.com/javadoc/com/eteks/sweethome3d/plugin/Plugin.html)
- [PluginAction](http://www.sweethome3d.com/javadoc/com/eteks/sweethome3d/plugin/PluginAction.html)
