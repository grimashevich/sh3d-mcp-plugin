package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Обработчик команды "set_camera".
 * Переключает камеру (top/observer) и опционально задаёт позицию.
 *
 * <pre>
 * Параметры:
 *   mode — "top" или "observer" (обязательный)
 *   x, y, z — позиция камеры в см (опционально, только для observer)
 *   yaw — горизонтальный поворот в градусах (опционально)
 *   pitch — вертикальный наклон в градусах (опционально)
 *   fov — угол обзора в градусах (опционально)
 * </pre>
 */
public class SetCameraHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(SetCameraHandler.class.getName());

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String mode = request.getString("mode");
        if (mode == null || mode.isEmpty()) {
            return Response.error("Parameter 'mode' is required (top or observer)");
        }
        mode = mode.toLowerCase();
        if (!"top".equals(mode) && !"observer".equals(mode)) {
            return Response.error("Parameter 'mode' must be 'top' or 'observer', got '" + mode + "'");
        }

        String finalMode = mode;
        Map<String, Object> camInfo = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            if ("top".equals(finalMode)) {
                home.setCamera(home.getTopCamera());
            } else {
                ObserverCamera observer = home.getObserverCamera();
                Map<String, Object> params = request.getParams();

                if (params.containsKey("x")) {
                    observer.setX(request.getFloat("x"));
                }
                if (params.containsKey("y")) {
                    observer.setY(request.getFloat("y"));
                }
                if (params.containsKey("z")) {
                    observer.setZ(request.getFloat("z"));
                }
                if (params.containsKey("yaw")) {
                    observer.setYaw((float) Math.toRadians(request.getFloat("yaw")));
                }
                if (params.containsKey("pitch")) {
                    observer.setPitch((float) Math.toRadians(request.getFloat("pitch")));
                }
                if (params.containsKey("fov")) {
                    observer.setFieldOfView((float) Math.toRadians(request.getFloat("fov")));
                }

                home.setCamera(observer);
            }

            Camera cam = home.getCamera();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("mode", finalMode);
            info.put("x", cam.getX());
            info.put("y", cam.getY());
            info.put("z", cam.getZ());
            info.put("yaw_degrees", Math.toDegrees(cam.getYaw()));
            info.put("pitch_degrees", Math.toDegrees(cam.getPitch()));
            info.put("fov_degrees", Math.toDegrees(cam.getFieldOfView()));
            return info;
        });

        LOG.info("Camera set to " + finalMode);
        return Response.ok(camInfo);
    }

    @Override
    public String getDescription() {
        return "Sets the camera mode and optionally adjusts position. "
                + "Use mode 'top' for a top-down 2D view, or 'observer' for a 3D perspective view. "
                + "When using 'observer', you can set x, y, z position (in cm), "
                + "yaw/pitch rotation (in degrees), and field of view.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", enumProp("Camera mode: 'top' (2D plan view) or 'observer' (3D perspective)", "observer", "top"));
        properties.put("x", prop("number", "Camera X position in cm (observer mode only)"));
        properties.put("y", prop("number", "Camera Y position in cm (observer mode only)"));
        properties.put("z", prop("number", "Camera Z (height) position in cm (observer mode only)"));
        properties.put("yaw", prop("number", "Camera horizontal rotation in degrees (observer mode only)"));
        properties.put("pitch", prop("number", "Camera vertical tilt in degrees (observer mode only)"));
        properties.put("fov", prop("number", "Camera field of view in degrees (observer mode only, default ~63)"));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("mode"));
        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> enumProp(String description, String... values) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", description);
        p.put("enum", Arrays.asList(values));
        return p;
    }
}
