package com.sh3d.mcp.server;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.command.PingHandler;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.protocol.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TcpServerTest {

    private TcpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        PluginConfig config = mock(PluginConfig.class);
        when(config.getPort()).thenReturn(0);            // ephemeral port
        when(config.getMaxLineLength()).thenReturn(65536);

        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", new PingHandler());
        registry.register("echo", (request, accessor) ->
                Response.ok(Collections.singletonMap("action", request.getAction())));

        HomeAccessor mockAccessor = mock(HomeAccessor.class);
        server = new TcpServer(config, registry, mockAccessor);
        server.start();
        waitForRunning();
        port = server.getActualPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testStartAndStop() {
        assertTrue(server.isRunning());
        assertEquals(ServerState.RUNNING, server.getState());
        assertTrue(port > 0);

        server.stop();

        assertFalse(server.isRunning());
        assertEquals(ServerState.STOPPED, server.getState());
    }

    @Test
    void testPingOverSocket() throws Exception {
        try (Socket socket = connect()) {
            String response = sendAndReceive(socket, "{\"action\":\"ping\"}");

            assertTrue(response.contains("\"status\":\"ok\""));
            assertTrue(response.contains("\"version\":\"0.1.0\""));
        }
    }

    @Test
    void testUnknownActionReturnsError() throws Exception {
        try (Socket socket = connect()) {
            String response = sendAndReceive(socket, "{\"action\":\"no_such_cmd\"}");

            assertTrue(response.contains("\"status\":\"error\""));
            assertTrue(response.contains("Unknown action"));
        }
    }

    @Test
    void testInvalidJsonReturnsError() throws Exception {
        try (Socket socket = connect()) {
            String response = sendAndReceive(socket, "this is not json");

            assertTrue(response.contains("\"status\":\"error\""));
        }
    }

    @Test
    void testMultipleCommandsOnSameConnection() throws Exception {
        try (Socket socket = connect()) {
            String resp1 = sendAndReceive(socket, "{\"action\":\"ping\"}");
            String resp2 = sendAndReceive(socket, "{\"action\":\"echo\"}");

            assertTrue(resp1.contains("\"status\":\"ok\""));
            assertTrue(resp2.contains("\"status\":\"ok\""));
            assertTrue(resp2.contains("\"action\":\"echo\""));
        }
    }

    @Test
    void testMultipleConcurrentClients() throws Exception {
        try (Socket client1 = connect();
             Socket client2 = connect()) {

            String resp1 = sendAndReceive(client1, "{\"action\":\"ping\"}");
            String resp2 = sendAndReceive(client2, "{\"action\":\"ping\"}");

            assertTrue(resp1.contains("\"status\":\"ok\""));
            assertTrue(resp2.contains("\"status\":\"ok\""));
        }
    }

    @Test
    void testEmptyLinesIgnored() throws Exception {
        try (Socket socket = connect()) {
            PrintWriter writer = writer(socket);
            BufferedReader reader = reader(socket);

            writer.println("");
            writer.println("");
            writer.println("{\"action\":\"ping\"}");

            String response = reader.readLine();
            assertTrue(response.contains("\"status\":\"ok\""));
        }
    }

    @Test
    void testDoubleStartIgnored() {
        assertTrue(server.isRunning());
        int portBefore = server.getActualPort();

        server.start(); // second start — should be no-op

        assertTrue(server.isRunning());
        assertEquals(portBefore, server.getActualPort());
    }

    @Test
    void testStopClosesClientConnections() throws Exception {
        Socket socket = connect();
        String resp = sendAndReceive(socket, "{\"action\":\"ping\"}");
        assertTrue(resp.contains("\"status\":\"ok\""));

        server.stop();

        BufferedReader reader = reader(socket);
        String line = reader.readLine();
        assertNull(line);
        socket.close();
    }

    @Test
    void testGetActualPortReturnsNegativeWhenStopped() {
        server.stop();
        assertEquals(-1, server.getActualPort());
    }

    @Test
    void testStateListenerNotifiedOnStartAndStop() throws Exception {
        // Fresh server for this test — the @BeforeEach one is already running
        server.stop();

        PluginConfig config = mock(PluginConfig.class);
        when(config.getPort()).thenReturn(0);
        when(config.getMaxLineLength()).thenReturn(65536);

        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", new PingHandler());

        HomeAccessor mockAccessor = mock(HomeAccessor.class);
        TcpServer fresh = new TcpServer(config, registry, mockAccessor);

        List<String> transitions = Collections.synchronizedList(new ArrayList<>());
        fresh.addStateListener((oldState, newState) ->
                transitions.add(oldState + "->" + newState));

        fresh.start();
        for (int i = 0; i < 50; i++) {
            if (fresh.isRunning()) break;
            Thread.sleep(50);
        }

        assertTrue(transitions.contains("STOPPED->STARTING"));
        assertTrue(transitions.contains("STARTING->RUNNING"));

        fresh.stop();

        assertTrue(transitions.contains("RUNNING->STOPPING"));
        assertTrue(transitions.contains("STOPPING->STOPPED"));

        // Replace server ref so @AfterEach doesn't double-stop
        server = fresh;
    }

    @Test
    void testStateListenerRemovedNoLongerCalled() throws Exception {
        server.stop();

        PluginConfig config = mock(PluginConfig.class);
        when(config.getPort()).thenReturn(0);
        when(config.getMaxLineLength()).thenReturn(65536);

        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", new PingHandler());

        HomeAccessor mockAccessor = mock(HomeAccessor.class);
        TcpServer fresh = new TcpServer(config, registry, mockAccessor);

        List<String> transitions = Collections.synchronizedList(new ArrayList<>());
        ServerStateListener listener = (oldState, newState) ->
                transitions.add(oldState + "->" + newState);

        fresh.addStateListener(listener);
        fresh.removeStateListener(listener);

        fresh.start();
        for (int i = 0; i < 50; i++) {
            if (fresh.isRunning()) break;
            Thread.sleep(50);
        }
        fresh.stop();

        assertTrue(transitions.isEmpty(), "Removed listener should not be called");
        server = fresh;
    }

    // -- helpers --

    private Socket connect() throws Exception {
        Socket socket = new Socket("localhost", port);
        socket.setSoTimeout(5000);
        return socket;
    }

    private String sendAndReceive(Socket socket, String json) throws Exception {
        PrintWriter writer = writer(socket);
        BufferedReader reader = reader(socket);
        writer.println(json);
        return reader.readLine();
    }

    private PrintWriter writer(Socket socket) throws Exception {
        return new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    private BufferedReader reader(Socket socket) throws Exception {
        return new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private void waitForRunning() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (server.isRunning()) return;
            Thread.sleep(50);
        }
        fail("Server did not reach RUNNING state within timeout");
    }
}
