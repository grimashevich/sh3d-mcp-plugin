package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

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
 *   boundingBox — {minX, minY, maxX, maxY}
 * </pre>
 */
public class GetStateHandler implements CommandHandler {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // TODO: реализовать
        // accessor.runOnEDT(() -> {
        //   Home home = accessor.getHome();
        //   Map<String, Object> data = new LinkedHashMap<>();
        //   data.put("wallCount", home.getWalls().size());
        //
        //   List<Object> furnitureList = new ArrayList<>();
        //   for (HomePieceOfFurniture piece : home.getFurniture()) {
        //     Map<String, Object> item = new LinkedHashMap<>();
        //     item.put("name", piece.getName());
        //     item.put("x", piece.getX());
        //     item.put("y", piece.getY());
        //     item.put("angle", Math.toDegrees(piece.getAngle()));
        //     item.put("width", piece.getWidth());
        //     item.put("depth", piece.getDepth());
        //     item.put("height", piece.getHeight());
        //     furnitureList.add(item);
        //   }
        //   data.put("furniture", furnitureList);
        //   data.put("roomCount", home.getRooms().size());
        //
        //   // bounding box по стенам
        //   // ...
        //   return data;
        // });
        throw new UnsupportedOperationException("TODO: implement get_state");
    }
}
