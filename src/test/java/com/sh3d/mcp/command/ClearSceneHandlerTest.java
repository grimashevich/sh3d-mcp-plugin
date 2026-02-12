package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClearSceneHandlerTest {

    private ClearSceneHandler handler;
    private Home home;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new ClearSceneHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    // --- Functional ---

    @Test
    @SuppressWarnings("unchecked")
    void testClearEmptyScene() {
        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("deletedWalls"));
        assertEquals(0, data.get("deletedFurniture"));
        assertEquals(0, data.get("deletedRooms"));
        assertEquals(0, data.get("deletedLabels"));
        assertEquals(0, data.get("deletedDimensionLines"));
        assertEquals(0, data.get("totalDeleted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearWalls() {
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        home.addWall(new Wall(500, 0, 500, 400, 10, 250));
        assertEquals(2, home.getWalls().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, data.get("deletedWalls"));
        assertEquals(2, data.get("totalDeleted"));
        assertTrue(home.getWalls().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearFurniture() {
        HomePieceOfFurniture piece = new HomePieceOfFurniture(
                new com.eteks.sweethome3d.model.CatalogPieceOfFurniture(
                        "Test Chair", null, null, 50f, 50f, 90f, true, false));
        home.addPieceOfFurniture(piece);
        assertEquals(1, home.getFurniture().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, data.get("deletedFurniture"));
        assertTrue(home.getFurniture().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearRooms() {
        home.addRoom(new Room(new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}}));
        home.addRoom(new Room(new float[][]{{600, 0}, {900, 0}, {900, 300}}));
        assertEquals(2, home.getRooms().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(2, data.get("deletedRooms"));
        assertTrue(home.getRooms().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearLabels() {
        home.addLabel(new Label("Label 1", 100, 100));
        home.addLabel(new Label("Label 2", 200, 200));
        home.addLabel(new Label("Label 3", 300, 300));
        assertEquals(3, home.getLabels().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(3, data.get("deletedLabels"));
        assertTrue(home.getLabels().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearDimensionLines() {
        home.addDimensionLine(new DimensionLine(0, 0, 500, 0, 20));
        assertEquals(1, home.getDimensionLines().size());

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(1, data.get("deletedDimensionLines"));
        assertTrue(home.getDimensionLines().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearMixedScene() {
        // Add various objects
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        home.addWall(new Wall(500, 0, 500, 400, 10, 250));
        home.addWall(new Wall(500, 400, 0, 400, 10, 250));
        home.addRoom(new Room(new float[][]{{0, 0}, {500, 0}, {500, 400}, {0, 400}}));
        home.addLabel(new Label("Kitchen", 250, 200));
        home.addDimensionLine(new DimensionLine(0, 0, 500, 0, 20));

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(3, data.get("deletedWalls"));
        assertEquals(0, data.get("deletedFurniture"));
        assertEquals(1, data.get("deletedRooms"));
        assertEquals(1, data.get("deletedLabels"));
        assertEquals(1, data.get("deletedDimensionLines"));
        assertEquals(6, data.get("totalDeleted"));

        // Verify everything is empty
        assertTrue(home.getWalls().isEmpty());
        assertTrue(home.getFurniture().isEmpty());
        assertTrue(home.getRooms().isEmpty());
        assertTrue(home.getLabels().isEmpty());
        assertTrue(home.getDimensionLines().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearTwiceSecondIsNoop() {
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        execute();

        Response resp = execute();
        assertFalse(resp.isError());

        Map<String, Object> data = (Map<String, Object>) resp.getData();
        assertEquals(0, data.get("totalDeleted"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testResponseContainsAllFields() {
        Response resp = execute();
        Map<String, Object> data = (Map<String, Object>) resp.getData();

        assertTrue(data.containsKey("deletedWalls"));
        assertTrue(data.containsKey("deletedFurniture"));
        assertTrue(data.containsKey("deletedRooms"));
        assertTrue(data.containsKey("deletedLabels"));
        assertTrue(data.containsKey("deletedDimensionLines"));
        assertTrue(data.containsKey("totalDeleted"));
        assertEquals(6, data.size());
    }

    // --- Descriptor ---

    @Test
    void testDescriptionNotEmpty() {
        String desc = handler.getDescription();
        assertNotNull(desc);
        assertFalse(desc.isEmpty());
        assertTrue(desc.contains("removes") || desc.contains("Removes") || desc.contains("clear") || desc.contains("Clear"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSchemaNoParams() {
        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props);
        assertTrue(props.isEmpty());

        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required);
        assertTrue(required.isEmpty());
    }

    @Test
    void testToolNameNull() {
        assertNull(handler.getToolName());
    }

    @Test
    void testImplementsInterfaces() {
        assertTrue(handler instanceof CommandHandler);
        assertTrue(handler instanceof CommandDescriptor);
    }

    // --- Helper ---

    private Response execute() {
        return handler.execute(new Request("clear_scene", Collections.emptyMap()), accessor);
    }
}
