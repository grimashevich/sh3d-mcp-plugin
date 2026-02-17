package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.TextStyle;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "add_label".
 * Добавляет текстовую метку (аннотацию) на 2D-план.
 */
public class AddLabelHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> params = request.getParams();

        // Required: text
        String text = request.getString("text");
        if (text == null || text.isEmpty()) {
            return Response.error("Missing required parameter: 'text'");
        }

        // Required: x, y
        Object xVal = params.get("x");
        Object yVal = params.get("y");
        if (!(xVal instanceof Number) || !(yVal instanceof Number)) {
            return Response.error("Missing required numeric parameters: 'x' and 'y'");
        }
        float x = ((Number) xVal).floatValue();
        float y = ((Number) yVal).floatValue();

        // Optional: color
        Integer color = null;
        boolean hasColor = params.containsKey("color");
        if (hasColor) {
            Object colorVal = params.get("color");
            if (colorVal != null) {
                String hex = colorVal.toString();
                if (!hex.matches("^#[0-9A-Fa-f]{6}$")) {
                    return Response.error("Invalid color format: '" + hex + "'. Expected '#RRGGBB'");
                }
                color = Integer.parseInt(hex.substring(1), 16);
            }
        }

        // Optional: outlineColor
        Integer outlineColor = null;
        boolean hasOutlineColor = params.containsKey("outlineColor");
        if (hasOutlineColor) {
            Object outlineVal = params.get("outlineColor");
            if (outlineVal != null) {
                String hex = outlineVal.toString();
                if (!hex.matches("^#[0-9A-Fa-f]{6}$")) {
                    return Response.error("Invalid outlineColor format: '" + hex + "'. Expected '#RRGGBB'");
                }
                outlineColor = Integer.parseInt(hex.substring(1), 16);
            }
        }

        // Optional: angle (degrees)
        float angle = request.getFloat("angle", 0f);

        // Optional: elevation
        float elevation = request.getFloat("elevation", 0f);

        // Optional: pitch (degrees, nullable)
        Float pitch = null;
        boolean hasPitch = params.containsKey("pitch");
        if (hasPitch) {
            Object pitchVal = params.get("pitch");
            if (pitchVal instanceof Number) {
                pitch = (float) Math.toRadians(((Number) pitchVal).floatValue());
            }
            // null pitch = horizontal (on plan) — leave as null
        }

        // Optional: TextStyle (fontSize triggers creation)
        TextStyle style = null;
        if (params.containsKey("fontSize")) {
            float fontSize = request.getFloat("fontSize");
            Boolean bold = request.getBoolean("bold");
            Boolean italic = request.getBoolean("italic");

            TextStyle.Alignment alignment = TextStyle.Alignment.CENTER;
            String alignStr = request.getString("alignment");
            if (alignStr != null) {
                try {
                    alignment = TextStyle.Alignment.valueOf(alignStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Response.error("Invalid alignment: '" + alignStr
                            + "'. Expected LEFT, CENTER, or RIGHT");
                }
            }

            style = new TextStyle(null, fontSize, bold != null && bold, italic != null && italic, alignment);
        }

        final Integer finalColor = color;
        final boolean setColor = hasColor;
        final Integer finalOutlineColor = outlineColor;
        final boolean setOutlineColor = hasOutlineColor;
        final Float finalPitch = pitch;
        final boolean setPitch = hasPitch;
        final TextStyle finalStyle = style;

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Label label = new Label(text, x, y);

            if (angle != 0f) {
                label.setAngle((float) Math.toRadians(angle));
            }

            if (setColor) {
                label.setColor(finalColor);
            }

            if (setOutlineColor) {
                label.setOutlineColor(finalOutlineColor);
            }

            if (elevation != 0f) {
                label.setElevation(elevation);
            }

            if (setPitch) {
                label.setPitch(finalPitch);
            }

            if (finalStyle != null) {
                label.setStyle(finalStyle);
            }

            home.addLabel(label);

            int id = new ArrayList<>(home.getLabels()).indexOf(label);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("text", label.getText());
            result.put("x", round2(label.getX()));
            result.put("y", round2(label.getY()));
            result.put("angle", round2((float) Math.toDegrees(label.getAngle())));
            result.put("color", colorToHex(label.getColor()));
            result.put("outlineColor", colorToHex(label.getOutlineColor()));
            result.put("elevation", round2(label.getElevation()));
            Float lp = label.getPitch();
            result.put("pitch", lp != null ? round2((float) Math.toDegrees(lp)) : null);
            if (label.getStyle() != null) {
                TextStyle ts = label.getStyle();
                Map<String, Object> styleMap = new LinkedHashMap<>();
                styleMap.put("fontSize", ts.getFontSize());
                styleMap.put("bold", ts.isBold());
                styleMap.put("italic", ts.isItalic());
                styleMap.put("alignment", ts.getAlignment().name());
                result.put("style", styleMap);
            }
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Add a text label (annotation) to the 2D plan. "
                + "Labels can display room names, dimensions, notes, or any text. "
                + "Coordinates are in centimeters. "
                + "Optionally set font size, style, color, rotation angle, and 3D elevation.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("text", prop("string", "Label text content (e.g. 'Living Room', '5.0m', 'Entry')"));
        properties.put("x", prop("number", "X position in centimeters"));
        properties.put("y", prop("number", "Y position in centimeters"));

        Map<String, Object> colorProp = new LinkedHashMap<>();
        colorProp.put("type", Arrays.asList("string", "null"));
        colorProp.put("description", "Text color as hex '#RRGGBB' (e.g. '#000000' for black), or null for default");
        properties.put("color", colorProp);

        Map<String, Object> outlineColorProp = new LinkedHashMap<>();
        outlineColorProp.put("type", Arrays.asList("string", "null"));
        outlineColorProp.put("description", "Outline color as hex '#RRGGBB', or null for no outline");
        properties.put("outlineColor", outlineColorProp);

        properties.put("angle", propWithDefault("number", "Rotation angle in degrees (clockwise)", 0));
        properties.put("fontSize", prop("number", "Font size in points (e.g. 18, 24, 36). If set, creates a TextStyle"));
        properties.put("bold", propWithDefault("boolean", "Bold text (requires fontSize)", false));
        properties.put("italic", propWithDefault("boolean", "Italic text (requires fontSize)", false));

        Map<String, Object> alignProp = new LinkedHashMap<>();
        alignProp.put("type", "string");
        alignProp.put("enum", Arrays.asList("LEFT", "CENTER", "RIGHT"));
        alignProp.put("description", "Text alignment (requires fontSize). Default: CENTER");
        alignProp.put("default", "CENTER");
        properties.put("alignment", alignProp);

        properties.put("elevation", propWithDefault("number", "Elevation in 3D view (cm). 0 = on the floor", 0));

        Map<String, Object> pitchProp = new LinkedHashMap<>();
        pitchProp.put("type", Arrays.asList("number", "null"));
        pitchProp.put("description", "Pitch angle in degrees for 3D view. null = flat on plan (default). "
                + "90 = vertical (like a wall sign)");
        properties.put("pitch", pitchProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("text", "x", "y"));
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
