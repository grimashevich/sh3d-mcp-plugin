package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetStateHandlerTest {

    private GetStateHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new GetStateHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    @Test
    void testEmptyScene() {
        Request req = new Request("get_state", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(0, resp.getData().get("wallCount"));
        assertEquals(0, resp.getData().get("roomCount"));
        assertNull(resp.getData().get("boundingBox"));

        @SuppressWarnings("unchecked")
        List<Object> furniture = (List<Object>) resp.getData().get("furniture");
        assertNotNull(furniture);
        assertTrue(furniture.isEmpty());
    }

    @Test
    void testWithWallsReturnsCorrectCount() {
        home.addWall(new Wall(0, 0, 500, 0, 10));
        home.addWall(new Wall(500, 0, 500, 300, 10));

        Request req = new Request("get_state", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(2, resp.getData().get("wallCount"));
    }

    @Test
    void testBoundingBoxCalculation() {
        home.addWall(new Wall(100, 200, 500, 200, 10));
        home.addWall(new Wall(500, 200, 500, 600, 10));
        home.addWall(new Wall(500, 600, 100, 600, 10));
        home.addWall(new Wall(100, 600, 100, 200, 10));

        Request req = new Request("get_state", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        @SuppressWarnings("unchecked")
        Map<String, Object> bb = (Map<String, Object>) resp.getData().get("boundingBox");
        assertNotNull(bb);
        assertEquals(100.0, (double) bb.get("minX"), 0.01);
        assertEquals(200.0, (double) bb.get("minY"), 0.01);
        assertEquals(500.0, (double) bb.get("maxX"), 0.01);
        assertEquals(600.0, (double) bb.get("maxY"), 0.01);
    }

    @Test
    void testBoundingBoxNullWhenNoWalls() {
        Request req = new Request("get_state", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertNull(resp.getData().get("boundingBox"));
    }

    @Test
    void testWithFurnitureReturnsCorrectFields() {
        HomePieceOfFurniture mockPiece = mock(HomePieceOfFurniture.class);
        when(mockPiece.getName()).thenReturn("TestChair");
        when(mockPiece.getX()).thenReturn(150.0f);
        when(mockPiece.getY()).thenReturn(250.0f);
        when(mockPiece.getAngle()).thenReturn((float) Math.toRadians(90));
        when(mockPiece.getWidth()).thenReturn(50.0f);
        when(mockPiece.getDepth()).thenReturn(50.0f);
        when(mockPiece.getHeight()).thenReturn(80.0f);

        home.addPieceOfFurniture(mockPiece);

        Request req = new Request("get_state", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        @SuppressWarnings("unchecked")
        List<Object> furnitureList = (List<Object>) resp.getData().get("furniture");
        assertEquals(1, furnitureList.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) furnitureList.get(0);
        assertEquals("TestChair", item.get("name"));
        assertEquals(150.0, (double) item.get("x"), 0.01);
        assertEquals(250.0, (double) item.get("y"), 0.01);
        assertEquals(50.0, (double) item.get("width"), 0.01);
        assertEquals(50.0, (double) item.get("depth"), 0.01);
        assertEquals(80.0, (double) item.get("height"), 0.01);
    }

    @Test
    void testAngleConvertedToDegrees() {
        HomePieceOfFurniture mockPiece = mock(HomePieceOfFurniture.class);
        when(mockPiece.getName()).thenReturn("Table");
        when(mockPiece.getAngle()).thenReturn((float) Math.toRadians(45));

        home.addPieceOfFurniture(mockPiece);

        Request req = new Request("get_state", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        @SuppressWarnings("unchecked")
        List<Object> furnitureList = (List<Object>) resp.getData().get("furniture");
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) furnitureList.get(0);

        assertEquals(45.0, (double) item.get("angle"), 0.1);
    }

    @Test
    void testAlwaysReturnsOk() {
        Request req = new Request("get_state", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertFalse(resp.isError());
    }
}
