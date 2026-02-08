package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.command.CreateWallsHandler;
import com.sh3d.mcp.command.GetStateHandler;
import com.sh3d.mcp.command.ListFurnitureCatalogHandler;
import com.sh3d.mcp.command.PingHandler;
import com.sh3d.mcp.command.PlaceFurnitureHandler;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.server.TcpServer;

import java.util.logging.Logger;

/**
 * Главный класс плагина Sweet Home 3D MCP.
 * Указывается в ApplicationPlugin.properties.
 */
public class SH3DMcpPlugin extends Plugin {

    private static final Logger LOG = Logger.getLogger(SH3DMcpPlugin.class.getName());

    private TcpServer tcpServer;
    private PluginConfig config;

    @Override
    public PluginAction[] getActions() {
        config = PluginConfig.load();

        HomeAccessor accessor = new HomeAccessor(
                getHome(),
                getUserPreferences()
        );

        CommandRegistry registry = createCommandRegistry();
        tcpServer = new TcpServer(config, registry, accessor);

        LOG.info("SH3D MCP Plugin initialized (port: " + config.getPort() + ")");

        if (config.isAutoStart()) {
            tcpServer.start();
        }

        return new PluginAction[]{
                new ServerToggleAction(this, tcpServer)
        };
    }

    @Override
    public void destroy() {
        if (tcpServer != null && tcpServer.isRunning()) {
            tcpServer.stop();
            LOG.info("SH3D MCP Plugin destroyed, server stopped");
        }
    }

    private CommandRegistry createCommandRegistry() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", new PingHandler());
        registry.register("create_walls", new CreateWallsHandler());
        registry.register("place_furniture", new PlaceFurnitureHandler());
        registry.register("get_state", new GetStateHandler());
        registry.register("list_furniture_catalog", new ListFurnitureCatalogHandler());
        return registry;
    }
}
