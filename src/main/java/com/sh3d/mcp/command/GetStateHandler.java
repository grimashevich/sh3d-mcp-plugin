package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "get_state".
 * Возвращает полное состояние сцены: стены, мебель, комнаты, labels,
 * dimension lines, камера, уровни, bounding box.
 *
 * Каждый объект получает стабильный строковый ID ({@code HomeObject.getId()}),
 * который не сдвигается при удалении других объектов и может использоваться
 * в последующих командах (delete, modify и т.д.).
 */
public class GetStateHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Map<String, Object> result = new LinkedHashMap<>();

            // --- Walls ---
            List<Object> wallList = buildWalls(home.getWalls());
            result.put("wallCount", wallList.size());
            result.put("walls", wallList);

            // --- Furniture ---
            List<Object> furnitureList = buildFurniture(home.getFurniture());
            result.put("furnitureCount", furnitureList.size());
            result.put("furniture", furnitureList);

            // --- Rooms ---
            List<Object> roomList = buildRooms(home.getRooms());
            result.put("roomCount", roomList.size());
            result.put("rooms", roomList);

            // --- Labels ---
            List<Object> labelList = buildLabels(home.getLabels());
            result.put("labelCount", labelList.size());
            result.put("labels", labelList);

            // --- Dimension lines ---
            List<Object> dimList = buildDimensionLines(home.getDimensionLines());
            result.put("dimensionLineCount", dimList.size());
            result.put("dimensionLines", dimList);

            // --- Camera ---
            result.put("camera", buildCamera(home));

            // --- Stored cameras ---
            List<Camera> storedCameras = home.getStoredCameras();
            List<Object> storedCamList = new ArrayList<>();
            for (Camera sc : storedCameras) {
                Map<String, Object> cam = new LinkedHashMap<>();
                cam.put("id", sc.getId());
                cam.put("name", sc.getName());
                storedCamList.add(cam);
            }
            result.put("storedCameraCount", storedCamList.size());
            result.put("storedCameras", storedCamList);

            // --- Levels ---
            List<Object> levelList = buildLevels(home.getLevels(), home.getSelectedLevel());
            result.put("levelCount", levelList.size());
            result.put("levels", levelList);

            // --- Environment ---
            result.put("environment", buildEnvironment(home.getEnvironment()));

            // --- Bounding box ---
            result.put("boundingBox", buildBoundingBox(home.getWalls()));

            return result;
        });

        return Response.ok(data);
    }

    // --- Wall builders ---

    private List<Object> buildWalls(Collection<Wall> walls) {
        List<Object> list = new ArrayList<>();
        for (Wall w : walls) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", w.getId());
            item.put("xStart", round2(w.getXStart()));
            item.put("yStart", round2(w.getYStart()));
            item.put("xEnd", round2(w.getXEnd()));
            item.put("yEnd", round2(w.getYEnd()));
            item.put("thickness", round2(w.getThickness()));
            item.put("height", w.getHeight() != null ? round2(w.getHeight()) : null);
            item.put("heightAtEnd", w.getHeightAtEnd() != null ? round2(w.getHeightAtEnd()) : null);
            item.put("length", round2(w.getLength()));
            item.put("arcExtent", w.getArcExtent() != null
                    ? round2(Math.toDegrees(w.getArcExtent())) : null);
            item.put("leftSideColor", colorToHex(w.getLeftSideColor()));
            item.put("rightSideColor", colorToHex(w.getRightSideColor()));
            item.put("topColor", colorToHex(w.getTopColor()));
            item.put("leftSideShininess", round2(w.getLeftSideShininess()));
            item.put("rightSideShininess", round2(w.getRightSideShininess()));
            item.put("leftSideTexture", textureName(w.getLeftSideTexture()));
            item.put("rightSideTexture", textureName(w.getRightSideTexture()));
            Level level = w.getLevel();
            item.put("level", level != null ? level.getName() : null);
            list.add(item);
        }
        return list;
    }

    // --- Furniture builders ---

    private List<Object> buildFurniture(List<HomePieceOfFurniture> furniture) {
        List<Object> list = new ArrayList<>();
        for (HomePieceOfFurniture piece : furniture) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", piece.getId());
            item.put("name", piece.getName());
            item.put("catalogId", piece.getCatalogId());
            item.put("x", round2(piece.getX()));
            item.put("y", round2(piece.getY()));
            item.put("elevation", round2(piece.getElevation()));
            item.put("angle", round2(Math.toDegrees(piece.getAngle())));
            item.put("width", round2(piece.getWidth()));
            item.put("depth", round2(piece.getDepth()));
            item.put("height", round2(piece.getHeight()));
            item.put("isDoorOrWindow", piece.isDoorOrWindow());
            item.put("visible", piece.isVisible());
            Level level = piece.getLevel();
            item.put("level", level != null ? level.getName() : null);
            list.add(item);
        }
        return list;
    }

    // --- Room builders ---

    private List<Object> buildRooms(List<Room> rooms) {
        List<Object> list = new ArrayList<>();
        for (Room room : rooms) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", room.getId());
            item.put("name", room.getName());
            item.put("area", round2(room.getArea()));
            item.put("areaVisible", room.isAreaVisible());
            item.put("floorVisible", room.isFloorVisible());
            item.put("ceilingVisible", room.isCeilingVisible());
            item.put("floorColor", colorToHex(room.getFloorColor()));
            item.put("ceilingColor", colorToHex(room.getCeilingColor()));
            item.put("floorShininess", round2(room.getFloorShininess()));
            item.put("ceilingShininess", round2(room.getCeilingShininess()));
            item.put("floorTexture", textureName(room.getFloorTexture()));
            item.put("ceilingTexture", textureName(room.getCeilingTexture()));
            item.put("xCenter", round2(room.getXCenter()));
            item.put("yCenter", round2(room.getYCenter()));

            // Points (polygon)
            float[][] points = room.getPoints();
            List<Object> pointList = new ArrayList<>();
            for (float[] pt : points) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("x", round2(pt[0]));
                p.put("y", round2(pt[1]));
                pointList.add(p);
            }
            item.put("points", pointList);

            Level level = room.getLevel();
            item.put("level", level != null ? level.getName() : null);
            list.add(item);
        }
        return list;
    }

    // --- Label builders ---

    private List<Object> buildLabels(Collection<Label> labels) {
        List<Object> list = new ArrayList<>();
        for (Label label : labels) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", label.getId());
            item.put("text", label.getText());
            item.put("x", round2(label.getX()));
            item.put("y", round2(label.getY()));
            item.put("angle", round2(Math.toDegrees(label.getAngle())));
            item.put("color", colorToHex(label.getColor()));
            Level level = label.getLevel();
            item.put("level", level != null ? level.getName() : null);
            list.add(item);
        }
        return list;
    }

    // --- Dimension line builders ---

    private List<Object> buildDimensionLines(Collection<DimensionLine> dimensionLines) {
        List<Object> list = new ArrayList<>();
        for (DimensionLine dim : dimensionLines) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", dim.getId());
            item.put("xStart", round2(dim.getXStart()));
            item.put("yStart", round2(dim.getYStart()));
            item.put("xEnd", round2(dim.getXEnd()));
            item.put("yEnd", round2(dim.getYEnd()));
            item.put("offset", round2(dim.getOffset()));
            item.put("length", round2(dim.getLength()));
            Level level = dim.getLevel();
            item.put("level", level != null ? level.getName() : null);
            list.add(item);
        }
        return list;
    }

    // --- Camera builder ---

    private Map<String, Object> buildCamera(Home home) {
        Camera cam = home.getCamera();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", cam instanceof ObserverCamera ? "observer" : "top");
        info.put("x", round2(cam.getX()));
        info.put("y", round2(cam.getY()));
        info.put("z", round2(cam.getZ()));
        info.put("yaw_degrees", round2(Math.toDegrees(cam.getYaw())));
        info.put("pitch_degrees", round2(Math.toDegrees(cam.getPitch())));
        info.put("fov_degrees", round2(Math.toDegrees(cam.getFieldOfView())));
        return info;
    }

    // --- Level builders ---

    private List<Object> buildLevels(List<Level> levels, Level selectedLevel) {
        List<Object> list = new ArrayList<>();
        for (Level level : levels) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", level.getId());
            item.put("name", level.getName());
            item.put("elevation", round2(level.getElevation()));
            item.put("height", round2(level.getHeight()));
            item.put("floorThickness", round2(level.getFloorThickness()));
            item.put("viewable", level.isViewable());
            item.put("selected", level.equals(selectedLevel));
            list.add(item);
        }
        return list;
    }

    // --- Environment builder ---

    private Map<String, Object> buildEnvironment(HomeEnvironment env) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("groundColor", colorToHex(env.getGroundColor()));
        info.put("groundTexture", textureName(env.getGroundTexture()));
        info.put("skyColor", colorToHex(env.getSkyColor()));
        info.put("skyTexture", textureName(env.getSkyTexture()));
        info.put("lightColor", colorToHex(env.getLightColor()));
        info.put("ceilingLightColor", colorToHex(env.getCeillingLightColor()));
        info.put("wallsAlpha", round2(env.getWallsAlpha()));
        info.put("drawingMode", env.getDrawingMode().name());
        info.put("allLevelsVisible", env.isAllLevelsVisible());
        return info;
    }

    // --- Bounding box ---

    private Map<String, Object> buildBoundingBox(Collection<Wall> walls) {
        if (walls.isEmpty()) {
            return null;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Wall w : walls) {
            minX = Math.min(minX, Math.min(w.getXStart(), w.getXEnd()));
            minY = Math.min(minY, Math.min(w.getYStart(), w.getYEnd()));
            maxX = Math.max(maxX, Math.max(w.getXStart(), w.getXEnd()));
            maxY = Math.max(maxY, Math.max(w.getYStart(), w.getYEnd()));
        }
        Map<String, Object> bb = new LinkedHashMap<>();
        bb.put("minX", round2(minX));
        bb.put("minY", round2(minY));
        bb.put("maxX", round2(maxX));
        bb.put("maxY", round2(maxY));
        return bb;
    }

    // --- Utilities ---

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String textureName(HomeTexture texture) {
        return texture != null ? texture.getName() : null;
    }

    private static String colorToHex(Integer color) {
        if (color == null) {
            return null;
        }
        return String.format("#%06X", color & 0xFFFFFF);
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Returns the full state of the Sweet Home 3D scene: walls with coordinates, "
                + "furniture with positions and IDs, rooms with polygons, labels, dimension lines, "
                + "camera settings, environment (ground, sky, light, wallsAlpha, drawingMode), "
                + "and levels. Each object has a stable string 'id' field that can be "
                + "used in subsequent commands (delete, modify, etc.). Always call this before "
                + "making changes to understand the current scene.";
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
