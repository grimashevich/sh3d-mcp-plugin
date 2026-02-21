package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.FormatUtil.round2;
import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "modify_furniture".
 * Изменяет свойства мебели по стабильному строковому ID.
 */
public class ModifyFurnitureHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "x", "y", "angle", "elevation", "width", "depth", "height",
            "color", "visible", "mirrored", "name"
    );

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> params = request.getParams();
        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. "
                    + "Supported: x, y, angle, elevation, width, depth, height, color, visible, mirrored, name");
        }

        // Validate color format before EDT
        Integer parsedColor = null;
        boolean clearColor = false;
        if (params.containsKey("color")) {
            Object colorVal = params.get("color");
            if (colorVal == null) {
                clearColor = true;
            } else {
                String hex = colorVal.toString();
                if (!hex.matches("^#[0-9A-Fa-f]{6}$")) {
                    return Response.error("Invalid color format: '" + hex + "'. Expected '#RRGGBB'");
                }
                parsedColor = Integer.parseInt(hex.substring(1), 16);
            }
        }

        final Integer colorToSet = parsedColor;
        final boolean doClearColor = clearColor;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, id);

            if (piece == null) {
                return null;
            }

            // Position
            if (params.containsKey("x")) {
                piece.setX(request.getFloat("x"));
            }
            if (params.containsKey("y")) {
                piece.setY(request.getFloat("y"));
            }
            if (params.containsKey("angle")) {
                piece.setAngle((float) Math.toRadians(request.getFloat("angle")));
            }
            if (params.containsKey("elevation")) {
                piece.setElevation(request.getFloat("elevation"));
            }

            // Dimensions
            if (params.containsKey("width")) {
                piece.setWidth(request.getFloat("width"));
            }
            if (params.containsKey("depth")) {
                piece.setDepth(request.getFloat("depth"));
            }
            if (params.containsKey("height")) {
                piece.setHeight(request.getFloat("height"));
            }

            // Appearance
            if (doClearColor) {
                piece.setColor(null);
            } else if (colorToSet != null) {
                piece.setColor(colorToSet);
            }

            // Flags
            Boolean visible = request.getBoolean("visible");
            if (visible != null) {
                piece.setVisible(visible);
            }
            Boolean mirrored = request.getBoolean("mirrored");
            if (mirrored != null) {
                piece.setModelMirrored(mirrored);
            }

            // Name
            if (params.containsKey("name")) {
                String newName = request.getString("name");
                if (newName != null) {
                    piece.setName(newName);
                }
            }

            // Build response with current state
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", piece.getId());
            result.put("name", piece.getName());
            result.put("x", round2(piece.getX()));
            result.put("y", round2(piece.getY()));
            result.put("angle", round2((float) Math.toDegrees(piece.getAngle())));
            result.put("elevation", round2(piece.getElevation()));
            result.put("width", round2(piece.getWidth()));
            result.put("depth", round2(piece.getDepth()));
            result.put("height", round2(piece.getHeight()));
            Integer color = piece.getColor();
            result.put("color", color != null ? String.format("#%06X", color & 0xFFFFFF) : null);
            result.put("visible", piece.isVisible());
            result.put("mirrored", piece.isModelMirrored());
            return result;
        });

        if (data == null) {
            return Response.error("Furniture not found: " + id);
        }

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Modifies properties of existing furniture by ID. Use get_state to find furniture IDs. "
                + "Only provided properties are changed; omitted properties remain unchanged. "
                + "Coordinates are in centimeters, angle in degrees. "
                + "Color is a hex string like '#FF0000' (red) or null to reset to default.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", prop("string", "Furniture ID from get_state"));
        properties.put("x", prop("number", "New X coordinate in cm"));
        properties.put("y", prop("number", "New Y coordinate in cm"));
        properties.put("angle", prop("number", "New rotation angle in degrees (0-360)"));
        properties.put("elevation", prop("number", "Height above floor in cm"));
        properties.put("width", prop("number", "New width in cm"));
        properties.put("depth", prop("number", "New depth in cm"));
        properties.put("height", prop("number", "New height in cm"));

        Map<String, Object> colorProp = new LinkedHashMap<>();
        colorProp.put("type", Arrays.asList("string", "null"));
        colorProp.put("description", "Color as hex '#RRGGBB' (e.g. '#FF0000' for red), or null to reset");
        properties.put("color", colorProp);

        properties.put("visible", prop("boolean", "Whether furniture is visible in the scene"));
        properties.put("mirrored", prop("boolean", "Whether furniture model is mirrored"));
        properties.put("name", prop("string", "New display name for the furniture"));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("id"));
        return schema;
    }

}
