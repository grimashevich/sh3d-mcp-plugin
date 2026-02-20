package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик HTTP-запросов для MCP Streamable HTTP endpoint.
 * <p>
 * POST /mcp — JSON-RPC 2.0 запросы (initialize, tools/list, tools/call)
 * GET /mcp — SSE-поток для server→client уведомлений
 * DELETE /mcp — завершение сессии
 */
public class McpRequestHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(McpRequestHandler.class.getName());

    /** Версия MCP-протокола, поддерживаемая сервером */
    private static final String SUPPORTED_PROTOCOL_VERSION = "2025-03-26";

    private final CommandRegistry commandRegistry;
    private final HomeAccessor accessor;
    private final SessionManager sessionManager;

    public McpRequestHandler(CommandRegistry commandRegistry, HomeAccessor accessor) {
        this.commandRegistry = commandRegistry;
        this.accessor = accessor;
        this.sessionManager = new SessionManager();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Валидация Origin (DNS rebinding protection)
            if (!validateOrigin(exchange)) {
                sendJson(exchange, 403, JsonRpcProtocol.formatError(null,
                        JsonRpcProtocol.INTERNAL_ERROR, "Forbidden origin"));
                return;
            }

            String method = exchange.getRequestMethod();
            switch (method) {
                case "POST":
                    handlePost(exchange);
                    break;
                case "GET":
                    handleGet(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    sendJson(exchange, 405, JsonRpcProtocol.formatError(null,
                            JsonRpcProtocol.METHOD_NOT_FOUND, "Method not allowed"));
                    break;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error handling MCP request", e);
            try {
                sendJson(exchange, 500, JsonRpcProtocol.formatError(null,
                        JsonRpcProtocol.INTERNAL_ERROR, "Internal server error: " + e.getMessage()));
            } catch (IOException ignored) {
                // Клиент мог уже отключиться
            }
        } finally {
            exchange.close();
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        if (body.isEmpty()) {
            sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.PARSE_ERROR, "Empty request body"));
            return;
        }

        Map<String, Object> request;
        try {
            request = JsonRpcProtocol.parseRequest(body);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.PARSE_ERROR, "Invalid JSON: " + e.getMessage()));
            return;
        }

        String method = JsonRpcProtocol.getMethod(request);
        Object id = JsonRpcProtocol.getId(request);

        if (method == null) {
            sendJson(exchange, 400, JsonRpcProtocol.formatError(id,
                    JsonRpcProtocol.INVALID_REQUEST, "Missing 'method' field"));
            return;
        }

        LOG.fine("MCP request: method=" + method + " id=" + id);

        switch (method) {
            case "initialize":
                handleInitialize(exchange, request, id);
                break;
            case "notifications/initialized":
                handleInitialized(exchange, request);
                break;
            case "tools/list":
                handleToolsList(exchange, request, id);
                break;
            case "tools/call":
                handleToolsCall(exchange, request, id);
                break;
            case "ping":
                sendJson(exchange, 200, JsonRpcProtocol.formatResult(id, new java.util.LinkedHashMap<>()));
                break;
            default:
                sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
                        JsonRpcProtocol.METHOD_NOT_FOUND, "Unknown method: " + method));
                break;
        }
    }

    private void handleInitialize(HttpExchange exchange, Map<String, Object> request, Object id)
            throws IOException {
        Map<String, Object> params = JsonRpcProtocol.getParams(request);
        String clientVersion = params.containsKey("protocolVersion")
                ? params.get("protocolVersion").toString()
                : SUPPORTED_PROTOCOL_VERSION;

        // Negotiation: сервер отвечает своей версией
        McpSession session = sessionManager.createSession(SUPPORTED_PROTOCOL_VERSION);

        exchange.getResponseHeaders().set("Mcp-Session-Id", session.getSessionId());

        String response = JsonRpcProtocol.formatInitializeResult(id, SUPPORTED_PROTOCOL_VERSION);
        sendJson(exchange, 200, response);

        LOG.info("MCP session created: " + session.getSessionId()
                + " (client version: " + clientVersion + ")");
    }

    private void handleInitialized(HttpExchange exchange, Map<String, Object> request)
            throws IOException {
        String sessionId = getSessionIdHeader(exchange);
        McpSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.setInitialized(true);
        }
        // Notification → 202 Accepted, no body
        exchange.sendResponseHeaders(202, -1);
    }

    private void handleToolsList(HttpExchange exchange, Map<String, Object> request, Object id)
            throws IOException {
        McpSession session = validateSession(exchange);
        if (session == null) return;

        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map.Entry<String, CommandHandler> entry : commandRegistry.getHandlers().entrySet()) {
            String action = entry.getKey();
            CommandHandler handler = entry.getValue();

            if (handler instanceof CommandDescriptor) {
                CommandDescriptor descriptor = (CommandDescriptor) handler;
                Map<String, Object> tool = new LinkedHashMap<>();

                String toolName = descriptor.getToolName();
                tool.put("name", (toolName != null && !toolName.isEmpty()) ? toolName : action);
                tool.put("description", descriptor.getDescription());
                tool.put("inputSchema", descriptor.getSchema());
                tools.add(tool);
            }
        }

        sendJson(exchange, 200, JsonRpcProtocol.formatToolsListResult(id, tools));
    }

    @SuppressWarnings("unchecked")
    private void handleToolsCall(HttpExchange exchange, Map<String, Object> request, Object id)
            throws IOException {
        McpSession session = validateSession(exchange);
        if (session == null) return;

        Map<String, Object> params = JsonRpcProtocol.getParams(request);
        Object nameObj = params.get("name");
        if (nameObj == null) {
            sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
                    JsonRpcProtocol.INVALID_PARAMS, "Missing 'name' in tools/call params"));
            return;
        }
        String toolName = nameObj.toString();

        // Извлекаем arguments
        Map<String, Object> arguments;
        Object argsObj = params.get("arguments");
        if (argsObj instanceof Map) {
            arguments = (Map<String, Object>) argsObj;
        } else {
            arguments = Collections.emptyMap();
        }

        // Находим action по toolName (может совпадать с action или CommandDescriptor.getToolName())
        String action = resolveAction(toolName);
        if (action == null) {
            sendJson(exchange, 200, JsonRpcProtocol.formatError(id,
                    JsonRpcProtocol.METHOD_NOT_FOUND, "Unknown tool: " + toolName));
            return;
        }

        // Dispatch через CommandRegistry
        Request cmdRequest = new Request(action, arguments);
        Response cmdResponse = commandRegistry.dispatch(cmdRequest, accessor);

        sendJson(exchange, 200, JsonRpcProtocol.formatToolCallResult(id, cmdResponse));
    }

    /**
     * Разрешает toolName → action. Учитывает CommandDescriptor.getToolName().
     */
    private String resolveAction(String toolName) {
        // Прямое совпадение с action
        if (commandRegistry.hasHandler(toolName)) {
            return toolName;
        }
        // Поиск по CommandDescriptor.getToolName()
        for (Map.Entry<String, CommandHandler> entry : commandRegistry.getHandlers().entrySet()) {
            CommandHandler handler = entry.getValue();
            if (handler instanceof CommandDescriptor) {
                String descriptorToolName = ((CommandDescriptor) handler).getToolName();
                if (toolName.equals(descriptorToolName)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        // SSE-поток для server→client уведомлений
        // В MVP не используется — сервер не инициирует запросы к клиенту
        exchange.sendResponseHeaders(405, -1);
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        String sessionId = getSessionIdHeader(exchange);
        if (sessionId != null) {
            sessionManager.removeSession(sessionId);
            LOG.info("MCP session removed: " + sessionId);
        }
        exchange.sendResponseHeaders(200, -1);
    }

    // === Утилиты ===

    /**
     * Валидирует сессию по Mcp-Session-Id header.
     * Если сессия не найдена или истекла — автоматически создаёт новую.
     * Возвращает null и отправляет ошибку только если заголовок отсутствует.
     */
    private McpSession validateSession(HttpExchange exchange) throws IOException {
        String sessionId = getSessionIdHeader(exchange);
        if (sessionId == null) {
            sendJson(exchange, 400, JsonRpcProtocol.formatError(null,
                    JsonRpcProtocol.INVALID_REQUEST, "Missing Mcp-Session-Id header"));
            return null;
        }
        McpSession session = sessionManager.getSession(sessionId);
        if (session == null) {
            // Auto-recreate session reusing the same ID (avoids session leak)
            session = sessionManager.createSessionWithId(sessionId, SUPPORTED_PROTOCOL_VERSION);
            session.setInitialized(true);
            LOG.warning("MCP session auto-recreated with same ID: " + sessionId);
        }
        return session;
    }

    private boolean validateOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        // null origin допустим (curl, non-browser clients)
        if (origin == null) {
            return true;
        }
        // Допускаем только localhost origins
        return origin.startsWith("http://localhost")
                || origin.startsWith("http://127.0.0.1")
                || origin.startsWith("https://localhost")
                || origin.startsWith("https://127.0.0.1");
    }

    private String getSessionIdHeader(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
