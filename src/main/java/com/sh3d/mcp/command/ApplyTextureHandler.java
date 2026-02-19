package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogTexture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "apply_texture".
 * Применяет текстуру из каталога к стене (left/right side) или комнате (floor/ceiling).
 *
 * <pre>
 * Параметры:
 *   targetType      — "wall" или "room" (required)
 *   targetId        — ID объекта из get_state (required)
 *   surface         — стены: "left", "right", "both"; комнаты: "floor", "ceiling", "both" (required)
 *   textureName     — имя текстуры из каталога (exact match), null для сброса (required)
 *   textureCategory — категория для уточнения (optional)
 *   angle           — угол поворота в градусах (optional, default 0)
 *   scale           — масштаб текстуры (optional, default 1.0)
 *
 * EDT: мутации модели выполняются через runOnEDT().
 * Поиск в каталоге — вне EDT (thread-safe).
 * </pre>
 */
public class ApplyTextureHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> WALL_SURFACES = Arrays.asList("left", "right", "both");
    private static final List<String> ROOM_SURFACES = Arrays.asList("floor", "ceiling", "both");

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        // --- Validate required params ---
        String targetType = request.getString("targetType");
        if (targetType == null) {
            return Response.error("Missing required parameter 'targetType'. Expected 'wall' or 'room'");
        }
        if (!"wall".equals(targetType) && !"room".equals(targetType)) {
            return Response.error("Invalid targetType: '" + targetType + "'. Expected 'wall' or 'room'");
        }

        Map<String, Object> params = request.getParams();
        if (!params.containsKey("targetId")) {
            return Response.error("Missing required parameter 'targetId'");
        }
        int targetId = (int) request.getFloat("targetId");
        if (targetId < 0) {
            return Response.error("Parameter 'targetId' must be non-negative, got " + targetId);
        }

        String surface = request.getString("surface");
        if (surface == null) {
            return Response.error("Missing required parameter 'surface'");
        }

        // Validate surface for target type
        if ("wall".equals(targetType)) {
            if (!WALL_SURFACES.contains(surface)) {
                return Response.error("Invalid surface '" + surface + "' for wall. Expected: left, right, both");
            }
        } else {
            if (!ROOM_SURFACES.contains(surface)) {
                return Response.error("Invalid surface '" + surface + "' for room. Expected: floor, ceiling, both");
            }
        }

        // textureName is required but can be null (for reset)
        if (!params.containsKey("textureName")) {
            return Response.error("Missing required parameter 'textureName'. "
                    + "Use a texture name from list_textures_catalog, or null to remove texture");
        }
        String textureName = request.getString("textureName");

        String textureCategory = request.getString("textureCategory");

        // Validate angle and scale
        float angle = 0f;
        if (params.containsKey("angle")) {
            angle = request.getFloat("angle");
        }

        float scale = 1f;
        if (params.containsKey("scale")) {
            scale = request.getFloat("scale");
            if (scale <= 0) {
                return Response.error("Parameter 'scale' must be positive, got " + scale);
            }
        }

        // --- Find texture in catalog (outside EDT, thread-safe) ---
        HomeTexture homeTexture = null;
        String resolvedName = null;
        String resolvedCategory = null;

        if (textureName != null) {
            TexturesCatalog catalog = accessor.getTexturesCatalog();
            if (catalog == null) {
                return Response.error("Texture catalog is not available");
            }

            CatalogSearchUtil.TextureSearchResult texResult =
                    CatalogSearchUtil.findTexture(catalog, textureName, textureCategory);
            if (!texResult.isFound()) {
                return Response.error("Texture not found: '" + textureName + "'"
                        + (textureCategory != null ? " in category '" + textureCategory + "'" : "")
                        + ". Use list_textures_catalog to browse available textures");
            }
            CatalogTexture found = texResult.getFound();

            resolvedName = found.getName();
            resolvedCategory = found.getCategory().getName();

            // Create HomeTexture with optional angle and scale
            boolean hasAngleOrScale = params.containsKey("angle") || params.containsKey("scale");
            if (hasAngleOrScale) {
                float angleRad = (float) Math.toRadians(angle);
                homeTexture = new HomeTexture(found, 0f, 0f, angleRad, scale, true);
            } else {
                homeTexture = new HomeTexture(found);
            }
        }

        // Capture for lambda
        final HomeTexture finalTexture = homeTexture;
        final String finalResolvedName = resolvedName;
        final String finalResolvedCategory = resolvedCategory;

        // --- Apply on EDT ---
        Map<String, Object> data;
        if ("wall".equals(targetType)) {
            data = applyToWall(accessor, targetId, surface, finalTexture, finalResolvedName, finalResolvedCategory);
        } else {
            data = applyToRoom(accessor, targetId, surface, finalTexture, finalResolvedName, finalResolvedCategory);
        }

        if (data == null) {
            String typeName = "wall".equals(targetType) ? "Wall" : "Room";
            return Response.error(typeName + " not found: targetId " + targetId + " is out of range");
        }

        if (data.containsKey("__error")) {
            return Response.error((String) data.get("__error"));
        }

        return Response.ok(data);
    }

    private Map<String, Object> applyToWall(HomeAccessor accessor, int targetId, String surface,
                                             HomeTexture texture, String texName, String texCategory) {
        return accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Wall> walls = new ArrayList<>(home.getWalls());

            if (targetId >= walls.size()) {
                return null;
            }

            Wall wall = walls.get(targetId);

            if ("left".equals(surface) || "both".equals(surface)) {
                wall.setLeftSideTexture(texture);
            }
            if ("right".equals(surface) || "both".equals(surface)) {
                wall.setRightSideTexture(texture);
            }

            return buildWallResponse(targetId, wall, surface, texName, texCategory);
        });
    }

    private Map<String, Object> applyToRoom(HomeAccessor accessor, int targetId, String surface,
                                             HomeTexture texture, String texName, String texCategory) {
        return accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Room> rooms = home.getRooms();

            if (targetId >= rooms.size()) {
                return null;
            }

            Room room = rooms.get(targetId);

            if ("floor".equals(surface) || "both".equals(surface)) {
                room.setFloorTexture(texture);
            }
            if ("ceiling".equals(surface) || "both".equals(surface)) {
                room.setCeilingTexture(texture);
            }

            return buildRoomResponse(targetId, room, surface, texName, texCategory);
        });
    }

    // --- Response builders ---

    private static Map<String, Object> buildWallResponse(int id, Wall wall, String surface,
                                                          String texName, String texCategory) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetType", "wall");
        result.put("targetId", id);
        result.put("surface", surface);
        result.put("textureName", texName);
        result.put("textureCategory", texCategory);
        result.put("leftSideTexture", textureName(wall.getLeftSideTexture()));
        result.put("rightSideTexture", textureName(wall.getRightSideTexture()));
        return result;
    }

    private static Map<String, Object> buildRoomResponse(int id, Room room, String surface,
                                                          String texName, String texCategory) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetType", "room");
        result.put("targetId", id);
        result.put("surface", surface);
        result.put("textureName", texName);
        result.put("textureCategory", texCategory);
        result.put("floorTexture", textureName(room.getFloorTexture()));
        result.put("ceilingTexture", textureName(room.getCeilingTexture()));
        return result;
    }

    private static String textureName(HomeTexture texture) {
        return texture != null ? texture.getName() : null;
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Applies a texture from the catalog to a wall side or room surface. "
                + "Use list_textures_catalog to find available texture names first. "
                + "For walls: surface can be 'left', 'right', or 'both' (sides relative to wall direction from start to end). "
                + "For rooms: surface can be 'floor', 'ceiling', or 'both'. "
                + "Setting a texture overrides any solid color on that surface; "
                + "to switch back to solid color, first remove the texture by passing textureName=null, then use modify_wall/modify_room to set the color. "
                + "Use angle (degrees) to rotate and scale to resize the texture pattern.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("targetType", enumProp("string",
                "Type of object to apply texture to",
                Arrays.asList("wall", "room")));
        properties.put("targetId", prop("integer", "Object ID from get_state"));
        properties.put("surface", prop("string",
                "Surface to apply texture to. Walls: 'left', 'right', 'both'. Rooms: 'floor', 'ceiling', 'both'"));
        properties.put("textureName", nullableProp("string",
                "Exact texture name from list_textures_catalog, or null to remove texture"));
        properties.put("textureCategory", prop("string",
                "Category name to disambiguate if multiple textures share the same name"));
        properties.put("angle", prop("number",
                "Texture rotation angle in degrees (default 0)"));
        properties.put("scale", prop("number",
                "Texture scale factor (default 1.0, values > 1 enlarge the pattern)"));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("targetType", "targetId", "surface", "textureName"));
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> nullableProp(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", Arrays.asList(type, "null"));
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> enumProp(String type, String description, List<String> values) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        p.put("enum", values);
        return p;
    }
}
