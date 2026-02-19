package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.config.PluginConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpMcpServerTest {

    private HttpMcpServer server;

    @BeforeEach
    void setUp() {
        PluginConfig config = mock(PluginConfig.class);
        when(config.getPort()).thenReturn(9877);
        CommandRegistry registry = mock(CommandRegistry.class);
        HomeAccessor accessor = mock(HomeAccessor.class);
        server = new HttpMcpServer(config, registry, accessor);
    }

    @Test
    void testInitialPort() {
        assertEquals(9877, server.getPort());
    }

    @Test
    void testSetPortWhenStopped() {
        server.setPort(8080);
        assertEquals(8080, server.getPort());
    }

    @Test
    void testSetPortInvalidLow() {
        assertThrows(IllegalArgumentException.class, () -> server.setPort(0));
    }

    @Test
    void testSetPortInvalidHigh() {
        assertThrows(IllegalArgumentException.class, () -> server.setPort(70000));
    }

    @Test
    void testSetPortNegative() {
        assertThrows(IllegalArgumentException.class, () -> server.setPort(-1));
    }

    @Test
    void testSetPortBoundaryValid() {
        server.setPort(1);
        assertEquals(1, server.getPort());

        server.setPort(65535);
        assertEquals(65535, server.getPort());
    }
}
