package com.sh3d.mcp.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Парсинг входящих JSON-запросов и форматирование исходящих JSON-ответов.
 * <p>
 * Минимальный ручной парсер — наш протокол оперирует плоским JSON
 * с заранее известной структурой.
 */
public final class JsonProtocol {

    private JsonProtocol() {
        // Утилитный класс
    }

    /**
     * Парсит JSON-строку запроса в объект Request.
     *
     * @param json строка вида {"action": "...", "params": {...}}
     * @return распарсенный Request
     * @throws IllegalArgumentException если JSON невалиден или отсутствует action
     */
    public static Request parseRequest(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid JSON: empty input");
        }
        JsonReader reader = new JsonReader(json);
        Object parsed = reader.readValue();
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Invalid JSON: expected object at root");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        Object action = root.get("action");
        if (action == null) {
            throw new IllegalArgumentException("Missing 'action' field");
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
        appendValue(sb, data);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Форматирует ответ с ошибкой.
     */
    public static String formatError(String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"error\",\"message\":");
        appendString(sb, message);
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            appendString(sb, (String) value);
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            appendObject(sb, (Map<String, Object>) value);
        } else if (value instanceof List) {
            appendArray(sb, (List<Object>) value);
        } else {
            appendString(sb, value.toString());
        }
    }

    private static void appendString(StringBuilder sb, String str) {
        sb.append('"');
        if (str != null) {
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                switch (c) {
                    case '"':  sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n");  break;
                    case '\r': sb.append("\\r");  break;
                    case '\t': sb.append("\\t");  break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
        }
        sb.append('"');
    }

    private static void appendObject(StringBuilder sb, Map<String, Object> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            appendString(sb, entry.getKey());
            sb.append(':');
            appendValue(sb, entry.getValue());
        }
        sb.append('}');
    }

    private static void appendArray(StringBuilder sb, List<Object> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            appendValue(sb, list.get(i));
        }
        sb.append(']');
    }

    /**
     * Минимальный рекурсивный JSON-парсер.
     * Поддерживает: String, Number (int/double), Boolean, Null, Object, Array.
     */
    static final class JsonReader {
        private final String src;
        private int pos;

        JsonReader(String src) {
            this.src = src;
            this.pos = 0;
        }

        Object readValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw error("Unexpected end of input");
            }
            char c = src.charAt(pos);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't' || c == 'f') return readBoolean();
            if (c == 'n') return readNull();
            if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
            throw error("Unexpected character '" + c + "'");
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                if (pos >= src.length()) {
                    throw error("Unterminated object");
                }
                if (src.charAt(pos) == '}') {
                    pos++;
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new java.util.ArrayList<>();
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                if (pos >= src.length()) {
                    throw error("Unterminated array");
                }
                if (src.charAt(pos) == ']') {
                    pos++;
                    return list;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw error("Unterminated escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw error("Unterminated \\uXXXX");
                            String hex = src.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("Invalid \\u escape: " + hex);
                            }
                            pos += 4;
                            break;
                        default:
                            throw error("Invalid escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private Number readNumber() {
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') pos++;
            while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            String numStr = src.substring(start, pos);
            try {
                if (isFloat) {
                    return Double.parseDouble(numStr);
                }
                long val = Long.parseLong(numStr);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return (int) val;
                }
                return val;
            } catch (NumberFormatException e) {
                throw error("Invalid number: " + numStr);
            }
        }

        private Boolean readBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw error("Expected boolean");
        }

        private Object readNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw error("Expected null");
        }

        private void skipWhitespace() {
            while (pos < src.length() && src.charAt(pos) <= ' ') {
                pos++;
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != expected) {
                char actual = pos < src.length() ? src.charAt(pos) : '?';
                throw error("Expected '" + expected + "' but got '" + actual + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Invalid JSON: " + msg + " at position " + pos);
        }
    }
}
