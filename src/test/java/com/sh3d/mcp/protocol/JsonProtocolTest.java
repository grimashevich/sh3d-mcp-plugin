package com.sh3d.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonProtocolTest {

    @Test
    void testParseValidRequest() {
        String json = "{\"action\": \"create_walls\", \"params\": {\"x\": 0, \"y\": 0, \"width\": 500, \"height\": 400}}";
        Request req = JsonProtocol.parseRequest(json);
        assertEquals("create_walls", req.getAction());
        assertEquals(0, ((Number) req.getParams().get("x")).intValue());
        assertEquals(500, ((Number) req.getParams().get("width")).intValue());
    }

    @Test
    void testParseRequestWithoutParams() {
        String json = "{\"action\": \"ping\"}";
        Request req = JsonProtocol.parseRequest(json);
        assertEquals("ping", req.getAction());
        assertTrue(req.getParams().isEmpty());
    }

    @Test
    void testParseRequestWithEmptyParams() {
        String json = "{\"action\": \"get_state\", \"params\": {}}";
        Request req = JsonProtocol.parseRequest(json);
        assertEquals("get_state", req.getAction());
        assertTrue(req.getParams().isEmpty());
    }

    @Test
    void testParseRequestWithStringParam() {
        String json = "{\"action\": \"place_furniture\", \"params\": {\"name\": \"Table\", \"x\": 100.5, \"y\": 200}}";
        Request req = JsonProtocol.parseRequest(json);
        assertEquals("place_furniture", req.getAction());
        assertEquals("Table", req.getString("name"));
        assertEquals(100.5f, req.getFloat("x"), 0.01f);
        assertEquals(200f, req.getFloat("y"), 0.01f);
    }

    @Test
    void testParseRequestWithNegativeNumbers() {
        String json = "{\"action\": \"test\", \"params\": {\"x\": -10, \"y\": -5.5}}";
        Request req = JsonProtocol.parseRequest(json);
        assertEquals(-10f, req.getFloat("x"), 0.01f);
        assertEquals(-5.5f, req.getFloat("y"), 0.01f);
    }

    @Test
    void testParseRequestWithBooleanAndNull() {
        String json = "{\"action\": \"test\", \"params\": {\"flag\": true, \"other\": null}}";
        Request req = JsonProtocol.parseRequest(json);
        assertEquals(Boolean.TRUE, req.getParams().get("flag"));
        assertNull(req.getParams().get("other"));
    }

    @Test
    void testParseRequestWithEscapedString() {
        String json = "{\"action\": \"test\", \"params\": {\"msg\": \"hello\\nworld\\t!\"}}";
        Request req = JsonProtocol.parseRequest(json);
        assertEquals("hello\nworld\t!", req.getString("msg"));
    }

    @Test
    void testParseInvalidJson() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonProtocol.parseRequest("not a json"));
    }

    @Test
    void testParseEmptyInput() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonProtocol.parseRequest(""));
        assertThrows(IllegalArgumentException.class,
                () -> JsonProtocol.parseRequest(null));
    }

    @Test
    void testParseMissingAction() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonProtocol.parseRequest("{\"params\": {}}"));
    }

    @Test
    void testFormatSuccessResponse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", "0.1.0");
        String json = JsonProtocol.formatSuccess(data);
        assertTrue(json.contains("\"status\":\"ok\""));
        assertTrue(json.contains("\"version\":\"0.1.0\""));
    }

    @Test
    void testFormatErrorResponse() {
        String json = JsonProtocol.formatError("Something went wrong");
        assertTrue(json.contains("\"status\":\"error\""));
        assertTrue(json.contains("Something went wrong"));
    }

    @Test
    void testRoundTrip() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wallsCreated", 4);
        data.put("message", "Room 500x400 created");
        Response resp = Response.ok(data);
        String json = JsonProtocol.formatResponse(resp);
        assertTrue(json.contains("\"status\":\"ok\""));
        assertTrue(json.contains("\"wallsCreated\":4"));
        assertTrue(json.contains("Room 500x400 created"));
    }
}
