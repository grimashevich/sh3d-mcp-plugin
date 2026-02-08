package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

/**
 * Обработчик команды "place_furniture".
 * Ищет мебель в каталоге SH3D и размещает на плане.
 *
 * <pre>
 * Параметры:
 *   name   — поисковый запрос (string, case-insensitive contains)
 *   x, y   — координаты размещения (float, см)
 *   angle  — угол поворота в градусах (float, default 0)
 *
 * Логика:
 *   1. Получить FurnitureCatalog из UserPreferences
 *   2. Итерировать по категориям/элементам, найти первое совпадение
 *   3. Если не найден → Response.error("Furniture not found: ...")
 *   4. В EDT: создать HomePieceOfFurniture, setX/Y/Angle, home.addPieceOfFurniture
 *   5. Вернуть данные размещённой мебели
 * </pre>
 */
public class PlaceFurnitureHandler implements CommandHandler {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // TODO: реализовать
        // 1. String name = request.getString("name") — обязательный
        // 2. float x = request.getFloat("x"), y = request.getFloat("y")
        // 3. float angle = request.getFloat("angle", 0f)
        // 4. Поиск в каталоге: accessor.getFurnitureCatalog()
        //    for (FurnitureCategory cat : catalog.getCategories())
        //      for (CatalogPieceOfFurniture piece : cat.getFurniture())
        //        if (piece.getName().toLowerCase().contains(name.toLowerCase()))
        // 5. accessor.runOnEDT(() -> {
        //      HomePieceOfFurniture hpf = new HomePieceOfFurniture(catalogPiece);
        //      hpf.setX(x); hpf.setY(y);
        //      hpf.setAngle((float) Math.toRadians(angle));
        //      accessor.getHome().addPieceOfFurniture(hpf);
        //      return hpf;
        //    });
        // 6. Response.ok(Map.of("name", ..., "x", ..., "y", ..., ...))
        throw new UnsupportedOperationException("TODO: implement place_furniture");
    }
}
