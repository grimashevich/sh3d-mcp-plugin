package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.sh3d.mcp.http.HttpMcpServer;
import com.sh3d.mcp.server.ServerState;
import com.sh3d.mcp.server.ServerStateListener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.BindException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerToggleActionTest {

    private HttpMcpServer mockServer;
    private Plugin mockPlugin;
    private ArgumentCaptor<ServerStateListener> listenerCaptor;

    @BeforeEach
    void setUp() {
        mockServer = mock(HttpMcpServer.class);
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

        listener.onStateChanged(ServerState.STOPPED, ServerState.STARTING);
        assertEquals("MCP Server: Stop", action.getPropertyValue(ServerToggleAction.Property.NAME));

        listener.onStateChanged(ServerState.STARTING, ServerState.RUNNING);
        assertEquals("MCP Server: Stop", action.getPropertyValue(ServerToggleAction.Property.NAME));

        listener.onStateChanged(ServerState.STOPPING, ServerState.STOPPED);
        assertEquals("MCP Server: Start", action.getPropertyValue(ServerToggleAction.Property.NAME));
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

        listener.onStateChanged(ServerState.STARTING, ServerState.STOPPED);

        verify(mockServer).getLastStartupError();
        verify(mockServer).getPort();
    }

    @Test
    void testNormalStopDoesNotQueryError() {
        when(mockServer.getState()).thenReturn(ServerState.STOPPED);

        new ServerToggleAction(mockPlugin, mockServer);

        verify(mockServer).addStateListener(listenerCaptor.capture());
        ServerStateListener listener = listenerCaptor.getValue();

        listener.onStateChanged(ServerState.STOPPING, ServerState.STOPPED);

        verify(mockServer, never()).getLastStartupError();
    }
}
