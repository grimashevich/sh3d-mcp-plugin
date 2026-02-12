package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "delete_furniture".
 * Удаляет мебель из сцены по ID (индексу из get_state).
 */
public class DeleteFurnitureHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        int id = (int) request.getFloat("id");

        if (id < 0) {
            return Response.error("Parameter 'id' must be non-negative, got " + id);
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<HomePieceOfFurniture> furniture = home.getFurniture();

            if (id >= furniture.size()) {
                return null;
            }

            HomePieceOfFurniture piece = furniture.get(id);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", piece.getName());
            info.put("x", round2(piece.getX()));
            info.put("y", round2(piece.getY()));

            home.deletePieceOfFurniture(piece);
            return info;
        });

        if (data == null) {
            return Response.error("Furniture not found: id " + id + " is out of range");
        }

        data.put("message", "Furniture '" + data.get("name") + "' deleted");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Deletes a piece of furniture from the scene by its ID. "
                + "Use get_state to find furniture IDs before deleting. "
                + "Note: after deletion, remaining furniture IDs may shift.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", prop("integer", "Furniture ID from get_state"));
        schema.put("properties", properties);

        schema.put("required", Arrays.asList("id"));
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
}
