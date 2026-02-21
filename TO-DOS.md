# TO-DOS -- Sweet Home 3D MCP Plugin

Roadmap и текущие задачи. Плагин содержит встроенный MCP-сервер (Streamable HTTP).

Детали API: [RESEARCH.md](RESEARCH.md)

---

## Фаза 3 (v0.3) -- Visual Design

- [x] modify_wall (цвет, shininess, высота, толщина, дуга) -- 30 тестов
- [x] modify_room (имя, цвет пола/потолка, shininess, видимость) -- 23 теста
- [x] delete_room
- [x] list_textures_catalog
- [x] apply_texture (к стене/полу/потолку)
- [x] place_door_or_window (привязка к стене) -- 26 тестов
- ~~undo / redo~~ — убрано: агент имеет точные инструменты (delete_*, modify_*, clear_scene), undo-стек ненадёжен при смешанном использовании UI + MCP

---

## Фаза 4 (v0.4) -- Annotations & Cameras

- [x] store_camera / get_cameras (сохранение именованных точек обзора) -- 36 тестов, живой тест OK
- [x] add_label
- [x] add_dimension_line
- [x] set_environment (земля, небо, свет, текстуры, прозрачность, drawingMode) -- 39 тестов

---

## Фаза 5 (v0.5) -- Multi-level & Export

- [x] add_level / list_levels / set_selected_level / delete_level -- 53 теста
- [x] export_to_obj (6 тестов, живой тест OK)
- [x] save_home (7 тестов)
- [x] batch_commands (21 тест, живой тест OK)

---

## Фаза 6 (v0.6) -- Built-in MCP & UX

