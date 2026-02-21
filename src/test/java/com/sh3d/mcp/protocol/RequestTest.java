package com.sh3d.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.util.Collections;
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

    // ==================== getFloat: non-numeric value ====================

    @Test
    void testGetFloatWithNonNumericValueThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", "not-a-number");
        Request req = new Request("test", params);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> req.getFloat("x"));
        assertTrue(ex.getMessage().contains("expected number"));
        assertTrue(ex.getMessage().contains("not-a-number"));
    }

    @Test
    void testGetFloatWithDefaultAndNonNumericValueThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", "abc");
        Request req = new Request("test", params);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> req.getFloat("x", 5.0f));
        assertTrue(ex.getMessage().contains("expected number"));
    }

    @Test
    void testGetFloatWithNullDefaultReturnsDefault() {
        Map<String, Object> params = new LinkedHashMap<>();
        // key absent -> null
        Request req = new Request("test", params);
        assertEquals(42.0f, req.getFloat("missing", 42.0f), 0.01f);
    }

    // ==================== getFloat: string-parseable number ====================

    @Test
    void testGetFloatWithStringNumber() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", "3.14");
        Request req = new Request("test", params);
        assertEquals(3.14f, req.getFloat("x"), 0.01f);
    }

    // ==================== getBoolean ====================

    @Test
    void testGetBooleanTrue() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("flag", true);
        Request req = new Request("test", params);
        assertEquals(Boolean.TRUE, req.getBoolean("flag"));
    }

    @Test
    void testGetBooleanFalse() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("flag", false);
        Request req = new Request("test", params);
        assertEquals(Boolean.FALSE, req.getBoolean("flag"));
    }

    @Test
    void testGetBooleanMissingReturnsNull() {
        Request req = new Request("test", Collections.emptyMap());
        assertNull(req.getBoolean("missing"));
    }

    @Test
    void testGetBooleanWithNonBooleanValueThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("flag", "yes");
        Request req = new Request("test", params);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> req.getBoolean("flag"));
        assertTrue(ex.getMessage().contains("expected boolean"));
        assertTrue(ex.getMessage().contains("yes"));
    }

    @Test
    void testGetBooleanWithIntegerValueThrows() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("flag", 1);
        Request req = new Request("test", params);

        assertThrows(IllegalArgumentException.class, () -> req.getBoolean("flag"));
    }

    // ==================== getString: missing key ====================

    @Test
    void testGetStringMissingKeyReturnsNull() {
        Request req = new Request("test", Collections.emptyMap());
        assertNull(req.getString("nonexistent"));
    }

    @Test
    void testGetStringConvertsNonStringToString() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("num", 42);
        Request req = new Request("test", params);
        assertEquals("42", req.getString("num"));
    }

    // ==================== getRequiredString ====================

    @Test
    void testGetRequiredStringSuccess() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Table");
        Request req = new Request("test", params);
        assertEquals("Table", req.getRequiredString("name"));
    }

    @Test
    void testGetRequiredStringMissingThrows() {
        Request req = new Request("test", Collections.emptyMap());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> req.getRequiredString("name"));
        assertTrue(ex.getMessage().contains("Missing required parameter"));
        assertTrue(ex.getMessage().contains("name"));
    }

    // ==================== Constructor: null params ====================

    @Test
    void testNullParamsTreatedAsEmpty() {
        Request req = new Request("test", null);
        assertNotNull(req.getParams());
        assertTrue(req.getParams().isEmpty());
    }

    // ==================== getAction ====================

    @Test
    void testGetAction() {
        Request req = new Request("create_wall", Collections.emptyMap());
        assertEquals("create_wall", req.getAction());
    }

    // ==================== getParams: unmodifiable ====================

    @Test
    void testGetParamsReturnsUnmodifiableMap() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", "value");
        Request req = new Request("test", params);

        assertThrows(UnsupportedOperationException.class,
                () -> req.getParams().put("new", "val"));
    }

    // ==================== nested objects in params ====================

    @Test
    void testNestedObjectInParams() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("r", 255);
        nested.put("g", 128);
        nested.put("b", 0);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("color", nested);
        Request req = new Request("test", params);

        Object color = req.getParams().get("color");
        assertInstanceOf(Map.class, color);

        @SuppressWarnings("unchecked")
        Map<String, Object> colorMap = (Map<String, Object>) color;
        assertEquals(255, colorMap.get("r"));
        assertEquals(128, colorMap.get("g"));
        assertEquals(0, colorMap.get("b"));
    }

    @Test
    void testGetStringOnNestedObjectReturnsToString() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("x", 1);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("obj", nested);
        Request req = new Request("test", params);

        // getString converts via toString(), so it returns the map's toString
        String result = req.getString("obj");
        assertNotNull(result);
    }
}
