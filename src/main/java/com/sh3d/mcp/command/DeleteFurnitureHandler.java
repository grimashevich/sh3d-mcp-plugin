package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "delete_furniture".
 * Удаляет мебель из сцены по стабильному строковому ID.
 */
public class DeleteFurnitureHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getRequiredString("id");

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, id);

            if (piece == null) {
                return null;
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", piece.getName());
            info.put("x", round2(piece.getX()));
            info.put("y", round2(piece.getY()));

            home.deletePieceOfFurniture(piece);
            return info;
        });

        if (data == null) {
            return Response.error("Furniture not found: " + id);
        }

        data.put("message", "Furniture '" + data.get("name") + "' deleted");
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Deletes a piece of furniture from the scene by its ID. "
                + "Use get_state to find furniture IDs before deleting.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", prop("string", "Furniture ID from get_state"));
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
