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
    private boolean running;

    public ServerToggleAction(Plugin plugin, TcpServer tcpServer) {
        super();
        this.tcpServer = tcpServer;
        this.running = false;
        updateMenuText();
    }

    @Override
    public void execute() {
        if (running) {
            tcpServer.stop();
            running = false;
            LOG.info("MCP Server stopped by user");
        } else {
            tcpServer.start();
            running = true;
            LOG.info("MCP Server started by user");
        }
        updateMenuText();
    }

    private void updateMenuText() {
        putPropertyValue(Property.NAME, running ? "MCP Server: Stop" : "MCP Server: Start");
    }
}
