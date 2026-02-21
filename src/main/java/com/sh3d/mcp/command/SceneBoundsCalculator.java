package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;

/**
 * Computes bounding boxes of the scene or a specific object.
 * Extracted from RenderPhotoHandler to reduce complexity.
 */
class SceneBoundsCalculator {

    /** Minimum scene height in cm, used as floor for maxZ when scene is very flat. */
    private static final float MIN_SCENE_HEIGHT = 100.0f;
    /** Padding around focused furniture as a ratio of its largest dimension (50%). */
    static final float FURNITURE_PADDING_RATIO = 0.5f;
    /** Minimum padding around focused furniture in cm, even for small items. */
    static final float MIN_FURNITURE_PADDING = 200.0f;
    /** Fixed padding around focused room in cm. */
    static final float ROOM_PADDING = 50.0f;

    /** Computes bounding box of the entire visible scene. */
    SceneBounds computeSceneBounds(HomeAccessor accessor) {
        return accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float maxZ = 0;
            boolean hasContent = false;

            // Walls
            for (Wall wall : home.getWalls()) {
                if (isLevelHidden(wall)) {
                    continue;
                }
                float[][] points = wall.getPoints();
                for (float[] pt : points) {
                    minX = Math.min(minX, pt[0]);
                    minY = Math.min(minY, pt[1]);
                    maxX = Math.max(maxX, pt[0]);
                    maxY = Math.max(maxY, pt[1]);
                }
                Float wallHeight = wall.getHeight();
                float h = wallHeight != null ? wallHeight : home.getWallHeight();
                float baseElevation = wall.getLevel() != null ? wall.getLevel().getElevation() : 0;
                maxZ = Math.max(maxZ, baseElevation + h);
                hasContent = true;
            }

            // Furniture
            for (HomePieceOfFurniture piece : home.getFurniture()) {
                if (!piece.isVisible()) {
                    continue;
                }
                if (isLevelHidden(piece)) {
                    continue;
                }
                if (piece instanceof HomeFurnitureGroup) {
                    for (HomePieceOfFurniture child : ((HomeFurnitureGroup) piece).getFurniture()) {
                        if (child.isVisible()) {
                            float[][] pts = child.getPoints();
                            for (float[] pt : pts) {
                                minX = Math.min(minX, pt[0]);
                                minY = Math.min(minY, pt[1]);
                                maxX = Math.max(maxX, pt[0]);
                                maxY = Math.max(maxY, pt[1]);
                            }
                            maxZ = Math.max(maxZ, child.getElevation() + child.getHeight());
                            hasContent = true;
                        }
                    }
                } else {
                    float[][] pts = piece.getPoints();
                    for (float[] pt : pts) {
                        minX = Math.min(minX, pt[0]);
                        minY = Math.min(minY, pt[1]);
                        maxX = Math.max(maxX, pt[0]);
                        maxY = Math.max(maxY, pt[1]);
                    }
                    maxZ = Math.max(maxZ, piece.getElevation() + piece.getHeight());
                    hasContent = true;
                }
            }

            // Rooms
            for (Room room : home.getRooms()) {
                if (isLevelHidden(room)) {
                    continue;
                }
                float[][] points = room.getPoints();
                for (float[] pt : points) {
                    minX = Math.min(minX, pt[0]);
                    minY = Math.min(minY, pt[1]);
                    maxX = Math.max(maxX, pt[0]);
                    maxY = Math.max(maxY, pt[1]);
                }
                hasContent = true;
            }

            if (!hasContent) {
                return null;
            }

            return SceneBounds.of(minX, minY, maxX, maxY, Math.max(maxZ, MIN_SCENE_HEIGHT));
        });
    }

    /** Computes bounds of a specific object (furniture or room) with padding. */
    SceneBounds computeFocusBounds(HomeAccessor accessor, String type, String id) {
        return accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float maxZ = 0;

            if ("furniture".equals(type)) {
                HomePieceOfFurniture piece = ObjectResolver.findFurniture(home, id);
                if (piece == null) {
                    return null;
                }
                float[][] pts = piece.getPoints();
                for (float[] pt : pts) {
                    minX = Math.min(minX, pt[0]);
                    minY = Math.min(minY, pt[1]);
                    maxX = Math.max(maxX, pt[0]);
                    maxY = Math.max(maxY, pt[1]);
                }
                maxZ = piece.getElevation() + piece.getHeight();
            } else if ("room".equals(type)) {
                Room room = ObjectResolver.findRoom(home, id);
                if (room == null) {
                    return null;
                }
                float[][] pts = room.getPoints();
                for (float[] pt : pts) {
                    minX = Math.min(minX, pt[0]);
                    minY = Math.min(minY, pt[1]);
                    maxX = Math.max(maxX, pt[0]);
                    maxY = Math.max(maxY, pt[1]);
                }
                maxZ = home.getWallHeight();
            } else {
                return null;
            }

            maxZ = Math.max(maxZ, MIN_SCENE_HEIGHT);

            // Padding: furniture — 50% of size (min 200 cm), room — fixed 50 cm
            float w = maxX - minX;
            float d = maxY - minY;
            float padding;
            if ("furniture".equals(type)) {
                padding = Math.max(Math.max(w, d) * FURNITURE_PADDING_RATIO, MIN_FURNITURE_PADDING);
            } else {
                padding = ROOM_PADDING;
            }
            minX -= padding;
            minY -= padding;
            maxX += padding;
            maxY += padding;

            return SceneBounds.of(minX, minY, maxX, maxY, maxZ);
        });
    }

    private static boolean isLevelHidden(Object item) {
        if (item instanceof Elevatable) {
            com.eteks.sweethome3d.model.Level level = ((Elevatable) item).getLevel();
            if (level != null && !level.isViewableAndVisible()) {
                return true;
            }
        }
        return false;
    }
}
