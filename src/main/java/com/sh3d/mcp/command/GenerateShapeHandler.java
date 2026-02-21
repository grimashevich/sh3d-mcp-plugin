/*
 * GenerateShapeHandler.java
 *
 * 3D shape generation logic adapted from ShapeGenerator plugin v1.2.1
 * Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
 * Licensed under GNU GPL v2+
 * Source: https://www.sweethome3d.com/support/forum/viewthread_thread,6600
 */
package com.sh3d.mcp.command;

import com.eteks.sweethome3d.j3d.OBJWriter;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.tools.OperatingSystem;
import com.eteks.sweethome3d.tools.TemporaryURLContent;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.Appearance;
import javax.media.j3d.BranchGroup;
import javax.media.j3d.Material;
import javax.media.j3d.Shape3D;
import javax.media.j3d.TransparencyAttributes;
import javax.vecmath.Color3f;
import javax.vecmath.Point3f;
import javax.vecmath.TexCoord2f;

import com.sun.j3d.utils.geometry.GeometryInfo;
import com.sun.j3d.utils.geometry.NormalGenerator;

import static com.sh3d.mcp.command.FormatUtil.round2;
import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler for "generate_shape" command.
 * Creates custom 3D shapes and adds them to the scene as furniture.
 *
 * Two modes:
 * - "extrude": extrudes a 2D polygon to a given height (walls, beams, countertops)
 * - "mesh": arbitrary triangle mesh from vertices + triangle indices (roofs, stairs, pyramids)
 */
public class GenerateShapeHandler implements CommandHandler, CommandDescriptor {

    private static final Logger LOG = Logger.getLogger(GenerateShapeHandler.class.getName());

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String mode = request.getString("mode");
        if (mode == null || mode.trim().isEmpty()) {
            return Response.error("Parameter 'mode' is required ('extrude' or 'mesh')");
        }

