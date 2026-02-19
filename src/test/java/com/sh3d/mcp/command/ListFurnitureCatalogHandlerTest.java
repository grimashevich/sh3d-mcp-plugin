package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListFurnitureCatalogHandlerTest {

    private ListFurnitureCatalogHandler handler;
    private HomeAccessor accessor;

    @BeforeEach
    void setUp() {
        handler = new ListFurnitureCatalogHandler();

        FurnitureCatalog catalog = new FurnitureCatalog();

        FurnitureCategory livingRoom = new FurnitureCategory("Living Room");
        FurnitureCategory bedroom = new FurnitureCategory("Bedroom");

        CatalogPieceOfFurniture table = new CatalogPieceOfFurniture(
                "Dining Table", null, null, 120f, 80f, 75f, true, false);
        CatalogPieceOfFurniture chair = new CatalogPieceOfFurniture(
                "Office Chair", null, null, 50f, 50f, 90f, true, false);
        CatalogPieceOfFurniture bed = new CatalogPieceOfFurniture(
                "Double Bed", null, null, 160f, 200f, 50f, true, false);

        catalog.add(livingRoom, table);
        catalog.add(livingRoom, chair);
        catalog.add(bedroom, bed);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);

        accessor = new HomeAccessor(new Home(), prefs);
    }

    @Test
    void testListAllWithoutFilters() {
        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(3, furniture.size());
    }

    @Test
    void testFilterByQuery() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "table");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Dining Table", item.get("name"));
    }

    @Test
    void testFilterByCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "bedroom");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Double Bed", item.get("name"));
        assertEquals("Bedroom", item.get("category"));
    }

    @Test
    void testFilterByQueryAndCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "chair");
        params.put("category", "living");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Office Chair", item.get("name"));
    }

    @Test
    void testCaseInsensitiveQuery() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "DINING");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Dining Table", item.get("name"));
    }

    @Test
    void testCaseInsensitiveCategory() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "LIVING ROOM");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(2, furniture.size());
    }

    @Test
    void testNoMatchReturnsEmptyList() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "nonexistent");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertTrue(furniture.isEmpty());
    }

    @Test
    void testCategoryNoMatchReturnsEmptyList() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "kitchen");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertTrue(furniture.isEmpty());
    }

    @Test
    void testResponseContainsDimensions() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Dining Table");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals(120.0, (double) item.get("width"), 0.01);
        assertEquals(80.0, (double) item.get("depth"), 0.01);
        assertEquals(75.0, (double) item.get("height"), 0.01);
    }

    @Test
    void testResponseContainsCategoryField() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "Double Bed");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Bedroom", item.get("category"));
    }

    @Test
    void testPartialQueryMatch() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "ble");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(2, furniture.size());
    }

    @Test
    void testNullParams() {
        Request req = new Request("list_furniture_catalog", null);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(3, furniture.size());
    }

    @Test
    void testEmptyStringQueryReturnsAll() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", "");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(3, furniture.size());
    }

    @Test
    void testEmptyStringCategoryReturnsAll() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "");

        Request req = new Request("list_furniture_catalog", params);
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(3, furniture.size());
    }

    @Test
    void testCatalogIdInOutputWhenPresent() {
        // Create catalog with piece that has an ID
        FurnitureCatalog catalog = new FurnitureCatalog();
        FurnitureCategory cat = new FurnitureCategory("Test");
        CatalogPieceOfFurniture piece = new CatalogPieceOfFurniture(
                "table-001", "Test Table", null, null, null,
                100f, 80f, 75f, 0f, true, null, null, true, null, null);
        catalog.add(cat, piece);

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor testAccessor = new HomeAccessor(new Home(), prefs);

        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, testAccessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("table-001", item.get("catalogId"));
    }

    @Test
    void testCatalogIdNotInOutputWhenNull() {
        // Standard catalog from setUp has no IDs
        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, accessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertFalse(furniture.isEmpty());
        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertNull(item.get("catalogId"));
    }

    @Test
    void testNullNamesInCatalogAreSkipped() {
        CatalogPieceOfFurniture normalPiece = mock(CatalogPieceOfFurniture.class);
        when(normalPiece.getName()).thenReturn("Sink");
        when(normalPiece.getWidth()).thenReturn(60f);
        when(normalPiece.getDepth()).thenReturn(50f);
        when(normalPiece.getHeight()).thenReturn(85f);

        CatalogPieceOfFurniture nullNamePiece = mock(CatalogPieceOfFurniture.class);
        when(nullNamePiece.getName()).thenReturn(null);

        FurnitureCategory normalCat = mock(FurnitureCategory.class);
        when(normalCat.getName()).thenReturn("Kitchen");
        when(normalCat.getFurniture()).thenReturn(List.of(normalPiece, nullNamePiece));

        FurnitureCategory nullCat = mock(FurnitureCategory.class);
        when(nullCat.getName()).thenReturn(null);
        CatalogPieceOfFurniture anotherPiece = mock(CatalogPieceOfFurniture.class);
        when(anotherPiece.getName()).thenReturn("Oven");
        when(nullCat.getFurniture()).thenReturn(List.of(anotherPiece));

        FurnitureCatalog catalog = mock(FurnitureCatalog.class);
        when(catalog.getCategories()).thenReturn(List.of(normalCat, nullCat));

        UserPreferences prefs = mock(UserPreferences.class);
        when(prefs.getFurnitureCatalog()).thenReturn(catalog);
        HomeAccessor testAccessor = new HomeAccessor(new Home(), prefs);

        Request req = new Request("list_furniture_catalog", Collections.emptyMap());
        Response resp = handler.execute(req, testAccessor);

        assertTrue(resp.isOk());
        List<?> furniture = (List<?>) resp.getData().get("furniture");
        assertEquals(1, furniture.size());

        Map<?, ?> item = (Map<?, ?>) furniture.get(0);
        assertEquals("Sink", item.get("name"));
        assertEquals("Kitchen", item.get("category"));
    }
}
