package com.sh3d.mcp.command;

import com.eteks.sweethome3d.j3d.AbstractPhotoRenderer;
import com.eteks.sweethome3d.j3d.PhotoRenderer;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик команды "render_photo".
 * Рендерит 3D-сцену через Sunflow ray-tracer.
 * Возвращает изображения как нативный MCP image content (JPEG по умолчанию).
 *
 * <p>Поддерживает два режима:
 * <ul>
 *   <li>Стандартный — одиночный рендер с текущей или заданной камерой</li>
 *   <li>Overhead (view="overhead") — автоматический bird's eye orbit:
 *       вычисляет bounding box сцены и рендерит с 4 диагональных углов.
 *       Поддерживает focusOn для зуммирования на конкретный объект.
 *       Автоматически устанавливает полупрозрачность стен и цвет пола.</li>
 * </ul>
 *
 * <pre>
 * Параметры:
 *   width      — ширина изображения в пикселях (default 800, max 4096)
 *   height     — высота изображения в пикселях (default 600, max 4096)
 *   quality    — "low" (быстрый) или "high" (ray-trace) (default "low")
 *   format     — "jpeg" (default inline) или "png" (default filePath)
 *   filePath   — путь для сохранения файла (опционально)
 *   view       — "overhead" для bird's eye orbit (опционально)
 *   focusOn    — "furniture:id" или "room:id" для зума на объект (требует overhead)
 *   angles     — 1 или 4 ракурса для overhead (default 4, или 1 для мебели)
 *   x, y, z    — позиция камеры в см (стандартный режим)
 *   yaw        — горизонтальный поворот в градусах (стандартный режим)
 *   pitch      — вертикальный наклон в градусах (default 30 для overhead)
 *   fov        — угол обзора в градусах (default 63)
 * </pre>
 */
