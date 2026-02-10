package com.sh3d.mcp.command;

import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.eteks.sweethome3d.model.Home;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SetCameraHandlerTest {

    private SetCameraHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new SetCameraHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    // --- Validation ---

    @Test
    void testMissingMode() {
        Response resp = execute();
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mode"));
    }

    @Test
    void testInvalidMode() {
        Response resp = execute("mode", "panoramic");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mode"));
        assertTrue(resp.getMessage().contains("panoramic"));
    }

    @Test
    void testEmptyMode() {
        Response resp = execute("mode", "");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("mode"));
    }

    // --- Functional ---

    @Test
    @SuppressWarnings("unchecked")
    void testSetTopCamera() {
        Response resp = execute("mode", "top");
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("top", data.get("mode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetObserverCamera() {
        Response resp = execute("mode", "observer");
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals("observer", data.get("mode"));
    }

    @Test
    void testModeCaseInsensitive() {
        Response resp = execute("mode", "TOP");
        assertFalse(resp.isError());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testObserverWithPosition() {
        Response resp = execute("mode", "observer", "x", 250.0, "y", 200.0, "z", 170.0);
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(250.0f, ((Number) data.get("x")).floatValue(), 0.1f);
        assertEquals(200.0f, ((Number) data.get("y")).floatValue(), 0.1f);
        assertEquals(170.0f, ((Number) data.get("z")).floatValue(), 0.1f);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testObserverWithYawPitch() {
        Response resp = execute("mode", "observer", "yaw", 90.0, "pitch", -30.0);
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(90.0, ((Number) data.get("yaw_degrees")).doubleValue(), 0.5);
        assertEquals(-30.0, ((Number) data.get("pitch_degrees")).doubleValue(), 0.5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testResponseContainsCameraInfo() {
        Response resp = execute("mode", "observer");
        assertFalse(resp.isError());
        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertTrue(data.containsKey("x"));
        assertTrue(data.containsKey("y"));
        assertTrue(data.containsKey("z"));
        assertTrue(data.containsKey("yaw_degrees"));
        assertTrue(data.containsKey("pitch_degrees"));
        assertTrue(data.containsKey("fov_degrees"));
    }

    // --- Descriptor ---

    @Test
    void testToolName() {
        assertEquals("set_camera", handler.getToolName());
    }

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("camera") || desc.contains("Camera"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaHasModeRequired() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("mode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaProperties() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("mode"));
        assertTrue(props.containsKey("x"));
        assertTrue(props.containsKey("y"));
        assertTrue(props.containsKey("z"));
        assertTrue(props.containsKey("yaw"));
        assertTrue(props.containsKey("pitch"));
        assertTrue(props.containsKey("fov"));
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandDescriptor);
        assertTrue(handler instanceof CommandHandler);
    }

    // --- Helper ---

    private Response execute(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return handler.execute(new Request("set_camera", params), accessor);
    }
}
