package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "ping".
 * Возвращает версию плагина. EDT не требуется.
 */
public class PingHandler implements CommandHandler {

    private static final String VERSION = "0.1.0";

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", VERSION);
        return Response.ok(data);
    }
}
