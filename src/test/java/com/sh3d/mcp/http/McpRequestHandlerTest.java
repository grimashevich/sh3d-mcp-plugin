package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpRequestHandlerTest {

    private CommandRegistry commandRegistry;
    private HomeAccessor mockAccessor;
    private McpRequestHandler handler;

    @BeforeEach
    void setUp() {
        commandRegistry = new CommandRegistry();
        mockAccessor = mock(HomeAccessor.class);
        handler = new McpRequestHandler(commandRegistry, mockAccessor);
    }

    // === POST initialize ===

    @Test
    void testInitializeReturns200WithSessionId() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\"}}";

        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        Headers respHeaders = exchange.getResponseHeaders();
        String sessionId = respHeaders.getFirst("Mcp-Session-Id");
        assertNotNull(sessionId, "Response should contain Mcp-Session-Id header");
        assertFalse(sessionId.isEmpty());

        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(response.contains("\"protocolVersion\":\"2025-03-26\""));
        assertTrue(response.contains("\"capabilities\""));
        assertTrue(response.contains("\"serverInfo\""));
    }

    // === POST notifications/initialized ===

    @Test
    void testNotificationsInitializedReturns202() throws Exception {
        // First, get a session via initialize
        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(202, -1);
    }

    // === POST tools/list ===

    @Test
    void testToolsListWithValidSession() throws Exception {
        // Register a tool with descriptor
        registerTestTool("create_wall", "Create a wall", null);

        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"tools\""));
        assertTrue(response.contains("\"create_wall\""));
        assertTrue(response.contains("\"Create a wall\""));
    }

    @Test
    void testToolsListWithoutSessionReturns400() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Missing Mcp-Session-Id header"));
    }

    @Test
    void testToolsListWithInvalidSessionAutoRecreatesSession() throws Exception {
        registerTestTool("create_wall", "Create a wall", null);

        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";
        HttpExchange exchange = createPostExchange(body, "nonexistent-session-id", null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"tools\""));
        assertTrue(response.contains("\"create_wall\""));

        // New session ID should be set in response
        String newSessionId = exchange.getResponseHeaders().getFirst("Mcp-Session-Id");
        assertNotNull(newSessionId, "Response should contain new Mcp-Session-Id after auto-recreate");
        assertNotEquals("nonexistent-session-id", newSessionId);
    }

    // === POST tools/call ===

    @Test
    void testToolsCallRegisteredTool() throws Exception {
        // Register a tool that returns data
        commandRegistry.register("get_state", (req, acc) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("walls", 5);
            return Response.ok(data);
        });

        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_state\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"isError\":false"));
        assertTrue(response.contains("walls"));
    }

    @Test
    void testToolsCallUnknownTool() throws Exception {
        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nonexistent_tool\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Unknown tool: nonexistent_tool"));
    }

    @Test
    void testToolsCallWithToolNameFromDescriptor() throws Exception {
        // Register with action "create_walls" but toolName "create_room"
        registerTestTool("create_walls", "Create room walls", "create_room");

        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"create_room\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"isError\":false"));
    }

    @Test
    void testToolsCallWithInvalidSessionAutoRecreatesSession() throws Exception {
        commandRegistry.register("get_state", (req, acc) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("walls", 0);
            return Response.ok(data);
        });

        String body = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_state\",\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, "expired-session-id", null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"isError\":false"));

        // Verify new session ID in response
        String newSessionId = exchange.getResponseHeaders().getFirst("Mcp-Session-Id");
        assertNotNull(newSessionId, "Auto-recreated session ID should be in response");
        assertNotEquals("expired-session-id", newSessionId);
    }

    @Test
    void testToolsCallWithoutSessionReturns400() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_state\"}}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void testToolsCallMissingNameInParams() throws Exception {
        String sessionId = initializeSession();

        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"arguments\":{}}}";
        HttpExchange exchange = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Missing 'name'"));
    }

    // === POST with missing/invalid data ===

    @Test
    void testPostMissingMethodReturns400() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Missing 'method' field"));
    }

    @Test
    void testPostEmptyBodyReturns400() throws Exception {
        HttpExchange exchange = createPostExchange("", null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Empty request body"));
    }

    @Test
    void testPostInvalidJsonReturns400() throws Exception {
        HttpExchange exchange = createPostExchange("{invalid json", null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(400), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Invalid JSON"));
    }

    @Test
    void testPostUnknownMethodReturnsError() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"unknown/method\"}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Unknown method: unknown/method"));
    }

    // === POST ping ===

    @Test
    void testPostPingReturns200() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("\"id\":99"));
        assertTrue(response.contains("\"result\":{}"));
    }

    // === DELETE ===

    @Test
    void testDeleteWithSessionRemovesIt() throws Exception {
        String sessionId = initializeSession();

        HttpExchange exchange = createExchange("DELETE", null, sessionId, null);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(200, -1);

        // After deletion, using the old session ID triggers auto-recreate (200, not 404)
        String body = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/list\"}";
        HttpExchange exchange2 = createPostExchange(body, sessionId, null);
        ByteArrayOutputStream responseBody2 = captureResponseBody(exchange2);

        handler.handle(exchange2);

        verify(exchange2).sendResponseHeaders(eq(200), anyLong());

        // A new session ID should be assigned
        String newSessionId = exchange2.getResponseHeaders().getFirst("Mcp-Session-Id");
        assertNotNull(newSessionId);
        assertNotEquals(sessionId, newSessionId);
    }

    @Test
    void testDeleteWithoutSessionReturns200() throws Exception {
        HttpExchange exchange = createExchange("DELETE", null, null, null);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(200, -1);
    }

    // === GET ===

    @Test
    void testGetReturns405() throws Exception {
        HttpExchange exchange = createExchange("GET", null, null, null);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(405, -1);
    }

    // === Unsupported HTTP method ===

    @Test
    void testPutReturns405() throws Exception {
        HttpExchange exchange = createExchange("PUT", null, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(405), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Method not allowed"));
    }

    // === Origin validation ===

    @Test
    void testNullOriginAllowed() throws Exception {
        // No Origin header -- should be allowed (curl, non-browser)
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, null);
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testLocalhostOriginAllowed() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "http://localhost:3000");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testLocalhostIpOriginAllowed() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "http://127.0.0.1:8080");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testHttpsLocalhostOriginAllowed() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "https://localhost");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testForeignOriginReturns403() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        HttpExchange exchange = createPostExchange(body, null, "https://evil.example.com");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(403), anyLong());
        String response = responseBody.toString(StandardCharsets.UTF_8.name());
        assertTrue(response.contains("Forbidden origin"));
    }

    @Test
    void testForeignOriginBlocksAllMethods() throws Exception {
        // DELETE with foreign origin should also be blocked
        HttpExchange exchange = createExchange("DELETE", null, null, "https://evil.example.com");
        ByteArrayOutputStream responseBody = captureResponseBody(exchange);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(eq(403), anyLong());
    }

    // === Helpers ===

    /**
     * Performs an initialize handshake and returns the session ID.
     */
    private String initializeSession() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-03-26\"}}";
        HttpExchange exchange = createPostExchange(body, null, null);
        captureResponseBody(exchange);

        handler.handle(exchange);

        return exchange.getResponseHeaders().getFirst("Mcp-Session-Id");
    }

    /**
     * Creates a mock HttpExchange for POST requests.
     */
    private HttpExchange createPostExchange(String body, String sessionId, String origin) {
        return createExchange("POST", body, sessionId, origin);
    }

    /**
     * Creates a mock HttpExchange with the specified HTTP method, body, session ID, and origin.
     */
    private HttpExchange createExchange(String method, String body, String sessionId, String origin) {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn(method);

        Headers requestHeaders = new Headers();
        if (sessionId != null) {
            requestHeaders.set("Mcp-Session-Id", sessionId);
        }
        if (origin != null) {
            requestHeaders.set("Origin", origin);
        }
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        // Response headers -- use a real Headers object to capture values
        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        if (body != null) {
            InputStream bodyStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            when(exchange.getRequestBody()).thenReturn(bodyStream);
        } else {
            InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
            when(exchange.getRequestBody()).thenReturn(emptyStream);
        }

        return exchange;
    }

    /**
     * Captures the response body written via exchange.getResponseBody().
     */
    private ByteArrayOutputStream captureResponseBody(HttpExchange exchange) {
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);
        return responseBody;
    }

    /**
     * Registers a test tool with CommandDescriptor in the registry.
     */
    private void registerTestTool(String action, String description, String toolName) {
        CommandHandler testHandler = new CommandHandler() {
            @Override
            public Response execute(Request request, HomeAccessor accessor) {
                return Response.ok(Collections.singletonMap("status", "done"));
            }
        };

        // Wrap in a class implementing both interfaces
        class TestDescriptorHandler implements CommandHandler, CommandDescriptor {
            @Override
            public Response execute(Request request, HomeAccessor accessor) {
                return testHandler.execute(request, accessor);
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public Map<String, Object> getSchema() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                schema.put("properties", new LinkedHashMap<>());
                return schema;
            }

            @Override
            public String getToolName() {
                return toolName;
            }
        }

        commandRegistry.register(action, new TestDescriptorHandler());
    }
}
