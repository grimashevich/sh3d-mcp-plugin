package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DescribeCommandsHandlerTest {

    private final HomeAccessor mockAccessor = mock(HomeAccessor.class);

    @Test
    void testDescribesAllDescribableHandlers() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", new PingHandler()); // не CommandDescriptor
        registry.register("cmd1", descriptorHandler("cmd1", null, "Desc 1"));
        registry.register("cmd2", descriptorHandler("cmd2", "tool2", "Desc 2"));
        registry.register("describe_commands", new DescribeCommandsHandler(registry));

        Response resp = new DescribeCommandsHandler(registry)
                .execute(new Request("describe_commands", Collections.emptyMap()), mockAccessor);

        assertTrue(resp.isOk());
        @SuppressWarnings("unchecked")
        List<Object> commands = (List<Object>) resp.getData().get("commands");
        assertEquals(2, commands.size());
    }

    @Test
    void testSkipsNonDescribableHandlers() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", new PingHandler());

        Response resp = new DescribeCommandsHandler(registry)
                .execute(new Request("describe_commands", Collections.emptyMap()), mockAccessor);

        assertTrue(resp.isOk());
        @SuppressWarnings("unchecked")
        List<Object> commands = (List<Object>) resp.getData().get("commands");
        assertTrue(commands.isEmpty());
    }

    @Test
    void testDescriptorContainsRequiredFields() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("my_action", descriptorHandler("my_action", null, "My description"));

        Response resp = new DescribeCommandsHandler(registry)
                .execute(new Request("describe_commands", Collections.emptyMap()), mockAccessor);

        @SuppressWarnings("unchecked")
        List<Object> commands = (List<Object>) resp.getData().get("commands");
        @SuppressWarnings("unchecked")
        Map<String, Object> cmd = (Map<String, Object>) commands.get(0);

        assertEquals("my_action", cmd.get("action"));
        assertEquals("My description", cmd.get("description"));
        assertNotNull(cmd.get("schema"));
        assertFalse(cmd.containsKey("toolName")); // null toolName не включается
    }

    @Test
    void testToolNameIncludedWhenDifferent() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("create_walls", descriptorHandler("create_walls", "create_room", "Creates room"));

        Response resp = new DescribeCommandsHandler(registry)
                .execute(new Request("describe_commands", Collections.emptyMap()), mockAccessor);

        @SuppressWarnings("unchecked")
        List<Object> commands = (List<Object>) resp.getData().get("commands");
        @SuppressWarnings("unchecked")
        Map<String, Object> cmd = (Map<String, Object>) commands.get(0);

        assertEquals("create_walls", cmd.get("action"));
        assertEquals("create_room", cmd.get("toolName"));
    }

    @Test
    void testVersionIncluded() {
        CommandRegistry registry = new CommandRegistry();
        Response resp = new DescribeCommandsHandler(registry)
                .execute(new Request("describe_commands", Collections.emptyMap()), mockAccessor);

        assertEquals("0.1.0", resp.getData().get("version"));
    }

    @Test
    void testEmptyRegistryReturnsEmptyList() {
        CommandRegistry registry = new CommandRegistry();
        Response resp = new DescribeCommandsHandler(registry)
                .execute(new Request("describe_commands", Collections.emptyMap()), mockAccessor);

        assertTrue(resp.isOk());
        @SuppressWarnings("unchecked")
        List<Object> commands = (List<Object>) resp.getData().get("commands");
        assertTrue(commands.isEmpty());
    }

    @Test
    void testSchemaIsValidStructure() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("test", descriptorHandler("test", null, "Test"));

        Response resp = new DescribeCommandsHandler(registry)
                .execute(new Request("describe_commands", Collections.emptyMap()), mockAccessor);

        @SuppressWarnings("unchecked")
        List<Object> commands = (List<Object>) resp.getData().get("commands");
        @SuppressWarnings("unchecked")
        Map<String, Object> cmd = (Map<String, Object>) commands.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) cmd.get("schema");

        assertEquals("object", schema.get("type"));
        assertNotNull(schema.get("properties"));
    }

    /**
     * Создаёт CommandHandler + CommandDescriptor для тестирования.
     */
    private static CommandHandler descriptorHandler(String action, String toolName, String description) {
        return new TestDescribableHandler(toolName, description);
    }

    private static class TestDescribableHandler implements CommandHandler, CommandDescriptor {
        private final String toolName;
        private final String description;

        TestDescribableHandler(String toolName, String description) {
            this.toolName = toolName;
            this.description = description;
        }

        @Override
        public Response execute(Request request, HomeAccessor accessor) {
            return Response.ok(Collections.emptyMap());
        }

        @Override
        public String getToolName() {
            return toolName;
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
    }
}
