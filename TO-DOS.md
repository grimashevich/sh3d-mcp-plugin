# TO-DOS -- Sweet Home 3D MCP Plugin

Roadmap и текущие задачи. Плагин содержит встроенный MCP-сервер (Streamable HTTP).

Детали API: [../RESEARCH.md](../RESEARCH.md)

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
- [ ] render_photo: параметр `view: "overhead"` — автоматический 3D-рендер сверху (bird's eye): вычислять bounding box сцены, ставить камеру по центру, pitch=90, подбирать z и fov
- [ ] UI диалог настройки агентов (ConfigureAction) — пункт меню "MCP: Configure Agents" с кнопками для автоконфигурации Claude Desktop, Claude Code и текстовым полем с JSON для копирования
- [ ] Обновить документацию (CLAUDE.md, ARCHITECTURE.md) — убрать TCP-протокол, добавить HTTP MCP, обновить архитектурную диаграмму

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
