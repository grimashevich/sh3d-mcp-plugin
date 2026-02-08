package com.sh3d.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseTest {

    @Test
    void testOkResponse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "value");
        Response resp = Response.ok(data);
        assertTrue(resp.isOk());
        assertFalse(resp.isError());
        assertEquals("ok", resp.getStatus());
        assertEquals("value", resp.getData().get("key"));
        assertNull(resp.getMessage());
    }

    @Test
    void testErrorResponse() {
        Response resp = Response.error("Something failed");
        assertTrue(resp.isError());
        assertFalse(resp.isOk());
        assertEquals("error", resp.getStatus());
        assertEquals("Something failed", resp.getMessage());
        assertNull(resp.getData());
    }
}
