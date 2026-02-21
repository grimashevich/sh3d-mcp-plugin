package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates 3D shapes by extruding a 2D polygon to a given height.
 * Handles validation, geometry construction, and scene integration for extrude mode.
 */
final class ExtrudeShapeGenerator {

    Response execute(Request request, HomeAccessor accessor) {
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

        Integer colorValue = ShapeGeneratorSupport.parseColor(request);

        // Build geometry
        // SH3D coordinate system: X right, Y down (plan view)
        // Java3D coordinate system: X right, Y up, Z towards viewer
        // Mapping: SH3D X -> J3D X, SH3D Y -> J3D Z, height -> J3D Y
        BranchGroup root = new BranchGroup();
        List<Point3f> allCoords = new ArrayList<>();

        // Bottom face (Y=0 in J3D)
        Point3f[] bottomCoords = buildFaceTriangles(polygon, 0f, false);
        if (bottomCoords.length > 0) {
            allCoords.addAll(Arrays.asList(bottomCoords));
        }

        // Top face (Y=height in J3D)
        Point3f[] topCoords = buildFaceTriangles(polygon, height, true);
        if (topCoords.length > 0) {
            allCoords.addAll(Arrays.asList(topCoords));
        }

        // Side faces
        for (int i = 0; i < polygon.length; i++) {
            int next = (i + 1) % polygon.length;
            float x0 = polygon[i][0], y0 = polygon[i][1];
            float x1 = polygon[next][0], y1 = polygon[next][1];

            // Two triangles per side quad (in J3D coords)
            Point3f[] sideCoords = new Point3f[]{
                    new Point3f(x0, 0, y0),
                    new Point3f(x1, 0, y1),
                    new Point3f(x1, height, y1),
                    new Point3f(x0, 0, y0),
                    new Point3f(x1, height, y1),
                    new Point3f(x0, height, y0)
            };
            allCoords.addAll(Arrays.asList(sideCoords));
        }

        if (allCoords.isEmpty()) {
            return Response.error("Generated shape has no geometry (degenerate polygon?)");
        }

        Point3f[] coords = allCoords.toArray(new Point3f[0]);
        ShapeGeneratorSupport.addShapeToRoot(root, coords, transparency);

        // Compute bounding box from polygon (SH3D coords)
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] p : polygon) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;
        float width2 = maxX - minX;
        float depth = maxY - minY;

        return ShapeGeneratorSupport.exportAndAddToScene(root, name, centerX, centerY,
                width2, depth, height, elevation, transparency, colorValue, accessor);
    }

    /**
     * Triangulates a polygon into triangle fan from first vertex.
     */
    private Point3f[] buildFaceTriangles(float[][] polygon, float yJ3D, boolean reverseWinding) {
        if (polygon.length < 3) {
            return new Point3f[0];
        }
        List<Point3f> triangles = new ArrayList<>();
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

    private float[][] parsePoints2D(List<?> list) {
        float[][] points = new float[list.size()][2];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof List)) return null;
            List<?> point = (List<?>) item;
            if (point.size() != 2) return null;
            try {
                points[i][0] = ShapeGeneratorSupport.toFloat(point.get(0));
                points[i][1] = ShapeGeneratorSupport.toFloat(point.get(1));
            } catch (Exception e) {
                return null;
            }
        }
        return points;
    }
}
