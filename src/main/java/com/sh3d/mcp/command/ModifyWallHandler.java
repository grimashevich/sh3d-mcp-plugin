package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.FormatUtil.colorToHex;
import static com.sh3d.mcp.command.FormatUtil.parseHexColor;
import static com.sh3d.mcp.command.FormatUtil.round2;
import static com.sh3d.mcp.command.SchemaUtil.nullableProp;
import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "modify_wall".
 * Изменяет свойства стены по стабильному ID.
 *
 * Стороны стены определяются направлением от (xStart,yStart) к (xEnd,yEnd):
 * - Left side — сторона слева при движении от start к end
 * - Right side — сторона справа при движении от start к end
 */
public class ModifyWallHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "xStart", "yStart", "xEnd", "yEnd",
            "height", "heightAtEnd", "thickness", "arcExtent",
            "leftSideColor", "rightSideColor", "topColor", "color",
            "leftSideShininess", "rightSideShininess", "shininess"
    );

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getRequiredString("id");

        Map<String, Object> params = request.getParams();
        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. "
                    + "Supported: xStart, yStart, xEnd, yEnd, "
                    + "height, heightAtEnd, thickness, arcExtent, "
                    + "leftSideColor, rightSideColor, topColor, color, "
                    + "leftSideShininess, rightSideShininess, shininess");
        }

        // Parse and validate colors before EDT
        ParsedColors colors = parseColors(params);
        if (colors.error != null) {
            return Response.error(colors.error);
        }

        // Validate shininess before EDT
        String shininessError = validateShininess(params);
        if (shininessError != null) {
            return Response.error(shininessError);
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Wall wall = ObjectResolver.findWall(home, id);

            if (wall == null) {
                return null;
            }

            // Coordinates
            if (params.containsKey("xStart")) {
                wall.setXStart(request.getFloat("xStart"));
            }
            if (params.containsKey("yStart")) {
                wall.setYStart(request.getFloat("yStart"));
            }
            if (params.containsKey("xEnd")) {
                wall.setXEnd(request.getFloat("xEnd"));
            }
            if (params.containsKey("yEnd")) {
                wall.setYEnd(request.getFloat("yEnd"));
            }

            // Height
            if (params.containsKey("height")) {
                float h = request.getFloat("height");
                if (h <= 0) {
                    return errorMap("Parameter 'height' must be positive, got " + h);
                }
                wall.setHeight(h);
            }

            if (params.containsKey("heightAtEnd")) {
                Object val = params.get("heightAtEnd");
                if (val == null) {
                    wall.setHeightAtEnd(null);
                } else {
                    float h = request.getFloat("heightAtEnd");
                    if (h <= 0) {
                        return errorMap("Parameter 'heightAtEnd' must be positive, got " + h);
                    }
                    wall.setHeightAtEnd(h);
                }
            }

            // Thickness
            if (params.containsKey("thickness")) {
                float t = request.getFloat("thickness");
                if (t <= 0) {
                    return errorMap("Parameter 'thickness' must be positive, got " + t);
                }
                wall.setThickness(t);
            }

            // Arc extent
            if (params.containsKey("arcExtent")) {
                Object val = params.get("arcExtent");
                if (val == null) {
                    wall.setArcExtent(null);
                } else {
                    float arc = request.getFloat("arcExtent");
                    wall.setArcExtent((float) Math.toRadians(arc));
                }
            }

            // Colors — 'color' shortcut sets both sides + top
            if (colors.colorBoth != null) {
                if (colors.colorBothClear) {
                    wall.setLeftSideColor(null);
                    wall.setRightSideColor(null);
                    wall.setTopColor(null);
                } else {
                    wall.setLeftSideColor(colors.colorBoth);
                    wall.setRightSideColor(colors.colorBoth);
                    wall.setTopColor(colors.colorBoth);
                }
            }
            // Individual colors override 'color' if both specified
            if (colors.leftColor != null) {
                wall.setLeftSideColor(colors.leftColorClear ? null : colors.leftColor);
            }
            if (colors.rightColor != null) {
                wall.setRightSideColor(colors.rightColorClear ? null : colors.rightColor);
            }
            if (colors.topColor != null) {
                wall.setTopColor(colors.topColorClear ? null : colors.topColor);
            }

            // Shininess — 'shininess' shortcut sets both sides
            if (params.containsKey("shininess")) {
                float s = request.getFloat("shininess");
                wall.setLeftSideShininess(s);
                wall.setRightSideShininess(s);
            }
            if (params.containsKey("leftSideShininess")) {
                wall.setLeftSideShininess(request.getFloat("leftSideShininess"));
            }
            if (params.containsKey("rightSideShininess")) {
                wall.setRightSideShininess(request.getFloat("rightSideShininess"));
            }

            // Build response
            return buildResponse(id, wall);
        });

        if (data == null) {
            return Response.error("Wall not found: id '" + id + "'");
        }

        // Check for inline error
        if (data.containsKey("__error")) {
            return Response.error((String) data.get("__error"));
        }

        return Response.ok(data);
    }

    private static Map<String, Object> buildResponse(String id, Wall wall) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("xStart", round2(wall.getXStart()));
        result.put("yStart", round2(wall.getYStart()));
        result.put("xEnd", round2(wall.getXEnd()));
        result.put("yEnd", round2(wall.getYEnd()));
        result.put("thickness", round2(wall.getThickness()));
        result.put("height", wall.getHeight() != null ? round2(wall.getHeight()) : null);
        result.put("heightAtEnd", wall.getHeightAtEnd() != null ? round2(wall.getHeightAtEnd()) : null);
        result.put("arcExtent", wall.getArcExtent() != null
                ? round2((float) Math.toDegrees(wall.getArcExtent())) : null);
        result.put("leftSideColor", colorToHex(wall.getLeftSideColor()));
        result.put("rightSideColor", colorToHex(wall.getRightSideColor()));
        result.put("topColor", colorToHex(wall.getTopColor()));
        result.put("leftSideShininess", round2(wall.getLeftSideShininess()));
        result.put("rightSideShininess", round2(wall.getRightSideShininess()));
        return result;
    }

    // --- Color parsing ---

    private static ParsedColors parseColors(Map<String, Object> params) {
        ParsedColors c = new ParsedColors();

        // 'color' shortcut
        if (params.containsKey("color")) {
            Object val = params.get("color");
            if (val == null) {
                c.colorBoth = 0;
                c.colorBothClear = true;
            } else {
                Integer parsed = parseHexColor(val.toString());
                if (parsed == null) {
                    c.error = "Invalid color format: '" + val + "'. Expected '#RRGGBB'";
                    return c;
                }
                c.colorBoth = parsed;
            }
        }

        // Individual colors
        c.error = parseSideColor(params, "leftSideColor", c, "left");
        if (c.error != null) return c;
        c.error = parseSideColor(params, "rightSideColor", c, "right");
        if (c.error != null) return c;
        c.error = parseSideColor(params, "topColor", c, "top");
        return c;
    }

    private static String parseSideColor(Map<String, Object> params, String key,
                                         ParsedColors c, String side) {
        if (!params.containsKey(key)) return null;
        Object val = params.get(key);
        if (val == null) {
            switch (side) {
                case "left": c.leftColor = 0; c.leftColorClear = true; break;
                case "right": c.rightColor = 0; c.rightColorClear = true; break;
                case "top": c.topColor = 0; c.topColorClear = true; break;
            }
            return null;
        }
        Integer parsed = parseHexColor(val.toString());
        if (parsed == null) {
            return "Invalid " + key + " format: '" + val + "'. Expected '#RRGGBB'";
        }
        switch (side) {
            case "left": c.leftColor = parsed; break;
            case "right": c.rightColor = parsed; break;
            case "top": c.topColor = parsed; break;
        }
        return null;
    }

    private static String validateShininess(Map<String, Object> params) {
        String[] keys = {"shininess", "leftSideShininess", "rightSideShininess"};
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

    private static Map<String, Object> errorMap(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("__error", message);
        return m;
    }

    // --- Parsed colors holder ---

    private static class ParsedColors {
        String error;
        Integer colorBoth;
        boolean colorBothClear;
        Integer leftColor;
        boolean leftColorClear;
        Integer rightColor;
        boolean rightColorClear;
        Integer topColor;
        boolean topColorClear;
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Modifies properties of an existing wall by ID. Use get_state to find wall IDs. "
                + "Only provided properties are changed; omitted ones remain unchanged. "
                + "Wall sides are relative to direction from start to end point: "
                + "'left' is the side on your left when walking from start to end, 'right' is the other side. "
                + "Use 'color' to set all sides at once, or 'leftSideColor'/'rightSideColor'/'topColor' individually. "
                + "Shininess 0.0 = matte, 1.0 = glossy.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", prop("string", "Wall ID from get_state"));
        properties.put("xStart", prop("number", "New X coordinate of wall start point in cm"));
        properties.put("yStart", prop("number", "New Y coordinate of wall start point in cm"));
        properties.put("xEnd", prop("number", "New X coordinate of wall end point in cm"));
        properties.put("yEnd", prop("number", "New Y coordinate of wall end point in cm"));
        properties.put("height", prop("number", "Wall height in cm (e.g. 250 for 2.5m)"));
        properties.put("heightAtEnd", nullableProp("number",
                "Height at end point in cm for sloped walls (null = same as height)"));
        properties.put("thickness", prop("number", "Wall thickness in cm"));
        properties.put("arcExtent", nullableProp("number",
                "Arc extent in degrees for curved walls (positive = curve left, negative = curve right, null = straight)"));
        properties.put("color", nullableProp("string",
                "Color as '#RRGGBB' for all sides at once, or null to reset all sides"));
        properties.put("leftSideColor", nullableProp("string",
                "Left side color as '#RRGGBB', or null to reset"));
        properties.put("rightSideColor", nullableProp("string",
                "Right side color as '#RRGGBB', or null to reset"));
        properties.put("topColor", nullableProp("string",
                "Top color as '#RRGGBB', or null to reset"));
        properties.put("shininess", prop("number",
                "Shininess for both sides: 0.0 (matte) to 1.0 (glossy)"));
        properties.put("leftSideShininess", prop("number",
                "Left side shininess: 0.0 (matte) to 1.0 (glossy)"));
        properties.put("rightSideShininess", prop("number",
                "Right side shininess: 0.0 (matte) to 1.0 (glossy)"));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("id"));
        return schema;
    }

}
