package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Room;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModifyRoomHandlerTest {

    private ModifyRoomHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new ModifyRoomHandler();
        home = new Home();
        accessor = new HomeAccessor(home, null);
    }

    // --- Name ---

    @Test
    void testSetName() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "name", "Kitchen"), accessor);

        assertTrue(resp.isOk());
        assertEquals("Kitchen", getRoom(0).getName());
        assertEquals("Kitchen", resp.getData().get("name"));
    }

    @Test
    void testSetNameToNull() {
        addRoom();
        getRoom(0).setName("Old Name");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("name", null);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isOk());
        assertNull(getRoom(0).getName());
    }

    // --- Visibility ---

    @Test
    void testSetFloorVisible() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "floorVisible", false), accessor);

        assertTrue(resp.isOk());
        assertFalse(getRoom(0).isFloorVisible());
        assertEquals(false, resp.getData().get("floorVisible"));
    }

    @Test
    void testSetCeilingVisible() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "ceilingVisible", false), accessor);

        assertTrue(resp.isOk());
        assertFalse(getRoom(0).isCeilingVisible());
        assertEquals(false, resp.getData().get("ceilingVisible"));
    }

    @Test
    void testSetAreaVisible() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "areaVisible", true), accessor);

        assertTrue(resp.isOk());
        assertTrue(getRoom(0).isAreaVisible());
        assertEquals(true, resp.getData().get("areaVisible"));
    }

    // --- Colors ---

    @Test
    void testSetFloorColor() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "floorColor", "#CCBB99"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xCCBB99, (int) getRoom(0).getFloorColor());
        assertEquals("#CCBB99", resp.getData().get("floorColor"));
    }

    @Test
    void testSetCeilingColor() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "ceilingColor", "#FFFFFF"), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFFFFFF, (int) getRoom(0).getCeilingColor());
        assertEquals("#FFFFFF", resp.getData().get("ceilingColor"));
    }

    @Test
    void testClearFloorColor() {
        addRoom();
        getRoom(0).setFloorColor(0xFF0000);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("floorColor", null);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isOk());
        assertNull(getRoom(0).getFloorColor());
        assertNull(resp.getData().get("floorColor"));
    }

    @Test
    void testClearCeilingColor() {
        addRoom();
        getRoom(0).setCeilingColor(0x00FF00);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("ceilingColor", null);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isOk());
        assertNull(getRoom(0).getCeilingColor());
        assertNull(resp.getData().get("ceilingColor"));
    }

    @Test
    void testInvalidFloorColorFormat() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "floorColor", "red"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("floorColor"));
    }

    @Test
    void testInvalidCeilingColorFormat() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "ceilingColor", "#GGG"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("ceilingColor"));
    }

    // --- Shininess ---

    @Test
    void testSetFloorShininess() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "floorShininess", 0.5), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.5f, getRoom(0).getFloorShininess(), 0.01f);
        assertEquals(0.5, ((Number) resp.getData().get("floorShininess")).doubleValue(), 0.01);
    }

    @Test
    void testSetCeilingShininess() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "ceilingShininess", 0.8), accessor);

        assertTrue(resp.isOk());
        assertEquals(0.8f, getRoom(0).getCeilingShininess(), 0.01f);
        assertEquals(0.8, ((Number) resp.getData().get("ceilingShininess")).doubleValue(), 0.01);
    }

    @Test
    void testFloorShininessOutOfRange() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "floorShininess", 1.5), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("floorShininess"));
        assertTrue(resp.getMessage().contains("0.0"));
    }

    @Test
    void testCeilingShininessNegative() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "ceilingShininess", -0.1), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("ceilingShininess"));
    }

    // --- ID validation ---

    @Test
    void testIdOutOfRange() {
        addRoom();

        Response resp = handler.execute(makeRequest(5, "name", "Test"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testNegativeId() {
        addRoom();

        Response resp = handler.execute(makeRequest(-1, "name", "Test"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("non-negative"));
    }

    @Test
    void testEmptyScene() {
        Response resp = handler.execute(makeRequest(0, "name", "Test"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("out of range"));
    }

    @Test
    void testNoModifiableProperties() {
        addRoom();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    // --- Partial updates ---

    @Test
    void testPartialUpdatePreservesOtherProperties() {
        addRoom();
        Room room = getRoom(0);
        room.setFloorColor(0xFF0000);
        room.setName("Original");

        Response resp = handler.execute(makeRequest(0, "ceilingShininess", 0.7), accessor);

        assertTrue(resp.isOk());
        assertEquals(0xFF0000, (int) room.getFloorColor());
        assertEquals("Original", room.getName());
        assertEquals(0.7f, room.getCeilingShininess(), 0.01f);
    }

    @Test
    void testMultiplePropertiesAtOnce() {
        addRoom();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", 0.0);
        params.put("name", "Living Room");
        params.put("floorColor", "#CCBB99");
        params.put("ceilingColor", "#FFFFFF");
        params.put("floorShininess", 0.3);
        params.put("areaVisible", true);
        Response resp = handler.execute(new Request("modify_room", params), accessor);

        assertTrue(resp.isOk());
        Room room = getRoom(0);
        assertEquals("Living Room", room.getName());
        assertEquals(0xCCBB99, (int) room.getFloorColor());
        assertEquals(0xFFFFFF, (int) room.getCeilingColor());
        assertEquals(0.3f, room.getFloorShininess(), 0.01f);
        assertTrue(room.isAreaVisible());
    }

    // --- Response format ---

    @Test
    void testResponseContainsAllFields() {
        addRoom();

        Response resp = handler.execute(makeRequest(0, "name", "Test Room"), accessor);

        assertTrue(resp.isOk());
        Map<String, Object> data = resp.getData();
        assertEquals(0, data.get("id"));
        assertEquals("Test Room", data.get("name"));
        assertNotNull(data.get("area"));
        assertTrue(data.containsKey("areaVisible"));
        assertTrue(data.containsKey("floorVisible"));
        assertTrue(data.containsKey("ceilingVisible"));
        assertTrue(data.containsKey("floorColor"));
        assertTrue(data.containsKey("ceilingColor"));
        assertNotNull(data.get("floorShininess"));
        assertNotNull(data.get("ceilingShininess"));
        assertNotNull(data.get("xCenter"));
        assertNotNull(data.get("yCenter"));
        assertNotNull(data.get("points"));

        @SuppressWarnings("unchecked")
        List<Object> points = (List<Object>) data.get("points");
        assertEquals(4, points.size());
    }

    // --- Descriptor ---

    @Test
    void testDescriptorFields() {
        assertNotNull(handler.getDescription());
        assertFalse(handler.getDescription().isEmpty());

        Map<String, Object> schema = handler.getSchema();
        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("floorVisible"));
        assertTrue(props.containsKey("ceilingVisible"));
        assertTrue(props.containsKey("areaVisible"));
        assertTrue(props.containsKey("floorColor"));
        assertTrue(props.containsKey("ceilingColor"));
        assertTrue(props.containsKey("floorShininess"));
        assertTrue(props.containsKey("ceilingShininess"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("id"));
        assertEquals(1, required.size());
    }

    // --- Helpers ---

    private void addRoom() {
        float[][] polygon = {
                {0, 0}, {500, 0}, {500, 400}, {0, 400}
        };
        Room room = new Room(polygon);
        home.addRoom(room);
    }

    private Room getRoom(int index) {
        return home.getRooms().get(index);
    }

    private Request makeRequest(int id, Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", (double) id);
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return new Request("modify_room", params);
    }
}