- [x] Встроенный MCP-сервер (Streamable HTTP) — заменить TCP+внешний MCP-сервер на единый HTTP endpoint `/mcp` (667 тестов, живой тест OK)
- [x] render_photo: параметр `view: "overhead"` — bird's eye orbit: 4 диагональных ракурса (NW, SE, NE, SW), pitch 30°, auto bounding box, filePath + base64 (39 новых тестов, живой тест OK)
- [x] render_photo overhead: параметр `focusOn` — фокус на `furniture:<id>` или `room:<id>`, bbox + padding, steeper pitch 55° (24 теста, живой тест OK)
- [x] render_photo overhead: скрытие стен — `hideWalls` (default true), временное уменьшение высоты до 1 см (Sunflow/YafaRay игнорируют wallsAlpha для стен)
- [x] render_photo overhead: разрешение 1200×900 по умолчанию
- [x] render_photo overhead: контраст — авто-серый пол (#E0E0E0) для комнат без текстур
- [x] load_home — загрузка сцены из .sh3d файла (парный метод к save_home), позволяет переиспользовать сохранённые планировки вместо создания с нуля — 24 теста, живой тест OK
- [x] checkpoint / restore_checkpoint / list_checkpoints — in-memory snapshot сцены (undo/redo таймлайн с курсором). `checkpoint` клонирует Home через `Home.clone()`, `restore_checkpoint` восстанавливает через clearAll + addAll (паттерн LoadHomeHandler), `list_checkpoints` показывает таймлайн с текущей позицией. Fork: новый checkpoint после restore отсекает forward-историю. maxDepth=32, ~50-200 KB на снимок. CheckpointManager в bridge-пакете — 84 теста, живой тест OK
- [x] UI диалог настроек MCP-сервера (McpSettingsDialog) — единый диалог Tools > "MCP Server..." с управлением сервером (status, port, start/stop), JSON-превью конфига, Copy to Clipboard, Auto-configure Claude Desktop (merge + .bak backup). Заменяет ServerToggleAction. ClaudeDesktopConfigurator (кросс-платформенный merge), JsonUtil.serializePretty(), HttpMcpServer.setPort() — 26 новых тестов, 857 всего
- [x] Обновить документацию (CLAUDE.md, ARCHITECTURE.md) — убрать TCP-протокол, добавить HTTP MCP, обновить архитектурную диаграмму

---

## Фаза 7 (v0.7) — Улучшения UX

### Возврат ID из команд создания [P1, High]

Команды создания объектов должны возвращать ID, чтобы агент мог ссылаться на них в последующих операциях (modify, delete, connect). Сейчас не возвращают ID:

- [x] `create_walls` — возвращает `wallIds` массив из 4 int (top, right, bottom, left)
- [x] `place_furniture` — возвращает `id` (индекс в коллекции мебели)
- [x] `place_door_or_window` — возвращает `id` (индекс в коллекции мебели)

### Каталог поиска [P0, High] -- DONE

- [x] Точное совпадение имени приоритетнее подстроки (CatalogSearchUtil)
- [x] Disambiguация: ошибка с подсказкой при множественном exact match
- [x] Параметр `catalogId` для точного выбора по ID каталога
- [x] `catalogId` в выводе list_furniture_catalog и list_textures_catalog

---

## Фаза 8 (v0.8) — UX-улучшения по результатам тестирования -- DONE

Источник: `test_reports/test_log_2026-02-19.md` — отчёт агента-тестировщика, воспроизводившего квартиру по чертежу БТИ.

- [x] **Стабильные ID объектов** [P0] — Перешли на `HomeObject.getId()` (UUID строки). ObjectResolver для линейного поиска по ID. Миграция всех хэндлеров (get_state, delete_*, modify_*, connect_walls, place_door_or_window, apply_texture, render_photo). 919 тестов, живой тест OK
- [x] **Авто-чекпоинт** [P1] — `clear_scene` и `batch_commands` автоматически создают checkpoint (Home.clone()) перед выполнением. CheckpointManager dependency injection
- [x] **Авто-подгонка depth дверей/окон** [P1] — `place_door_or_window`: если depth < wall thickness, увеличивает depth до thickness
- [x] **place_furniture: elevation** [P2] — Опциональный параметр `elevation` для настенных предметов (одним вызовом вместо двух)
- [x] **modify_wall: координаты** [P2] — Параметры `xStart`, `yStart`, `xEnd`, `yEnd` для перемещения стены без delete+create
- [x] **Толерантность MCP-сессии** [P2] — Auto-recreate сессии с тем же ID при невалидном Session ID (вместо 404). SessionManager.createSessionWithId()
- [x] **Review fixes** — описания GetStateHandler/ClearSceneHandler, dead code в 4 хэндлерах, session leak fix

---

## Фаза 10 (v1.0) — UX-улучшения по результатам UX-аудита

Источник: `test_reports/ux_audit_v0.9.md` — UX-аудит «Двухэтажный дом» (Wave 3, спринт v0.9).

**Примечание:** BUG-1 из аудита (двойное кодирование кириллицы) — НЕ баг плагина. Проблема в Git Bash на Windows при inline `-d` аргументах curl. При отправке через `--data-binary @file` UTF-8 работает корректно.

### Высокий приоритет

- [ ] **`isDoorOrWindow` в list_furniture_catalog** [P1, Low] — Добавить boolean-поле `isDoorOrWindow` в вывод каталога мебели для различения дверей/окон от обычной мебели
- [ ] **`lookAt` параметр в set_camera** [P1, Medium] — Параметр `lookAt: {x, y, z}` для авто-вычисления yaw/pitch. Или `target: "center"` для наведения на центр сцены
- [ ] **`type` фильтр в list_furniture_catalog** [P1, Low] — Значения: "all" (default), "furniture", "doorOrWindow"

### Средний приоритет

- [ ] **`filePath` для export_plan_image и export_svg** [P2, Low] — Опциональный параметр для сохранения на диск (как у render_photo)
- [ ] **Default offset для add_dimension_line** [P2, Low] — Значение по умолчанию 25 (сейчас обязательный параметр)
- [ ] **restore_checkpoint force mode** [P2, Medium] — Всегда восстанавливать состояние, даже если курсор не двигался, но сцена изменилась
- [ ] **Предупреждение о неизвестных параметрах** [P2, Medium] — Возвращать warning при передаче нераспознанных параметров (сейчас молча игнорируются)

### Низкий приоритет

- [ ] **quality: "medium" для render_photo** [P3, Medium] — Промежуточное качество между "low" (быстро, шумно) и "high" (медленно, фотореалистично)
- [ ] **English aliases для каталога** [P3, High] — Маппинг стандартных предметов на английские имена (door, window, bath, toilet и т.д.)

---

## Технический долг

- [x] TcpServerTest: интеграционные тесты (10 тестов: lifecycle, ping, ошибки, multi-client, etc.)
- [x] Тесты для wall height fix (CreateWallsHandler) -- 6 тестов
- [x] Исследовать race condition: текст меню не обновляется при toggle — state listener pattern (10 тестов)

### ~~Уведомление при занятом порте~~ [P1, Low] -- DONE

- [x] Показать диалог/уведомление при `BindException` (порт занят)
- [x] Включить номер порта в сообщение об ошибке

### ~~render_photo — сохранение в файл таймаутит~~ [P1, Medium] -- DONE

- [x] Параметр `filePath` — при указании пишет PNG на диск, возвращает метаданные без base64 (лёгкий ответ)
- [x] MCP-сервер: READ_TIMEOUT увеличен 30s → 120s для поддержки долгих рендеров

### ~~render_photo — размер ответа переполняет контекст~~ [P1, High] -- DONE

- [x] JPEG по умолчанию для inline (quality 0.85) — сжатие 12.7x (72 KB vs 923 KB для 800x600)
- [x] MCP image content type — Claude видит изображения нативно, не как base64 текст
- [x] Multi-content protocol (`_image`/`_images`/`_mimeType`) — metadata + image blocks в одном ответе
- [x] Overhead inline: 800x600 (было 1200x900) — 4 кадра суммарно ~385 KB (в лимит 1 MB с запасом)
- [x] Параметр `format`: "jpeg" (default inline) / "png" (default filePath)
- [x] export_plan_image: MCP image content type (PNG для линейной графики)

---

## Выполнено

- [x] SetCameraHandler: подробное описание координатной системы, yaw/pitch конвенций, типичных значений и примера в getDescription()/getSchema() для AI-агентов. Yaw-конвенция подтверждена эмпирически (4 рендера по сторонам света)

- [x] Auto-discovery команд (describe_commands, CommandDescriptor) -- v0.2
- [x] Mockito 5.11 -> 5.21 + argLine для JDK 24
- [x] JsonProtocol: recursive descent парсер (13 тестов)
- [x] CreateWallsHandler (9 тестов)
- [x] GetStateHandler enhanced -- стены с координатами, мебель с catalogId/elevation/isDoorOrWindow, комнаты с полигонами, labels, dimension lines, камера, levels (15 тестов, живой тест OK)
- [x] PlaceFurnitureHandler (14 тестов)
- [x] ListFurnitureCatalogHandler (12 тестов)
- [x] RenderPhotoHandler -- рендер 3D через Sunflow (12 тестов, живой тест OK)
- [x] ExportSvgHandler -- экспорт 2D-плана в SVG через ExportableView (6 тестов, живой тест OK)
- [x] SetCameraHandler -- управление камерой top/observer с позицией (14 тестов, живой тест OK). Включает switch_camera_mode из Фазы 4
- [x] CreateWallHandler -- одиночная стена по двум точкам (15 тестов)
- [x] DeleteFurnitureHandler -- удаление мебели по ID (9 тестов, живой тест OK)
- [x] ModifyFurnitureHandler -- изменение свойств мебели по ID: x, y, angle, elevation, width, depth, height, color, visible, mirrored, name (18 тестов, живой тест OK)
- [x] PluginConfig: кроссплатформенные пути
- [x] CommandRegistryTest: dispatch с mock HomeAccessor
- [x] PluginConfigTest: System property override
- [x] CreateRoomPolygonHandler -- создание комнаты по полигону точек: name, floorVisible, ceilingVisible, floorColor, ceilingColor, areaVisible (18 тестов)
- [x] Убран getToolName() override из CreateWallsHandler -- TCP/MCP имена теперь прозрачны
- [x] ClearSceneHandler -- удаление всех объектов из сцены с подсчётом (13 тестов)
- [x] ConnectWallsHandler -- соединение двух стен по ID с автоопределением ближайших концов (18 тестов)
- [x] DeleteWallHandler -- удаление стены по ID (9 тестов)
- [x] GenerateShapeHandler -- произвольные 3D-фигуры: extrude (2D-полигон + высота) и mesh (вершины + треугольники), адаптировано из ShapeGenerator GPL v2+ (24 теста, живой тест OK)
- [x] ModifyWallHandler -- изменение свойств стены: height, heightAtEnd, thickness, arcExtent, цвет (left/right/top + shortcut), shininess (left/right + shortcut). GetStateHandler расширен визуальными свойствами стен (30 тестов)
- [x] ModifyRoomHandler -- изменение свойств комнаты: name, floorVisible, ceilingVisible, areaVisible, floorColor, ceilingColor, floorShininess, ceilingShininess. GetStateHandler расширен shininess полей комнат (23 теста)
- [x] DeleteRoomHandler -- удаление комнаты по ID
- [x] ListTexturesCatalogHandler -- каталог текстур с фильтрацией
- [x] ApplyTextureHandler -- применение текстуры из каталога к стене (left/right/both) или комнате (floor/ceiling/both), поиск по имени + категории, angle/scale, сброс через null (29 тестов). GetStateHandler расширен полями текстур
- [x] PlaceDoorOrWindowHandler -- размещение двери/окна из каталога в стену по wallId + position (0-1), автовычисление координат и угла, elevation, mirrored (26 тестов)
- [x] ExportPlanImageHandler -- быстрый экспорт 2D-плана в PNG
