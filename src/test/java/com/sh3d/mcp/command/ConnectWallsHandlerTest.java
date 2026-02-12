package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
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

class ConnectWallsHandlerTest {

    private ConnectWallsHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ConnectWallsHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    @Test
    void testConnectEndToStart() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(0, 1, "end", "start"), accessor);

        assertTrue(resp.isOk());
        assertSame(w2, w1.getWallAtEnd());
        assertSame(w1, w2.getWallAtStart());
        assertEquals("end", resp.getData().get("wall1End"));
        assertEquals("start", resp.getData().get("wall2End"));
    }

    @Test
    void testConnectStartToEnd() {
        Wall w1 = addWall(500, 0, 500, 300);
        Wall w2 = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequest(0, 1, "start", "end"), accessor);

        assertTrue(resp.isOk());
        assertSame(w2, w1.getWallAtStart());
        assertSame(w1, w2.getWallAtEnd());
    }

    @Test
    void testConnectStartToStart() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(0, 0, 0, 300);

        Response resp = handler.execute(makeRequest(0, 1, "start", "start"), accessor);

        assertTrue(resp.isOk());
        assertSame(w2, w1.getWallAtStart());
        assertSame(w1, w2.getWallAtStart());
    }

    @Test
    void testConnectEndToEnd() {
        Wall w1 = addWall(0, 0, 500, 300);
        Wall w2 = addWall(800, 600, 500, 300);

        Response resp = handler.execute(makeRequest(0, 1, "end", "end"), accessor);

        assertTrue(resp.isOk());
        assertSame(w2, w1.getWallAtEnd());
        assertSame(w1, w2.getWallAtEnd());
    }

    @Test
    void testAutoDetectEndToStart() {
        // w1 ends at (500,0), w2 starts at (500,0) — closest pair is end-start
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequestAutoDetect(0, 1), accessor);

        assertTrue(resp.isOk());
        assertEquals("end", resp.getData().get("wall1End"));
        assertEquals("start", resp.getData().get("wall2End"));
        assertSame(w2, w1.getWallAtEnd());
        assertSame(w1, w2.getWallAtStart());
    }

    @Test
    void testAutoDetectStartToStart() {
        // Both walls start at (0,0) — closest pair is start-start
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(0, 0, 0, 300);

        Response resp = handler.execute(makeRequestAutoDetect(0, 1), accessor);

        assertTrue(resp.isOk());
        assertEquals("start", resp.getData().get("wall1End"));
        assertEquals("start", resp.getData().get("wall2End"));
        assertSame(w2, w1.getWallAtStart());
        assertSame(w1, w2.getWallAtStart());
    }

    @Test
    void testAutoDetectEndToEnd() {
        // Both walls end at (500,300) — closest pair is end-end
        Wall w1 = addWall(0, 0, 500, 300);
        Wall w2 = addWall(800, 600, 500, 300);

        Response resp = handler.execute(makeRequestAutoDetect(0, 1), accessor);

        assertTrue(resp.isOk());
        assertEquals("end", resp.getData().get("wall1End"));
        assertEquals("end", resp.getData().get("wall2End"));
    }

    @Test
    void testAutoDetectStartToEnd() {
        // w1 starts at (500,0), w2 ends at (500,0) — closest pair is start-end
        Wall w1 = addWall(500, 0, 500, 300);
        Wall w2 = addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequestAutoDetect(0, 1), accessor);

        assertTrue(resp.isOk());
        assertEquals("start", resp.getData().get("wall1End"));
        assertEquals("end", resp.getData().get("wall2End"));
    }

    @Test
    void testWall1IdOutOfRange() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequestAutoDetect(5, 0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall1Id"));
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testWall2IdOutOfRange() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequestAutoDetect(0, 5), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall2Id"));
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testSameWallIdError() {
        addWall(0, 0, 500, 0);

        Response resp = handler.execute(makeRequestAutoDetect(0, 0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("itself"));
    }

    @Test
    void testInvalidWall1End() {
        addWall(0, 0, 500, 0);
        addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(0, 1, "middle", "start"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall1End"));
        assertTrue(resp.getMessage().contains("middle"));
    }

    @Test
    void testInvalidWall2End() {
        addWall(0, 0, 500, 0);
        addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(0, 1, "end", "top"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall2End"));
        assertTrue(resp.getMessage().contains("top"));
    }

    @Test
    void testBidirectionalConnection() {
        Wall w1 = addWall(0, 0, 500, 0);
        Wall w2 = addWall(500, 0, 500, 300);

        handler.execute(makeRequest(0, 1, "end", "start"), accessor);

        // Двусторонняя проверка
        assertSame(w2, w1.getWallAtEnd());
        assertSame(w1, w2.getWallAtStart());
        // Другие концы не затронуты
        assertNull(w1.getWallAtStart());
        assertNull(w2.getWallAtEnd());
    }

    @Test
    void testResponseContainsMessage() {
        addWall(0, 0, 500, 0);
        addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(0, 1, "end", "start"), accessor);

        assertTrue(resp.isOk());
        String message = (String) resp.getData().get("message");
        assertNotNull(message);
        assertTrue(message.contains("wall 0"));
        assertTrue(message.contains("wall 1"));
    }

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("wall1Id"));
        assertTrue(props.containsKey("wall2Id"));
        assertTrue(props.containsKey("wall1End"));
        assertTrue(props.containsKey("wall2End"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("wall1Id"));
        assertTrue(required.contains("wall2Id"));
        assertFalse(required.contains("wall1End"));
        assertFalse(required.contains("wall2End"));
    }

    @Test
    void testNegativeWall1Id() {
        addWall(0, 0, 500, 0);
        addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(-1, 1, "end", "start"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall1Id"));
        assertTrue(resp.getMessage().contains("non-negative"));
    }

    @Test
    void testNegativeWall2Id() {
        addWall(0, 0, 500, 0);
        addWall(500, 0, 500, 300);

        Response resp = handler.execute(makeRequest(0, -1, "end", "start"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wall2Id"));
        assertTrue(resp.getMessage().contains("non-negative"));
    }

    // --- Helpers ---

    private Wall addWall(float xStart, float yStart, float xEnd, float yEnd) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10);
        wall.setHeight(250f);
        home.addWall(wall);
        return wall;
    }

    private Request makeRequest(int wall1Id, int wall2Id, String wall1End, String wall2End) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("wall1Id", (double) wall1Id);
        params.put("wall2Id", (double) wall2Id);
        if (wall1End != null) params.put("wall1End", wall1End);
        if (wall2End != null) params.put("wall2End", wall2End);
        return new Request("connect_walls", params);
    }

    private Request makeRequestAutoDetect(int wall1Id, int wall2Id) {
        return makeRequest(wall1Id, wall2Id, null, null);
    }
}
