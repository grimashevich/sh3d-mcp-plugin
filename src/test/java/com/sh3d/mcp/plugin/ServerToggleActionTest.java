package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.sh3d.mcp.server.TcpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class ServerToggleActionTest {

    @Mock
    private Plugin plugin;

    @Mock
    private TcpServer tcpServer;

    private ServerToggleAction action;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Ensure TcpServer mock behaves correctly if needed by constructor
        when(tcpServer.isRunning()).thenReturn(false);
    }

    @Test
    public void testActionIsEnabledByDefault() {
        action = new ServerToggleAction(plugin, tcpServer);
        assertTrue(action.isEnabled(), "ServerToggleAction should be enabled by default");
    }
}
