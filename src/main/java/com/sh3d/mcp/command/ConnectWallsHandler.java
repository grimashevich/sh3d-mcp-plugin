package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.SchemaUtil.enumProp;
import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "connect_walls".
 * Соединяет две стены по ID, устанавливая двустороннюю связь (setWallAtStart/End).
 * Необходимо для корректного рендеринга стен, созданных через create_wall.
 */
public class ConnectWallsHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String wall1Id = request.getRequiredString("wall1Id");
        String wall2Id = request.getRequiredString("wall2Id");

        if (wall1Id.equals(wall2Id)) {
            return Response.error("Cannot connect a wall to itself (wall1Id == wall2Id == " + wall1Id + ")");
        }

        String wall1EndParam = request.getString("wall1End");
        String wall2EndParam = request.getString("wall2End");

        if (wall1EndParam != null && !wall1EndParam.equals("start") && !wall1EndParam.equals("end")) {
            return Response.error("Parameter 'wall1End' must be 'start' or 'end', got '" + wall1EndParam + "'");
        }
        if (wall2EndParam != null && !wall2EndParam.equals("start") && !wall2EndParam.equals("end")) {
            return Response.error("Parameter 'wall2End' must be 'start' or 'end', got '" + wall2EndParam + "'");
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Wall wall1 = ObjectResolver.findWall(home, wall1Id);

            if (wall1 == null) {
                return null;
            }

            Wall wall2 = ObjectResolver.findWall(home, wall2Id);
            if (wall2 == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("_error", "wall2");
                return err;
            }

            String w1End = wall1EndParam;
            String w2End = wall2EndParam;

            // Автоопределение ближайших концов, если не указаны
            if (w1End == null || w2End == null) {
                String[] best = findClosestEnds(wall1, wall2);
                if (w1End == null) w1End = best[0];
                if (w2End == null) w2End = best[1];
            }

            // Установить двустороннюю связь
            setConnection(wall1, w1End, wall2);
            setConnection(wall2, w2End, wall1);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("wall1Id", wall1Id);
            result.put("wall1End", w1End);
            result.put("wall2Id", wall2Id);
            result.put("wall2End", w2End);
            result.put("message", "Walls connected: wall " + wall1Id + " (" + w1End
                    + ") \u2194 wall " + wall2Id + " (" + w2End + ")");
            return result;
        });

        if (data == null) {
            return Response.error("Wall not found: wall1Id '" + wall1Id + "'");
        }
        if (data.containsKey("_error")) {
            return Response.error("Wall not found: wall2Id '" + wall2Id + "'");
        }

        return Response.ok(data);
    }

    /**
     * Находит ближайшую пару концов двух стен.
     * Возвращает ["start"|"end", "start"|"end"] для wall1 и wall2 соответственно.
     */
    static String[] findClosestEnds(Wall wall1, Wall wall2) {
        float[][] endpoints = {
                {wall1.getXStart(), wall1.getYStart()},
                {wall1.getXEnd(), wall1.getYEnd()},
                {wall2.getXStart(), wall2.getYStart()},
                {wall2.getXEnd(), wall2.getYEnd()}
        };

        String[] w1Labels = {"start", "end"};
        String[] w2Labels = {"start", "end"};

        float minDist = Float.MAX_VALUE;
        String bestW1 = "end";
        String bestW2 = "start";

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                float dx = endpoints[i][0] - endpoints[2 + j][0];
                float dy = endpoints[i][1] - endpoints[2 + j][1];
                float dist = dx * dx + dy * dy;
                if (dist < minDist) {
                    minDist = dist;
                    bestW1 = w1Labels[i];
                    bestW2 = w2Labels[j];
                }
            }
        }

        return new String[]{bestW1, bestW2};
    }

    private static void setConnection(Wall wall, String end, Wall other) {
        if ("start".equals(end)) {
            wall.setWallAtStart(other);
        } else {
            wall.setWallAtEnd(other);
        }
    }

    @Override
    public String getDescription() {
        return "Connects two walls at their endpoints for proper corner rendering. "
                + "Use after creating individual walls with create_wall. "
                + "If wall1End/wall2End are omitted, automatically detects the closest endpoints. "
                + "Connection is bidirectional (both walls reference each other).";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("wall1Id", prop("string", "ID of the first wall (from get_state)"));
        properties.put("wall2Id", prop("string", "ID of the second wall (from get_state)"));
        properties.put("wall1End", enumProp(
                "Which endpoint of wall1 to connect: 'start' or 'end'. Auto-detected if omitted.",
                Arrays.asList("start", "end")));
        properties.put("wall2End", enumProp(
                "Which endpoint of wall2 to connect: 'start' or 'end'. Auto-detected if omitted.",
                Arrays.asList("start", "end")));
        schema.put("properties", properties);

        schema.put("required", Arrays.asList("wall1Id", "wall2Id"));
        return schema;
    }

}
