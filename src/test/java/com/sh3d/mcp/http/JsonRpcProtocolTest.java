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

    // === formatToolCallResult: multi-content (_image, _images) ===

    @Test
    void testFormatToolCallResultNewImageWithMetadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("width", 800);
        data.put("height", 600);
        data.put("size_bytes", 45000);
        data.put("_image", "jpegBase64Data");
        data.put("_mimeType", "image/jpeg");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(10, response);
        // Metadata text block (inside escaped JSON string)
        assertTrue(json.contains("\"type\":\"text\""), "Should have text block");
        assertTrue(json.contains("width"), "Text should contain metadata key 'width'");
        assertTrue(json.contains("height"), "Text should contain metadata key 'height'");
        assertTrue(json.contains("size_bytes"), "Text should contain metadata key 'size_bytes'");
        // Image block
        assertTrue(json.contains("\"type\":\"image\""), "Should have image block");
        assertTrue(json.contains("\"data\":\"jpegBase64Data\""));
        assertTrue(json.contains("\"mimeType\":\"image/jpeg\""));
        assertTrue(json.contains("\"isError\":false"));
    }

    @Test
    void testFormatToolCallResultNewImageDefaultMimeType() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_image", "pngBase64");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(11, response);
        assertTrue(json.contains("\"mimeType\":\"image/png\""), "Should default to image/png");
        assertTrue(json.contains("\"data\":\"pngBase64\""));
    }

    @Test
    void testFormatToolCallResultNewImageNoMetadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_image", "onlyImage");
        data.put("_mimeType", "image/png");
        // No non-underscore keys → no text block
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(12, response);
        assertTrue(json.contains("\"type\":\"image\""));
        assertFalse(json.contains("\"type\":\"text\""), "No text block when no metadata");
    }

    @Test
    void testFormatToolCallResultMultipleImages() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("view", "overhead");
        data.put("imageCount", 2);

        List<Map<String, Object>> images = new ArrayList<>();
        Map<String, Object> img1 = new LinkedHashMap<>();
        img1.put("data", "img1base64");
        img1.put("mimeType", "image/jpeg");
        images.add(img1);
        Map<String, Object> img2 = new LinkedHashMap<>();
        img2.put("data", "img2base64");
        img2.put("mimeType", "image/jpeg");
        images.add(img2);
        data.put("_images", images);

        Response response = Response.ok(data);
        String json = JsonRpcProtocol.formatToolCallResult(13, response);

        // Metadata text block (inside escaped JSON string)
        assertTrue(json.contains("overhead"), "Should contain view value");
        assertTrue(json.contains("imageCount"), "Should contain imageCount key");
        // Both image blocks
        assertTrue(json.contains("\"img1base64\""));
        assertTrue(json.contains("\"img2base64\""));
        assertTrue(json.contains("\"isError\":false"));
    }

    @Test
    void testFormatToolCallResultMultipleImagesDefaultMimeType() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> images = new ArrayList<>();
        Map<String, Object> img = new LinkedHashMap<>();
        img.put("data", "noMimeImage");
        // No mimeType → default image/png
        images.add(img);
        data.put("_images", images);

        Response response = Response.ok(data);
        String json = JsonRpcProtocol.formatToolCallResult(14, response);
        assertTrue(json.contains("\"mimeType\":\"image/png\""));
    }

    @Test
    void testFormatToolCallResultUnderscoreKeysExcludedFromMetadata() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("format", "jpeg");
        data.put("_image", "secretBase64");
        data.put("_mimeType", "image/jpeg");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(15, response);
        // Text block should have "format" but not "_image" or "_mimeType"
        assertTrue(json.contains("format"), "Metadata should contain 'format'");
        assertFalse(json.contains("_image"), "Metadata should NOT contain '_image' key");
        assertFalse(json.contains("_mimeType"), "Metadata should NOT contain '_mimeType' key");
        // Base64 data appears only in image block
        int count = json.split("secretBase64", -1).length - 1;
        assertEquals(1, count, "Base64 data should appear only in image block, not in text block");
    }

    @Test
    void testFormatToolCallResultLegacyImageStillWorks() {
        // Backward compatibility: old "image" key without underscore
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("image", "legacyBase64");
        data.put("mimeType", "image/png");
        Response response = Response.ok(data);

        String json = JsonRpcProtocol.formatToolCallResult(16, response);
        assertTrue(json.contains("\"type\":\"image\""));
        assertTrue(json.contains("\"data\":\"legacyBase64\""));
        assertTrue(json.contains("\"mimeType\":\"image/png\""));
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
