package com.sh3d.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RequestTest {

    @Test
    void testGetString() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Table");
        Request req = new Request("test", params);
        assertEquals("Table", req.getString("name"));
        assertNull(req.getString("missing"));
    }

    @Test
    void testGetFloat() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", 100.5);
        Request req = new Request("test", params);
        assertEquals(100.5f, req.getFloat("x"), 0.01f);
    }

    @Test
    void testGetFloatWithDefault() {
        Map<String, Object> params = new LinkedHashMap<>();
        Request req = new Request("test", params);
        assertEquals(10.0f, req.getFloat("thickness", 10.0f), 0.01f);
    }

    @Test
    void testGetFloatMissing() {
        Map<String, Object> params = new LinkedHashMap<>();
        Request req = new Request("test", params);
        assertThrows(IllegalArgumentException.class, () -> req.getFloat("missing"));
    }
}
