package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реестр команд: связывает имя action с обработчиком CommandHandler.
 */
public class CommandRegistry {

    private final Map<String, CommandHandler> handlers = new LinkedHashMap<>();

    /**
     * Регистрирует обработчик для указанного имени команды.
     */
    public void register(String action, CommandHandler handler) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Action name must not be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler must not be null");
        }
        handlers.put(action, handler);
    }

    /**
     * Маршрутизирует запрос к соответствующему обработчику.
     *
     * @return Response от обработчика, или error если команда неизвестна
     */
    public Response dispatch(Request request, HomeAccessor accessor) {
        String action = request.getAction();
        CommandHandler handler = handlers.get(action);
        if (handler == null) {
            return Response.error("Unknown action: " + action);
        }
        try {
            return handler.execute(request, accessor);
        } catch (CommandException e) {
            return Response.error(e.getMessage());
        } catch (Exception e) {
            return Response.error("Internal error: " + e.getMessage());
        }
    }

    /**
     * Проверяет, зарегистрирован ли обработчик для данной команды.
     */
    public boolean hasHandler(String action) {
        return handlers.containsKey(action);
    }

    /**
     * Возвращает неизменяемое представление зарегистрированных обработчиков.
     * Используется McpRequestHandler для итерации при tools/list.
     */
    public Map<String, CommandHandler> getHandlers() {
        return Collections.unmodifiableMap(handlers);
    }
}
