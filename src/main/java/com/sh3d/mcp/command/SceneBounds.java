package com.sh3d.mcp.command;

/**
 * Value object holding bounding box of a scene or focused object.
 * Extracted from RenderPhotoHandler.
 */
class SceneBounds {
    float minX, minY, maxX, maxY, maxZ;
    float centerX, centerY, sceneWidth, sceneDepth;

    static SceneBounds of(float minX, float minY, float maxX, float maxY, float maxZ) {
        SceneBounds b = new SceneBounds();
        b.minX = minX;
        b.minY = minY;
        b.maxX = maxX;
        b.maxY = maxY;
        b.maxZ = maxZ;
        b.centerX = (minX + maxX) / 2;
        b.centerY = (minY + maxY) / 2;
        b.sceneWidth = maxX - minX;
        b.sceneDepth = maxY - minY;
        return b;
    }
}
