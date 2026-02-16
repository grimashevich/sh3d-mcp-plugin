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

class ExportPlanImageHandlerTest {

    private ExportPlanImageHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new ExportPlanImageHandler(null);
        accessor = new HomeAccessor(new Home(), null);
    }

    // --- Null PlanView ---

    @Test
    void testExecuteWithNullPlanView() {
        Response resp = handler.execute(
                new Request("export_plan_image", Collections.emptyMap()), accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("PlanView"));
    }

    // --- Parameter validation ---

    @Test
    void testWidthZeroTreatedAsDefault() {
        // width=0 means "no scaling", but planView is null so error is about planView
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("width", 0.0);
        Response resp = handler.execute(new Request("export_plan_image", params), accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("PlanView"));
    }

    @Test
    void testWidthNegativeError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("width", -100.0);
        Response resp = handler.execute(new Request("export_plan_image", params), accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("width"));
    }

    @Test
    void testWidthTooLargeError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("width", 5000.0);
        Response resp = handler.execute(new Request("export_plan_image", params), accessor);
        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("width"));
    }

    // --- Descriptor tests ---

    @Test
    void testToolName() {
        assertNull(handler.getToolName());
    }

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("PNG") || desc.contains("png") || desc.contains("plan") || desc.contains("image"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaStructure() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("width"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaWidthProperty() {
        Map<String, Object> schema = handler.getSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> widthProp = (Map<String, Object>) properties.get("width");

        assertEquals("integer", widthProp.get("type"));
        assertNotNull(widthProp.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoRequiredParams() {
        Map<String, Object> schema = handler.getSchema();
        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandDescriptor);
        assertTrue(handler instanceof CommandHandler);
    }
}
