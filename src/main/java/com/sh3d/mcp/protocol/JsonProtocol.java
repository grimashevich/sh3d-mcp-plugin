package com.sh3d.mcp.protocol;

import java.util.Collections;
import java.util.Map;

/**
 * Парсинг входящих JSON-запросов и форматирование исходящих JSON-ответов.
 * Делегирует JSON-операции в {@link JsonUtil}.
 */
public final class JsonProtocol {

    private JsonProtocol() {
    }

    /**
     * Парсит JSON-строку запроса в объект Request.
     *
     * @param json строка вида {"action": "...", "params": {...}}
     * @return распарсенный Request
     * @throws IllegalArgumentException если JSON невалиден или отсутствует action
     */
    public static Request parseRequest(String json) {
        Object parsed = JsonUtil.parse(json);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Invalid JSON: expected object at root");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        Object action = root.get("action");
        if (action == null || action.toString().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty 'action' field");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = root.containsKey("params") && root.get("params") instanceof Map
                ? (Map<String, Object>) root.get("params")
                : Collections.emptyMap();
        return new Request(action.toString(), params);
    }

    /**
     * Форматирует Response в JSON-строку (одна строка, без переносов).
     */
    public static String formatResponse(Response response) {
        if (response.isOk()) {
            return formatSuccess(response.getData());
        } else {
            return formatError(response.getMessage());
        }
    }

    /**
     * Форматирует успешный ответ.
     */
    public static String formatSuccess(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"ok\",\"data\":");
        JsonUtil.appendValue(sb, data);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Форматирует ответ с ошибкой.
     */
    public static String formatError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"error\",\"message\":");
        JsonUtil.appendString(sb, message);
        sb.append("}");
        return sb.toString();
    }
}
