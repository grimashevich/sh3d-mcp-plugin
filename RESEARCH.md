# Исследование: возможности SH3D API и roadmap фич

**Дата:** 2026-02-10
**Текущая версия:** v0.1.0 (5 команд плагин, 4 MCP tools)
**Потенциал:** ~39 фич в 14 категориях

## Доступные API Sweet Home 3D (из SweetHome3D.jar 7.5)

| Модель | Возможности |
|--------|-------------|
| `Home` | Стены, комнаты, мебель, labels, dimension lines, polylines, cameras, levels |
| `Wall` | Высота, цвет/текстура с двух сторон, shininess, arc extent, baseboard |
| `Room` | Полигон пола, цвет/текстура пола и потолка, площадь, имя |
| `HomePieceOfFurniture` | Цвет, текстура, видимость, elevation, mirror, pitch/roll, масштаб, materials |
| `HomeDoorOrWindow` | Привязка к стене, sash, cut-out shape |
| `HomeFurnitureGroup` | Группировка мебели |
| `HomeEnvironment` | Земля, небо, свет, прозрачность стен, режим рендеринга |
| `Label` | Текст, позиция, стиль, цвет, угол, elevation |
| `DimensionLine` | Размерные линии, elevation, цвет, стиль |
| `Polyline` | Линии/стрелки, цвет, толщина, dash-стиль |
| `Camera`/`ObserverCamera` | Позиция, yaw, pitch, fieldOfView, lens, time |
| `Level` | Этажи (имя, elevation, высота, толщина пола) |
| `Compass` | Север, широта/долгота, часовой пояс |
| `TexturesCatalog` | Каталог текстур |
| `HomeController` | undo/redo, exportToOBJ/SVG/CSV, createPhoto, save |

## Полный список фич по категориям

### Категория 1: Управление стенами

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 1.1 | `create_room` (4 стены прямоугольник) | DONE | -- | -- | -- |
| 1.2 | `create_wall` (одиночная стена между двумя точками) | TODO | Low | **P0** | plugin + server |
| 1.3 | `modify_wall` (цвет, текстура, shininess, высота) | TODO | Medium | P1 | plugin + server |
| 1.4 | `delete_wall` | TODO | Medium | P1 | plugin + server |
| 1.5 | `connect_walls` (соединение в углу) | TODO | Medium | P1 | plugin + server |
| 1.6 | `create_arc_wall` (дугообразная стена) | TODO | Medium | P2 | plugin + server |
| 1.7 | `add_baseboard` (плинтусы) | TODO | Low | P2 | plugin + server |

### Категория 2: Комнаты (Room = пол/потолок)

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 2.1 | `create_room_polygon` (полигон пола/потолка) | TODO | Medium | **P0** | plugin + server |
| 2.2 | `modify_room` (цвет/текстура пола и потолка, имя) | TODO | Medium | P1 | plugin + server |
| 2.3 | `delete_room` | TODO | Low | P1 | plugin + server |

### Категория 3: Мебель

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 3.1 | `place_furniture` | DONE | -- | -- | -- |
| 3.2 | `modify_furniture` (позиция, угол, размеры, цвет, elevation) | TODO | Medium | **P0** | plugin + server |
| 3.3 | `delete_furniture` | TODO | Medium | **P0** | plugin + server |
| 3.4 | `place_door_or_window` (привязка к стене) | TODO | Medium-High | P1 | plugin + server |
| 3.5 | `group_furniture` | TODO | Medium | P3 | plugin + server |
| 3.6 | `set_furniture_materials` | TODO | High | P3 | plugin + server |

### Категория 4: Текстуры

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 4.1 | `list_textures_catalog` | TODO | Low | P1 | plugin + server |
| 4.2 | `apply_texture` (к стене/полу/потолку) | TODO | Medium | P1 | plugin + server |

### Категория 5: Метки и размеры

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 5.1 | `add_label` | TODO | Low | P2 | plugin + server |
| 5.2 | `add_dimension_line` | TODO | Low | P2 | plugin + server |
| 5.3 | `delete_label` / `delete_dimension_line` | TODO | Low | P2 | plugin + server |

### Категория 6: Полилинии

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 6.1 | `add_polyline` | TODO | Low-Medium | P3 | plugin + server |
| 6.2 | `delete_polyline` | TODO | Low | P3 | plugin + server |

### Категория 7: Камеры и визуализация

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 7.1 | `set_camera` (позиция, yaw, pitch, FOV) | TODO | Low-Medium | P1 | plugin + server |
| 7.2 | `store_camera` (сохранить ракурс) | TODO | Low | P2 | plugin + server |
| 7.3 | `get_cameras` | TODO | Low | P2 | plugin + server |
| 7.4 | `switch_camera_mode` (top/observer) | TODO | Low | P2 | plugin + server |

