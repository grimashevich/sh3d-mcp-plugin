package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeleteLevelHandlerTest {

    private DeleteLevelHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteLevelHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    @Test
    void testDeleteSingleLevel() {
        addLevel("Ground", 0, 12, 250);

        Response resp = handler.execute(makeRequest(0), accessor);

        assertTrue(resp.isOk());
        assertEquals(0, home.getLevels().size());
    }

    @Test
    void testResponseContainsLevelInfo() {
        addLevel("Ground Floor", 0, 12, 250);

        Response resp = handler.execute(makeRequest(0), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals("Ground Floor", data.get("name"));
        assertEquals(0.0, ((Number) data.get("elevation")).doubleValue(), 0.01);
        assertTrue(((String) data.get("message")).contains("deleted"));
        assertTrue(((String) data.get("message")).contains("Ground Floor"));
    }

    @Test
    void testDeleteFromMultiple() {
        addLevel("Ground", 0, 12, 250);
        addLevel("Upper", 250, 12, 250);
        addLevel("Attic", 500, 10, 180);

        Response resp = handler.execute(makeRequest(1), accessor);

        assertTrue(resp.isOk());
        assertEquals("Upper", resp.getData().get("name"));
        assertEquals(2, home.getLevels().size());
    }

    @Test
    void testDeleteFirst() {
        addLevel("First", 0, 12, 250);
        addLevel("Second", 250, 12, 250);

        handler.execute(makeRequest(0), accessor);

        assertEquals(1, home.getLevels().size());
        assertEquals("Second", home.getLevels().get(0).getName());
    }

    @Test
    void testDeleteLast() {
        addLevel("First", 0, 12, 250);
        addLevel("Second", 250, 12, 250);

        handler.execute(makeRequest(1), accessor);

        assertEquals(1, home.getLevels().size());
        assertEquals("First", home.getLevels().get(0).getName());
    }

    @Test
    void testCascadeDeletesWalls() {
        Level ground = addLevel("Ground", 0, 12, 250);
        Level upper = addLevel("Upper", 250, 12, 250);

        Wall wallOnGround = new Wall(0, 0, 500, 0, 10);
        home.addWall(wallOnGround);
        wallOnGround.setLevel(ground);

        Wall wallOnUpper = new Wall(0, 0, 500, 0, 10);
        home.addWall(wallOnUpper);
        wallOnUpper.setLevel(upper);

        Response resp = handler.execute(makeRequest(0), accessor);

        assertTrue(resp.isOk());
        assertEquals(1, ((Number) resp.getData().get("deletedWalls")).intValue());
        assertEquals(1, home.getWalls().size());
    }

    @Test
    void testCascadeDeletesRooms() {
        Level ground = addLevel("Ground", 0, 12, 250);

        Room room = new Room(new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}});
        home.addRoom(room);
        room.setLevel(ground);

        Response resp = handler.execute(makeRequest(0), accessor);

        assertTrue(resp.isOk());
        assertEquals(1, ((Number) resp.getData().get("deletedRooms")).intValue());
        assertEquals(0, home.getRooms().size());
    }

    @Test
    void testCascadeDeletesLabels() {
        Level ground = addLevel("Ground", 0, 12, 250);

        Label label = new Label("Test", 100, 100);
        home.addLabel(label);
        label.setLevel(ground);

        Response resp = handler.execute(makeRequest(0), accessor);

        assertTrue(resp.isOk());
        assertEquals(1, ((Number) resp.getData().get("deletedLabels")).intValue());
        assertEquals(0, home.getLabels().size());
    }

    @Test
    void testRemainingLevelsCount() {
        addLevel("A", 0, 12, 250);
        addLevel("B", 250, 12, 250);
        addLevel("C", 500, 12, 250);

        Response resp = handler.execute(makeRequest(1), accessor);

        assertTrue(resp.isOk());
        assertEquals(2, ((Number) resp.getData().get("remainingLevels")).intValue());
    }

    @Test
    void testDeletedObjectCountsZero() {
        addLevel("Empty", 0, 12, 250);

        Response resp = handler.execute(makeRequest(0), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(0, ((Number) data.get("deletedWalls")).intValue());
        assertEquals(0, ((Number) data.get("deletedFurniture")).intValue());
        assertEquals(0, ((Number) data.get("deletedRooms")).intValue());
        assertEquals(0, ((Number) data.get("deletedLabels")).intValue());
        assertEquals(0, ((Number) data.get("deletedDimensionLines")).intValue());
    }

    @Test
    void testIdOutOfRange() {
        addLevel("Only", 0, 12, 250);

        Response resp = handler.execute(makeRequest(5), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
        assertEquals(1, home.getLevels().size());
    }

    @Test
    void testNegativeId() {
        addLevel("Only", 0, 12, 250);

        Response resp = handler.execute(makeRequest(-1), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("non-negative"));
        assertEquals(1, home.getLevels().size());
    }

    @Test
    void testEmptyLevels() {
        Response resp = handler.execute(makeRequest(0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());
        assertTrue(handler.getDescription().toLowerCase().contains("level"));
        assertTrue(handler.getDescription().toUpperCase().contains("WARNING"));

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
    }

    private Level addLevel(String name, float elevation, float floorThickness, float height) {
        Level level = new Level(name, elevation, floorThickness, height);
        home.addLevel(level);
        return level;
    }

    private Request makeRequest(int id) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", (double) id);
        return new Request("delete_level", params);
    }
}
