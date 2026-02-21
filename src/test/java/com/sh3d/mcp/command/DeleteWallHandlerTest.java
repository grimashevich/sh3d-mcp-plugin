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

class DeleteWallHandlerTest {

    private DeleteWallHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteWallHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    @Test
    void testDeleteSingleWall() {
        Wall wall = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(wall.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testResponseContainsDeletedInfo() {
        Wall wall = addWall(100, 200, 600, 200);

        Response resp = handler.execute(makeRequest(wall.getId()), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(100f, ((Number) data.get("xStart")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yStart")).floatValue(), 0.01f);
        assertEquals(600f, ((Number) data.get("xEnd")).floatValue(), 0.01f);
        assertEquals(200f, ((Number) data.get("yEnd")).floatValue(), 0.01f);
        assertTrue(((Number) data.get("length")).floatValue() > 0);
        assertTrue(((String) data.get("message")).contains("deleted"));
    }

    @Test
    void testDeleteFromMultiple() {
        addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 400);
        addWall(500, 400, 0, 400);

        Response resp = handler.execute(makeRequest(w2.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(500f, ((Number) resp.getData().get("xStart")).floatValue(), 0.01f);
        assertEquals(0f, ((Number) resp.getData().get("yStart")).floatValue(), 0.01f);
        assertEquals(2, home.getWalls().size());
    }

    @Test
    void testDeleteFirst() {
        Wall w1 = addWall(0, 0, 500, 0);
        addWall(500, 0, 500, 400);

        Response resp = handler.execute(makeRequest(w1.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(0f, ((Number) resp.getData().get("xStart")).floatValue(), 0.01f);
        assertEquals(1, home.getWalls().size());

        Wall remaining = new ArrayList<>(home.getWalls()).get(0);
        assertEquals(500f, remaining.getXStart(), 0.01f);
    }

    @Test
    void testDeleteLast() {
        addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 400);

        Response resp = handler.execute(makeRequest(w2.getId()), accessor);

        assertTrue(resp.isOk());
        assertEquals(500f, ((Number) resp.getData().get("xStart")).floatValue(), 0.01f);
        assertEquals(1, home.getWalls().size());

        Wall remaining = new ArrayList<>(home.getWalls()).get(0);
        assertEquals(0f, remaining.getXStart(), 0.01f);
    }

    @Test
    void testIdNotFound() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest("nonexistent-id"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getWalls().size());
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
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }

    private Wall addWall(float xStart, float yStart, float xEnd, float yEnd) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10, 250);
        home.addWall(wall);
        return wall;
    }

    private Request makeRequest(String id) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", id);
        return new Request("delete_wall", params);
    }
}
