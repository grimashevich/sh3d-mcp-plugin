package com.sh3d.mcp.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Конфигурация плагина. Приоритет: System properties > файл > defaults.
 */
public class PluginConfig {

    public static final int DEFAULT_PORT = 9877;
    public static final int DEFAULT_MAX_LINE_LENGTH = 65536;
    public static final int DEFAULT_EDT_TIMEOUT = 10000;
    public static final boolean DEFAULT_AUTO_START = false;

    private final int port;
    private final int maxLineLength;
    private final int edtTimeout;
    private final boolean autoStart;

    private PluginConfig(int port, int maxLineLength, int edtTimeout, boolean autoStart) {
        this.port = port;
        this.maxLineLength = maxLineLength;
        this.edtTimeout = edtTimeout;
        this.autoStart = autoStart;
    }

    /**
     * Загружает конфигурацию: System properties -> sh3d-mcp.properties -> defaults.
     */
    public static PluginConfig load() {
        Properties fileProps = loadPropertiesFile();

        int port = getInt("sh3d.mcp.port", fileProps, DEFAULT_PORT);
        int maxLine = getInt("sh3d.mcp.maxLineLength", fileProps, DEFAULT_MAX_LINE_LENGTH);
        int edtTimeout = getInt("sh3d.mcp.edtTimeout", fileProps, DEFAULT_EDT_TIMEOUT);
        boolean autoStart = getBoolean("sh3d.mcp.autoStart", fileProps, DEFAULT_AUTO_START);

        return new PluginConfig(port, maxLine, edtTimeout, autoStart);
    }

    public int getPort() {
        return port;
    }

    public int getMaxLineLength() {
        return maxLineLength;
    }

    public int getEdtTimeout() {
        return edtTimeout;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        // TODO: определить путь к папке плагинов SH3D в зависимости от ОС
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            Path configPath = Paths.get(appData, "eTeks", "Sweet Home 3D", "plugins", "sh3d-mcp.properties");
            if (Files.exists(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                } catch (IOException e) {
                    // Игнорируем — используем defaults
                }
            }
        }
        return props;
    }

    private static int getInt(String key, Properties fileProps, int defaultValue) {
        String sysVal = System.getProperty(key);
        if (sysVal != null) {
            try {
                return Integer.parseInt(sysVal);
            } catch (NumberFormatException e) {
                // fallthrough
            }
        }
        String fileVal = fileProps.getProperty(key);
        if (fileVal != null) {
            try {
                return Integer.parseInt(fileVal);
            } catch (NumberFormatException e) {
                // fallthrough
            }
        }
        return defaultValue;
    }

    private static boolean getBoolean(String key, Properties fileProps, boolean defaultValue) {
        String sysVal = System.getProperty(key);
        if (sysVal != null) {
            return Boolean.parseBoolean(sysVal);
        }
        String fileVal = fileProps.getProperty(key);
        if (fileVal != null) {
            return Boolean.parseBoolean(fileVal);
        }
        return defaultValue;
    }
}
