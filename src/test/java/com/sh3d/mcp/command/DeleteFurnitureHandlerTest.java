package com.sh3d.mcp.command;

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

class DeleteFurnitureHandlerTest {

    private DeleteFurnitureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteFurnitureHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    @Test
    void testDeleteSingleFurniture() {
        HomePieceOfFurniture piece = addFurniture("Table", 100, 200);

        Response resp = handler.execute(makeRequest(piece.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testResponseContainsDeletedInfo() {
        HomePieceOfFurniture piece = addFurniture("Sofa", 150, 250);

        Response resp = handler.execute(makeRequest(piece.getId()), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals("Sofa", data.get("name"));
        assertEquals(150f, ((Number) data.get("x")).floatValue(), 0.01f);
        assertEquals(250f, ((Number) data.get("y")).floatValue(), 0.01f);
        assertTrue(((String) data.get("message")).contains("Sofa"));
    }

    @Test
    void testDeleteFromMultiple() {
        addFurniture("Chair", 0, 0);
        HomePieceOfFurniture table = addFurniture("Table", 100, 100);
        addFurniture("Lamp", 200, 200);

        Response resp = handler.execute(makeRequest(table.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("Table", resp.getData().get("name"));
        assertEquals(2, home.getFurniture().size());

        List<HomePieceOfFurniture> remaining = home.getFurniture();
        assertEquals("Chair", remaining.get(0).getName());
        assertEquals("Lamp", remaining.get(1).getName());
    }

    @Test
    void testDeleteFirst() {
        HomePieceOfFurniture a = addFurniture("A", 0, 0);
        addFurniture("B", 100, 100);

        Response resp = handler.execute(makeRequest(a.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("A", resp.getData().get("name"));
        assertEquals(1, home.getFurniture().size());
        assertEquals("B", home.getFurniture().get(0).getName());
    }

    @Test
    void testDeleteLast() {
        addFurniture("A", 0, 0);
        HomePieceOfFurniture b = addFurniture("B", 100, 100);

        Response resp = handler.execute(makeRequest(b.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals("B", resp.getData().get("name"));
        assertEquals(1, home.getFurniture().size());
        assertEquals("A", home.getFurniture().get(0).getName());
    }

    @Test
    void testIdNotFound() {
        addFurniture("Table", 0, 0);

        Response resp = handler.execute(makeRequest("nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getFurniture().size());
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeRequest("any-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
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

        @SuppressWarnings("unchecked")
        Map<String, Object> idProp = (Map<String, Object>) props.get("id");
        assertEquals("string", idProp.get("type"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }

    private HomePieceOfFurniture addFurniture(String name, float x, float y) {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                        name, null, null, 50f, 50f, 50f, true, false));
        piece.setX(x);
        piece.setY(y);
        home.addPieceOfFurniture(piece);
        return piece;
    }

    private Request makeRequest(String id) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        return new Request("delete_furniture", params);
    }
}
