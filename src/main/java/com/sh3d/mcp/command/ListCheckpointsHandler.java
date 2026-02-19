package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "list_checkpoints".
 * Возвращает список всех чекпоинтов с текущей позицией курсора.
 */
public class ListCheckpointsHandler implements CommandHandler, CommandDescriptor {

    private final CheckpointManager checkpointManager;

    public ListCheckpointsHandler(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        List<CheckpointManager.SnapshotInfo> snapshots = checkpointManager.list();

        List<Object> items = new ArrayList<>();
        for (CheckpointManager.SnapshotInfo info : snapshots) {
            items.add(info.toMap());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", snapshots.size());
        data.put("cursor", checkpointManager.getCursor());
        data.put("maxDepth", checkpointManager.getMaxDepth());
        data.put("checkpoints", items);
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Returns all saved checkpoints with their IDs, descriptions, and timestamps. "
                + "The 'current' field marks the checkpoint that was last restored "
                + "(the current position in the undo/redo timeline). "
                + "Use restore_checkpoint with an ID to jump to any checkpoint.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        schema.put("required", Collections.emptyList());
        return schema;
    }
}
