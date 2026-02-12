package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModifyFurnitureHandlerTest {

    private ModifyFurnitureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ModifyFurnitureHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    @Test
    void testModifyPosition() {
        addFurniture("Table", 100, 200);

        Response resp = handler.execute(makeRequest(0, "x", 300.0, "y", 400.0), accessor);

        assertTrue(resp.isOk());
        HomePieceOfFurniture piece = home.getFurniture().get(0);
        assertEquals(300f, piece.getX(), 0.01f);
        assertEquals(400f, piece.getY(), 0.01f);

        assertEquals(300.0, ((Number) resp.getData().get("x")).doubleValue(), 0.01);
        assertEquals(400.0, ((Number) resp.getData().get("y")).doubleValue(), 0.01);
    }

    @Test
    void testModifyAngle() {
        addFurniture("Chair", 0, 0);

        Response resp = handler.execute(makeRequest(0, "angle", 90.0), accessor);

        assertTrue(resp.isOk());
        HomePieceOfFurniture piece = home.getFurniture().get(0);
        assertEquals(Math.toRadians(90), piece.getAngle(), 0.01);

        assertEquals(90.0, ((Number) resp.getData().get("angle")).doubleValue(), 0.5);
    }

    @Test
    void testModifyDimensions() {
        addFurniture("Box", 0, 0);

        Response resp = handler.execute(makeRequest(0, "width", 120.0, "depth", 80.0, "height", 75.0), accessor);

        assertTrue(resp.isOk());
        HomePieceOfFurniture piece = home.getFurniture().get(0);
        assertEquals(120f, piece.getWidth(), 0.01f);
        assertEquals(80f, piece.getDepth(), 0.01f);
        assertEquals(75f, piece.getHeight(), 0.01f);
    }

    @Test
    void testModifyElevation() {
        addFurniture("Shelf", 0, 0);

        Response resp = handler.execute(makeRequest(0, "elevation", 150.0), accessor);

        assertTrue(resp.isOk());
        assertEquals(150f, home.getFurniture().get(0).getElevation(), 0.01f);
        assertEquals(150.0, ((Number) resp.getData().get("elevation")).doubleValue(), 0.01);
    }

    @Test
    void testSetColor() {
        addFurniture("Wall unit", 0, 0);

        Response resp = handler.execute(makeRequest(0, "color", "#FF0000"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFF0000, (int) home.getFurniture().get(0).getColor());
        assertEquals("#FF0000", resp.getData().get("color"));
    }

    @Test
    void testResetColor() {
        addFurniture("Desk", 0, 0);
        home.getFurniture().get(0).setColor(0x00FF00);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("color", null);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertNull(home.getFurniture().get(0).getColor());
        assertNull(resp.getData().get("color"));
    }

    @Test
    void testInvalidColorFormat() {
        addFurniture("Chair", 0, 0);

        Response resp = handler.execute(makeRequest(0, "color", "red"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("color"));
    }

    @Test
    void testSetVisible() {
        addFurniture("Lamp", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("visible", false);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertFalse(home.getFurniture().get(0).isVisible());
        assertEquals(false, resp.getData().get("visible"));
    }

    @Test
    void testSetMirrored() {
        addFurniture("Sofa", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("mirrored", true);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isOk());
        assertTrue(home.getFurniture().get(0).isModelMirrored());
        assertEquals(true, resp.getData().get("mirrored"));
    }

    @Test
    void testModifyName() {
        addFurniture("Old Name", 0, 0);

        Response resp = handler.execute(makeRequest(0, "name", "New Name"), accessor);

        assertTrue(resp.isOk());
        assertEquals("New Name", home.getFurniture().get(0).getName());
        assertEquals("New Name", resp.getData().get("name"));
    }

    @Test
    void testPartialUpdateOnlyX() {
        addFurniture("Table", 100, 200);
        home.getFurniture().get(0).setAngle((float) Math.toRadians(45));

        Response resp = handler.execute(makeRequest(0, "x", 500.0), accessor);

        assertTrue(resp.isOk());
        HomePieceOfFurniture piece = home.getFurniture().get(0);
        assertEquals(500f, piece.getX(), 0.01f);
        assertEquals(200f, piece.getY(), 0.01f);
        assertEquals(Math.toRadians(45), piece.getAngle(), 0.01);
    }

    @Test
    void testMultipleProperties() {
        addFurniture("Chair", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("x", 250.0);
        params.put("y", 350.0);
        params.put("angle", 180.0);
        params.put("elevation", 10.0);
        params.put("visible", true);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isOk());
        HomePieceOfFurniture piece = home.getFurniture().get(0);
        assertEquals(250f, piece.getX(), 0.01f);
        assertEquals(350f, piece.getY(), 0.01f);
        assertEquals(Math.toRadians(180), piece.getAngle(), 0.01);
        assertEquals(10f, piece.getElevation(), 0.01f);
        assertTrue(piece.isVisible());
    }

    @Test
    void testIdOutOfRange() {
        addFurniture("Table", 0, 0);

        Response resp = handler.execute(makeRequest(5, "x", 100.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testNegativeId() {
        addFurniture("Table", 0, 0);

        Response resp = handler.execute(makeRequest(-1, "x", 100.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("non-negative"));
    }

    @Test
    void testNoModifiableProperties() {
        addFurniture("Table", 0, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        Response resp = handler.execute(new Request("modify_furniture", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeRequest(0, "x", 100.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testResponseContainsAllFields() {
        addFurniture("Table", 100, 200);

        Response resp = handler.execute(makeRequest(0, "x", 300.0), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(0, data.get("id"));
        assertNotNull(data.get("name"));
        assertNotNull(data.get("x"));
        assertNotNull(data.get("y"));
        assertNotNull(data.get("angle"));
        assertNotNull(data.get("elevation"));
        assertNotNull(data.get("width"));
        assertNotNull(data.get("depth"));
        assertNotNull(data.get("height"));
        assertTrue(data.containsKey("color"));
        assertTrue(data.containsKey("visible"));
        assertTrue(data.containsKey("mirrored"));
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));
        assertTrue(props.containsKey("x"));
        assertTrue(props.containsKey("y"));
        assertTrue(props.containsKey("angle"));
        assertTrue(props.containsKey("color"));
        assertTrue(props.containsKey("visible"));
        assertTrue(props.containsKey("mirrored"));
        assertTrue(props.containsKey("name"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
        assertEquals(1, required.size());
    }

    private void addFurniture(String name, float x, float y) {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new CatalogPieceOfFurniture(
                        name, null, null, 50f, 50f, 50f, true, false));
        piece.setX(x);
        piece.setY(y);
        home.addPieceOfFurniture(piece);
    }

    private Request makeRequest(int id, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", (double) id);
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new Request("modify_furniture", params);
    }
}
