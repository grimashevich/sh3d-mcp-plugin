package com.sh3d.mcp.command;

import com.eteks.sweethome3d.j3d.AbstractPhotoRenderer;
import com.eteks.sweethome3d.j3d.PhotoRenderer;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик команды "render_photo".
 * Рендерит 3D-сцену в PNG через Sunflow ray-tracer и возвращает base64.
 *
 * <pre>
 * Параметры:
 *   width      — ширина изображения в пикселях (default 800, max 4096)
 *   height     — высота изображения в пикселях (default 600, max 4096)
 *   quality    — "low" (быстрый) или "high" (ray-trace) (default "low")
 *   x, y, z    — позиция камеры в см (опционально)
 *   yaw        — горизонтальный поворот в градусах (опционально)
 *   pitch      — вертикальный наклон в градусах (опционально)
 *   fov        — угол обзора в градусах (опционально, default 63)
 * </pre>
 */
public class RenderPhotoHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(RenderPhotoHandler.class.getName());

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int MAX_DIMENSION = 4096;

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

        // Получаем камеру в EDT (быстро, thread-safe)
        boolean hasCustomCamera = request.getParams().containsKey("x")
                || request.getParams().containsKey("y")
                || request.getParams().containsKey("z");

        Camera camera = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Camera cam = home.getCamera();
            if (cam == null) {
                cam = home.getObserverCamera();
            }
            // Клонируем, чтобы не менять камеру сцены
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

        // Рендерим вне EDT (как делает сам SH3D в PhotoPanel)
        PhotoRenderer renderer = null;
        try {
            Home home = accessor.getHome();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            renderer = new PhotoRenderer(home, quality);
            renderer.render(image, camera, null);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("png_base64", base64);
            data.put("width", width);
            data.put("height", height);
            data.put("quality", qualityStr);
            data.put("size_bytes", baos.size());

            Map<String, Object> camInfo = new LinkedHashMap<>();
            camInfo.put("x", camera.getX());
            camInfo.put("y", camera.getY());
            camInfo.put("z", camera.getZ());
            camInfo.put("yaw_degrees", Math.toDegrees(camera.getYaw()));
            camInfo.put("pitch_degrees", Math.toDegrees(camera.getPitch()));
            camInfo.put("fov_degrees", Math.toDegrees(camera.getFieldOfView()));
            data.put("camera", camInfo);

            LOG.info("Rendered photo " + width + "x" + height + " (" + qualityStr
                    + "), size=" + baos.size() + " bytes");

            return Response.ok(data);
        } catch (OutOfMemoryError e) {
            LOG.log(Level.SEVERE, "OOM during render", e);
            return Response.error("Out of memory: reduce image dimensions (current: "
                    + width + "x" + height + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Render failed", e);
            return Response.error("Rendering failed: " + e.getMessage());
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

    @Override
    public String getDescription() {
        return "Renders a 3D photo of the current scene using ray-tracing (Sunflow). "
                + "Returns the image as base64-encoded PNG. Use quality 'low' for quick preview "
                + "(seconds) or 'high' for photo-realistic output (may take longer). "
                + "Optionally specify camera position and orientation.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("width", propWithDefault("integer", "Image width in pixels", DEFAULT_WIDTH));
        properties.put("height", propWithDefault("integer", "Image height in pixels", DEFAULT_HEIGHT));
        properties.put("quality", enumProp("Quality: 'low' (fast preview) or 'high' (ray-traced)", "low", "high"));
        properties.put("x", prop("number", "Camera X position in cm (optional, uses current camera if omitted)"));
        properties.put("y", prop("number", "Camera Y position in cm"));
        properties.put("z", prop("number", "Camera Z (height) position in cm"));
        properties.put("yaw", prop("number", "Camera horizontal rotation in degrees (0 = facing right)"));
        properties.put("pitch", prop("number", "Camera vertical tilt in degrees (negative = looking down)"));
        properties.put("fov", propWithDefault("number", "Camera field of view in degrees", 63));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList());
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
