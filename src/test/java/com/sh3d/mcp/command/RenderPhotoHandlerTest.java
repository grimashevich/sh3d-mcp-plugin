package com.sh3d.mcp.command;

import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.eteks.sweethome3d.model.Home;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderPhotoHandlerTest {

    private RenderPhotoHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new RenderPhotoHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    // --- Validation tests ---

    @Test
    void testInvalidWidthZero() {
        Response resp = execute("width", 0.0, "height", 600.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("width"));
    }

    @Test
    void testInvalidWidthNegative() {
        Response resp = execute("width", -100.0, "height", 600.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("width"));
    }

    @Test
    void testInvalidWidthTooLarge() {
        Response resp = execute("width", 5000.0, "height", 600.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("width"));
    }

    @Test
    void testInvalidHeightZero() {
        Response resp = execute("width", 800.0, "height", 0.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("height"));
    }

    @Test
    void testInvalidHeightTooLarge() {
        Response resp = execute("width", 800.0, "height", 9999.0);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("height"));
    }

    @Test
    void testInvalidQuality() {
        Response resp = execute("width", 800.0, "height", 600.0, "quality", "ultra");
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("quality"));
    }

    // --- Descriptor tests ---

    @Test
    void testToolName() {
        assertEquals("render_photo", handler.getToolName());
    }

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("ray-trac") || desc.contains("render") || desc.contains("photo"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);

        // Все ожидаемые параметры
        assertTrue(properties.containsKey("width"));
        assertTrue(properties.containsKey("height"));
        assertTrue(properties.containsKey("quality"));
        assertTrue(properties.containsKey("x"));
        assertTrue(properties.containsKey("y"));
        assertTrue(properties.containsKey("z"));
        assertTrue(properties.containsKey("yaw"));
        assertTrue(properties.containsKey("pitch"));
        assertTrue(properties.containsKey("fov"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaQualityEnum() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> qualityProp = (Map<String, Object>) properties.get("quality");

        assertEquals("string", qualityProp.get("type"));
        List<String> enumValues = (List<String>) qualityProp.get("enum");
        assertNotNull(enumValues);
        assertTrue(enumValues.contains("low"));
        assertTrue(enumValues.contains("high"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoRequiredParams() {
        Map<String, Object> schema = handler.getSchema();
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty(), "All params should be optional");
    }

    @Test
    void testImplementsCommandDescriptor() {
        assertTrue(handler instanceof CommandDescriptor);
        assertTrue(handler instanceof CommandHandler);
    }

    // --- Helper ---

    private Response execute(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return handler.execute(new Request("render_photo", params), accessor);
    }
}
