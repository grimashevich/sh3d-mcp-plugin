package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.http.HttpMcpServer;
import com.sh3d.mcp.server.ServerState;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.logging.Logger;

/**
 * Пункт меню "MCP Server: Start / Stop".
 * Переключает состояние HTTP MCP-сервера.
 * Текст обновляется через state listener — всегда соответствует реальному состоянию.
 */
public class ServerToggleAction extends PluginAction {

    private static final Logger LOG = Logger.getLogger(ServerToggleAction.class.getName());

    private final HttpMcpServer httpServer;

    public ServerToggleAction(Plugin plugin, HttpMcpServer httpServer) {
        super();
        this.httpServer = httpServer;
        putPropertyValue(Property.MENU, "Tools");
        updateMenuText(httpServer.getState());
        setEnabled(true);

        httpServer.addStateListener((oldState, newState) -> {
            updateMenuText(newState);
            if (oldState == ServerState.STARTING && newState == ServerState.STOPPED) {
                Exception error = httpServer.getLastStartupError();
                if (error != null) {
                    showStartupError(httpServer.getPort(), error);
                }
            }
        });
    }

    @Override
    public void execute() {
        if (httpServer.getState() != ServerState.STOPPED) {
            httpServer.stop();
            LOG.info("MCP Server stopped by user");
        } else {
            httpServer.start();
            LOG.info("MCP Server started by user");
        }
    }

    private void updateMenuText(ServerState state) {
        String text = state == ServerState.STOPPED
                ? "MCP Server: Start"
                : "MCP Server: Stop";
        putPropertyValue(Property.NAME, text);
    }

    private void showStartupError(int port, Exception error) {
        String message = "Failed to start MCP server on port " + port + ".\n"
                + error.getMessage();
        LOG.warning(message);
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(null, message,
                        "MCP Server Error", JOptionPane.ERROR_MESSAGE));
    }
}
