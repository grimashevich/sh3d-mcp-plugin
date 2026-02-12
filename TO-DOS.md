# TO-DOS -- Sweet Home 3D MCP Plugin

Roadmap и текущие задачи. Вся разработка -- в plugin/, MCP-сервер трогать не нужно (auto-discovery).

Детали API: [../RESEARCH.md](../RESEARCH.md)

---

## Фаза 2 (v0.2) -- Visual Feedback + Core CRUD

### Визуальная обратная связь [P0, High]

Критично для тестирования всех последующих фич и для AI-агентов, выполняющих дизайн-проекты.

---

---

---

---

### create_wall -- одиночная стена [P0, Low]

- [ ] Новый `CreateWallHandler` (+ `CommandDescriptor`)
- [ ] Параметры: xStart, yStart, xEnd, yEnd, thickness, wallHeight
- [ ] Возвращать ID созданной стены

**API:** `new Wall(xStart, yStart, xEnd, yEnd, thickness)`, `wall.setHeight()`, `home.addWall()`

---

### delete_furniture [P0, Medium]

- [ ] Идентификация мебели по ID (из enhanced get_state)
- [ ] `home.deletePieceOfFurniture(piece)`

---

### modify_furniture [P0, Medium]

- [ ] Изменение: x, y, angle, width, depth, height, color, elevation, visible, mirrored
- [ ] Идентификация по ID

**API:** `piece.setX()`, `setY()`, `setAngle()`, `setWidth()`, `setColor()` и т.д.

---

### create_room_polygon [P0, Medium]

- [ ] Создание Room по массиву точек (полигон)
- [ ] Установка видимости пола/потолка
- [ ] Имя комнаты

**API:** `new Room(float[][] points)`, `room.setFloorVisible()`, `room.setCeilingVisible()`, `room.setName()`

---

### clear_scene [P1, Low]

- [ ] Удаление всех стен, мебели, комнат, labels, dimension lines
- [ ] Подтверждение в ответе (кол-во удалённых объектов)

---

### connect_walls [P1, Medium]

- [ ] Соединение двух стен по ID (setWallAtStart/End)
- [ ] Необходимо для корректного рендеринга при create_wall

---

### delete_wall [P1, Medium]

- [ ] Удаление стены по ID
- [ ] `home.deleteWall(wall)`

---

### generate_shape -- произвольная 3D-фигура [P1, High]

Позволить нейросети создавать произвольные 3D-объекты из координат вершин, аналогично плагину ShapeGenerator.

- [ ] Новый `GenerateShapeHandler` (+ `CommandDescriptor`)
- [ ] Параметры: `points` (массив из 8 точек [x,y,z]), `name` (опционально), `transparency` (0.0-1.0, опционально)
- [ ] Генерация OBJ-модели из 8 вершин (гексаэдр, 6 граней по 2 треугольника)
- [ ] Добавление в сцену как `HomePieceOfFurniture`
- [ ] Возвращать ID созданного объекта и его размеры

**Реф. имплементация:** плагин `ShapeGenerator-1.2.1` (автор Space Mushrooms):
- `GeometryInfo(TRIANGLE_ARRAY)` + `NormalGenerator` для построения геометрии
- `OBJWriter.writeNodeInZIPFile()` для экспорта в OBJ
- `TemporaryURLContent` для упаковки модели
- `ModelManager.getInstance().loadModel()` + `CatalogPieceOfFurniture` для добавления в сцену

**Особенности (из форума SH3D):**
- 6 граней = 6 отдельных материалов (цвет/текстура/видимость каждой грани отдельно)
- Текстуры маппятся на 100x100 см, масштабируются пропорционально
- Порядок вершин критичен — неправильный порядок создаёт открытые/полые фигуры
- Можно создавать: террейн, крыши, пирамиды, балки, наклонные поверхности

**API:** `javax.media.j3d.*`, `com.sun.j3d.utils.geometry.GeometryInfo`, `com.eteks.sweethome3d.j3d.OBJWriter`, `com.eteks.sweethome3d.tools.TemporaryURLContent`

---

## Фаза 3 (v0.3) -- Visual Design

- [ ] modify_wall (цвет, текстура, shininess, высота)
- [ ] modify_room (цвет/текстура пола и потолка, имя)
- [ ] delete_room
- [ ] list_textures_catalog
- [ ] apply_texture (к стене/полу/потолку)
- [ ] place_door_or_window (привязка к стене)
- [ ] undo / redo

---

## Фаза 4 (v0.4) -- Annotations & Cameras

- [ ] store_camera / get_cameras
- [ ] switch_camera_mode (top/observer)
- [ ] add_label
- [ ] add_dimension_line
- [ ] set_environment (земля, небо, свет)

---

## Фаза 5 (v0.5) -- Multi-level & Export

- [ ] add_level / list_levels / set_selected_level / delete_level
- [ ] export_to_obj
- [ ] save_home
- [ ] batch_commands

---

## Технический долг

- [ ] TcpServerTest: интеграционные тесты -- заглушки
- [ ] Тесты для wall height fix (CreateWallsHandler)
- [ ] Исследовать race condition: текст меню не обновляется при toggle

---

## Выполнено

- [x] Auto-discovery команд (describe_commands, CommandDescriptor) -- v0.2
- [x] Mockito 5.11 -> 5.21 + argLine для JDK 24
- [x] JsonProtocol: recursive descent парсер (13 тестов)
- [x] CreateWallsHandler (9 тестов)
- [x] GetStateHandler enhanced -- стены с координатами, мебель с catalogId/elevation/isDoorOrWindow, комнаты с полигонами, labels, dimension lines, камера, levels (15 тестов, живой тест OK)
- [x] PlaceFurnitureHandler (14 тестов)
- [x] ListFurnitureCatalogHandler (12 тестов)
- [x] RenderPhotoHandler -- рендер 3D через Sunflow (12 тестов, живой тест OK)
- [x] ExportSvgHandler -- экспорт 2D-плана в SVG через ExportableView (6 тестов, живой тест OK)
- [x] SetCameraHandler -- управление камерой top/observer с позицией (14 тестов, живой тест OK)
- [x] PluginConfig: кроссплатформенные пути
- [x] CommandRegistryTest: dispatch с mock HomeAccessor
- [x] PluginConfigTest: System property override
