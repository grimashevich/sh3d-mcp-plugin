package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.HomeTexture;

/**
 * Static utility methods for formatting values in command handler responses.
 */
public final class FormatUtil {

    private FormatUtil() {
    }

    /**
     * Rounds a double value to 2 decimal places.
     */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Converts an Integer color value to "#RRGGBB" hex string.
     * Returns null if the input is null.
     */
    public static String colorToHex(Integer color) {
        if (color == null) return null;
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /**
     * Parses a "#RRGGBB" hex string into an Integer color value.
     * Returns null if the format is invalid.
     */
    public static Integer parseHexColor(String hex) {
        if (!hex.matches("^#[0-9A-Fa-f]{6}$")) return null;
        return Integer.parseInt(hex.substring(1), 16);
    }

    /**
     * Returns the name of a HomeTexture, or null if the texture is null.
     */
    public static String textureName(HomeTexture texture) {
        return texture != null ? texture.getName() : null;
    }
}
