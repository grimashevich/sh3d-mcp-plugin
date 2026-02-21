package com.sh3d.mcp.http;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.server.ServerState;
import com.sh3d.mcp.server.ServerStateListener;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-сервер, реализующий MCP (Model Context Protocol) через Streamable HTTP.
 * Один endpoint /mcp принимает POST (JSON-RPC 2.0), GET (SSE), DELETE (session cleanup).
 * Использует встроенный com.sun.net.httpserver.HttpServer — ноль внешних зависимостей.
 */
public class HttpMcpServer {

    private static final Logger LOG = Logger.getLogger(HttpMcpServer.class.getName());
    private static final String MCP_ENDPOINT = "/mcp";

    private int port;
    private final CommandRegistry commandRegistry;
    private final HomeAccessor accessor;

    private volatile HttpServer httpServer;
    private volatile ExecutorService executor;
    private volatile Exception lastStartupError;

    private final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.STOPPED);
    private final List<ServerStateListener> stateListeners = new CopyOnWriteArrayList<>();

    public HttpMcpServer(PluginConfig config, CommandRegistry commandRegistry, HomeAccessor accessor) {
        this.port = config.getPort();
        this.commandRegistry = commandRegistry;
        this.accessor = accessor;
    }

    /**
     * Запускает HTTP MCP-сервер на 127.0.0.1:port.
     */
    public void start() {
        if (!transitionState(ServerState.STOPPED, ServerState.STARTING)) {
            LOG.warning("Server is not in STOPPED state, cannot start");
            return;
        }

        lastStartupError = null;

        Thread startThread = new Thread(this::doStart, "sh3d-mcp-http-start");
        startThread.setDaemon(true);
        startThread.start();
    }

    /**
     * Останавливает HTTP MCP-сервер.
     */
    public void stop() {
        if (!transitionState(ServerState.RUNNING, ServerState.STOPPING)) {
            forceState(ServerState.STOPPING);
        }

        HttpServer server = httpServer;
        if (server != null) {
            server.stop(1); // 1 секунда на завершение активных запросов
        }

        ExecutorService exec = executor;
        if (exec != null) {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                exec.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        httpServer = null;
        executor = null;
        forceState(ServerState.STOPPED);
        LOG.info("MCP HTTP server stopped");
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

    public Exception getLastStartupError() {
        return lastStartupError;
    }

    /**
     * Устанавливает порт сервера. Допустимо только в состоянии STOPPED.
     */
    public void setPort(int port) {
        if (state.get() != ServerState.STOPPED) {
            throw new IllegalStateException("Cannot change port while server is " + state.get());
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port + " (must be 1-65535)");
        }
        this.port = port;
    }

    public void addStateListener(ServerStateListener listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(ServerStateListener listener) {
        stateListeners.remove(listener);
    }

    /** Performs the actual server startup on a background thread (binds socket, registers handler). */
    private void doStart() {
        try {
            executor = Executors.newCachedThreadPool(new DaemonThreadFactory("sh3d-mcp-http"));

            httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            httpServer.setExecutor(executor);

            McpRequestHandler requestHandler = new McpRequestHandler(commandRegistry, accessor);
            httpServer.createContext(MCP_ENDPOINT, requestHandler);

            httpServer.start();

            if (!transitionState(ServerState.STARTING, ServerState.RUNNING)) {
                httpServer.stop(0);
                return;
            }

            LOG.info("MCP HTTP server started on http://127.0.0.1:" + port + MCP_ENDPOINT);

        } catch (IOException e) {
            lastStartupError = e;
            LOG.log(Level.SEVERE, "Failed to start MCP HTTP server on port " + port, e);
            forceState(ServerState.STOPPED);
        }
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
     * ThreadFactory, создающий daemon-потоки.
     */
    private static class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
