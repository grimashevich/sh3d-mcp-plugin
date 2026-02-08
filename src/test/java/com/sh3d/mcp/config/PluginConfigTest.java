package com.sh3d.mcp.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {

    @Test
    void testDefaultValues() {
        PluginConfig config = PluginConfig.load();
        assertEquals(PluginConfig.DEFAULT_PORT, config.getPort());
        assertEquals(PluginConfig.DEFAULT_MAX_LINE_LENGTH, config.getMaxLineLength());
        assertEquals(PluginConfig.DEFAULT_EDT_TIMEOUT, config.getEdtTimeout());
        assertFalse(config.isAutoStart());
    }

    @Test
    void testSystemPropertyOverride() {
        System.setProperty("sh3d.mcp.port", "9999");
        try {
            PluginConfig config = PluginConfig.load();
            assertEquals(9999, config.getPort());
        } finally {
            System.clearProperty("sh3d.mcp.port");
        }
    }

    @Test
    void testResolveConfigPathReturnsValidPath() {
        Path path = PluginConfig.resolveConfigPath();
        assertNotNull(path);
        String pathStr = path.toString();
        // На Windows будет APPDATA путь, на Linux/macOS — user.home путь
        assertTrue(pathStr.endsWith("sh3d-mcp.properties"),
                "Config path should end with sh3d-mcp.properties, got: " + pathStr);
    }
}
