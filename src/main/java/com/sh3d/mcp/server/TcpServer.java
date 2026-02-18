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
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final int maxLineLength;
    private final CommandRegistry commandRegistry;
    private final HomeAccessor accessor;

    private volatile ServerSocket serverSocket;
    private volatile Exception lastStartupError;
    private Thread acceptThread;
    private final List<ClientHandler> activeClients = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.STOPPED);
    private final List<ServerStateListener> stateListeners = new CopyOnWriteArrayList<>();

    public TcpServer(PluginConfig config, CommandRegistry commandRegistry, HomeAccessor accessor) {
        this.port = config.getPort();
        this.maxLineLength = config.getMaxLineLength();
        this.commandRegistry = commandRegistry;
        this.accessor = accessor;
    }

    /**
     * Запускает TCP-сервер в daemon-потоке.
     */
    public void start() {
        if (!transitionState(ServerState.STOPPED, ServerState.STARTING)) {
            LOG.warning("Server is not in STOPPED state, cannot start");
            return;
        }

        lastStartupError = null;

        acceptThread = new Thread(this::acceptLoop, "sh3d-mcp-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * Останавливает TCP-сервер: закрывает ServerSocket и все активные соединения.
     */
    public void stop() {
        if (!transitionState(ServerState.RUNNING, ServerState.STOPPING)) {
            // Может быть STARTING — попробуем и его остановить
            forceState(ServerState.STOPPING);
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

        forceState(ServerState.STOPPED);
        LOG.info("MCP TCP server stopped");
    }

    public boolean isRunning() {
        return state.get() == ServerState.RUNNING;
    }

    public ServerState getState() {
        return state.get();
    }

    public int getPort() {
        return port;
    }

    /**
     * Returns the exception that caused the last startup failure, or null if
     * the server started successfully or hasn't been started yet.
     */
    public Exception getLastStartupError() {
        return lastStartupError;
    }

    public void addStateListener(ServerStateListener listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(ServerStateListener listener) {
        stateListeners.remove(listener);
    }

    private boolean transitionState(ServerState expected, ServerState newState) {
        if (state.compareAndSet(expected, newState)) {
            fireStateChanged(expected, newState);
            return true;
        }
        return false;
    }

    private void forceState(ServerState newState) {
        ServerState old = state.getAndSet(newState);
        if (old != newState) {
            fireStateChanged(old, newState);
        }
    }

    private void fireStateChanged(ServerState oldState, ServerState newState) {
        for (ServerStateListener listener : stateListeners) {
            try {
                listener.onStateChanged(oldState, newState);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "State listener error", e);
            }
        }
    }

    /**
     * Возвращает фактический порт, на котором слушает сервер.
     * Полезно при запуске с портом 0 (ephemeral port).
     *
     * @return локальный порт или -1, если сервер не запущен
     */
    int getActualPort() {
        ServerSocket ss = serverSocket;
        return (ss != null && !ss.isClosed()) ? ss.getLocalPort() : -1;
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(port);
            if (!transitionState(ServerState.STARTING, ServerState.RUNNING)) {
                // stop() was called while we were starting — abort
                serverSocket.close();
                return;
            }
            LOG.info("MCP TCP server started on port " + port);

            while (state.get() == ServerState.RUNNING) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, commandRegistry, accessor, maxLineLength);
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
            ServerState current = state.get();
            if (current == ServerState.RUNNING || current == ServerState.STARTING) {
                lastStartupError = e;
                LOG.log(Level.SEVERE, "Accept loop error", e);
            }
            // Если STOPPING — это нормальное завершение через close()
        } finally {
            if (state.get() != ServerState.STOPPED) {
                forceState(ServerState.STOPPED);
            }
        }
    }
}
