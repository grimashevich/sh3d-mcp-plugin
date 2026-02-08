package com.sh3d.mcp.server;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.config.PluginConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TCP-сервер: принимает соединения на указанном порту,
 * создаёт ClientHandler для каждого клиента.
 */
public class TcpServer {

    private static final Logger LOG = Logger.getLogger(TcpServer.class.getName());

    private final int port;
    private final CommandRegistry commandRegistry;
    private final HomeAccessor accessor;

    private volatile ServerSocket serverSocket;
    private Thread acceptThread;
    private final List<ClientHandler> activeClients = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.STOPPED);

    public TcpServer(PluginConfig config, CommandRegistry commandRegistry, HomeAccessor accessor) {
        this.port = config.getPort();
        this.commandRegistry = commandRegistry;
        this.accessor = accessor;
    }

    /**
     * Запускает TCP-сервер в daemon-потоке.
     */
    public void start() {
        if (!state.compareAndSet(ServerState.STOPPED, ServerState.STARTING)) {
            LOG.warning("Server is not in STOPPED state, cannot start");
            return;
        }

        acceptThread = new Thread(this::acceptLoop, "sh3d-mcp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Останавливает TCP-сервер: закрывает ServerSocket и все активные соединения.
     */
    public void stop() {
        if (!state.compareAndSet(ServerState.RUNNING, ServerState.STOPPING)) {
            // Может быть STARTING — попробуем и его остановить
            state.set(ServerState.STOPPING);
        }

        // Закрыть ServerSocket — прерывает accept()
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing server socket", e);
            }
        }

        // Закрыть все активные клиентские соединения
        synchronized (activeClients) {
            for (ClientHandler client : activeClients) {
                client.close();
            }
            activeClients.clear();
        }

        // Дождаться завершения accept-потока
        if (acceptThread != null) {
            try {
                acceptThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        state.set(ServerState.STOPPED);
        LOG.info("MCP TCP server stopped");
    }

    public boolean isRunning() {
        return state.get() == ServerState.RUNNING;
    }

    public ServerState getState() {
        return state.get();
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            state.set(ServerState.RUNNING);
            LOG.info("MCP TCP server started on port " + port);

            while (state.get() == ServerState.RUNNING) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, commandRegistry, accessor);
                activeClients.add(handler);

                Thread clientThread = new Thread(() -> {
                    try {
                        handler.run();
                    } finally {
                        activeClients.remove(handler);
                    }
                }, "sh3d-mcp-client-" + clientSocket.getRemoteSocketAddress());
                clientThread.setDaemon(true);
                clientThread.start();
            }

        } catch (IOException e) {
            if (state.get() == ServerState.RUNNING) {
                LOG.log(Level.SEVERE, "Accept loop error", e);
            }
            // Если STOPPING — это нормальное завершение через close()
        } finally {
            if (state.get() != ServerState.STOPPED) {
                state.set(ServerState.STOPPED);
            }
        }
    }
}
