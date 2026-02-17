package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Обработчик команды "store_camera".
 * Сохраняет текущую позицию камеры как именованную точку обзора (stored camera).
 * Если камера с таким именем существует — перезаписывает.
 * С параметром delete=true — удаляет stored camera по имени.
 */
public class StoreCameraHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(StoreCameraHandler.class.getName());

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            return Response.error("Parameter 'name' is required");
        }
        name = name.trim();

        Boolean delete = request.getBoolean("delete");
        if (Boolean.TRUE.equals(delete)) {
            return deleteCamera(name, accessor);
        }

        return storeCamera(name, accessor);
    }

    private Response storeCamera(String name, HomeAccessor accessor) {
        String finalName = name;
        Map<String, Object> result = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Camera current = home.getCamera();

            // Clone current camera and set name
            Camera stored = current.clone();
            stored.setName(finalName);

            // Get existing stored cameras, replace if name exists
            List<Camera> cameras = new ArrayList<>(home.getStoredCameras());
            boolean replaced = false;
            for (int i = 0; i < cameras.size(); i++) {
                Camera c = cameras.get(i);
                if (finalName.equals(c.getName())) {
                    cameras.set(i, stored);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                cameras.add(stored);
            }
            home.setStoredCameras(cameras);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", finalName);
            data.put("action", replaced ? "replaced" : "created");
            data.put("x", round2(stored.getX()));
            data.put("y", round2(stored.getY()));
            data.put("z", round2(stored.getZ()));
            data.put("yaw_degrees", round2(Math.toDegrees(stored.getYaw())));
            data.put("pitch_degrees", round2(Math.toDegrees(stored.getPitch())));
            data.put("fov_degrees", round2(Math.toDegrees(stored.getFieldOfView())));
            data.put("totalStoredCameras", cameras.size());
            return data;
        });

        LOG.info("Stored camera '" + name + "'");
        return Response.ok(result);
    }

    private Response deleteCamera(String name, HomeAccessor accessor) {
        String finalName = name;
        Map<String, Object> result = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            List<Camera> cameras = new ArrayList<>(home.getStoredCameras());
            boolean removed = cameras.removeIf(c -> finalName.equals(c.getName()));

            if (!removed) {
                return null; // signal not found
            }

            home.setStoredCameras(cameras);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", finalName);
            data.put("action", "deleted");
            data.put("totalStoredCameras", cameras.size());
            return data;
        });

        if (result == null) {
            return Response.error("Stored camera '" + name + "' not found");
        }

        LOG.info("Deleted stored camera '" + name + "'");
        return Response.ok(result);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public String getDescription() {
        return "Saves the current camera position as a named viewpoint (stored camera). "
                + "Use this to bookmark important views (e.g., 'Kitchen overview', 'Living room angle'). "
                + "If a camera with the same name exists, it will be overwritten. "
                + "Set delete=true to remove a stored camera by name.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> nameProp = new LinkedHashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "Name for the stored camera viewpoint (e.g., 'Kitchen overview')");
        properties.put("name", nameProp);

        Map<String, Object> deleteProp = new LinkedHashMap<>();
        deleteProp.put("type", "boolean");
        deleteProp.put("description", "Set to true to delete a stored camera by name instead of storing");
        properties.put("delete", deleteProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("name"));
        return schema;
    }
}
