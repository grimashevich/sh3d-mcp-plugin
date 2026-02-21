package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.SchemaUtil.enumProp;
import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
        String name = request.getString("name");

        // Restore from stored camera by name
        if (name != null && !name.isEmpty()) {
            return restoreStoredCamera(name.trim(), accessor);
        }

        String mode = request.getString("mode");
        if (mode == null || mode.isEmpty()) {
            return Response.error("Parameter 'mode' is required (top or observer), or use 'name' to restore a stored camera");
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

            return buildCameraInfo(home.getCamera(), finalMode);
        });

        LOG.info("Camera set to " + finalMode);
        return Response.ok(camInfo);
    }

    private Response restoreStoredCamera(String name, HomeAccessor accessor) {
        Map<String, Object> result = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Camera> stored = home.getStoredCameras();

            Camera found = null;
            for (Camera c : stored) {
                if (name.equals(c.getName())) {
                    found = c;
                    break;
                }
            }
            if (found == null) {
                return null; // signal not found
            }

            // Apply stored camera position to observer camera
            ObserverCamera observer = home.getObserverCamera();
            observer.setX(found.getX());
            observer.setY(found.getY());
            observer.setZ(found.getZ());
            observer.setYaw(found.getYaw());
            observer.setPitch(found.getPitch());
            observer.setFieldOfView(found.getFieldOfView());
            home.setCamera(observer);

            Map<String, Object> info = buildCameraInfo(observer, "observer");
            info.put("restoredFrom", name);
            return info;
        });

        if (result == null) {
            return Response.error("Stored camera '" + name + "' not found");
        }

        LOG.info("Camera restored from stored '" + name + "'");
        return Response.ok(result);
    }

    private static Map<String, Object> buildCameraInfo(Camera cam, String mode) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mode", mode);
        info.put("x", cam.getX());
        info.put("y", cam.getY());
        info.put("z", cam.getZ());
        info.put("yaw_degrees", Math.toDegrees(cam.getYaw()));
        info.put("pitch_degrees", Math.toDegrees(cam.getPitch()));
        info.put("fov_degrees", Math.toDegrees(cam.getFieldOfView()));
        return info;
    }

    @Override
    public String getDescription() {
        return "Sets the camera mode and optionally adjusts position. "
                + "Use mode 'top' for a top-down 2D view, or 'observer' for a 3D perspective view. "
                + "Alternatively, use 'name' to restore a previously stored camera viewpoint "
                + "(saved via store_camera). When 'name' is provided, 'mode' is not required.\n\n"
                + "COORDINATE SYSTEM (observer mode):\n"
                + "- X axis: increases to the right on the 2D plan\n"
                + "- Y axis: increases downward on the 2D plan (screen coordinates)\n"
                + "- Z axis: height above ground floor, increases upward\n\n"
                + "YAW (horizontal rotation, degrees):\n"
                + "- 0 = looking south (toward +Y)\n"
                + "- 90 = looking west (toward -X)\n"
                + "- 180 = looking north (toward -Y)\n"
                + "- 270 = looking east (toward +X)\n"
                + "- Increases clockwise when viewed from above\n\n"
                + "PITCH (vertical tilt, degrees):\n"
                + "- 0 = looking horizontally\n"
                + "- Positive values = looking downward\n"
                + "- Negative values = looking upward\n\n"
                + "TYPICAL VALUES:\n"
                + "- z=170 approximates human eye height (170 cm)\n"
                + "- pitch=10..20 gives a natural slight downward look\n"
                + "- fov=63 is the default field of view\n\n"
                + "EXAMPLE: To place a camera in the NW corner of a 500x400 room looking "
                + "diagonally toward the SE center, use x=50, y=50, z=170, yaw=135, pitch=15.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mode", enumProp("Camera mode: 'top' (2D plan view) or 'observer' (3D perspective). Not required if 'name' is provided.", "observer", "top"));
        properties.put("name", prop("string", "Name of a stored camera viewpoint to restore (saved via store_camera). When provided, mode is not required."));
        properties.put("x", prop("number", "Camera X position in cm. X increases to the right on the 2D plan. Observer mode only."));
        properties.put("y", prop("number", "Camera Y position in cm. Y increases downward on the 2D plan (screen coordinates). Observer mode only."));
        properties.put("z", prop("number", "Camera height above ground in cm. Typical: 170 (eye level). Observer mode only."));
        properties.put("yaw", prop("number", "Horizontal rotation in degrees. 0=south(+Y), 90=west(-X), 180=north(-Y), 270=east(+X). Increases clockwise from above. Observer mode only."));
        properties.put("pitch", prop("number", "Vertical tilt in degrees. 0=horizontal, positive=down, negative=up. Typical: 10-20 for natural view. Observer mode only."));
        properties.put("fov", prop("number", "Field of view in degrees. Default ~63. Observer mode only."));

        schema.put("properties", properties);
        // Neither mode nor name is strictly required — one of them must be provided
        schema.put("required", Collections.emptyList());
        return schema;
    }

}
