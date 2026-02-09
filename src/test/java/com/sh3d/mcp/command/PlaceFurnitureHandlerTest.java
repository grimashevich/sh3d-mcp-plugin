package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceFurnitureHandlerTest {

    private PlaceFurnitureHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new PlaceFurnitureHandler();
        home = new Home();

        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory category = new FurnitureCategory("Living Room");

        CatalogPieceOfFurniture table = new CatalogPieceOfFurniture(
                "Dining Table", null, null, 120f, 80f, 75f, true, false);
        CatalogPieceOfFurniture chair = new CatalogPieceOfFurniture(
                "Office Chair", null, null, 50f, 50f, 90f, true, false);

        catalog.add(category, table);
        catalog.add(category, chair);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(home, prefs);
    }

    @Test
    void testPlaceFurnitureSuccess() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 250.0);
        params.put("y", 150.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals("Dining Table", resp.getData().get("name"));
        assertEquals(250.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(150.0, (double) resp.getData().get("y"), 0.01);
    }

    @Test
    void testFurnitureAddedToHome() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);

        Request req = new Request("place_furniture", params);
        handler.execute(req, accessor);

        List<HomePieceOfFurniture> furniture = home.getFurniture();
        assertEquals(1, furniture.size());
        assertEquals("Dining Table", furniture.get(0).getName());
    }

    @Test
    void testCaseInsensitiveSearch() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "dining table");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals("Dining Table", resp.getData().get("name"));
    }

    @Test
    void testPartialNameMatch() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Chair");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals("Office Chair", resp.getData().get("name"));
    }

    @Test
    void testFurnitureNotFound() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Sofa");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Furniture not found"));
        assertTrue(resp.getMessage().contains("Sofa"));
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testMissingNameReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testEmptyNameReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testBlankNameReturnsError() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "   ");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testMissingXThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        assertThrows(IllegalArgumentException.class, () -> handler.execute(req, accessor));
    }

    @Test
    void testMissingYThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 0.0);

        Request req = new Request("place_furniture", params);
        assertThrows(IllegalArgumentException.class, () -> handler.execute(req, accessor));
    }

    @Test
    void testDefaultAngleIsZero() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(0.0, (double) resp.getData().get("angle"), 0.01);
    }

    @Test
    void testCustomAngleConvertedToRadians() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 100.0);
        params.put("y", 200.0);
        params.put("angle", 90.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(90.0, (double) resp.getData().get("angle"), 0.01);

        HomePieceOfFurniture placed = home.getFurniture().get(0);
        assertEquals(Math.toRadians(90), placed.getAngle(), 0.01);
    }

    @Test
    void testResponseContainsDimensions() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Dining Table");
        params.put("x", 0.0);
        params.put("y", 0.0);

        Request req = new Request("place_furniture", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        assertEquals(120.0, (double) resp.getData().get("width"), 0.01);
        assertEquals(80.0, (double) resp.getData().get("depth"), 0.01);
        assertEquals(75.0, (double) resp.getData().get("height"), 0.01);
    }

    @Test
    void testPlaceMultiplePieces() {
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("name", "Dining Table");
        params1.put("x", 100.0);
        params1.put("y", 100.0);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("name", "Office Chair");
        params2.put("x", 200.0);
        params2.put("y", 200.0);

        handler.execute(new Request("place_furniture", params1), accessor);
        handler.execute(new Request("place_furniture", params2), accessor);

        assertEquals(2, home.getFurniture().size());
    }
}
