package com.sh3d.mcp.config;

import org.junit.jupiter.api.Test;

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
        // TODO: implement
        // System.setProperty("sh3d.mcp.port", "9999");
        // try {
        //     PluginConfig config = PluginConfig.load();
        //     assertEquals(9999, config.getPort());
        // } finally {
        //     System.clearProperty("sh3d.mcp.port");
        // }
    }
}
