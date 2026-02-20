package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик команды "clear_scene".
 * Удаляет все объекты из сцены: стены, мебель, комнаты, labels, dimension lines.
 * Возвращает количество удалённых объектов каждого типа.
 *
 * <p>Перед очисткой автоматически создаёт checkpoint для возможности отката.
 */
public class ClearSceneHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(ClearSceneHandler.class.getName());

    private final CheckpointManager checkpointManager;

    public ClearSceneHandler(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // Auto-checkpoint before destructive operation
        autoCheckpoint(accessor);

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            int walls = deleteAllWalls(home);
            int furniture = deleteAllFurniture(home);
            int rooms = deleteAllRooms(home);
            int labels = deleteAllLabels(home);
            int dimensionLines = deleteAllDimensionLines(home);

            int total = walls + furniture + rooms + labels + dimensionLines;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("deletedWalls", walls);
            result.put("deletedFurniture", furniture);
            result.put("deletedRooms", rooms);
            result.put("deletedLabels", labels);
            result.put("deletedDimensionLines", dimensionLines);
            result.put("totalDeleted", total);
            return result;
        });

        return Response.ok(data);
    }

    private int deleteAllWalls(Home home) {
        var walls = new ArrayList<>(home.getWalls());
        for (Wall wall : walls) {
            home.deleteWall(wall);
        }
        return walls.size();
    }

    private int deleteAllFurniture(Home home) {
        var furniture = new ArrayList<>(home.getFurniture());
        for (HomePieceOfFurniture piece : furniture) {
            home.deletePieceOfFurniture(piece);
        }
        return furniture.size();
    }

    private int deleteAllRooms(Home home) {
        var rooms = new ArrayList<>(home.getRooms());
        for (Room room : rooms) {
            home.deleteRoom(room);
        }
        return rooms.size();
    }

    private int deleteAllLabels(Home home) {
        var labels = new ArrayList<>(home.getLabels());
        for (Label label : labels) {
            home.deleteLabel(label);
        }
        return labels.size();
    }

    private int deleteAllDimensionLines(Home home) {
        var lines = new ArrayList<>(home.getDimensionLines());
        for (DimensionLine line : lines) {
            home.deleteDimensionLine(line);
        }
        return lines.size();
    }

    private void autoCheckpoint(HomeAccessor accessor) {
        try {
            Home clonedHome = accessor.runOnEDT(() -> accessor.getHome().clone());
            checkpointManager.push(clonedHome, "Auto: before clear_scene");
            LOG.info("Auto-checkpoint created before clear_scene");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to create auto-checkpoint before clear_scene", e);
        }
    }

    @Override
    public String getDescription() {
        return "Removes ALL objects from the scene: walls, furniture, rooms, labels, "
                + "and dimension lines. Returns the count of deleted objects by type. "
                + "An automatic checkpoint is created before clearing, so you can use "
                + "restore_checkpoint to undo. Levels are preserved; use delete_level to remove them.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        schema.put("required", Collections.emptyList());
        return schema;
    }
}