public class RenderPhotoHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(RenderPhotoHandler.class.getName());

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int MAX_DIMENSION = 4096;

    private static final float DEFAULT_OVERHEAD_PITCH_DEG = 30.0f;
    static final float DEFAULT_FOCUS_PITCH_DEG = 55.0f;
    private static final float DEFAULT_FOV_DEG = 63.0f;
    private static final float OVERHEAD_MARGIN = 1.05f;
    private static final float MIN_SCENE_HEIGHT = 100.0f;

    static final int DEFAULT_OVERHEAD_WIDTH = 1200;
    static final int DEFAULT_OVERHEAD_HEIGHT = 900;
    static final float OVERHEAD_WALL_HEIGHT = 1.0f;
    static final int DEFAULT_FLOOR_COLOR = 0xE0E0E0;
    static final float FURNITURE_PADDING_RATIO = 0.5f;
    static final float MIN_FURNITURE_PADDING = 200.0f;
    static final float ROOM_PADDING = 50.0f;

    static final float JPEG_QUALITY = 0.85f;

    static final float[] OVERHEAD_YAWS = {315f, 135f, 45f, 225f};
    static final String[] OVERHEAD_LABELS = {
            "NW_to_SE", "SE_to_NW", "NE_to_SW", "SW_to_NE"
    };

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        int width = (int) request.getFloat("width", DEFAULT_WIDTH);
        int height = (int) request.getFloat("height", DEFAULT_HEIGHT);

        if (width <= 0 || width > MAX_DIMENSION) {
            return Response.error("Parameter 'width' must be between 1 and " + MAX_DIMENSION + ", got " + width);
        }
        if (height <= 0 || height > MAX_DIMENSION) {
            return Response.error("Parameter 'height' must be between 1 and " + MAX_DIMENSION + ", got " + height);
        }

        String qualityStr = request.getString("quality");
        if (qualityStr == null) {
            qualityStr = "low";
        }
        qualityStr = qualityStr.toLowerCase();
        if (!"low".equals(qualityStr) && !"high".equals(qualityStr)) {
            return Response.error("Parameter 'quality' must be 'low' or 'high', got '" + qualityStr + "'");
        }

        AbstractPhotoRenderer.Quality quality = "high".equals(qualityStr)
                ? AbstractPhotoRenderer.Quality.HIGH
                : AbstractPhotoRenderer.Quality.LOW;

        String filePath = request.getString("filePath");

        // Формат изображения: jpeg (default inline) или png (default filePath)
        String format = request.getString("format");
        if (format == null) {
            format = (filePath != null && !filePath.trim().isEmpty()) ? "png" : "jpeg";
        }
        format = format.toLowerCase();
        if (!"png".equals(format) && !"jpeg".equals(format)) {
            return Response.error("Parameter 'format' must be 'png' or 'jpeg', got '" + format + "'");
        }

        // Проверка view и focusOn
        String view = request.getString("view");
        String focusOn = request.getString("focusOn");

        if (focusOn != null && !"overhead".equalsIgnoreCase(view)) {
            return Response.error("Parameter 'focusOn' requires view='overhead'. "
                    + "Use: {\"view\": \"overhead\", \"focusOn\": \"furniture:<id>\"}");
        }

        if (view != null) {
            if ("overhead".equalsIgnoreCase(view)) {
                return executeOverhead(request, accessor, width, height, quality, qualityStr, filePath, format);
            }
            return Response.error("Parameter 'view' must be 'overhead', got '" + view + "'");
        }

        // --- Стандартный режим (без view) ---
        boolean hasCustomCamera = request.getParams().containsKey("x")
                || request.getParams().containsKey("y")
                || request.getParams().containsKey("z");

        Camera camera = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Camera cam = home.getCamera();
            if (cam == null) {
                cam = home.getObserverCamera();
            }
            Camera clone = cam.clone();

            if (hasCustomCamera) {
                if (request.getParams().containsKey("x")) {
                    clone.setX(request.getFloat("x"));
                }
                if (request.getParams().containsKey("y")) {
                    clone.setY(request.getFloat("y"));
                }
                if (request.getParams().containsKey("z")) {
                    clone.setZ(request.getFloat("z"));
                }
                if (request.getParams().containsKey("yaw")) {
                    clone.setYaw((float) Math.toRadians(request.getFloat("yaw")));
                }
                if (request.getParams().containsKey("pitch")) {
                    clone.setPitch((float) Math.toRadians(request.getFloat("pitch")));
                }
                if (request.getParams().containsKey("fov")) {
                    clone.setFieldOfView((float) Math.toRadians(request.getFloat("fov")));
                }
            }
            return clone;
        });

        try {
            Map<String, Object> data = renderSingleImage(accessor.getHome(), camera,
                    width, height, quality, filePath, format);
            data.put("quality", qualityStr);

            LOG.info("Rendered photo " + width + "x" + height + " (" + qualityStr
                    + "), size=" + data.get("size_bytes") + " bytes"
                    + (filePath != null ? ", saved to " + filePath : ""));

            return Response.ok(data);
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "OOM during render", e);
            return Response.error("Out of memory: reduce image dimensions (current: "
                    + width + "x" + height + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Render failed", e);
            return Response.error("Rendering failed: " + e.getMessage());
        }
    }

    // --- Overhead mode ---

    private Response executeOverhead(Request request, HomeAccessor accessor,
                                     int width, int height,
                                     AbstractPhotoRenderer.Quality quality,
                                     String qualityStr, String filePath,
                                     String format) {
        // Валидация: overhead несовместим с ручными координатами
        if (request.getParams().containsKey("x")
                || request.getParams().containsKey("y")
                || request.getParams().containsKey("z")) {
            return Response.error("Parameters x/y/z are incompatible with view='overhead'. "
                    + "Overhead mode auto-computes camera position from scene bounds.");
        }
        if (request.getParams().containsKey("yaw")) {
            return Response.error("Parameter 'yaw' is incompatible with view='overhead'. "
                    + "Overhead mode renders from predefined orbital angles.");
        }

        // Overhead-дефолты для разрешения
        // filePath: 1200×900 (полное качество для файлов), inline: 800×600 (экономия размера)
        boolean hasFilePath = filePath != null && !filePath.trim().isEmpty();
        if (!request.getParams().containsKey("width")) {
            width = hasFilePath ? DEFAULT_OVERHEAD_WIDTH : DEFAULT_WIDTH;
        }
        if (!request.getParams().containsKey("height")) {
            height = hasFilePath ? DEFAULT_OVERHEAD_HEIGHT : DEFAULT_HEIGHT;
        }

        // Парсинг focusOn
        String focusOn = request.getString("focusOn");
        String focusType = null;
        String focusId = null;

        if (focusOn != null) {
            String[] parts = focusOn.split(":", 2);
            if (parts.length != 2) {
                return Response.error("Parameter 'focusOn' format must be 'furniture:<id>' "
                        + "or 'room:<id>', got '" + focusOn + "'");
            }
            focusType = parts[0].trim().toLowerCase();
            if (!"furniture".equals(focusType) && !"room".equals(focusType)) {
                return Response.error("Parameter 'focusOn' must start with 'furniture:' or 'room:', "
                        + "got '" + focusOn + "'");
            }
            focusId = parts[1].trim();
            if (focusId.isEmpty()) {
                return Response.error("Parameter 'focusOn' requires an ID after ':', got empty string");
            }
        }

        // Angles: default 1 для мебели, 4 для комнаты или всей сцены
        int defaultAngles = "furniture".equals(focusType) ? 1 : 4;
        int angles = (int) request.getFloat("angles", defaultAngles);
        if (angles != 1 && angles != 4) {
            return Response.error("Parameter 'angles' must be 1 or 4, got " + angles);
        }

        float defaultPitch = (focusOn != null) ? DEFAULT_FOCUS_PITCH_DEG : DEFAULT_OVERHEAD_PITCH_DEG;
        float pitchDeg = request.getParams().containsKey("pitch")
                ? request.getFloat("pitch")
                : defaultPitch;
        if (pitchDeg <= 0 || pitchDeg > 90) {
            return Response.error("Parameter 'pitch' for overhead must be between 0 (exclusive) "
                    + "and 90 degrees, got " + pitchDeg);
        }

        float fovDeg = request.getParams().containsKey("fov")
                ? request.getFloat("fov")
                : DEFAULT_FOV_DEG;

        // hideWalls: по умолчанию true (стены скрыты для лучшей видимости мебели)
        boolean hideWalls = true;
        if (request.getParams().containsKey("hideWalls")) {
            Object hw = request.getParams().get("hideWalls");
            hideWalls = Boolean.TRUE.equals(hw) || "true".equals(String.valueOf(hw));
        }

        // Bounding box: focusOn → конкретный объект, иначе вся сцена
        SceneBounds bounds;
        if (focusOn != null) {
            bounds = computeFocusBounds(accessor, focusType, focusId);
            if (bounds == null) {
                String label = focusType.substring(0, 1).toUpperCase() + focusType.substring(1);
                return Response.error(label + " not found: " + focusId);
            }
        } else {
            bounds = computeSceneBounds(accessor);
            if (bounds == null) {
                return Response.error("Cannot compute overhead view: scene is empty "
                        + "(no walls, furniture, or rooms)");
            }
        }

        // Клонируем камеру для шаблона
        Camera baseCamera = accessor.runOnEDT(() -> {
            Camera cam = accessor.getHome().getCamera();
            if (cam == null) {
                cam = accessor.getHome().getObserverCamera();
            }
            return cam.clone();
        });

        // Вычисляем камеры для каждого ракурса
        List<Camera> cameras = new ArrayList<>();
        for (int i = 0; i < angles; i++) {
            cameras.add(computeOverheadCamera(baseCamera, bounds,
                    OVERHEAD_YAWS[i], pitchDeg, fovDeg, width, height));
        }

        // Clone the Home to avoid mutating the live object (a parallel get_state
        // call would see walls with height 1cm if we modified the live Home)
        boolean finalHideWalls = hideWalls;
        Home renderHome = accessor.runOnEDT(() -> {
            Home clone = accessor.getHome().clone();
            // Стены: уменьшить высоту до 1 см если hideWalls=true
            if (finalHideWalls) {
                for (Wall wall : clone.getWalls()) {
                    wall.setHeight(OVERHEAD_WALL_HEIGHT);
                }
            }
            // Полы: серый цвет для комнат без текстур
            for (Room room : clone.getRooms()) {
                if (room.getFloorColor() == null && room.getFloorTexture() == null) {
                    room.setFloorColor(DEFAULT_FLOOR_COLOR);
                }
            }
            return clone;
        });

        // Render loop
        List<Object> imagesMeta = new ArrayList<>();
        List<Map<String, Object>> mcpImages = new ArrayList<>();

        try {
            for (int i = 0; i < cameras.size(); i++) {
                Camera cam = cameras.get(i);

                String currentFilePath = null;
                if (hasFilePath) {
                    if (angles > 1) {
                        currentFilePath = generateIndexedFilePath(filePath, i + 1);
                    } else {
                        currentFilePath = filePath;
                    }
                }

                Map<String, Object> imageResult = renderSingleImage(
                        renderHome, cam, width, height, quality, currentFilePath, format);
                imageResult.put("index", i);
                imageResult.put("direction", OVERHEAD_LABELS[i]);

                if (currentFilePath == null) {
                    // Inline: извлекаем base64 для MCP image content
                    Object imgData = imageResult.remove("_image");
                    Object imgMime = imageResult.remove("_mimeType");
                    if (imgData != null) {
                        Map<String, Object> mcpImg = new LinkedHashMap<>();
                        mcpImg.put("data", imgData);
                        mcpImg.put("mimeType", imgMime != null ? imgMime : "image/png");
                        mcpImages.add(mcpImg);
                    }
                }
                imagesMeta.add(imageResult);
            }
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "OOM during overhead render", e);
            return Response.error("Out of memory during overhead render: reduce image dimensions "
                    + "(current: " + width + "x" + height + ", angles: " + angles + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Overhead render failed", e);
            return Response.error("Overhead rendering failed: " + e.getMessage());
        }

        // Response
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("view", "overhead");
        data.put("imageCount", imagesMeta.size());
        data.put("images", imagesMeta);
        data.put("quality", qualityStr);
        data.put("format", format);

        Map<String, Object> boundsInfo = new LinkedHashMap<>();
        boundsInfo.put("minX", round2(bounds.minX));
        boundsInfo.put("minY", round2(bounds.minY));
        boundsInfo.put("maxX", round2(bounds.maxX));
        boundsInfo.put("maxY", round2(bounds.maxY));
        boundsInfo.put("maxZ", round2(bounds.maxZ));
        boundsInfo.put("centerX", round2(bounds.centerX));
        boundsInfo.put("centerY", round2(bounds.centerY));
        data.put("sceneBounds", boundsInfo);

        if (focusOn != null) {
            data.put("focusOn", focusOn);
        }
        if (hideWalls) {
            data.put("hideWalls", true);
        }
        if (!mcpImages.isEmpty()) {
            data.put("_images", mcpImages);
        }

        LOG.info("Overhead render complete: " + imagesMeta.size() + " images, "
                + width + "x" + height + " (" + qualityStr + ")"
                + (focusOn != null ? ", focus: " + focusOn : "")
                + (hideWalls ? ", walls hidden" : ""));

        return Response.ok(data);
    }

    // --- Bounding box ---

    /** Package-private для тестов. */
    SceneBounds computeSceneBounds(HomeAccessor accessor) {
        return accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float maxZ = 0;
            boolean hasContent = false;

            // Стены
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

            // Мебель
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

            // Комнаты
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

            SceneBounds bounds = new SceneBounds();
            bounds.minX = minX;
            bounds.minY = minY;
            bounds.maxX = maxX;
            bounds.maxY = maxY;
            bounds.maxZ = Math.max(maxZ, MIN_SCENE_HEIGHT);
            bounds.centerX = (minX + maxX) / 2;
            bounds.centerY = (minY + maxY) / 2;
            bounds.sceneWidth = maxX - minX;
            bounds.sceneDepth = maxY - minY;
            return bounds;
        });
    }

    /** Package-private для тестов. Вычисляет bounds конкретного объекта + padding. */
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

            // Padding: мебель — 50% от размера (min 100 см), комната — фикс. 50 см
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

            SceneBounds bounds = new SceneBounds();
            bounds.minX = minX;
            bounds.minY = minY;
            bounds.maxX = maxX;
            bounds.maxY = maxY;
            bounds.maxZ = maxZ;
            bounds.centerX = (minX + maxX) / 2;
            bounds.centerY = (minY + maxY) / 2;
            bounds.sceneWidth = maxX - minX;
            bounds.sceneDepth = maxY - minY;
            return bounds;
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

    // --- Camera computation ---

    /** Package-private для тестов. */
    Camera computeOverheadCamera(Camera template, SceneBounds bounds,
                                 float yawDeg, float pitchDeg, float fovDeg,
                                 int imageWidth, int imageHeight) {
        Camera cam = template.clone();

        float pitchRad = (float) Math.toRadians(pitchDeg);
        float yawRad = (float) Math.toRadians(yawDeg);
        float fovRad = (float) Math.toRadians(fovDeg);

        // Derive vertical FOV from horizontal FOV using the image aspect ratio
        float vFov = (float) (2 * Math.atan(Math.tan(fovRad / 2) * imageHeight / imageWidth));

        // Use scene diagonal as worst-case extent (fits any yaw rotation)
        float diagonal = (float) Math.sqrt(
                bounds.sceneWidth * bounds.sceneWidth + bounds.sceneDepth * bounds.sceneDepth);
        diagonal = Math.max(diagonal, 50.0f); // avoid division by zero for point-like scenes

        // Horizontal distance: ensure the full diagonal fits within the horizontal FOV
        float hDist = (diagonal / 2) / (float) Math.tan(fovRad / 2);

        // Vertical distance: project scene depth and height onto the camera's vertical axis
        // fullVertExtent = diagonal projected through pitch + scene height projected through pitch
        float fullVertExtent = diagonal * (float) Math.sin(pitchRad)
                + bounds.maxZ * (float) Math.cos(pitchRad);
        float vDist = (fullVertExtent / 2) / (float) Math.tan(vFov / 2);

        float distance = Math.max(hDist, vDist) * OVERHEAD_MARGIN;

        // Позиция: от центра назад по вектору, обратному направлению взгляда
        // SH3D direction convention: lookDir = (-sin(yaw), cos(yaw))
        // Camera position = center - lookDir * distance * cos(pitch)
        //                 = center + (sin(yaw), -cos(yaw)) * distance * cos(pitch)
        float cosPitch = (float) Math.cos(pitchRad);
        float camX = bounds.centerX + distance * cosPitch * (float) Math.sin(yawRad);
        float camY = bounds.centerY - distance * cosPitch * (float) Math.cos(yawRad);
        float camZ = bounds.maxZ / 2 + distance * (float) Math.sin(pitchRad);

        cam.setX(camX);
        cam.setY(camY);
        cam.setZ(camZ);
        cam.setYaw(yawRad);
        cam.setPitch(pitchRad);
        cam.setFieldOfView(fovRad);

        return cam;
    }

    // --- Single image render ---

    private Map<String, Object> renderSingleImage(Home home, Camera camera,
                                                   int width, int height,
                                                   AbstractPhotoRenderer.Quality quality,
                                                   String filePath,
                                                   String format) throws Exception {
        PhotoRenderer renderer = null;
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            renderer = new PhotoRenderer(home, quality);
            renderer.render(image, camera, null);

            Map<String, Object> result = new LinkedHashMap<>();
            int sizeBytes;

            if (filePath != null && !filePath.trim().isEmpty()) {
                // File mode: сохранение на диск
                Path path = Paths.get(filePath).toAbsolutePath().normalize();
                String pathLower = path.toString().toLowerCase();
                if ("jpeg".equals(format)) {
                    if (pathLower.endsWith(".png")) {
                        path = Paths.get(path.toString().substring(0, path.toString().length() - 4) + ".jpg");
                    } else if (!pathLower.endsWith(".jpg") && !pathLower.endsWith(".jpeg")) {
                        path = Paths.get(path.toString() + ".jpg");
                    }
                } else {
                    if (!pathLower.endsWith(".png")) {
                        path = Paths.get(path.toString() + ".png");
                    }
                }
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                File file = path.toFile();
                writeImage(image, format, file);
                sizeBytes = (int) Files.size(path);
                result.put("filePath", path.toString());
                result.put("format", format);
            } else {
                // Inline mode: MCP image content
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                writeImage(image, format, baos);
                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                sizeBytes = baos.size();
                String mimeType = "jpeg".equals(format) ? "image/jpeg" : "image/png";
                result.put("_image", base64);
                result.put("_mimeType", mimeType);
                result.put("format", format);
            }

            result.put("width", width);
            result.put("height", height);
            result.put("size_bytes", sizeBytes);

            Map<String, Object> camInfo = new LinkedHashMap<>();
            camInfo.put("x", round2(camera.getX()));
            camInfo.put("y", round2(camera.getY()));
            camInfo.put("z", round2(camera.getZ()));
            camInfo.put("yaw_degrees", round2(Math.toDegrees(camera.getYaw())));
            camInfo.put("pitch_degrees", round2(Math.toDegrees(camera.getPitch())));
            camInfo.put("fov_degrees", round2(Math.toDegrees(camera.getFieldOfView())));
            result.put("camera", camInfo);

            return result;
        } finally {
            if (renderer != null) {
                try {
                    renderer.dispose();
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Error disposing renderer", e);
                }
            }
        }
    }

    // --- Helpers ---

    static String generateIndexedFilePath(String filePath, int index) {
        String clean = filePath.trim();
        String lower = clean.toLowerCase();
        if (lower.endsWith(".png")) {
            return clean.substring(0, clean.length() - 4) + "_" + index + ".png";
        }
        if (lower.endsWith(".jpg")) {
            return clean.substring(0, clean.length() - 4) + "_" + index + ".jpg";
        }
        if (lower.endsWith(".jpeg")) {
            return clean.substring(0, clean.length() - 5) + "_" + index + ".jpeg";
        }
        return clean + "_" + index;
    }

    /**
     * Записывает изображение в указанном формате (png/jpeg).
     * JPEG записывается с quality {@link #JPEG_QUALITY}.
     */
    private static void writeImage(BufferedImage image, String format, File file) throws Exception {
        if ("jpeg".equals(format)) {
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
                writeJpeg(ensureRgb(image), ios);
            }
        } else {
            ImageIO.write(image, "png", file);
        }
    }

    private static void writeImage(BufferedImage image, String format,
                                    ByteArrayOutputStream baos) throws Exception {
        if ("jpeg".equals(format)) {
            try (ImageOutputStream ios = new MemoryCacheImageOutputStream(baos)) {
                writeJpeg(ensureRgb(image), ios);
            }
        } else {
            ImageIO.write(image, "png", baos);
        }
    }

    private static void writeJpeg(BufferedImage image, ImageOutputStream ios) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG ImageWriter available");
        }
        ImageWriter writer = writers.next();
        try {
            JPEGImageWriteParam param = new JPEGImageWriteParam(null);
            param.setCompressionMode(JPEGImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** Конвертирует ARGB в RGB для JPEG (JPEG не поддерживает alpha). */
    private static BufferedImage ensureRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB
                || image.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
            BufferedImage rgb = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            return rgb;
        }
        return image;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // --- Value object ---

    static class SceneBounds {
        float minX, minY, maxX, maxY, maxZ;
        float centerX, centerY, sceneWidth, sceneDepth;
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Renders a 3D photo of the current scene using ray-tracing (Sunflow). "
                + "Returns images as native MCP image content (JPEG by default for compact size). "
                + "Use format='png' for lossless quality.\n\n"
                + "RECOMMENDED: Use view='overhead' for scene assessment — it automatically renders "
                + "the scene from 4 bird's eye angles (NW, SE, NE, SW) with a single call, "
                + "providing comprehensive 3D coverage. This is the most informative option "
                + "for evaluating the scene before making changes.\n\n"
                + "Use focusOn='furniture:<id>' or focusOn='room:<id>' to zoom into a specific object "
                + "for detailed inspection of placement, rotation, and proportions.\n\n"
                + "WALLS: By default walls are hidden in overhead mode for unobstructed view of furniture. "
                + "Set hideWalls=false to show walls (useful for checking wall textures, doors, or windows).\n\n"
                + "Standard mode: returns a single image from the current or specified camera position. "
                + "If 'filePath' is provided, saves image(s) to disk and returns only metadata (no base64). "
                + "Use quality 'low' for quick preview or 'high' for photo-realistic output.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("view", enumProp(
                "View mode: 'overhead' for automatic bird's eye orbit rendering from 4 diagonal angles. "
                        + "Recommended for scene assessment. Incompatible with x/y/z/yaw parameters.",
                "overhead"));
        properties.put("angles", propWithDefault("integer",
                "Number of orbital angles for overhead view: 1 (single NW-to-SE view) "
                        + "or 4 (all four diagonal directions). Only used with view='overhead'.",
                4));
        properties.put("focusOn", prop("string",
                "Focus on a specific object: 'furniture:<id>' or 'room:<id>'. "
                        + "Zooms the overhead camera to show only the specified object with context. "
                        + "Requires view='overhead'. Default angles=1 for furniture, angles=4 for room."));
        properties.put("hideWalls", propWithDefault("boolean",
                "Hide walls in overhead render by reducing them to 1cm height. "
                        + "Default: true (walls hidden for unobstructed view of furniture). "
                        + "Set to false to show walls (e.g. to check wall textures or door/window placement). "
                        + "Only used with view='overhead'.",
                true));
        properties.put("width", propWithDefault("integer", "Image width in pixels", DEFAULT_WIDTH));
        properties.put("height", propWithDefault("integer", "Image height in pixels", DEFAULT_HEIGHT));
        properties.put("quality", enumProp("Quality: 'low' (fast preview) or 'high' (ray-traced)", "low", "high"));
        properties.put("format", enumProp(
                "Image format: 'jpeg' (smaller, default for inline) or 'png' (lossless, default for filePath). "
                        + "JPEG typically produces 5-10x smaller images than PNG for 3D renders.",
                "jpeg", "png"));
        properties.put("filePath", prop("string",
                "Absolute path to save image file(s). Extension is auto-corrected to match format. "
                        + "For overhead with angles=4, files are saved as {path}_1 through {path}_4."));
        properties.put("x", prop("number", "Camera X position in cm (standard mode only)"));
        properties.put("y", prop("number", "Camera Y position in cm (standard mode only)"));
        properties.put("z", prop("number", "Camera Z (height) position in cm (standard mode only)"));
        properties.put("yaw", prop("number", "Camera horizontal rotation in degrees (standard mode only)"));
        properties.put("pitch", prop("number",
                "Camera vertical tilt in degrees. For overhead mode: bird's eye angle "
                        + "(default 30, range 0-90). For standard mode: negative = looking down."));
        properties.put("fov", propWithDefault("number", "Camera field of view in degrees", 63));

        schema.put("properties", properties);
        schema.put("required", Collections.emptyList());
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> propWithDefault(String type, String description, Object defaultValue) {
        Map<String, Object> p = prop(type, description);
        p.put("default", defaultValue);
        return p;
    }

    private static Map<String, Object> enumProp(String description, String... values) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", description);
        p.put("enum", Arrays.asList(values));
        p.put("default", values[0]);
        return p;
    }
}