### Категория 8: Окружение

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 8.1 | `set_environment` (земля, небо, свет) | TODO | Medium | P2 | plugin + server |
| 8.2 | `set_compass` (север, широта/долгота) | TODO | Low | P3 | plugin + server |

### Категория 9: Этажи

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 9.1 | `add_level` (новый этаж) | TODO | Medium | P2 | plugin + server |
| 9.2 | `set_selected_level` | TODO | Medium | P2 | plugin + server |
| 9.3 | `list_levels` | TODO | Low | P2 | plugin + server |
| 9.4 | `delete_level` | TODO | Low | P2 | plugin + server |

### Категория 10: Экспорт

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 10.1 | `export_to_obj` | TODO | High | P2 | plugin + server |
| 10.2 | `export_to_svg` | TODO | High | P3 | plugin + server |
| 10.3 | `save_home` | TODO | Medium | P2 | plugin + server |
| 10.4 | `export_furniture_csv` | TODO | Medium | P3 | plugin + server |

### Категория 11: Расширенное состояние

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 11.1 | `get_state` enhanced (ID, wall coords, room data, cameras) | Частично | Medium | **P0** | plugin + server |

### Категория 12: Undo/Redo

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 12.1 | `undo` | TODO | Low-Medium | P1 | plugin + server |
| 12.2 | `redo` | TODO | Low | P1 | plugin + server |

### Категория 13: Пакетные операции

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 13.1 | `clear_scene` (удалить всё) | TODO | Low-Medium | P1 | plugin + server |
| 13.2 | `batch_commands` (атомарное выполнение) | TODO | Medium | P2 | plugin |

### Категория 14: Инфраструктура

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 14.1 | **Auto-discovery** (плагин описывает команды, сервер регистрирует динамически) | TODO | Medium-High | **P0** | plugin + server |

### Категория 15: Визуальная обратная связь

| # | Фича | Статус | Сложность | Приоритет | Изменения |
|---|-------|--------|-----------|-----------|-----------|
| 15.1 | `render_photo` (ray-traced 3D рендер → base64 PNG) | TODO | Medium-High | **P0** | plugin + server |
| 15.2 | `export_svg` (2D план → SVG строка) | TODO | Medium | **P0** | plugin + server |
| 15.3 | `set_camera` (управление камерой: позиция, режим top/observer) | TODO | Low-Medium | **P0** | plugin + server |
| 15.4 | `screenshot_3d` (быстрый OpenGL offscreen захват) | TODO | Medium | P1 | plugin + server |

**Исследование API (PhotoRenderer):**
- `new PhotoRenderer(home, Quality.HIGH)` → `render(BufferedImage, Camera, null)` → ray-traced рендер
- Quality: `LOW` (быстро, превью) / `HIGH` (финальное качество)
- Camera: `home.getTopCamera()`, `home.getObserverCamera()`, `new Camera(x, y, z, yaw, pitch, fov)`
- Camera.Lens: `PINHOLE`, `NORMAL`, `FISHEYE`, `SPHERICAL`
- HomeComponent3D: `getOffScreenImage(w, h)` — быстрый OpenGL захват (нужен живой компонент)
- PlanComponent: `exportToSVG(OutputStream)` / `exportData(OutputStream, FormatType.SVG, null)`

## Сводка по приоритетам

| Приоритет | Кол-во | Описание |
|-----------|--------|----------|
| **P0** | 9 | Критичные: визуальный фидбек (render_photo, export_svg, set_camera), auto-discovery, enhanced get_state, CRUD мебели, стены, Room polygon |
| **P1** | 12 | Важные: screenshot_3d, delete/modify walls/rooms, двери/окна, текстуры, undo/redo, clear |
| **P2** | 15 | Полезные: labels, dimensions, levels, environment, export, batch |
| **P3** | 7 | Nice-to-have: group, materials, polylines, compass, SVG/CSV export |

## Рекомендуемый roadmap

### Фаза 2 (v0.2) — Visual Feedback + Auto-discovery + Core CRUD
1. **render_photo** (ray-traced 3D рендер через PhotoRenderer)
2. **export_svg** (2D план в SVG)
3. **set_camera** (управление камерой)
4. Auto-discovery (describe_commands → динамическая регистрация tools)
5. get_state enhanced (ID объектов)
6. create_wall (одиночная стена)
7. connect_walls
8. delete_wall
9. delete_furniture
10. modify_furniture
11. create_room_polygon
12. clear_scene

### Фаза 3 (v0.3) — Visual Design
10. modify_wall (цвет, текстура)
11. modify_room (пол/потолок)
12. delete_room
13. list_textures_catalog
14. apply_texture
15. place_door_or_window
16. undo / redo

### Фаза 4 (v0.4) — Annotations & Cameras
17. set_camera
18. store_camera / get_cameras
19. add_label
20. add_dimension_line
21. set_environment

### Фаза 5 (v0.5) — Multi-level & Export
22. add_level / list_levels / set_selected_level
23. export_to_obj
24. save_home
25. batch_commands
