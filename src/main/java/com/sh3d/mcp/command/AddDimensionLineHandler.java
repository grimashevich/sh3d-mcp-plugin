package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.FormatUtil.round2;
import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Обработчик команды "add_dimension_line".
 * Добавляет размерную линию (аннотацию измерения) на 2D-план.
 */
public class AddDimensionLineHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> params = request.getParams();

        // Required: xStart, yStart, xEnd, yEnd, offset
        Object xsVal = params.get("xStart");
        Object ysVal = params.get("yStart");
        Object xeVal = params.get("xEnd");
        Object yeVal = params.get("yEnd");
        Object offVal = params.get("offset");

        if (!(xsVal instanceof Number) || !(ysVal instanceof Number)) {
            return Response.error("Missing required numeric parameters: 'xStart' and 'yStart'");
        }
        if (!(xeVal instanceof Number) || !(yeVal instanceof Number)) {
            return Response.error("Missing required numeric parameters: 'xEnd' and 'yEnd'");
        }
        if (!(offVal instanceof Number)) {
            return Response.error("Missing required numeric parameter: 'offset'");
        }

        float xStart = ((Number) xsVal).floatValue();
        float yStart = ((Number) ysVal).floatValue();
        float xEnd = ((Number) xeVal).floatValue();
        float yEnd = ((Number) yeVal).floatValue();
        float offset = ((Number) offVal).floatValue();

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();

            DimensionLine dim = new DimensionLine(xStart, yStart, xEnd, yEnd, offset);
            home.addDimensionLine(dim);

            int id = new ArrayList<>(home.getDimensionLines()).indexOf(dim);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("xStart", round2(dim.getXStart()));
            result.put("yStart", round2(dim.getYStart()));
            result.put("xEnd", round2(dim.getXEnd()));
            result.put("yEnd", round2(dim.getYEnd()));
            result.put("offset", round2(dim.getOffset()));
            result.put("length", round2(dim.getLength()));
            return result;
        });

        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Add a dimension line (measurement annotation) to the 2D plan. "
                + "Shows the distance between two points with extension lines and an auto-calculated length label. "
                + "All coordinates in centimeters. "
                + "Offset controls perpendicular distance of the label from the measured line "
                + "(positive = above/left, negative = below/right, typical value: 20-50).";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("xStart", prop("number", "X coordinate of the start point in centimeters"));
        properties.put("yStart", prop("number", "Y coordinate of the start point in centimeters"));
        properties.put("xEnd", prop("number", "X coordinate of the end point in centimeters"));
        properties.put("yEnd", prop("number", "Y coordinate of the end point in centimeters"));
        properties.put("offset", prop("number",
                "Perpendicular distance (cm) of the dimension label from the measured line. "
                + "Positive = above/left, negative = below/right. Typical: 20-50"));

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("xStart", "yStart", "xEnd", "yEnd", "offset"));
        return schema;
    }

}
