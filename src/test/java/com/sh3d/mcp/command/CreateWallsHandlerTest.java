package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CreateWallsHandlerTest {

    private CreateWallsHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new CreateWallsHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    @Test
    void testNegativeThicknessReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 100.0);
        params.put("height", 100.0);
        params.put("thickness", -5.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("thickness"));
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testCreateRoomAdds4Walls() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 500.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(4, resp.getData().get("wallsCreated"));

        Collection<Wall> walls = home.getWalls();
        assertEquals(4, walls.size());
    }

    @Test
    void testWallCoordinates() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 100.0);
        params.put("y", 200.0);
        params.put("width", 400.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        List<Wall> walls = new ArrayList<>(home.getWalls());
        // Порядок добавления: w1(AB), w2(BC), w3(CD), w4(DA)
        // Ищем стену по координатам — A(100,200)→B(500,200)
        Wall top = findWall(walls, 100, 200, 500, 200);
        assertNotNull(top, "Top wall A→B not found");

        // B(500,200)→C(500,500)
        Wall right = findWall(walls, 500, 200, 500, 500);
        assertNotNull(right, "Right wall B→C not found");

        // C(500,500)→D(100,500)
        Wall bottom = findWall(walls, 500, 500, 100, 500);
        assertNotNull(bottom, "Bottom wall C→D not found");

        // D(100,500)→A(100,200)
        Wall left = findWall(walls, 100, 500, 100, 200);
        assertNotNull(left, "Left wall D→A not found");
    }

    @Test
    void testWallsConnectedInLoop() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 400.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        List<Wall> walls = new ArrayList<>(home.getWalls());
        Wall top = findWall(walls, 0, 0, 400, 0);
        assertNotNull(top);

        // Обход по контуру через wallAtEnd
        Wall current = top;
        int count = 0;
        do {
            assertNotNull(current.getWallAtEnd(), "Wall chain broken at step " + count);
            current = current.getWallAtEnd();
            count++;
        } while (current != top && count < 10);

        assertEquals(4, count, "Loop should contain exactly 4 walls");
    }

    @Test
    void testDefaultThickness() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 100.0);
        params.put("height", 100.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(10.0f, w.getThickness(), 0.01f);
        }
    }

    @Test
    void testCustomThickness() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 100.0);
        params.put("height", 100.0);
        params.put("thickness", 25.0);

        Request req = new Request("create_walls", params);
        handler.execute(req, accessor);

        for (Wall w : home.getWalls()) {
            assertEquals(25.0f, w.getThickness(), 0.01f);
        }
    }

    @Test
    void testNegativeWidthReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", -100.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("width"));
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testZeroHeightReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 100.0);
        params.put("height", 0.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("height"));
        assertEquals(0, home.getWalls().size());
    }

    @Test
    void testMissingRequiredParamThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        // width отсутствует

        Request req = new Request("create_walls", params);
        assertThrows(IllegalArgumentException.class, () -> handler.execute(req, accessor));
    }

    @Test
    void testResponseMessage() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);
        params.put("width", 500.0);
        params.put("height", 300.0);

        Request req = new Request("create_walls", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals("Room 500x300 created", resp.getData().get("message"));
    }

    private Wall findWall(List<Wall> walls, float xs, float ys, float xe, float ye) {
        for (Wall w : walls) {
            if (Math.abs(w.getXStart() - xs) < 0.01f
                    && Math.abs(w.getYStart() - ys) < 0.01f
                    && Math.abs(w.getXEnd() - xe) < 0.01f
                    && Math.abs(w.getYEnd() - ye) < 0.01f) {
                return w;
            }
        }
        return null;
    }
}
