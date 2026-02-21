package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.FormatUtil.colorToHex;
import static com.sh3d.mcp.command.FormatUtil.parseHexColor;
import static com.sh3d.mcp.command.FormatUtil.round2;
import static com.sh3d.mcp.command.SchemaUtil.nullableProp;
import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "modify_room".
 * Изменяет свойства комнаты по стабильному ID.
 */
public class ModifyRoomHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "name", "floorVisible", "ceilingVisible", "areaVisible",
            "floorColor", "ceilingColor",
            "floorShininess", "ceilingShininess"
    );

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null || id.trim().isEmpty()) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> params = request.getParams();
        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. "
                    + "Supported: name, floorVisible, ceilingVisible, areaVisible, "
                    + "floorColor, ceilingColor, floorShininess, ceilingShininess");
        }

        // Parse and validate colors before EDT
        Integer floorColor = null;
        boolean hasFloorColor = params.containsKey("floorColor");
        boolean clearFloorColor = false;
        if (hasFloorColor) {
            Object val = params.get("floorColor");
            if (val == null) {
                clearFloorColor = true;
            } else {
                floorColor = parseHexColor(val.toString());
                if (floorColor == null) {
                    return Response.error("Invalid floorColor format: '" + val + "'. Expected '#RRGGBB'");
                }
            }
        }

        Integer ceilingColor = null;
        boolean hasCeilingColor = params.containsKey("ceilingColor");
        boolean clearCeilingColor = false;
        if (hasCeilingColor) {
            Object val = params.get("ceilingColor");
            if (val == null) {
                clearCeilingColor = true;
            } else {
                ceilingColor = parseHexColor(val.toString());
                if (ceilingColor == null) {
                    return Response.error("Invalid ceilingColor format: '" + val + "'. Expected '#RRGGBB'");
                }
            }
        }

        // Validate shininess before EDT
        String shininessError = validateShininess(params);
        if (shininessError != null) {
            return Response.error(shininessError);
        }

        // Capture for lambda
        final Integer finalFloorColor = floorColor;
        final boolean doSetFloorColor = hasFloorColor;
        final boolean doClearFloorColor = clearFloorColor;
        final Integer finalCeilingColor = ceilingColor;
        final boolean doSetCeilingColor = hasCeilingColor;
        final boolean doClearCeilingColor = clearCeilingColor;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Room room = ObjectResolver.findRoom(home, id);
            if (room == null) {
                return null;
            }

            // Name
            if (params.containsKey("name")) {
                room.setName(request.getString("name"));
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
            if (doSetFloorColor) {
                room.setFloorColor(doClearFloorColor ? null : finalFloorColor);
            }
            if (doSetCeilingColor) {
                room.setCeilingColor(doClearCeilingColor ? null : finalCeilingColor);
            }

            // Shininess
            if (params.containsKey("floorShininess")) {
                room.setFloorShininess(request.getFloat("floorShininess"));
            }
            if (params.containsKey("ceilingShininess")) {
                room.setCeilingShininess(request.getFloat("ceilingShininess"));
            }

            return buildResponse(id, room);
        });

        if (data == null) {
            return Response.error("Room not found: " + id);
        }

        return Response.ok(data);
    }

    private static Map<String, Object> buildResponse(String id, Room room) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", room.getName());
        result.put("area", round2(room.getArea()));
        result.put("areaVisible", room.isAreaVisible());
        result.put("floorVisible", room.isFloorVisible());
        result.put("ceilingVisible", room.isCeilingVisible());
        result.put("floorColor", colorToHex(room.getFloorColor()));
        result.put("ceilingColor", colorToHex(room.getCeilingColor()));
        result.put("floorShininess", round2(room.getFloorShininess()));
        result.put("ceilingShininess", round2(room.getCeilingShininess()));
        result.put("xCenter", round2(room.getXCenter()));
        result.put("yCenter", round2(room.getYCenter()));

        float[][] points = room.getPoints();
        List<Object> pointList = new ArrayList<>();
        for (float[] pt : points) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("x", round2(pt[0]));
            p.put("y", round2(pt[1]));
            pointList.add(p);
        }
        result.put("points", pointList);

        return result;
    }

    // --- Validation ---

    private static String validateShininess(Map<String, Object> params) {
        String[] keys = {"floorShininess", "ceilingShininess"};
        for (String key : keys) {
            if (params.containsKey(key)) {
                Object val = params.get(key);
                if (val instanceof Number) {
                    float v = ((Number) val).floatValue();
                    if (v < 0 || v > 1) {
                        return "Parameter '" + key + "' must be between 0.0 and 1.0, got " + v;
                    }
                }
            }
        }
        return null;
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Modifies properties of an existing room by ID. Use get_state to find room IDs. "
                + "Only provided properties are changed; omitted ones remain unchanged. "
                + "Colors are hex strings like '#CCBB99' (beige floor), or null to reset to default. "
                + "Shininess ranges from 0.0 (matte) to 1.0 (glossy). "
                + "Room geometry (points) cannot be modified — use delete_room + create_room_polygon instead.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", prop("string", "Room ID from get_state"));
        properties.put("name", prop("string", "Room name (e.g. 'Kitchen', 'Living Room')"));
        properties.put("floorVisible", prop("boolean", "Whether floor surface is visible in 3D"));
        properties.put("ceilingVisible", prop("boolean", "Whether ceiling surface is visible in 3D"));
        properties.put("areaVisible", prop("boolean", "Whether area label is shown on the plan"));
        properties.put("floorColor", nullableProp("string",
                "Floor color as '#RRGGBB' (e.g. '#CCBB99' for beige), or null to reset"));
        properties.put("ceilingColor", nullableProp("string",
                "Ceiling color as '#RRGGBB', or null to reset"));
        properties.put("floorShininess", prop("number",
                "Floor shininess: 0.0 (matte) to 1.0 (glossy)"));
        properties.put("ceilingShininess", prop("number",
                "Ceiling shininess: 0.0 (matte) to 1.0 (glossy)"));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("id"));
        return schema;
    }

}
