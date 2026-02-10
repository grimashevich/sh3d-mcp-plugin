package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Мета-команда "describe_commands".
 * Возвращает дескрипторы всех зарегистрированных команд,
 * реализующих CommandDescriptor.
 *
 * EDT: не требуется. Реестр read-only после инициализации.
 */
public class DescribeCommandsHandler implements CommandHandler {

    private static final String VERSION = "0.1.0";
    private final CommandRegistry registry;

    public DescribeCommandsHandler(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        List<Object> commands = new ArrayList<>();

        for (Map.Entry<String, CommandHandler> entry : registry.getHandlers().entrySet()) {
            String action = entry.getKey();
            CommandHandler handler = entry.getValue();

            if (handler instanceof CommandDescriptor) {
                CommandDescriptor descriptor = (CommandDescriptor) handler;
                Map<String, Object> cmd = new LinkedHashMap<>();
                cmd.put("action", action);
                String toolName = descriptor.getToolName();
                if (toolName != null && !toolName.isEmpty()) {
                    cmd.put("toolName", toolName);
                }
                cmd.put("description", descriptor.getDescription());
                cmd.put("schema", descriptor.getSchema());
                commands.add(cmd);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("commands", commands);
        data.put("version", VERSION);
        return Response.ok(data);
    }
}
