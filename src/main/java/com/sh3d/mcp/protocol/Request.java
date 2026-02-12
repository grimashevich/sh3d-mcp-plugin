package com.sh3d.mcp.protocol;

import java.util.Collections;
import java.util.Map;

/**
 * Value object: распарсенный JSON-запрос (action + params).
 */
public class Request {

    private final String action;
    private final Map<String, Object> params;

    public Request(String action, Map<String, Object> params) {
        this.action = action;
        this.params = params != null ? params : Collections.emptyMap();
    }

    public String getAction() {
        return action;
    }

    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(params);
    }

    /**
     * Возвращает строковый параметр или null, если отсутствует.
     */
    public String getString(String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * Возвращает float-параметр. Бросает IllegalArgumentException если отсутствует.
     */
    public float getFloat(String key) {
        Object val = params.get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        try {
            return Float.parseFloat(val.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid parameter '" + key + "': expected number, got '" + val + "'");
        }
    }

    /**
     * Возвращает Boolean-параметр или null, если отсутствует.
     */
    public Boolean getBoolean(String key) {
        Object val = params.get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        throw new IllegalArgumentException(
                "Invalid parameter '" + key + "': expected boolean, got '" + val + "'");
    }

    /**
     * Возвращает float-параметр или defaultValue, если отсутствует.
     */
    public float getFloat(String key, float defaultValue) {
        Object val = params.get(key);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        try {
            return Float.parseFloat(val.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid parameter '" + key + "': expected number, got '" + val + "'");
        }
    }
}
