package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import javax.media.j3d.BranchGroup;
import javax.vecmath.Point3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates 3D shapes from arbitrary triangle meshes (vertices + triangle indices).
 * Handles validation, geometry construction, and scene integration for mesh mode.
 */
final class MeshShapeGenerator {

    Response execute(Request request, HomeAccessor accessor) {
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

        Integer colorValue = ShapeGeneratorSupport.parseColor(request);

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
        ShapeGeneratorSupport.addShapeToRoot(root, coordArray, transparency);

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

        return ShapeGeneratorSupport.exportAndAddToScene(root, name, centerX, centerY,
                maxX - minX, maxY - minY, maxZ - minZ,
                minZ + elevation, transparency, colorValue, accessor);
    }

    private float[][] parsePoints3D(List<?> list) {
        float[][] points = new float[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof List)) return null;
            List<?> point = (List<?>) item;
            if (point.size() != 3) return null;
            try {
                points[i][0] = ShapeGeneratorSupport.toFloat(point.get(0));
                points[i][1] = ShapeGeneratorSupport.toFloat(point.get(1));
                points[i][2] = ShapeGeneratorSupport.toFloat(point.get(2));
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
                    int idx = ShapeGeneratorSupport.toInt(tri.get(j));
                    if (idx < 0 || idx >= vertexCount) return null;
                    triangles[i][j] = idx;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return triangles;
    }
}
