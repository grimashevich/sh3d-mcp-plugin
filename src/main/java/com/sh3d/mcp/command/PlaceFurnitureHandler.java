package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

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
public class PlaceFurnitureHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            return Response.error("Parameter 'name' is required and must not be empty");
        }

        if (!request.getParams().containsKey("x")) {
            return Response.error("Missing required parameter: x");
        }
        if (!request.getParams().containsKey("y")) {
            return Response.error("Missing required parameter: y");
        }

        float x = request.getFloat("x");
        float y = request.getFloat("y");
        float angle = request.getFloat("angle", 0f);

        CatalogPieceOfFurniture found = findInCatalog(accessor.getFurnitureCatalog(), name);
        if (found == null) {
            return Response.error("Furniture not found: " + name);
        }

        float angleRad = (float) Math.toRadians(angle);

        HomePieceOfFurniture placed = accessor.runOnEDT(() -> {
            HomePieceOfFurniture piece = new HomePieceOfFurniture(found);
            piece.setX(x);
            piece.setY(y);
            piece.setAngle(angleRad);
            accessor.getHome().addPieceOfFurniture(piece);
            return piece;
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", placed.getName());
        data.put("x", round2(placed.getX()));
        data.put("y", round2(placed.getY()));
        data.put("angle", round2(Math.toDegrees(placed.getAngle())));
        data.put("width", round2(placed.getWidth()));
        data.put("depth", round2(placed.getDepth()));
        data.put("height", round2(placed.getHeight()));
        return Response.ok(data);
    }

    private CatalogPieceOfFurniture findInCatalog(FurnitureCatalog catalog, String query) {
        String lowerQuery = query.toLowerCase();
        for (FurnitureCategory category : catalog.getCategories()) {
            for (CatalogPieceOfFurniture piece : category.getFurniture()) {
                if (piece.getName().toLowerCase().contains(lowerQuery)) {
                    return piece;
                }
            }
        }
        return null;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public String getDescription() {
        return "Places a piece of furniture from the Sweet Home 3D catalog. "
                + "Searches the catalog by name (case-insensitive, partial match). "
                + "Coordinates are in centimeters. "
                + "Angle is in degrees (0 = default orientation, 90 = rotated clockwise).";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", prop("string", "Furniture name to search in catalog (e.g., 'bed', 'sofa', 'table')"));
        properties.put("x", prop("number", "X coordinate in cm"));
        properties.put("y", prop("number", "Y coordinate in cm"));

        Map<String, Object> angleProp = new LinkedHashMap<>();
        angleProp.put("type", "number");
        angleProp.put("description", "Rotation angle in degrees");
        angleProp.put("default", 0);
        properties.put("angle", angleProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("name", "x", "y"));
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
