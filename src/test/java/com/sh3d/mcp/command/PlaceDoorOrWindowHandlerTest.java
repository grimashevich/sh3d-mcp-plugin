package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceDoorOrWindowHandlerTest {

    private PlaceDoorOrWindowHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() throws Exception {
        handler = new PlaceDoorOrWindowHandler();
        home = new Home();

        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory doorsCategory = new FurnitureCategory("Doors");
        FurnitureCategory windowsCategory = new FurnitureCategory("Windows");
        FurnitureCategory furnitureCategory = new FurnitureCategory("Living Room");

        // SH3D bug: простой конструктор не передаёт doorOrWindow в master-конструктор.
        // Используем reflection для установки private final поля.
        CatalogPieceOfFurniture door = new CatalogPieceOfFurniture(
                "Front Door", null, null, 80f, 10f, 210f, false, false);
        setDoorOrWindow(door, true);

        CatalogPieceOfFurniture window = new CatalogPieceOfFurniture(
                "Double Window", null, null, 120f, 8f, 100f, false, false);
        setDoorOrWindow(window, true);

        // isDoorOrWindow = false — regular furniture
        CatalogPieceOfFurniture table = new CatalogPieceOfFurniture(
                "Dining Table", null, null, 120f, 80f, 75f, true, false);

        catalog.add(doorsCategory, door);
        catalog.add(windowsCategory, window);
        catalog.add(furnitureCategory, table);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(home, prefs);
    }

    /**
     * Устанавливает doorOrWindow через reflection.
     * Простой конструктор CatalogPieceOfFurniture (SH3D 7.x) не передаёт
     * этот параметр в master-конструктор — баг в цепочке делегирования.
     */
    private static void setDoorOrWindow(CatalogPieceOfFurniture piece, boolean value)
            throws Exception {
        Field field = CatalogPieceOfFurniture.class.getDeclaredField("doorOrWindow");
        field.setAccessible(true);
        field.set(piece, value);
    }

    private Wall addWall(float xStart, float yStart, float xEnd, float yEnd) {
        Wall wall = new Wall(xStart, yStart, xEnd, yEnd, 10);
        wall.setHeight(250f);
        home.addWall(wall);
        return wall;
    }

    // --- Success cases ---

    @Test
    void testPlaceDoorInWallCenter() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Front Door", resp.getData().get("name"));
        assertEquals(250.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(0.0, (double) resp.getData().get("y"), 0.01);
        assertEquals(true, resp.getData().get("isDoorOrWindow"));
    }

    @Test
    void testFurnitureAddedToHome() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);

        handler.execute(new Request("place_door_or_window", params), accessor);

        assertEquals(1, home.getFurniture().size());
        assertTrue(home.getFurniture().get(0).isDoorOrWindow());
    }

    @Test
    void testPlaceWindowWithElevation() {
        addWall(0, 0, 400, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Double Window");
        params.put("wallId", 0.0);
        params.put("elevation", 90.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(90.0, (double) resp.getData().get("elevation"), 0.01);
        assertEquals(90f, home.getFurniture().get(0).getElevation(), 0.01f);
    }

    @Test
    void testPlaceWithMirroredTrue() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);
        params.put("mirrored", true);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(true, resp.getData().get("mirrored"));
        assertTrue(home.getFurniture().get(0).isModelMirrored());
    }

    @Test
    void testDefaultMirroredIsFalse() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(false, resp.getData().get("mirrored"));
    }

    // --- Position calculations ---

    @Test
    void testPlaceAtPositionZero() {
        addWall(100, 200, 500, 200);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);
        params.put("position", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(100.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(200.0, (double) resp.getData().get("y"), 0.01);
    }

    @Test
    void testPlaceAtPositionOne() {
        addWall(100, 200, 500, 200);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);
        params.put("position", 1.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(500.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(200.0, (double) resp.getData().get("y"), 0.01);
    }

    @Test
    void testCustomPosition() {
        addWall(0, 0, 400, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);
        params.put("position", 0.25);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(100.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(0.0, (double) resp.getData().get("y"), 0.01);
    }

    // --- Angle calculations ---

    @Test
    void testAngleHorizontalWall() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.0, (double) resp.getData().get("angle"), 0.01);
    }

    @Test
    void testAngleVerticalWall() {
        addWall(0, 0, 0, 300);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(90.0, (double) resp.getData().get("angle"), 0.01);
    }

    @Test
    void testAngleDiagonalWall() {
        addWall(0, 0, 300, 300);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(45.0, (double) resp.getData().get("angle"), 0.01);
    }

    // --- Catalog filtering ---

    @Test
    void testCatalogFiltersOnlyDoorsWindows() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Front Door", resp.getData().get("name"));
    }

    @Test
    void testRegularFurnitureNotFoundAsDoorOrWindow() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Table");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Door/window not found"));
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testCaseInsensitiveSearch() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "front door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Front Door", resp.getData().get("name"));
    }

    @Test
    void testPartialNameMatch() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Window");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals("Double Window", resp.getData().get("name"));
    }

    // --- Validation errors ---

    @Test
    void testMissingNameReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testEmptyNameReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("name"));
    }

    @Test
    void testMissingWallIdReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("wallId"));
    }

    @Test
    void testWallIdOutOfRangeReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 5.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
        assertEquals(0, home.getFurniture().size());
    }

    @Test
    void testNegativeWallIdReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", -1.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("non-negative"));
    }

    @Test
    void testPositionAboveOneReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);
        params.put("position", 1.5);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("position"));
    }

    @Test
    void testPositionBelowZeroReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);
        params.put("position", -0.1);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("position"));
    }

    // --- Misc ---

    @Test
    void testMultiplePlacementsOnSameWall() {
        addWall(0, 0, 600, 0);

        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("name", "Front Door");
        params1.put("wallId", 0.0);
        params1.put("position", 0.25);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("name", "Double Window");
        params2.put("wallId", 0.0);
        params2.put("position", 0.75);

        handler.execute(new Request("place_door_or_window", params1), accessor);
        handler.execute(new Request("place_door_or_window", params2), accessor);

        assertEquals(2, home.getFurniture().size());
        assertEquals(150f, home.getFurniture().get(0).getX(), 0.01f);
        assertEquals(450f, home.getFurniture().get(1).getX(), 0.01f);
    }

    @Test
    void testResponseContainsAllFields() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertNotNull(data.get("name"));
        assertNotNull(data.get("x"));
        assertNotNull(data.get("y"));
        assertNotNull(data.get("angle"));
        assertNotNull(data.get("elevation"));
        assertNotNull(data.get("width"));
        assertNotNull(data.get("depth"));
        assertNotNull(data.get("height"));
        assertNotNull(data.get("isDoorOrWindow"));
        assertNotNull(data.get("mirrored"));
        assertNotNull(data.get("wallId"));
        assertNotNull(data.get("position"));
    }

    @Test
    void testDefaultPositionIsCenter() {
        addWall(0, 0, 400, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Front Door");
        params.put("wallId", 0.0);
        // no position param — should default to 0.5

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isOk());
        assertEquals(200.0, (double) resp.getData().get("x"), 0.01);
        assertEquals(0.5, (double) resp.getData().get("position"), 0.01);
    }

    @Test
    void testDoorNotFoundReturnsError() {
        addWall(0, 0, 500, 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "NonExistent Door");
        params.put("wallId", 0.0);

        Response resp = handler.execute(new Request("place_door_or_window", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Door/window not found"));
        assertTrue(resp.getMessage().contains("NonExistent Door"));
    }
}
