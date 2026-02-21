package com.sh3d.mcp.plugin;

import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.command.AddDimensionLineHandler;
import com.sh3d.mcp.command.AddLabelHandler;
import com.sh3d.mcp.command.AddLevelHandler;
import com.sh3d.mcp.command.ApplyTextureHandler;
import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.command.BatchCommandsHandler;
import com.sh3d.mcp.command.CheckpointHandler;
import com.sh3d.mcp.command.ClearSceneHandler;
import com.sh3d.mcp.command.CommandRegistry;
import com.sh3d.mcp.command.CreateRoomPolygonHandler;
import com.sh3d.mcp.command.ConnectWallsHandler;
import com.sh3d.mcp.command.DeleteWallHandler;
import com.sh3d.mcp.command.CreateWallHandler;
import com.sh3d.mcp.command.DeleteFurnitureHandler;
import com.sh3d.mcp.command.DeleteLevelHandler;
import com.sh3d.mcp.command.DeleteRoomHandler;
import com.sh3d.mcp.command.CreateWallsHandler;
import com.sh3d.mcp.command.ExportPlanImageHandler;
import com.sh3d.mcp.command.ExportSvgHandler;
import com.sh3d.mcp.command.ExportToObjHandler;
import com.sh3d.mcp.command.GenerateShapeHandler;
import com.sh3d.mcp.command.GetCamerasHandler;
import com.sh3d.mcp.command.ModifyFurnitureHandler;
import com.sh3d.mcp.command.ModifyRoomHandler;
import com.sh3d.mcp.command.ModifyWallHandler;
import com.sh3d.mcp.command.GetStateHandler;
import com.sh3d.mcp.command.ListCheckpointsHandler;
import com.sh3d.mcp.command.ListCategoriesHandler;
import com.sh3d.mcp.command.ListFurnitureCatalogHandler;
import com.sh3d.mcp.command.ListLevelsHandler;
import com.sh3d.mcp.command.ListTexturesCatalogHandler;
import com.sh3d.mcp.command.PlaceDoorOrWindowHandler;
import com.sh3d.mcp.command.PlaceFurnitureHandler;
import com.sh3d.mcp.command.RenderPhotoHandler;
import com.sh3d.mcp.command.RestoreCheckpointHandler;
import com.sh3d.mcp.command.LoadHomeHandler;
import com.sh3d.mcp.command.SaveHomeHandler;
import com.sh3d.mcp.command.SetCameraHandler;
import com.sh3d.mcp.command.SetEnvironmentHandler;
import com.sh3d.mcp.command.SetSelectedLevelHandler;
import com.sh3d.mcp.command.StoreCameraHandler;
import com.sh3d.mcp.config.PluginConfig;
import com.sh3d.mcp.http.HttpMcpServer;
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

    public static final String PLUGIN_VERSION = PluginConfig.PLUGIN_VERSION;

    private static final Logger LOG = Logger.getLogger(SH3DMcpPlugin.class.getName());

    private HttpMcpServer httpServer;
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
        httpServer = new HttpMcpServer(config, registry, accessor);

        LOG.info("SH3D MCP Plugin initialized (port: " + config.getPort() + ")");

        if (config.isAutoStart()) {
            httpServer.start();
        }

        return new PluginAction[]{
                new McpSettingsAction(this, httpServer)
        };
    }

    @Override
    public void destroy() {
        if (httpServer != null && httpServer.isRunning()) {
            httpServer.stop();
            LOG.info("SH3D MCP Plugin destroyed, server stopped");
        }
        if (logFileHandler != null) {
            Logger.getLogger("com.sh3d.mcp").removeHandler(logFileHandler);
            logFileHandler.close();
            logFileHandler = null;
        }
    }

    /** Configures file-based logging for the com.sh3d.mcp logger hierarchy. */
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

            LOG.info("SH3D MCP Plugin v" + PLUGIN_VERSION + " | port=" + cfg.getPort()
                    + " | Java " + System.getProperty("java.version")
                    + " | " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                    + " | log=" + logPath);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to setup file logging at " + logPath, e);
        }
    }

    /** Resolves the plan view for SVG/PNG export via HomeController. */
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
        CheckpointManager checkpointManager = new CheckpointManager();
        registry.register("checkpoint", new CheckpointHandler(checkpointManager));
        registry.register("restore_checkpoint", new RestoreCheckpointHandler(checkpointManager));
        registry.register("list_checkpoints", new ListCheckpointsHandler(checkpointManager));
        registry.register("add_dimension_line", new AddDimensionLineHandler());
        registry.register("add_label", new AddLabelHandler());
        registry.register("add_level", new AddLevelHandler());
        registry.register("apply_texture", new ApplyTextureHandler());
        registry.register("clear_scene", new ClearSceneHandler(checkpointManager));
        registry.register("connect_walls", new ConnectWallsHandler());
        registry.register("create_room_polygon", new CreateRoomPolygonHandler());
        registry.register("create_wall", new CreateWallHandler());
        registry.register("create_walls", new CreateWallsHandler());
        registry.register("delete_furniture", new DeleteFurnitureHandler());
        registry.register("delete_level", new DeleteLevelHandler());
        registry.register("delete_room", new DeleteRoomHandler());
        registry.register("delete_wall", new DeleteWallHandler());
        registry.register("generate_shape", new GenerateShapeHandler());
        registry.register("modify_furniture", new ModifyFurnitureHandler());
        registry.register("modify_room", new ModifyRoomHandler());
        registry.register("modify_wall", new ModifyWallHandler());
        registry.register("place_door_or_window", new PlaceDoorOrWindowHandler());
        registry.register("place_furniture", new PlaceFurnitureHandler());
        registry.register("get_state", new GetStateHandler());
        registry.register("list_categories", new ListCategoriesHandler());
        registry.register("list_furniture_catalog", new ListFurnitureCatalogHandler());
        registry.register("list_levels", new ListLevelsHandler());
        registry.register("list_textures_catalog", new ListTexturesCatalogHandler());
        registry.register("render_photo", new RenderPhotoHandler());
        registry.register("load_home", new LoadHomeHandler());
        registry.register("save_home", new SaveHomeHandler());
        registry.register("export_plan_image", new ExportPlanImageHandler(planView));
        registry.register("export_svg", new ExportSvgHandler(planView));
        registry.register("export_to_obj", new ExportToObjHandler());
        registry.register("set_camera", new SetCameraHandler());
        registry.register("set_environment", new SetEnvironmentHandler());
        registry.register("set_selected_level", new SetSelectedLevelHandler());
        registry.register("store_camera", new StoreCameraHandler());
        registry.register("get_cameras", new GetCamerasHandler());
        registry.register("batch_commands", new BatchCommandsHandler(registry, checkpointManager));
        return registry;
    }
}
