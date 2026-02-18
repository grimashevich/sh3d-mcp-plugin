package com.sh3d.mcp.http;

import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcProtocolTest {

    // === parseRequest ===

    @Test
    void testParseRequestValidJsonRpc() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-03-26\"}}";
        Map<String, Object> request = JsonRpcProtocol.parseRequest(json);

        assertEquals("2.0", request.get("jsonrpc"));
        assertEquals(1, request.get("id"));
        assertEquals("initialize", request.get("method"));
        assertNotNull(request.get("params"));
    }

    @Test
    void testParseRequestMinimalObject() {
        String json = "{\"method\":\"ping\"}";
        Map<String, Object> request = JsonRpcProtocol.parseRequest(json);

        assertEquals("ping", request.get("method"));
        assertNull(request.get("id"));
    }

    @Test
    void testParseRequestInvalidJsonThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest("{broken json"));
    }

    @Test
    void testParseRequestEmptyStringThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest(""));
    }

    @Test
    void testParseRequestNullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest(null));
    }

    @Test
    void testParseRequestArrayThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest("[1, 2, 3]"));
    }

    @Test
    void testParseRequestStringLiteralThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonRpcProtocol.parseRequest("\"just a string\""));
    }

    // === getMethod ===

    @Test
    void testGetMethodReturnsMethod() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", "tools/list");
        assertEquals("tools/list", JsonRpcProtocol.getMethod(request));
    }

    @Test
    void testGetMethodReturnsNullWhenMissing() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", 1);
        assertNull(JsonRpcProtocol.getMethod(request));
    }

    @Test
    void testGetMethodConvertsNonStringToString() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", 42);
        assertEquals("42", JsonRpcProtocol.getMethod(request));
    }

    // === getId ===

    @Test
    void testGetIdReturnsIntegerId() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", 42);
        assertEquals(42, JsonRpcProtocol.getId(request));
    }

    @Test
    void testGetIdReturnsStringId() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", "abc-123");
        assertEquals("abc-123", JsonRpcProtocol.getId(request));
    }

    @Test
    void testGetIdReturnsNullWhenMissing() {
        Map<String, Object> request = new LinkedHashMap<>();
        assertNull(JsonRpcProtocol.getId(request));
    }

    // === getParams ===

    @Test
    void testGetParamsReturnsParamsMap() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "test_tool");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("params", params);

        Map<String, Object> result = JsonRpcProtocol.getParams(request);
        assertEquals("test_tool", result.get("name"));
    }

    @Test
    void testGetParamsReturnsEmptyMapWhenMissing() {
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Object> result = JsonRpcProtocol.getParams(request);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetParamsReturnsEmptyMapWhenNotAMap() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("params", "not a map");
        Map<String, Object> result = JsonRpcProtocol.getParams(request);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // === isNotification ===

    @Test
    void testIsNotificationTrueWhenNoId() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", "notifications/initialized");
        assertTrue(JsonRpcProtocol.isNotification(request));
    }

    @Test
    void testIsNotificationFalseWhenIdPresent() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", 1);
        request.put("method", "initialize");
        assertFalse(JsonRpcProtocol.isNotification(request));
    }

    @Test
    void testIsNotificationFalseWhenIdIsNull() {
        // containsKey("id") returns true even if value is null
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", null);
        request.put("method", "initialize");
        assertFalse(JsonRpcProtocol.isNotification(request));
    }

    // === formatResult ===

    @Test
    void testFormatResultWithIntegerId() {
        String json = JsonRpcProtocol.formatResult(1, "ok");
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"result\":\"ok\""));
    }

    @Test
    void testFormatResultWithStringId() {
        String json = JsonRpcProtocol.formatResult("abc", "done");
        assertTrue(json.contains("\"id\":\"abc\""));
        assertTrue(json.contains("\"result\":\"done\""));
    }

    @Test
    void testFormatResultWithNullId() {
        String json = JsonRpcProtocol.formatResult(null, "value");
        assertTrue(json.contains("\"id\":null"));
    }

    @Test
    void testFormatResultWithMapResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", "value");
        String json = JsonRpcProtocol.formatResult(1, result);
        assertTrue(json.contains("\"result\":{\"key\":\"value\"}"));
    }

    @Test
    void testFormatResultWithNullResult() {
        String json = JsonRpcProtocol.formatResult(1, null);
        assertTrue(json.contains("\"result\":null"));
    }

    // === formatError ===

    @Test
    void testFormatErrorParseError() {
        String json = JsonRpcProtocol.formatError(null, JsonRpcProtocol.PARSE_ERROR, "Invalid JSON");
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":null"));
        assertTrue(json.contains("\"code\":-32700"));
        assertTrue(json.contains("\"message\":\"Invalid JSON\""));
    }

    @Test
    void testFormatErrorInvalidRequest() {
        String json = JsonRpcProtocol.formatError(1, JsonRpcProtocol.INVALID_REQUEST, "Bad request");
        assertTrue(json.contains("\"code\":-32600"));
        assertTrue(json.contains("\"id\":1"));
    }

    @Test
    void testFormatErrorMethodNotFound() {
        String json = JsonRpcProtocol.formatError(2, JsonRpcProtocol.METHOD_NOT_FOUND, "Not found");
        assertTrue(json.contains("\"code\":-32601"));
    }

    @Test
    void testFormatErrorInvalidParams() {
        String json = JsonRpcProtocol.formatError(3, JsonRpcProtocol.INVALID_PARAMS, "Invalid params");
        assertTrue(json.contains("\"code\":-32602"));
    }

    @Test
    void testFormatErrorInternalError() {
        String json = JsonRpcProtocol.formatError(4, JsonRpcProtocol.INTERNAL_ERROR, "Internal error");
        assertTrue(json.contains("\"code\":-32603"));
    }

    @Test
    void testFormatErrorEscapesMessage() {
        String json = JsonRpcProtocol.formatError(1, JsonRpcProtocol.PARSE_ERROR, "Error with \"quotes\"");
        assertTrue(json.contains("\\\"quotes\\\""));
    }

    // === formatInitializeResult ===

    @Test
    void testFormatInitializeResultContainsProtocolVersion() {
        String json = JsonRpcProtocol.formatInitializeResult(1, "2025-03-26");
        assertTrue(json.contains("\"protocolVersion\":\"2025-03-26\""));
    }

    @Test
    void testFormatInitializeResultContainsCapabilities() {
        String json = JsonRpcProtocol.formatInitializeResult(1, "2025-03-26");
        assertTrue(json.contains("\"capabilities\":{\"tools\":{\"listChanged\":true}}"));
    }

    @Test
    void testFormatInitializeResultContainsServerInfo() {
        String json = JsonRpcProtocol.formatInitializeResult(1, "2025-03-26");
        assertTrue(json.contains("\"serverInfo\":{\"name\":\"sweethome3d\",\"version\":\"0.1.0\"}"));
    }

    @Test
    void testFormatInitializeResultIsValidJsonRpc() {
        String json = JsonRpcProtocol.formatInitializeResult(42, "2025-03-26");
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":42"));
        assertTrue(json.contains("\"result\":{"));
    }

    // === formatToolsListResult ===

    @Test
    void testFormatToolsListResultEmptyList() {
        String json = JsonRpcProtocol.formatToolsListResult(1, Collections.emptyList());
        assertTrue(json.contains("\"tools\":[]"));
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":1"));
    }

    @Test
    void testFormatToolsListResultWithTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", "create_wall");
        tool.put("description", "Create a wall");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        tool.put("inputSchema", schema);
        tools.add(tool);

        String json = JsonRpcProtocol.formatToolsListResult(1, tools);
        assertTrue(json.contains("\"name\":\"create_wall\""));
        assertTrue(json.contains("\"description\":\"Create a wall\""));
        assertTrue(json.contains("\"inputSchema\":{\"type\":\"object\"}"));
    }

    @Test
    void testFormatToolsListResultMultipleTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        Map<String, Object> tool1 = new LinkedHashMap<>();
        tool1.put("name", "tool_a");
        tools.add(tool1);

        Map<String, Object> tool2 = new LinkedHashMap<>();
        tool2.put("name", "tool_b");
        tools.add(tool2);

        String json = JsonRpcProtocol.formatToolsListResult(1, tools);
        assertTrue(json.contains("\"tool_a\""));
        assertTrue(json.contains("\"tool_b\""));
    }

    // === formatToolCallResult ===

    @Test
    void testFormatToolCallResultOkResponseTextContent() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("wallId", "wall-1");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(1, response);
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"isError\":false"));
        assertTrue(json.contains("\"type\":\"text\""));
        assertTrue(json.contains("\"text\":"));
        assertTrue(json.contains("wallId"));
    }

    @Test
    void testFormatToolCallResultErrorResponse() {
        Response response = Response.error("Wall not found");

        String json = JsonRpcProtocol.formatToolCallResult(2, response);
        assertTrue(json.contains("\"isError\":true"));
        assertTrue(json.contains("\"type\":\"text\""));
        assertTrue(json.contains("Wall not found"));
    }

    @Test
    void testFormatToolCallResultImageResponse() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("image", "iVBORw0KGgoAAAANSUhEUgAAAAE=");
        data.put("mimeType", "image/png");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(3, response);
        assertTrue(json.contains("\"type\":\"image\""));
        assertTrue(json.contains("\"data\":\"iVBORw0KGgoAAAANSUhEUgAAAAE=\""));
        assertTrue(json.contains("\"mimeType\":\"image/png\""));
        assertTrue(json.contains("\"isError\":false"));
    }

    @Test
    void testFormatToolCallResultImageResponseDefaultMimeType() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("image", "base64data");
        // No mimeType key -- should default to image/png
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(4, response);
        assertTrue(json.contains("\"mimeType\":\"image/png\""));
    }

    @Test
    void testFormatToolCallResultOkWithNullData() {
        Response response = Response.ok(null);

        String json = JsonRpcProtocol.formatToolCallResult(5, response);
        assertTrue(json.contains("\"isError\":false"));
        assertTrue(json.contains("\"type\":\"text\""));
        // null data serializes to some text representation
        assertTrue(json.contains("\"text\":"));
    }

    @Test
    void testFormatToolCallResultOkWithEmptyData() {
        Response response = Response.ok(Collections.emptyMap());

        String json = JsonRpcProtocol.formatToolCallResult(6, response);
        assertTrue(json.contains("\"isError\":false"));
        assertTrue(json.contains("\"type\":\"text\""));
    }

    // === Error code constants ===

    @Test
    void testErrorCodeConstants() {
        assertEquals(-32700, JsonRpcProtocol.PARSE_ERROR);
        assertEquals(-32600, JsonRpcProtocol.INVALID_REQUEST);
        assertEquals(-32601, JsonRpcProtocol.METHOD_NOT_FOUND);
        assertEquals(-32602, JsonRpcProtocol.INVALID_PARAMS);
        assertEquals(-32603, JsonRpcProtocol.INTERNAL_ERROR);
    }
}
