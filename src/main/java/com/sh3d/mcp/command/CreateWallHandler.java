package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.FormatUtil.round2;
import static com.sh3d.mcp.command.SchemaUtil.prop;
import static com.sh3d.mcp.command.SchemaUtil.propWithDefault;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "create_wall".
 * Создаёт одиночную стену по двум точкам.
 */
public class CreateWallHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        float xStart = request.getFloat("xStart");
        float yStart = request.getFloat("yStart");
        float xEnd = request.getFloat("xEnd");
        float yEnd = request.getFloat("yEnd");
        float thickness = request.getFloat("thickness", 10.0f);
        float wallHeight = request.getFloat("height", 0f);

        if (thickness <= 0) {
            return Response.error("Parameter 'thickness' must be positive, got " + thickness);
        }

        float dx = xEnd - xStart;
        float dy = yEnd - yStart;
        if (dx == 0 && dy == 0) {
            return Response.error("Wall has zero length: start and end points are identical");
        }

        Wall created = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            float h = wallHeight > 0 ? wallHeight
                    : home.getWallHeight() > 0 ? home.getWallHeight()
                    : 250f;

            Wall wall = new Wall(xStart, yStart, xEnd, yEnd, thickness);
            wall.setHeight(h);
            home.addWall(wall);
            return wall;
        });

        float length = (float) Math.sqrt(dx * dx + dy * dy);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", created.getId());
        data.put("xStart", round2(xStart));
        data.put("yStart", round2(yStart));
        data.put("xEnd", round2(xEnd));
        data.put("yEnd", round2(yEnd));
        data.put("length", round2(length));
        data.put("thickness", round2(thickness));
        data.put("height", round2(created.getHeight()));
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Creates a single wall between two points in Sweet Home 3D. "
                + "Coordinates are in centimeters (e.g., 500 = 5 meters). "
                + "X points right, Y points down. "
                + "Returns the wall ID for use with connect_walls, delete_wall, modify_wall.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("xStart", prop("number", "X coordinate of wall start point in cm"));
        properties.put("yStart", prop("number", "Y coordinate of wall start point in cm"));
        properties.put("xEnd", prop("number", "X coordinate of wall end point in cm"));
        properties.put("yEnd", prop("number", "Y coordinate of wall end point in cm"));
        properties.put("thickness", propWithDefault("number", "Wall thickness in cm", 10));
        properties.put("height", propWithDefault("number", "Wall height in cm (0 = use home default or 250)", 0));
        schema.put("properties", properties);

        schema.put("required", Arrays.asList("xStart", "yStart", "xEnd", "yEnd"));
        return schema;
    }

}
