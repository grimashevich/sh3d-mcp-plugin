package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

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
        // TODO: реализовать
        // 1. Валидация: x, y (обязательные), width > 0, height > 0
        // 2. thickness = request.getFloat("thickness", 10.0f)
        // 3. accessor.runOnEDT(() -> {
        //      Home home = accessor.getHome();
        //      Wall w1 = new Wall(x, y, x + width, y);
        //      w1.setThickness(thickness);
        //      Wall w2 = new Wall(x + width, y, x + width, y + height);
        //      ... создать w3, w4
        //      w1.setWallAtEnd(w2); w2.setWallAtStart(w1);
        //      w2.setWallAtEnd(w3); w3.setWallAtStart(w2);
        //      w3.setWallAtEnd(w4); w4.setWallAtStart(w3);
        //      w4.setWallAtEnd(w1); w1.setWallAtStart(w4);
        //      home.addWall(w1); home.addWall(w2); ...
        //      return 4;
        //    });
        // 4. Вернуть Response.ok(Map.of("wallsCreated", 4, "message", ...))
        throw new UnsupportedOperationException("TODO: implement create_walls");
    }
}
