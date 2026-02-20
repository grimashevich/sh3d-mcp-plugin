package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Level;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "add_level".
 * Создаёт новый уровень (этаж) и делает его активным.
 */
public class AddLevelHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            return Response.error("Parameter 'name' is required and must not be empty");
        }

        float elevation = request.getFloat("elevation");
        float height = request.getFloat("height", 250f);
        float floorThickness = request.getFloat("floorThickness", 12f);

        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive, got " + height);
        }
        if (floorThickness < 0) {
            return Response.error("Parameter 'floorThickness' must be non-negative, got " + floorThickness);
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            Level level = new Level(name.trim(), elevation, floorThickness, height);
            home.addLevel(level);
            home.setSelectedLevel(level);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", level.getId());
            result.put("name", level.getName());
            result.put("elevation", round2(level.getElevation()));
            result.put("height", round2(level.getHeight()));
            result.put("floorThickness", round2(level.getFloorThickness()));
            result.put("levelCount", home.getLevels().size());
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Creates a new level (floor/storey) in the home. The new level becomes the active (selected) level. "
                + "All subsequent create commands (walls, rooms, furniture) will be placed on the selected level. "
                + "Elevation is the bottom height of the level in cm (e.g., 0 for ground floor, 250 for second floor). "
                + "Height is the wall height on this level (default 250 cm). "
                + "Use list_levels to see all levels, set_selected_level to switch between them.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", prop("string", "Name of the level (e.g., 'Ground Floor', 'Second Floor', 'Attic')"));
        properties.put("elevation", prop("number", "Bottom elevation of the level in cm. 0 for ground floor, typically previous level's elevation + height for upper floors"));
        properties.put("height", propWithDefault("number", "Wall height on this level in cm", 250));
        properties.put("floorThickness", propWithDefault("number", "Floor/ceiling slab thickness in cm", 12));
        schema.put("properties", properties);

        schema.put("required", Arrays.asList("name", "elevation"));
        return schema;
    }

    private static float round2(float v) {
        return Math.round(v * 100f) / 100f;
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
