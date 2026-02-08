package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "create_walls".
 * Создаёт 4 стены прямоугольной комнаты и соединяет их.
 *
 * <pre>
 * Параметры:
 *   x, y       — координаты верхнего левого угла (float, см)
 *   width      — ширина (float, см, > 0)
 *   height     — высота (float, см, > 0)
 *   thickness  — толщина стен (float, default 10.0)
 *
 * Логика (в EDT):
 *   1. Рассчитать 4 угла: A(x,y) B(x+w,y) C(x+w,y+h) D(x,y+h)
 *   2. Создать 4 Wall: AB, BC, CD, DA
 *   3. Соединить: w1↔w2↔w3↔w4↔w1
 *   4. home.addWall(w1..w4)
 * </pre>
 */
public class CreateWallsHandler implements CommandHandler {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        float x = request.getFloat("x");
        float y = request.getFloat("y");
        float width = request.getFloat("width");
        float height = request.getFloat("height");
        float thickness = request.getFloat("thickness", 10.0f);

        if (width <= 0) {
            return Response.error("Parameter 'width' must be positive, got " + width);
        }
        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive, got " + height);
        }

        accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            // A(x,y) → B(x+w,y) → C(x+w,y+h) → D(x,y+h)
            Wall w1 = new Wall(x, y, x + width, y, thickness);
            Wall w2 = new Wall(x + width, y, x + width, y + height, thickness);
            Wall w3 = new Wall(x + width, y + height, x, y + height, thickness);
            Wall w4 = new Wall(x, y + height, x, y, thickness);

            // Замкнутый контур: w1↔w2↔w3↔w4↔w1
            w1.setWallAtEnd(w2);
            w2.setWallAtStart(w1);
            w2.setWallAtEnd(w3);
            w3.setWallAtStart(w2);
            w3.setWallAtEnd(w4);
            w4.setWallAtStart(w3);
            w4.setWallAtEnd(w1);
            w1.setWallAtStart(w4);

            home.addWall(w1);
            home.addWall(w2);
            home.addWall(w3);
            home.addWall(w4);

            return null;
        });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wallsCreated", 4);
        data.put("message", "Room " + (int) width + "x" + (int) height + " created");
        return Response.ok(data);
    }
}
