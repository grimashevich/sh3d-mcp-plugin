/*
 * GenerateShapeHandler.java
 *
 * 3D shape generation logic adapted from ShapeGenerator plugin v1.2.1
 * Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
 * Licensed under GNU GPL v2+
 * Source: https://www.sweethome3d.com/support/forum/viewthread_thread,6600
 */
package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handler for "generate_shape" command.
 * Creates custom 3D shapes and adds them to the scene as furniture.
 *
 * Two modes:
 * - "extrude": extrudes a 2D polygon to a given height (walls, beams, countertops)
 * - "mesh": arbitrary triangle mesh from vertices + triangle indices (roofs, stairs, pyramids)
 *
 * Delegates actual generation to {@link ExtrudeShapeGenerator} and {@link MeshShapeGenerator}.
 */
public class GenerateShapeHandler implements CommandHandler, CommandDescriptor {

    private final ExtrudeShapeGenerator extrudeGenerator = new ExtrudeShapeGenerator();
    private final MeshShapeGenerator meshGenerator = new MeshShapeGenerator();

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String mode = request.getString("mode");
        if (mode == null || mode.trim().isEmpty()) {
            return Response.error("Parameter 'mode' is required ('extrude' or 'mesh')");
        }

        switch (mode) {
            case "extrude":
                return extrudeGenerator.execute(request, accessor);
            case "mesh":
                return meshGenerator.execute(request, accessor);
            default:
                return Response.error("Unknown mode '" + mode + "'. Use 'extrude' or 'mesh'");
        }
    }

    // ======================== COMMAND DESCRIPTOR ========================

    @Override
    public String getDescription() {
        return "Generates a custom 3D shape and adds it to the scene as furniture. "
                + "Two modes: 'extrude' creates a prism by extruding a 2D polygon to a given height "
                + "(ideal for custom walls, beams, countertops, L-shaped structures). "
                + "'mesh' creates an arbitrary shape from vertices and triangle indices "
                + "(ideal for roofs, stairs, pyramids). "
                + "All coordinates are in centimeters. "
                + "In extrude mode, polygon points use SH3D plan coordinates (X right, Y down). "
                + "In mesh mode, vertices are [x, y, z] where x/y are plan coordinates and z is height above floor.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        // mode
        Map<String, Object> modeProp = new LinkedHashMap<>();
        modeProp.put("type", "string");
        modeProp.put("description", "Shape generation mode: 'extrude' (2D polygon + height) or 'mesh' (vertices + triangles)");
        modeProp.put("enum", Arrays.asList("extrude", "mesh"));
        properties.put("mode", modeProp);

        // polygon (extrude mode)
        Map<String, Object> polygonProp = new LinkedHashMap<>();
        polygonProp.put("type", "array");
        polygonProp.put("description", "Extrude mode only. Array of [x, y] points defining the 2D polygon base. "
                + "Points are in SH3D plan coordinates (cm). Minimum 3 points. "
                + "Example for a 400x10cm wall base: [[0,0],[400,0],[400,10],[0,10]]");
        Map<String, Object> point2DItems = new LinkedHashMap<>();
        point2DItems.put("type", "array");
        Map<String, Object> numItem = new LinkedHashMap<>();
        numItem.put("type", "number");
        point2DItems.put("items", numItem);
        point2DItems.put("minItems", 2);
        point2DItems.put("maxItems", 2);
        polygonProp.put("items", point2DItems);
        polygonProp.put("minItems", 3);
        properties.put("polygon", polygonProp);

        // height (extrude mode)
        Map<String, Object> heightProp = new LinkedHashMap<>();
        heightProp.put("type", "number");
        heightProp.put("description", "Extrude mode only. Height of extrusion in cm (e.g., 250 for a wall)");
        properties.put("height", heightProp);

        // vertices (mesh mode)
        Map<String, Object> verticesProp = new LinkedHashMap<>();
        verticesProp.put("type", "array");
        verticesProp.put("description", "Mesh mode only. Array of [x, y, z] vertices. "
                + "x/y = SH3D plan coordinates, z = height above floor (cm)");
        Map<String, Object> point3DItems = new LinkedHashMap<>();
        point3DItems.put("type", "array");
        point3DItems.put("items", numItem);
        point3DItems.put("minItems", 3);
        point3DItems.put("maxItems", 3);
        verticesProp.put("items", point3DItems);
        verticesProp.put("minItems", 3);
        properties.put("vertices", verticesProp);

        // triangles (mesh mode)
        Map<String, Object> trianglesProp = new LinkedHashMap<>();
        trianglesProp.put("type", "array");
        trianglesProp.put("description", "Mesh mode only. Array of [i0, i1, i2] vertex index triples "
                + "defining triangular faces. Indices are 0-based.");
        Map<String, Object> triItems = new LinkedHashMap<>();
        triItems.put("type", "array");
        Map<String, Object> intItem = new LinkedHashMap<>();
        intItem.put("type", "integer");
        triItems.put("items", intItem);
        triItems.put("minItems", 3);
        triItems.put("maxItems", 3);
        trianglesProp.put("items", triItems);
        properties.put("triangles", trianglesProp);

        // Common optional params
        properties.put("name", prop("string", "Name of the shape (default: 'Extruded Shape' or 'Custom Mesh')"));

        Map<String, Object> transparencyProp = new LinkedHashMap<>();
        transparencyProp.put("type", "number");
        transparencyProp.put("description", "Transparency from 0.0 (opaque) to 1.0 (invisible). Default 0");
        transparencyProp.put("default", 0);
        properties.put("transparency", transparencyProp);

        Map<String, Object> elevationProp = new LinkedHashMap<>();
        elevationProp.put("type", "number");
        elevationProp.put("description", "Elevation above floor in cm. Default 0 (on floor)");
        elevationProp.put("default", 0);
        properties.put("elevation", elevationProp);

        Map<String, Object> colorProp = new LinkedHashMap<>();
        colorProp.put("type", "integer");
        colorProp.put("description", "Color as RGB integer (e.g., 16711680 for red = 0xFF0000). "
                + "Can also be hex string like '#FF0000'");
        properties.put("color", colorProp);

        schema.put("properties", properties);
        schema.put("required", Arrays.asList("mode"));
        return schema;
    }

}
