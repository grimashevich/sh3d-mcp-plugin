package com.sh3d.mcp.command;

import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PingHandlerTest {

    @Test
    void testPingReturnsVersion() {
        PingHandler handler = new PingHandler();
        Request req = new Request("ping", Collections.emptyMap());
        Response resp = handler.execute(req, null);

        assertTrue(resp.isOk());
        assertEquals("0.1.0", resp.getData().get("version"));
    }
}