        switch (mode) {
            case "extrude":
                return executeExtrude(request, accessor);
            case "mesh":
                return executeMesh(request, accessor);
            default:
                return Response.error("Unknown mode '" + mode + "'. Use 'extrude' or 'mesh'");
        }
    }

    // ======================== EXTRUDE MODE ========================

    private Response executeExtrude(Request request, HomeAccessor accessor) {
        // Parse polygon points
        Object polygonRaw = request.getParams().get("polygon");
        if (polygonRaw == null) {
            return Response.error("Parameter 'polygon' is required for extrude mode");
        }
        if (!(polygonRaw instanceof List)) {
            return Response.error("Parameter 'polygon' must be an array of [x, y] points");
        }
        List<?> polygonList = (List<?>) polygonRaw;
        if (polygonList.size() < 3) {
            return Response.error("Polygon must have at least 3 points");
        }

        float[][] polygon = parsePoints2D(polygonList);
        if (polygon == null) {
            return Response.error("Invalid polygon format: each point must be [x, y] with numeric values");
        }

        if (!request.getParams().containsKey("height")) {
            return Response.error("Parameter 'height' is required for extrude mode");
        }
        float height = request.getFloat("height");
        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive");
        }

        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            name = "Extruded Shape";
        }

        float transparency = request.getFloat("transparency", 0f);
        if (transparency < 0 || transparency > 1) {
            return Response.error("Parameter 'transparency' must be between 0.0 and 1.0");
        }

        float elevation = request.getFloat("elevation", 0f);

        Integer colorValue = parseColor(request);

        // Build geometry
        // SH3D coordinate system: X right, Y down (plan view)
        // Java3D coordinate system: X right, Y up, Z towards viewer
        // Mapping: SH3D X -> J3D X, SH3D Y -> J3D Z, height -> J3D Y
        BranchGroup root = new BranchGroup();
        List<Point3f> allCoords = new ArrayList<>();
        List<String> faceNames = new ArrayList<>();

        // Bottom face (Y=0 in J3D)
        Point3f[] bottomCoords = buildFaceTriangles(polygon, 0f, false);
        if (bottomCoords.length > 0) {
            allCoords.addAll(Arrays.asList(bottomCoords));
            faceNames.add("bottom");
        }

        // Top face (Y=height in J3D)
        Point3f[] topCoords = buildFaceTriangles(polygon, height, true);
        if (topCoords.length > 0) {
            allCoords.addAll(Arrays.asList(topCoords));
            faceNames.add("top");
        }

        // Side faces
        for (int i = 0; i < polygon.length; i++) {
            int next = (i + 1) % polygon.length;
            float x0 = polygon[i][0], y0 = polygon[i][1];
            float x1 = polygon[next][0], y1 = polygon[next][1];

            // Two triangles per side quad (in J3D coords)
            // Winding order for outward-facing normals
            Point3f[] sideCoords = new Point3f[]{
                    new Point3f(x0, 0, y0),
                    new Point3f(x1, 0, y1),
                    new Point3f(x1, height, y1),
                    new Point3f(x0, 0, y0),
                    new Point3f(x1, height, y1),
                    new Point3f(x0, height, y0)
            };
            allCoords.addAll(Arrays.asList(sideCoords));
            faceNames.add("side_" + i);
        }

        // Create single shape with all triangles
        if (allCoords.isEmpty()) {
            return Response.error("Generated shape has no geometry (degenerate polygon?)");
        }

        Point3f[] coords = allCoords.toArray(new Point3f[0]);
        addShapeToRoot(root, coords, transparency);

        return exportAndAddToScene(root, name, polygon, height, elevation, transparency,
                colorValue, accessor);
    }

    /**
     * Triangulates a polygon into triangle fan from first vertex.
     * Simple ear-clipping for convex polygons.
     */
    private Point3f[] buildFaceTriangles(float[][] polygon, float yJ3D, boolean reverseWinding) {
        if (polygon.length < 3) {
            return new Point3f[0];
        }
        List<Point3f> triangles = new ArrayList<>();
        // Fan triangulation from vertex 0
        for (int i = 1; i < polygon.length - 1; i++) {
            if (reverseWinding) {
                triangles.add(new Point3f(polygon[0][0], yJ3D, polygon[0][1]));
                triangles.add(new Point3f(polygon[i + 1][0], yJ3D, polygon[i + 1][1]));
                triangles.add(new Point3f(polygon[i][0], yJ3D, polygon[i][1]));
            } else {
                triangles.add(new Point3f(polygon[0][0], yJ3D, polygon[0][1]));
                triangles.add(new Point3f(polygon[i][0], yJ3D, polygon[i][1]));
                triangles.add(new Point3f(polygon[i + 1][0], yJ3D, polygon[i + 1][1]));
            }
        }
        return triangles.toArray(new Point3f[0]);
    }

    // ======================== MESH MODE ========================

    private Response executeMesh(Request request, HomeAccessor accessor) {
        // Parse vertices
        Object verticesRaw = request.getParams().get("vertices");
        if (verticesRaw == null) {
            return Response.error("Parameter 'vertices' is required for mesh mode");
        }
        if (!(verticesRaw instanceof List)) {
            return Response.error("Parameter 'vertices' must be an array of [x, y, z] points");
        }
        List<?> verticesList = (List<?>) verticesRaw;
        if (verticesList.size() < 3) {
            return Response.error("Mesh must have at least 3 vertices");
        }

        float[][] vertices = parsePoints3D(verticesList);
        if (vertices == null) {
            return Response.error("Invalid vertices format: each vertex must be [x, y, z] with numeric values");
        }

        // Parse triangles (indices)
        Object trianglesRaw = request.getParams().get("triangles");
        if (trianglesRaw == null) {
            return Response.error("Parameter 'triangles' is required for mesh mode");
        }
        if (!(trianglesRaw instanceof List)) {
            return Response.error("Parameter 'triangles' must be an array of [i0, i1, i2] index triples");
        }
        List<?> trianglesList = (List<?>) trianglesRaw;
        if (trianglesList.isEmpty()) {
            return Response.error("Mesh must have at least 1 triangle");
        }

        int[][] triangles = parseTriangleIndices(trianglesList, vertices.length);
        if (triangles == null) {
            return Response.error("Invalid triangles format: each must be [i0, i1, i2] "
                    + "with valid vertex indices (0 to " + (vertices.length - 1) + ")");
        }

        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            name = "Custom Mesh";
        }

        float transparency = request.getFloat("transparency", 0f);
        if (transparency < 0 || transparency > 1) {
            return Response.error("Parameter 'transparency' must be between 0.0 and 1.0");
        }

        float elevation = request.getFloat("elevation", 0f);

        Integer colorValue = parseColor(request);

        // Build geometry in J3D coordinates
        // Input: x = SH3D X (right), y = SH3D Y (down in plan), z = height (up)
        // J3D:   X = SH3D X,         Y = height (up),            Z = SH3D Y
        BranchGroup root = new BranchGroup();
        List<Point3f> coords = new ArrayList<>();
        for (int[] tri : triangles) {
            for (int idx : tri) {
                float sx = vertices[idx][0]; // SH3D X
                float sy = vertices[idx][1]; // SH3D Y
                float sz = vertices[idx][2]; // height
                coords.add(new Point3f(sx, sz, sy)); // J3D: x, y(up), z
            }
        }

        Point3f[] coordArray = coords.toArray(new Point3f[0]);
        addShapeToRoot(root, coordArray, transparency);

        // Compute bounding box from input vertices (SH3D coords)
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (float[] v : vertices) {
            minX = Math.min(minX, v[0]); maxX = Math.max(maxX, v[0]);
            minY = Math.min(minY, v[1]); maxY = Math.max(maxY, v[1]);
            minZ = Math.min(minZ, v[2]); maxZ = Math.max(maxZ, v[2]);
        }

        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;

        return exportAndAddToScene(root, name, centerX, centerY,
                maxX - minX, maxY - minY, maxZ - minZ,
                minZ + elevation, transparency, colorValue, accessor);
    }

    // ======================== SHARED LOGIC ========================

    /**
     * Adds a Shape3D with auto-generated normals to the BranchGroup.
     * Adapted from ShapeGenerator plugin's createShape() method.
     */
    private void addShapeToRoot(BranchGroup root, Point3f[] coords, float transparency) {
        GeometryInfo geometryInfo = new GeometryInfo(GeometryInfo.TRIANGLE_ARRAY);
        geometryInfo.setCoordinates(coords);
        new NormalGenerator(0).generateNormals(geometryInfo);

        Appearance appearance = new Appearance();
        appearance.setMaterial(new Material(
                new Color3f(0.2f, 0.2f, 0.2f),
                new Color3f(),
                new Color3f(1.0f, 1.0f, 1.0f),
                new Color3f(),
                0));
        if (transparency > 0) {
            appearance.setTransparencyAttributes(
                    new TransparencyAttributes(TransparencyAttributes.NICEST, transparency));
        }
        root.addChild(new Shape3D(geometryInfo.getIndexedGeometryArray(), appearance));
    }

    /**
     * Exports J3D scene to OBJ, loads model, creates furniture piece, adds to Home.
     * Overload for extrude mode.
     */
    private Response exportAndAddToScene(BranchGroup root, String name,
                                         float[][] polygon, float height,
                                         float elevation, float transparency,
                                         Integer color, HomeAccessor accessor) {
        // Compute bounding box from polygon (SH3D coords)
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] p : polygon) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;
        float width = maxX - minX;
        float depth = maxY - minY;

        return exportAndAddToScene(root, name, centerX, centerY,
                width, depth, height, elevation, transparency, color, accessor);
    }

    /**
     * Core method: exports J3D scene to OBJ ZIP, creates furniture piece, adds to Home.
     * Dimensions are computed from input coordinates (no ModelManager.loadModel needed).
     */
    private Response exportAndAddToScene(BranchGroup root, String name,
                                         float centerX, float centerY,
                                         float width, float depth, float modelHeight,
                                         float elevation, float transparency,
                                         Integer color, HomeAccessor accessor) {
        try {
            // Export to OBJ in ZIP
            String safeName = name.replaceAll("[^a-zA-Z0-9_-]", "_");
            File tempZipFile = OperatingSystem.createTemporaryFile(safeName, ".zip");
            String objFile = safeName + ".obj";
            OBJWriter.writeNodeInZIPFile(root, tempZipFile, 0, objFile,
                    "Generated by SH3D MCP Plugin");

            URL zipUrl = new URL("jar:" + tempZipFile.toURI().toURL() + "!/" + objFile);
            TemporaryURLContent modelContent = new TemporaryURLContent(zipUrl);

            // CatalogPieceOfFurniture: width = J3D X, depth = J3D Z, height = J3D Y
            final String finalName = name;
            final float fw = Math.max(width, 0.1f);
            final float fd = Math.max(depth, 0.1f);
            final float fh = Math.max(modelHeight, 0.1f);

            HomePieceOfFurniture placed = accessor.runOnEDT(() -> {
                CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(
                        null,        // id
                        finalName,   // name
                        null,        // description
                        null,        // icon
                        modelContent,// model
                        fw, fd, fh,  // width, depth, height
                        0,           // elevation
                        true,        // movable
                        null,        // staircaseCutOutShape
                        null,        // creator
                        true,        // resizable
                        null,        // price
                        null);       // valueAddedTaxPercentage
                HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
                piece.setX(centerX);
                piece.setY(centerY);
                piece.setElevation(elevation);

                if (color != null) {
                    piece.setColor(color);
                }

                accessor.getHome().addPieceOfFurniture(piece);
                return piece;
            });

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", placed.getId());
            data.put("name", placed.getName());
            data.put("x", round2(placed.getX()));
            data.put("y", round2(placed.getY()));
            data.put("elevation", round2(placed.getElevation()));
            data.put("width", round2(placed.getWidth()));
            data.put("depth", round2(placed.getDepth()));
            data.put("height", round2(placed.getHeight()));
            return Response.ok(data);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to generate shape", e);
            return Response.error("Failed to generate shape: " + e.getMessage());
        }
    }

    // ======================== PARSING HELPERS ========================

    private float[][] parsePoints2D(List<?> list) {
        float[][] points = new float[list.size()][2];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof List)) return null;
            List<?> point = (List<?>) item;
            if (point.size() != 2) return null;
            try {
                points[i][0] = toFloat(point.get(0));
                points[i][1] = toFloat(point.get(1));
            } catch (Exception e) {
                return null;
            }
        }
        return points;
    }

    private float[][] parsePoints3D(List<?> list) {
        float[][] points = new float[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof List)) return null;
            List<?> point = (List<?>) item;
            if (point.size() != 3) return null;
            try {
                points[i][0] = toFloat(point.get(0));
                points[i][1] = toFloat(point.get(1));
                points[i][2] = toFloat(point.get(2));
            } catch (Exception e) {
                return null;
            }
        }
        return points;
    }

    private int[][] parseTriangleIndices(List<?> list, int vertexCount) {
        int[][] triangles = new int[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof List)) return null;
            List<?> tri = (List<?>) item;
            if (tri.size() != 3) return null;
            try {
                for (int j = 0; j < 3; j++) {
                    int idx = toInt(tri.get(j));
                    if (idx < 0 || idx >= vertexCount) return null;
                    triangles[i][j] = idx;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return triangles;
    }

    private Integer parseColor(Request request) {
        Object colorRaw = request.getParams().get("color");
        if (colorRaw == null) return null;
        if (colorRaw instanceof Number) {
            return ((Number) colorRaw).intValue();
        }
        if (colorRaw instanceof String) {
            String hex = ((String) colorRaw).trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
            return Integer.parseInt(hex, 16);
        }
        return null;
    }

    private float toFloat(Object obj) {
        if (obj instanceof Number) return ((Number) obj).floatValue();
        return Float.parseFloat(obj.toString());
    }

    private int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        return Integer.parseInt(obj.toString());
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
