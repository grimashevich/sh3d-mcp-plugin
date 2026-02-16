package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModifyWallHandlerTest {

    private ModifyWallHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ModifyWallHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    // --- Height ---

    @Test
    void testModifyHeight() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "height", 300.0), accessor);

        assertTrue(resp.isOk());
        Wall wall = getWall(0);
        assertEquals(300f, wall.getHeight(), 0.01f);
        assertEquals(300.0, ((Number) resp.getData().get("height")).doubleValue(), 0.01);
    }

    @Test
    void testModifyHeightAtEnd() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "heightAtEnd", 200.0), accessor);

        assertTrue(resp.isOk());
        Wall wall = getWall(0);
        assertEquals(200f, wall.getHeightAtEnd(), 0.01f);
        assertEquals(200.0, ((Number) resp.getData().get("heightAtEnd")).doubleValue(), 0.01);
    }

    @Test
    void testClearHeightAtEnd() {
        addWall(0, 0, 500, 0);
        getWall(0).setHeightAtEnd(200f);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("heightAtEnd", null);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertNull(getWall(0).getHeightAtEnd());
        assertNull(resp.getData().get("heightAtEnd"));
    }

    @Test
    void testInvalidHeight() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "height", -10.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("height"));
        assertTrue(resp.getMessage().contains("positive"));
    }

    // --- Thickness ---

    @Test
    void testModifyThickness() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "thickness", 20.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(20f, getWall(0).getThickness(), 0.01f);
        assertEquals(20.0, ((Number) resp.getData().get("thickness")).doubleValue(), 0.01);
    }

    @Test
    void testInvalidThickness() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "thickness", 0.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("thickness"));
        assertTrue(resp.getMessage().contains("positive"));
    }

    // --- Arc extent ---

    @Test
    void testSetArcExtent() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "arcExtent", 45.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(Math.toRadians(45), getWall(0).getArcExtent(), 0.01);
        assertEquals(45.0, ((Number) resp.getData().get("arcExtent")).doubleValue(), 0.5);
    }

    @Test
    void testClearArcExtent() {
        addWall(0, 0, 500, 0);
        getWall(0).setArcExtent((float) Math.toRadians(30));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("arcExtent", null);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertNull(getWall(0).getArcExtent());
        assertNull(resp.getData().get("arcExtent"));
    }

    // --- Colors ---

    @Test
    void testSetLeftSideColor() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "leftSideColor", "#FF0000"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFF0000, (int) getWall(0).getLeftSideColor());
        assertEquals("#FF0000", resp.getData().get("leftSideColor"));
    }

    @Test
    void testSetRightSideColor() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "rightSideColor", "#00FF00"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0x00FF00, (int) getWall(0).getRightSideColor());
        assertEquals("#00FF00", resp.getData().get("rightSideColor"));
    }

    @Test
    void testSetTopColor() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "topColor", "#0000FF"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0x0000FF, (int) getWall(0).getTopColor());
        assertEquals("#0000FF", resp.getData().get("topColor"));
    }

    @Test
    void testColorShortcutSetsBothSidesAndTop() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "color", "#AABBCC"), accessor);

        assertTrue(resp.isOk());
        Wall wall = getWall(0);
        assertEquals(0xAABBCC, (int) wall.getLeftSideColor());
        assertEquals(0xAABBCC, (int) wall.getRightSideColor());
        assertEquals(0xAABBCC, (int) wall.getTopColor());
    }

    @Test
    void testColorShortcutClearsAll() {
        addWall(0, 0, 500, 0);
        Wall wall = getWall(0);
        wall.setLeftSideColor(0xFF0000);
        wall.setRightSideColor(0x00FF00);
        wall.setTopColor(0x0000FF);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("color", null);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertNull(getWall(0).getLeftSideColor());
        assertNull(getWall(0).getRightSideColor());
        assertNull(getWall(0).getTopColor());
    }

    @Test
    void testIndividualColorOverridesShortcut() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("color", "#AAAAAA");
        params.put("leftSideColor", "#FF0000");
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        Wall wall = getWall(0);
        assertEquals(0xFF0000, (int) wall.getLeftSideColor());
        assertEquals(0xAAAAAA, (int) wall.getRightSideColor());
        assertEquals(0xAAAAAA, (int) wall.getTopColor());
    }

    @Test
    void testClearLeftSideColor() {
        addWall(0, 0, 500, 0);
        getWall(0).setLeftSideColor(0xFF0000);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("leftSideColor", null);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        assertNull(getWall(0).getLeftSideColor());
        assertNull(resp.getData().get("leftSideColor"));
    }

    @Test
    void testInvalidColorFormat() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "leftSideColor", "red"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("leftSideColor"));
    }

    @Test
    void testInvalidColorShortcut() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "color", "invalid"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("color"));
    }

    // --- Shininess ---

    @Test
    void testSetLeftSideShininess() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "leftSideShininess", 0.5), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.5f, getWall(0).getLeftSideShininess(), 0.01f);
        assertEquals(0.5, ((Number) resp.getData().get("leftSideShininess")).doubleValue(), 0.01);
    }

    @Test
    void testSetRightSideShininess() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "rightSideShininess", 0.8), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.8f, getWall(0).getRightSideShininess(), 0.01f);
    }

    @Test
    void testShininessShortcutSetsBothSides() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "shininess", 0.7), accessor);

        assertTrue(resp.isOk());
        Wall wall = getWall(0);
        assertEquals(0.7f, wall.getLeftSideShininess(), 0.01f);
        assertEquals(0.7f, wall.getRightSideShininess(), 0.01f);
    }

    @Test
    void testShininessOutOfRange() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "shininess", 1.5), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("shininess"));
        assertTrue(resp.getMessage().contains("0.0"));
    }

    @Test
    void testShininessNegative() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, "leftSideShininess", -0.1), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("leftSideShininess"));
    }

    // --- ID validation ---

    @Test
    void testIdOutOfRange() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(5, "height", 300.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testNegativeId() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(-1, "height", 300.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("non-negative"));
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeRequest(0, "height", 300.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testNoModifiableProperties() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    // --- Partial updates ---

    @Test
    void testPartialUpdatePreservesOtherProperties() {
        addWall(0, 0, 500, 0);
        Wall wall = getWall(0);
        wall.setLeftSideColor(0xFF0000);

        Response resp = handler.execute(makeRequest(0, "height", 300.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(300f, wall.getHeight(), 0.01f);
        assertEquals(0xFF0000, (int) wall.getLeftSideColor());
    }

    @Test
    void testMultiplePropertiesAtOnce() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("height", 300.0);
        params.put("thickness", 15.0);
        params.put("color", "#FF5500");
        params.put("shininess", 0.5);
        Response resp = handler.execute(new Request("modify_wall", params), accessor);

        assertTrue(resp.isOk());
        Wall wall = getWall(0);
        assertEquals(300f, wall.getHeight(), 0.01f);
        assertEquals(15f, wall.getThickness(), 0.01f);
        assertEquals(0xFF5500, (int) wall.getLeftSideColor());
        assertEquals(0xFF5500, (int) wall.getRightSideColor());
        assertEquals(0.5f, wall.getLeftSideShininess(), 0.01f);
        assertEquals(0.5f, wall.getRightSideShininess(), 0.01f);
    }

    // --- Response format ---

    @Test
    void testResponseContainsAllFields() {
        addWall(100, 200, 600, 200);

        Response resp = handler.execute(makeRequest(0, "height", 300.0), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(0, data.get("id"));
        assertNotNull(data.get("xStart"));
        assertNotNull(data.get("yStart"));
        assertNotNull(data.get("xEnd"));
        assertNotNull(data.get("yEnd"));
        assertNotNull(data.get("thickness"));
        assertNotNull(data.get("height"));
        assertTrue(data.containsKey("heightAtEnd"));
        assertTrue(data.containsKey("arcExtent"));
        assertTrue(data.containsKey("leftSideColor"));
        assertTrue(data.containsKey("rightSideColor"));
        assertTrue(data.containsKey("topColor"));
        assertNotNull(data.get("leftSideShininess"));
        assertNotNull(data.get("rightSideShininess"));
    }

    // --- Descriptor ---

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));
        assertTrue(props.containsKey("height"));
        assertTrue(props.containsKey("heightAtEnd"));
        assertTrue(props.containsKey("thickness"));
        assertTrue(props.containsKey("arcExtent"));
        assertTrue(props.containsKey("color"));
        assertTrue(props.containsKey("leftSideColor"));
        assertTrue(props.containsKey("rightSideColor"));
        assertTrue(props.containsKey("topColor"));
        assertTrue(props.containsKey("shininess"));
        assertTrue(props.containsKey("leftSideShininess"));
        assertTrue(props.containsKey("rightSideShininess"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
        assertEquals(1, required.size());
    }

    // --- Helpers ---

    private void addWall(float xStart, float yStart, float xEnd, float yEnd) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10, 250);
        home.addWall(wall);
    }

    private Wall getWall(int index) {
        return new ArrayList<>(home.getWalls()).get(index);
    }

    private Request makeRequest(int id, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", (double) id);
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new Request("modify_wall", params);
    }
}
