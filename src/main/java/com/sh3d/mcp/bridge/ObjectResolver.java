package com.sh3d.mcp.bridge;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;

import java.util.Collection;

/**
 * Поиск объектов по стабильному ID (HomeObject.getId()).
 * <p>
 * SH3D 7.x — все объекты наследуют {@code HomeObject} с {@code final String getId()}.
 * ID автогенерируется, стабилен (не сдвигается при удалении других объектов),
 * сохраняется при сериализации и клонировании.
 */
public final class ObjectResolver {

    private ObjectResolver() {
    }

    /**
     * Находит стену по ID.
     *
     * @return Wall или null если не найдена
     */
    public static Wall findWall(Home home, String id) {
        for (Wall w : home.getWalls()) {
            if (w.getId().equals(id)) {
                return w;
            }
        }
        return null;
    }

    /**
     * Находит мебель по ID (включая двери/окна).
     *
     * @return HomePieceOfFurniture или null если не найдена
     */
    public static HomePieceOfFurniture findFurniture(Home home, String id) {
        for (HomePieceOfFurniture p : home.getFurniture()) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Находит комнату по ID.
     *
     * @return Room или null если не найдена
     */
    public static Room findRoom(Home home, String id) {
        for (Room r : home.getRooms()) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Находит уровень по ID.
     *
     * @return Level или null если не найден
     */
    public static Level findLevel(Home home, String id) {
        for (Level l : home.getLevels()) {
            if (l.getId().equals(id)) {
                return l;
            }
        }
        return null;
    }

    /**
     * Находит размерную линию по ID.
     *
     * @return DimensionLine или null если не найдена
     */
    public static DimensionLine findDimensionLine(Home home, String id) {
        for (DimensionLine d : home.getDimensionLines()) {
            if (d.getId().equals(id)) {
                return d;
            }
        }
        return null;
    }
}
