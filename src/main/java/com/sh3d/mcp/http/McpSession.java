package com.sh3d.mcp.http;

/**
 * MCP-сессия. Создаётся при initialize, хранится до DELETE или timeout.
 */
public class McpSession {

    private final String sessionId;
    private final String protocolVersion;
    private final long createdAt;
    private volatile long lastAccessedAt;
    private volatile boolean initialized;

    McpSession(String sessionId, String protocolVersion) {
        this.sessionId = sessionId;
        this.protocolVersion = protocolVersion;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
        this.initialized = false;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}
