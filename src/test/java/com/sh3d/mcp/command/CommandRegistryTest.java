package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CommandRegistryTest {

    private CommandRegistry registry;
    private HomeAccessor mockAccessor;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        registry.register("ping", new PingHandler());
        mockAccessor = mock(HomeAccessor.class);
    }

    @Test
    void testRegisterAndDispatch() {
        Request req = new Request("ping", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isOk());
        assertNotNull(resp.getData());
        assertEquals("0.1.0", resp.getData().get("version"));
    }

    @Test
    void testDispatchUnknownAction() {
        Request req = new Request("unknown", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Unknown action"));
        assertTrue(resp.getMessage().contains("unknown"));
    }

    @Test
    void testDispatchCommandException() {
        registry.register("failing", (request, accessor) -> {
            throw new CommandException("Validation failed");
        });

        Request req = new Request("failing", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isError());
        assertEquals("Validation failed", resp.getMessage());
    }

    @Test
    void testDispatchInternalError() {
        registry.register("broken", (request, accessor) -> {
            throw new RuntimeException("NPE simulation");
        });

        Request req = new Request("broken", Collections.emptyMap());
        Response resp = registry.dispatch(req, mockAccessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("Internal error"));
        assertTrue(resp.getMessage().contains("NPE simulation"));
    }

    @Test
    void testHasHandler() {
        assertTrue(registry.hasHandler("ping"));
        assertFalse(registry.hasHandler("nonexistent"));
    }
}
