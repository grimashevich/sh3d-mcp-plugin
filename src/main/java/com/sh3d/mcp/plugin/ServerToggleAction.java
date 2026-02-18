package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.server.ServerState;
import com.sh3d.mcp.server.TcpServer;

import java.util.logging.Logger;

/**
 * Пункт меню "MCP Server: Start / Stop".
 * Переключает состояние TCP-сервера.
 * Текст обновляется через state listener — всегда соответствует реальному состоянию.
 */
public class ServerToggleAction extends PluginAction {

    private static final Logger LOG = Logger.getLogger(ServerToggleAction.class.getName());

    private final TcpServer tcpServer;

    public ServerToggleAction(Plugin plugin, TcpServer tcpServer) {
        super();
        this.tcpServer = tcpServer;
        putPropertyValue(Property.MENU, "Tools");
        updateMenuText(tcpServer.getState());
        setEnabled(true);

        tcpServer.addStateListener((oldState, newState) -> updateMenuText(newState));
    }

    @Override
    public void execute() {
        if (tcpServer.getState() != ServerState.STOPPED) {
            tcpServer.stop();
            LOG.info("MCP Server stopped by user");
        } else {
            tcpServer.start();
            LOG.info("MCP Server started by user");
        }
    }

    private void updateMenuText(ServerState state) {
        String text = state == ServerState.STOPPED
                ? "MCP Server: Start"
                : "MCP Server: Stop";
        putPropertyValue(Property.NAME, text);
    }
}
