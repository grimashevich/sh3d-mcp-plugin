package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "get_state".
 * Возвращает текущее состояние сцены: стены, мебель, комнаты, bounding box.
 *
 * <pre>
 * Параметры: нет
 *
 * Ответ:
 *   wallCount   — количество стен
 *   furniture   — массив [{name, x, y, angle, width, depth, height}, ...]
 *   roomCount   — количество комнат
 *   boundingBox — {minX, minY, maxX, maxY} или null (если нет стен)
 * </pre>
 */
public class GetStateHandler implements CommandHandler {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Map<String, Object> result = new LinkedHashMap<>();

            Collection<Wall> walls = home.getWalls();
            result.put("wallCount", walls.size());

            List<Object> furnitureList = new ArrayList<>();
            for (HomePieceOfFurniture piece : home.getFurniture()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", piece.getName());
                item.put("x", (double) piece.getX());
                item.put("y", (double) piece.getY());
                item.put("angle", Math.toDegrees(piece.getAngle()));
                item.put("width", (double) piece.getWidth());
                item.put("depth", (double) piece.getDepth());
                item.put("height", (double) piece.getHeight());
                furnitureList.add(item);
            }
            result.put("furniture", furnitureList);

            result.put("roomCount", home.getRooms().size());

            if (!walls.isEmpty()) {
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                for (Wall w : walls) {
                    minX = Math.min(minX, Math.min(w.getXStart(), w.getXEnd()));
                    minY = Math.min(minY, Math.min(w.getYStart(), w.getYEnd()));
                    maxX = Math.max(maxX, Math.max(w.getXStart(), w.getXEnd()));
                    maxY = Math.max(maxY, Math.max(w.getYStart(), w.getYEnd()));
                }
                Map<String, Object> bb = new LinkedHashMap<>();
                bb.put("minX", (double) minX);
                bb.put("minY", (double) minY);
                bb.put("maxX", (double) maxX);
                bb.put("maxY", (double) maxY);
                result.put("boundingBox", bb);
            } else {
                result.put("boundingBox", null);
            }

            return result;
        });

        return Response.ok(data);
    }
}
