package com.sh3d.mcp.command;

import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CommandRegistryTest {

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        registry.register("ping", new PingHandler());
    }

    @Test
    void testRegisterAndDispatch() {
        // TODO: implement — requires HomeAccessor mock
        // Request req = new Request("ping", Collections.emptyMap());
        // Response resp = registry.dispatch(req, mockAccessor);
        // assertTrue(resp.isOk());
    }

    @Test
    void testDispatchUnknownAction() {
        // TODO: implement
        // Request req = new Request("unknown", Collections.emptyMap());
        // Response resp = registry.dispatch(req, mockAccessor);
        // assertTrue(resp.isError());
        // assertTrue(resp.getMessage().contains("Unknown action"));
    }

    @Test
    void testHasHandler() {
        assertTrue(registry.hasHandler("ping"));
        assertFalse(registry.hasHandler("nonexistent"));
    }
}
