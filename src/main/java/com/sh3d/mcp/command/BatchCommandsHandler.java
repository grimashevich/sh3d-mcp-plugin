package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Мета-команда "batch_commands".
 * Выполняет массив команд последовательно с collect-all стратегией:
 * все команды выполняются независимо от ошибок отдельных.
 *
 * EDT: зависит от sub-команд (каждый handler управляет своим EDT-доступом).
 */
public class BatchCommandsHandler implements CommandHandler, CommandDescriptor {

    private static final int MAX_BATCH_SIZE = 50;
    private static final String ACTION_NAME = "batch_commands";

    private final CommandRegistry registry;

    public BatchCommandsHandler(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Object commandsObj = request.getParams().get("commands");
        if (commandsObj == null) {
            return Response.error("Missing required parameter: commands");
        }
        if (!(commandsObj instanceof List)) {
            return Response.error("Parameter 'commands' must be an array");
        }

        List<?> commandsList = (List<?>) commandsObj;
        if (commandsList.isEmpty()) {
            return Response.error("Parameter 'commands' must not be empty");
        }
        if (commandsList.size() > MAX_BATCH_SIZE) {
            return Response.error("Batch size " + commandsList.size()
                    + " exceeds maximum of " + MAX_BATCH_SIZE);
        }

        List<Object> results = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        for (int i = 0; i < commandsList.size(); i++) {
            Object item = commandsList.get(i);

            if (!(item instanceof Map)) {
                results.add(errorResult(i, null,
                        "Command at index " + i + " is not an object"));
                failed++;
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> cmdMap = (Map<String, Object>) item;

            Object actionObj = cmdMap.get("action");
            if (actionObj == null || actionObj.toString().isEmpty()) {
                results.add(errorResult(i, null,
                        "Command at index " + i + " is missing 'action' field"));
                failed++;
                continue;
            }

            String action = actionObj.toString();

            if (ACTION_NAME.equals(action)) {
                results.add(errorResult(i, action,
                        "Nested batch_commands is not allowed"));
                failed++;
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> params = cmdMap.containsKey("params")
                    && cmdMap.get("params") instanceof Map
                    ? (Map<String, Object>) cmdMap.get("params")
                    : Collections.emptyMap();

            Request subRequest = new Request(action, params);
            Response subResponse = registry.dispatch(subRequest, accessor);

            Map<String, Object> resultEntry = new LinkedHashMap<>();
            resultEntry.put("index", i);
            resultEntry.put("action", action);
            if (subResponse.isOk()) {
                resultEntry.put("status", "ok");
                resultEntry.put("data", subResponse.getData());
                succeeded++;
            } else {
                resultEntry.put("status", "error");
                resultEntry.put("message", subResponse.getMessage());
                failed++;
            }
            results.add(resultEntry);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", commandsList.size());
        data.put("succeeded", succeeded);
        data.put("failed", failed);
        data.put("results", results);
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Executes multiple commands in a single request. "
                + "All commands run sequentially; failures do not stop execution. "
                + "Returns individual results for each command. Maximum 50 commands per batch.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> commandsProp = new LinkedHashMap<>();
        commandsProp.put("type", "array");
        commandsProp.put("description",
                "Array of commands to execute sequentially. "
                + "Each element is an object with 'action' (string) and optional 'params' (object).");
        commandsProp.put("maxItems", MAX_BATCH_SIZE);

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");

        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("action", prop("string", "Command action name (e.g. 'create_wall')"));
        itemProps.put("params", prop("object", "Command parameters (varies per action)"));
        itemSchema.put("properties", itemProps);
        itemSchema.put("required", Arrays.asList("action"));

        commandsProp.put("items", itemSchema);
        properties.put("commands", commandsProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("commands"));
        return schema;
    }

    private static Map<String, Object> errorResult(int index, String action, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", index);
        result.put("action", action);
        result.put("status", "error");
        result.put("message", message);
        return result;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
