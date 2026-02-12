package com.sh3d.mcp.command;

import com.eteks.sweethome3d.viewcontroller.ExportableView;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Обработчик команды "export_svg".
 * Экспортирует текущий 2D-план в SVG через ExportableView.exportData().
 * Работает как с PlanComponent, так и с MultipleLevelsPlanPanel.
 *
 * <pre>
 * Параметры: нет
 * Возвращает: SVG-строка (текст)
 * </pre>
 */
public class ExportSvgHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(ExportSvgHandler.class.getName());

    private final ExportableView planView;

    public ExportSvgHandler(ExportableView planView) {
        this.planView = planView;
    }

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        if (planView == null) {
            return Response.error("PlanView is not available — SVG export requires SH3D UI");
        }

        try {
            String svgContent = accessor.runOnEDT(() -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                planView.exportData(out, ExportableView.FormatType.SVG, null);
                return out.toString("UTF-8");
            });

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("svg", svgContent);
            data.put("size_bytes", svgContent.getBytes("UTF-8").length);

            LOG.info("Exported SVG plan (" + svgContent.length() + " chars)");
            return Response.ok(data);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "SVG export failed", e);
            return Response.error("SVG export failed: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Exports the current 2D floor plan as SVG (Scalable Vector Graphics). "
                + "Returns the SVG content as a string. Shows walls, furniture, rooms, labels, "
                + "and dimension lines exactly as they appear in the plan view. "
                + "No parameters required.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        schema.put("required", Arrays.asList());
        return schema;
    }
}
