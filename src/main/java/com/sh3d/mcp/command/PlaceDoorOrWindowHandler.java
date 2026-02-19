package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "place_door_or_window".
 * Размещает дверь/окно из каталога в указанную стену, автоматически
 * вычисляя координаты и угол поворота из геометрии стены.
 *
 * <p>Поиск: exact match имени приоритетнее substring.
 * При нескольких exact match — ошибка disambiguации.
 * Параметр catalogId позволяет выбрать конкретный элемент по ID каталога.
 */
public class PlaceDoorOrWindowHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // --- Validate name or catalogId ---
        String name = request.getString("name");
        String catalogId = request.getString("catalogId");
        if ((name == null || name.trim().isEmpty())
                && (catalogId == null || catalogId.trim().isEmpty())) {
            return Response.error("Either 'name' or 'catalogId' must be provided");
        }

        // --- Validate wallId ---
        Map<String, Object> params = request.getParams();
        if (!params.containsKey("wallId")) {
            return Response.error("Missing required parameter: wallId");
        }
        int wallId = (int) request.getFloat("wallId");
        if (wallId < 0) {
            return Response.error("Parameter 'wallId' must be non-negative, got " + wallId);
        }

        // --- Validate position ---
        float position = request.getFloat("position", 0.5f);
        if (position < 0f || position > 1f) {
            return Response.error("Parameter 'position' must be between 0.0 and 1.0, got " + position);
        }

        // --- Optional params ---
        boolean hasElevation = params.containsKey("elevation");
        float elevation = hasElevation ? request.getFloat("elevation") : 0f;
        Boolean mirrored = request.getBoolean("mirrored");

        // --- Search catalog (only doors/windows) ---
        CatalogSearchUtil.FurnitureSearchResult searchResult =
                CatalogSearchUtil.findFurniture(
                        accessor.getFurnitureCatalog(), name, catalogId,
                        CatalogPieceOfFurniture::isDoorOrWindow);
        if (searchResult.isError()) {
            return Response.error(searchResult.getError());
        }
        if (!searchResult.isFound()) {
            return Response.error("Door/window not found in catalog: " + name);
        }
        CatalogPieceOfFurniture found = searchResult.getFound();

        // --- Place in EDT ---
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Wall> walls = new ArrayList<>(home.getWalls());

            if (wallId >= walls.size()) {
                return null;
            }

            Wall wall = walls.get(wallId);

            float xStart = wall.getXStart();
            float yStart = wall.getYStart();
            float xEnd = wall.getXEnd();
            float yEnd = wall.getYEnd();

            float x = xStart + position * (xEnd - xStart);
            float y = yStart + position * (yEnd - yStart);
            float angle = (float) Math.atan2(yEnd - yStart, xEnd - xStart);

            HomePieceOfFurniture piece = new HomePieceOfFurniture(found);
            piece.setX(x);
            piece.setY(y);
            piece.setAngle(angle);

            if (hasElevation) {
                piece.setElevation(elevation);
            }
            if (mirrored != null && mirrored) {
                piece.setModelMirrored(true);
            }

            home.addPieceOfFurniture(piece);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", piece.getName());
            result.put("x", round2(piece.getX()));
            result.put("y", round2(piece.getY()));
            result.put("angle", round2(Math.toDegrees(piece.getAngle())));
            result.put("elevation", round2(piece.getElevation()));
            result.put("width", round2(piece.getWidth()));
            result.put("depth", round2(piece.getDepth()));
            result.put("height", round2(piece.getHeight()));
            result.put("isDoorOrWindow", piece.isDoorOrWindow());
            result.put("mirrored", piece.isModelMirrored());
            result.put("wallId", wallId);
            result.put("position", round2(position));
            return result;
        });

        if (data == null) {
            return Response.error("Wall not found: wallId " + wallId + " is out of range");
        }

        return Response.ok(data);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public String getDescription() {
        return "Places a door or window from the catalog into a specific wall. "
                + "Searches the catalog by name, filtering only doors and windows (not regular furniture). "
                + "The piece is automatically positioned and rotated to align with the wall. "
                + "Use 'position' (0.0-1.0) to control placement along the wall: "
                + "0.0 = wall start, 0.5 = center (default), 1.0 = wall end. "
                + "Use 'elevation' for windows (typically 80-100 cm above floor). "
                + "Doors usually have elevation 0 (default from catalog). "
                + "Use get_state to find wall IDs and list_furniture_catalog to browse available doors/windows. "
                + "Use 'catalogId' for precise selection when multiple items share the same name.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", prop("string",
                "Door/window name to search in catalog (e.g., 'door', 'window', 'French door')"));
        properties.put("catalogId", prop("string",
                "Exact catalog ID for precise selection (bypasses name search). "
                        + "Use list_furniture_catalog to find catalog IDs"));
        properties.put("wallId", prop("integer",
                "ID of the wall to place the door/window in (from get_state)"));

        Map<String, Object> positionProp = new LinkedHashMap<>();
        positionProp.put("type", "number");
        positionProp.put("description",
                "Position along the wall: 0.0 = start, 0.5 = center, 1.0 = end");
        positionProp.put("default", 0.5);
        properties.put("position", positionProp);

        properties.put("elevation", prop("number",
                "Height above floor in cm. Doors default to 0, windows typically 80-100"));

        Map<String, Object> mirroredProp = new LinkedHashMap<>();
        mirroredProp.put("type", "boolean");
        mirroredProp.put("description", "Mirror the door/window model (e.g., change hinge side)");
        mirroredProp.put("default", false);
        properties.put("mirrored", mirroredProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("wallId"));
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
