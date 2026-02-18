package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.sh3d.mcp.server.ServerState;
import com.sh3d.mcp.server.ServerStateListener;
import com.sh3d.mcp.server.TcpServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.BindException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerToggleActionTest {

    private TcpServer mockServer;
    private Plugin mockPlugin;
    private ArgumentCaptor<ServerStateListener> listenerCaptor;

    @BeforeEach
    void setUp() {
        mockServer = mock(TcpServer.class);
        mockPlugin = mock(Plugin.class);
        listenerCaptor = ArgumentCaptor.forClass(ServerStateListener.class);
    }

    @Test
    void testInitialTextWhenStopped() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);
        ServerToggleAction action = new ServerToggleAction(mockPlugin, mockServer);

        assertEquals("MCP Server: Start", action.getPropertyValue(ServerToggleAction.Property.NAME));
    }

    @Test
    void testInitialTextWhenRunning() {
        when(mockServer.getState()).thenReturn(ServerState.RUNNING);
        ServerToggleAction action = new ServerToggleAction(mockPlugin, mockServer);

        assertEquals("MCP Server: Stop", action.getPropertyValue(ServerToggleAction.Property.NAME));
    }

    @Test
    void testInitialTextWhenStarting() {
        when(mockServer.getState()).thenReturn(ServerState.STARTING);
        ServerToggleAction action = new ServerToggleAction(mockPlugin, mockServer);

        assertEquals("MCP Server: Stop", action.getPropertyValue(ServerToggleAction.Property.NAME));
    }

    @Test
    void testExecuteCallsStartWhenStopped() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);
        ServerToggleAction action = new ServerToggleAction(mockPlugin, mockServer);

        action.execute();

        verify(mockServer).start();
        verify(mockServer, never()).stop();
    }

    @Test
    void testExecuteCallsStopWhenRunning() {
        when(mockServer.getState()).thenReturn(ServerState.RUNNING);
        ServerToggleAction action = new ServerToggleAction(mockPlugin, mockServer);

        action.execute();

        verify(mockServer).stop();
        verify(mockServer, never()).start();
    }

    @Test
    void testListenerUpdatesTextOnStateChange() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);
        ServerToggleAction action = new ServerToggleAction(mockPlugin, mockServer);

        verify(mockServer).addStateListener(listenerCaptor.capture());
        ServerStateListener listener = listenerCaptor.getValue();

        // Simulate: STOPPED -> STARTING
        listener.onStateChanged(ServerState.STOPPED, ServerState.STARTING);
        assertEquals("MCP Server: Stop", action.getPropertyValue(ServerToggleAction.Property.NAME));

        // Simulate: STARTING -> RUNNING
        listener.onStateChanged(ServerState.STARTING, ServerState.RUNNING);
        assertEquals("MCP Server: Stop", action.getPropertyValue(ServerToggleAction.Property.NAME));

        // Simulate: RUNNING -> STOPPING
        listener.onStateChanged(ServerState.RUNNING, ServerState.STOPPING);
        assertEquals("MCP Server: Stop", action.getPropertyValue(ServerToggleAction.Property.NAME));

        // Simulate: STOPPING -> STOPPED
        listener.onStateChanged(ServerState.STOPPING, ServerState.STOPPED);
        assertEquals("MCP Server: Start", action.getPropertyValue(ServerToggleAction.Property.NAME));
    }

    @Test
    void testListenerHandlesStartFailure() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);
        ServerToggleAction action = new ServerToggleAction(mockPlugin, mockServer);

        verify(mockServer).addStateListener(listenerCaptor.capture());
        ServerStateListener listener = listenerCaptor.getValue();

        // Start attempt
        listener.onStateChanged(ServerState.STOPPED, ServerState.STARTING);
        assertEquals("MCP Server: Stop", action.getPropertyValue(ServerToggleAction.Property.NAME));

        // Start fails — acceptLoop falls back to STOPPED
        listener.onStateChanged(ServerState.STARTING, ServerState.STOPPED);
        assertEquals("MCP Server: Start", action.getPropertyValue(ServerToggleAction.Property.NAME));
    }

    @Test
    void testRegistersListenerOnConstruction() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);
        new ServerToggleAction(mockPlugin, mockServer);

        verify(mockServer).addStateListener(any(ServerStateListener.class));
    }

    @Test
    void testStartupFailureQueriesError() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);
        when(mockServer.getPort()).thenReturn(9877);
        when(mockServer.getLastStartupError())
                .thenReturn(new BindException("Address already in use"));

        new ServerToggleAction(mockPlugin, mockServer);

        verify(mockServer).addStateListener(listenerCaptor.capture());
        ServerStateListener listener = listenerCaptor.getValue();

        // Simulate startup failure: STARTING -> STOPPED
        listener.onStateChanged(ServerState.STARTING, ServerState.STOPPED);

        // Should query the error
        verify(mockServer).getLastStartupError();
        verify(mockServer).getPort();
    }

    @Test
    void testNormalStopDoesNotQueryError() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);

        new ServerToggleAction(mockPlugin, mockServer);

        verify(mockServer).addStateListener(listenerCaptor.capture());
        ServerStateListener listener = listenerCaptor.getValue();

        // Normal shutdown: STOPPING -> STOPPED (not STARTING -> STOPPED)
        listener.onStateChanged(ServerState.STOPPING, ServerState.STOPPED);

        // Should NOT query error — this is a normal stop
        verify(mockServer, never()).getLastStartupError();
    }

    @Test
    void testStartupFailureWithNoErrorDoesNotQueryPort() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);
        when(mockServer.getLastStartupError()).thenReturn(null);

        new ServerToggleAction(mockPlugin, mockServer);

        verify(mockServer).addStateListener(listenerCaptor.capture());
        ServerStateListener listener = listenerCaptor.getValue();

        // STARTING -> STOPPED but no error (e.g., stop() called during startup)
        listener.onStateChanged(ServerState.STARTING, ServerState.STOPPED);

        verify(mockServer).getLastStartupError();
        verify(mockServer, never()).getPort();
    }
}
