package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
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
 * <p>Поиск: exact match имени приоритетнее substring.
 * При нескольких exact match — ошибка disambiguации.
 * Параметр catalogId позволяет выбрать конкретный элемент по ID каталога.
 */
public class PlaceFurnitureHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        String catalogId = request.getString("catalogId");

        if ((name == null || name.trim().isEmpty())
                && (catalogId == null || catalogId.trim().isEmpty())) {
            return Response.error("Either 'name' or 'catalogId' must be provided");
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
        boolean hasElevation = request.getParams().containsKey("elevation");
        float elevation = hasElevation ? request.getFloat("elevation") : 0f;

        CatalogSearchUtil.FurnitureSearchResult searchResult =
                CatalogSearchUtil.findFurniture(
                        accessor.getFurnitureCatalog(), name, catalogId, null);
        if (searchResult.isError()) {
            return Response.error(searchResult.getError());
        }
        if (!searchResult.isFound()) {
            return Response.error("Furniture not found: " + name);
        }
        CatalogPieceOfFurniture found = searchResult.getFound();

        float angleRad = (float) Math.toRadians(angle);

        HomePieceOfFurniture placed = accessor.runOnEDT(() -> {
            HomePieceOfFurniture piece = new HomePieceOfFurniture(found);
            piece.setX(x);
            piece.setY(y);
            piece.setAngle(angleRad);
            if (hasElevation) {
                piece.setElevation(elevation);
            }
            accessor.getHome().addPieceOfFurniture(piece);
            return piece;
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", placed.getId());
        data.put("name", placed.getName());
        data.put("x", round2(placed.getX()));
        data.put("y", round2(placed.getY()));
        data.put("angle", round2(Math.toDegrees(placed.getAngle())));
        data.put("elevation", round2(placed.getElevation()));
        data.put("width", round2(placed.getWidth()));
        data.put("depth", round2(placed.getDepth()));
        data.put("height", round2(placed.getHeight()));
        return Response.ok(data);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public String getDescription() {
        return "Places a piece of furniture from the Sweet Home 3D catalog. "
                + "Searches the catalog by name (case-insensitive, partial match). "
                + "Coordinates are in centimeters. "
                + "Angle is in degrees (0 = default orientation, 90 = rotated clockwise). "
                + "Use 'catalogId' for precise selection when multiple items share the same name. "
                + "Returns the furniture id for use with modify_furniture, delete_furniture.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", prop("string",
                "Furniture name to search in catalog (e.g., 'bed', 'sofa', 'table')"));
        properties.put("catalogId", prop("string",
                "Exact catalog ID for precise selection (bypasses name search). "
                        + "Use list_furniture_catalog to find catalog IDs"));
        properties.put("x", prop("number", "X coordinate in cm"));
        properties.put("y", prop("number", "Y coordinate in cm"));

        Map<String, Object> angleProp = new LinkedHashMap<>();
        angleProp.put("type", "number");
        angleProp.put("description", "Rotation angle in degrees");
        angleProp.put("default", 0);
        properties.put("angle", angleProp);

        Map<String, Object> elevationProp = new LinkedHashMap<>();
        elevationProp.put("type", "number");
        elevationProp.put("description", "Elevation above floor in cm (e.g., for wall-mounted items)");
        elevationProp.put("default", 0);
        properties.put("elevation", elevationProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("x", "y"));
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
