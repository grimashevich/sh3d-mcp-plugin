package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "create_room_polygon".
 * Создаёт комнату (Room) по массиву точек полигона.
 */
public class CreateRoomPolygonHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> params = request.getParams();

        // Parse points
        Object pointsObj = params.get("points");
        if (pointsObj == null) {
            return Response.error("Missing required parameter: 'points'");
        }
        if (!(pointsObj instanceof List)) {
            return Response.error("Parameter 'points' must be an array of {x, y} objects");
        }

        @SuppressWarnings("unchecked")
        List<Object> pointsList = (List<Object>) pointsObj;

        if (pointsList.size() < 3) {
            return Response.error("Parameter 'points' must contain at least 3 points, got " + pointsList.size());
        }

        float[][] polygon = new float[pointsList.size()][2];
        for (int i = 0; i < pointsList.size(); i++) {
            Object ptObj = pointsList.get(i);
            if (!(ptObj instanceof Map)) {
                return Response.error("Point at index " + i + " must be an object with 'x' and 'y'");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> pt = (Map<String, Object>) ptObj;

            Object xVal = pt.get("x");
            Object yVal = pt.get("y");
            if (!(xVal instanceof Number) || !(yVal instanceof Number)) {
                return Response.error("Point at index " + i + " must have numeric 'x' and 'y'");
            }
            polygon[i][0] = ((Number) xVal).floatValue();
            polygon[i][1] = ((Number) yVal).floatValue();
        }

        // Validate colors before EDT
        Integer floorColor = null;
        boolean hasFloorColor = params.containsKey("floorColor");
        if (hasFloorColor) {
            Object colorVal = params.get("floorColor");
            if (colorVal != null) {
                String hex = colorVal.toString();
                if (!hex.matches("^#[0-9A-Fa-f]{6}$")) {
                    return Response.error("Invalid floorColor format: '" + hex + "'. Expected '#RRGGBB'");
                }
                floorColor = Integer.parseInt(hex.substring(1), 16);
            }
        }

        Integer ceilingColor = null;
        boolean hasCeilingColor = params.containsKey("ceilingColor");
        if (hasCeilingColor) {
            Object colorVal = params.get("ceilingColor");
            if (colorVal != null) {
                String hex = colorVal.toString();
                if (!hex.matches("^#[0-9A-Fa-f]{6}$")) {
                    return Response.error("Invalid ceilingColor format: '" + hex + "'. Expected '#RRGGBB'");
                }
                ceilingColor = Integer.parseInt(hex.substring(1), 16);
            }
        }

        final Integer finalFloorColor = floorColor;
        final boolean setFloorColor = hasFloorColor;
        final Integer finalCeilingColor = ceilingColor;
        final boolean setCeilingColor = hasCeilingColor;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Room room = new Room(polygon);

            // Name
            String name = request.getString("name");
            if (name != null) {
                room.setName(name);
            }

            // Visibility
            Boolean floorVisible = request.getBoolean("floorVisible");
            if (floorVisible != null) {
                room.setFloorVisible(floorVisible);
            }
            Boolean ceilingVisible = request.getBoolean("ceilingVisible");
            if (ceilingVisible != null) {
                room.setCeilingVisible(ceilingVisible);
            }
            Boolean areaVisible = request.getBoolean("areaVisible");
            if (areaVisible != null) {
                room.setAreaVisible(areaVisible);
            }

            // Colors
            if (setFloorColor) {
                room.setFloorColor(finalFloorColor);
            }
            if (setCeilingColor) {
                room.setCeilingColor(finalCeilingColor);
            }

            home.addRoom(room);

            // Build response
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", room.getId());
            result.put("name", room.getName());
            result.put("area", round2(room.getArea()));
            result.put("areaVisible", room.isAreaVisible());
            result.put("floorVisible", room.isFloorVisible());
            result.put("ceilingVisible", room.isCeilingVisible());
            result.put("floorColor", colorToHex(room.getFloorColor()));
            result.put("ceilingColor", colorToHex(room.getCeilingColor()));
            result.put("xCenter", round2(room.getXCenter()));
            result.put("yCenter", round2(room.getYCenter()));

            List<Object> pointList = new ArrayList<>();
            for (float[] pt : room.getPoints()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("x", round2(pt[0]));
                p.put("y", round2(pt[1]));
                pointList.add(p);
            }
            result.put("points", pointList);

            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Creates a room (floor/ceiling polygon) from an array of points. "
                + "Rooms define floor and ceiling surfaces, independent of walls. "
                + "Coordinates are in centimeters. Minimum 3 points required. "
                + "Use create_walls for rectangular wall outlines.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        // Points array
        Map<String, Object> pointSchema = new LinkedHashMap<>();
        pointSchema.put("type", "object");
        Map<String, Object> pointProps = new LinkedHashMap<>();
        pointProps.put("x", prop("number", "X coordinate in cm"));
        pointProps.put("y", prop("number", "Y coordinate in cm"));
        pointSchema.put("properties", pointProps);
        pointSchema.put("required", Arrays.asList("x", "y"));

        Map<String, Object> pointsArray = new LinkedHashMap<>();
        pointsArray.put("type", "array");
        pointsArray.put("items", pointSchema);
        pointsArray.put("minItems", 3);
        pointsArray.put("description", "Polygon vertices in cm. Minimum 3 points. "
                + "Example: [{\"x\":0,\"y\":0},{\"x\":500,\"y\":0},{\"x\":500,\"y\":400},{\"x\":0,\"y\":400}]");
        properties.put("points", pointsArray);

        properties.put("name", prop("string", "Room name (e.g. 'Living Room')"));
        properties.put("floorVisible", propWithDefault("boolean", "Whether floor is visible", true));
        properties.put("ceilingVisible", propWithDefault("boolean", "Whether ceiling is visible", true));

        Map<String, Object> floorColorProp = new LinkedHashMap<>();
        floorColorProp.put("type", Arrays.asList("string", "null"));
        floorColorProp.put("description", "Floor color as hex '#RRGGBB' (e.g. '#CCBB99'), or null for default");
        properties.put("floorColor", floorColorProp);

        Map<String, Object> ceilingColorProp = new LinkedHashMap<>();
        ceilingColorProp.put("type", Arrays.asList("string", "null"));
        ceilingColorProp.put("description", "Ceiling color as hex '#RRGGBB', or null for default");
        properties.put("ceilingColor", ceilingColorProp);

        properties.put("areaVisible", propWithDefault("boolean", "Whether area label is shown", false));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("points"));
        return schema;
    }

    private static float round2(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static String colorToHex(Integer color) {
        return color != null ? String.format("#%06X", color & 0xFFFFFF) : null;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> propWithDefault(String type, String description, Object defaultValue) {
        Map<String, Object> p = prop(type, description);
        p.put("default", defaultValue);
        return p;
    }
}
