package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.server.TcpServer;

import java.util.logging.Logger;

/**
 * Пункт меню "MCP Server: Start / Stop".
 * Переключает состояние TCP-сервера.
 */
public class ServerToggleAction extends PluginAction {

    private static final Logger LOG = Logger.getLogger(ServerToggleAction.class.getName());

    private final TcpServer tcpServer;

    public ServerToggleAction(Plugin plugin, TcpServer tcpServer) {
        super();
        this.tcpServer = tcpServer;
        putPropertyValue(Property.MENU, "Tools");
        boolean started = tcpServer.getState() != com.sh3d.mcp.server.ServerState.STOPPED;
        putPropertyValue(Property.NAME, started ? "MCP Server: Stop" : "MCP Server: Start");
        setEnabled(true);
    }

    @Override
    public void execute() {
        boolean running = tcpServer.isRunning();
        if (running) {
            tcpServer.stop();
            LOG.info("MCP Server stopped by user");
        } else {
            tcpServer.start();
            LOG.info("MCP Server started by user");
        }
        // Текст основан на действии, а не на isRunning() — start() возвращается
        // до перехода в RUNNING (race condition), поэтому инвертируем напрямую.
        putPropertyValue(Property.NAME, running ? "MCP Server: Start" : "MCP Server: Stop");
    }
}
