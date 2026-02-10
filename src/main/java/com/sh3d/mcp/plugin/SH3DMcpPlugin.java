package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.command.CreateWallsHandler;
import com.sh3d.mcp.command.ExportSvgHandler;
import com.sh3d.mcp.command.GetStateHandler;
import com.sh3d.mcp.command.ListFurnitureCatalogHandler;
import com.sh3d.mcp.command.DescribeCommandsHandler;
import com.sh3d.mcp.command.PingHandler;
import com.sh3d.mcp.command.PlaceFurnitureHandler;
import com.sh3d.mcp.command.RenderPhotoHandler;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.server.TcpServer;
import com.eteks.sweethome3d.viewcontroller.ExportableView;
import com.eteks.sweethome3d.viewcontroller.PlanView;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Главный класс плагина Sweet Home 3D MCP.
 * Указывается в ApplicationPlugin.properties.
 */
public class SH3DMcpPlugin extends Plugin {

    private static final Logger LOG = Logger.getLogger(SH3DMcpPlugin.class.getName());

    private TcpServer tcpServer;
    private PluginConfig config;
    private FileHandler logFileHandler;

    @Override
    public PluginAction[] getActions() {
        config = PluginConfig.load();
        setupFileLogging(config);

        HomeAccessor accessor = new HomeAccessor(
                getHome(),
                getUserPreferences()
        );

        ExportableView planView = resolvePlanView();
        CommandRegistry registry = createCommandRegistry(planView);
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
        if (logFileHandler != null) {
            Logger.getLogger("com.sh3d.mcp").removeHandler(logFileHandler);
            logFileHandler.close();
            logFileHandler = null;
        }
    }

    private void setupFileLogging(PluginConfig cfg) {
        Path logPath = PluginConfig.resolveLogPath();
        if (logPath == null) {
            return;
        }
        try {
            FileHandler fh = new FileHandler(logPath.toString(), 1_048_576, 2, true);
            fh.setFormatter(new SimpleFormatter());

            Logger rootLogger = Logger.getLogger("com.sh3d.mcp");
            rootLogger.addHandler(fh);
            logFileHandler = fh;

            Level level;
            try {
                level = Level.parse(cfg.getLogLevel());
            } catch (IllegalArgumentException e) {
                LOG.warning("Invalid log level '" + cfg.getLogLevel() + "', falling back to INFO");
                level = Level.INFO;
            }
            rootLogger.setLevel(level);

            LOG.info("SH3D MCP Plugin v0.1.0 | port=" + cfg.getPort()
                    + " | Java " + System.getProperty("java.version")
                    + " | " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                    + " | log=" + logPath);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to setup file logging at " + logPath, e);
        }
    }

    private ExportableView resolvePlanView() {
        try {
            PlanView planView = getHomeController().getPlanController().getView();
            if (planView instanceof ExportableView) {
                LOG.info("Resolved PlanView for SVG export: " + planView.getClass().getSimpleName());
                return (ExportableView) planView;
            }
            LOG.warning("PlanView does not implement ExportableView: " + planView.getClass().getName());
        } catch (Exception e) {
            LOG.warning("Could not resolve PlanView: " + e.getMessage());
        }
        return null;
    }

    private CommandRegistry createCommandRegistry(ExportableView planView) {
        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", new PingHandler());
        registry.register("create_walls", new CreateWallsHandler());
        registry.register("place_furniture", new PlaceFurnitureHandler());
        registry.register("get_state", new GetStateHandler());
        registry.register("list_furniture_catalog", new ListFurnitureCatalogHandler());
        registry.register("render_photo", new RenderPhotoHandler());
        registry.register("export_svg", new ExportSvgHandler(planView));
        registry.register("describe_commands", new DescribeCommandsHandler(registry));
        return registry;
    }
}
