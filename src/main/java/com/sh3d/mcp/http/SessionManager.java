package com.sh3d.mcp.http;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление MCP-сессиями.
 */
public class SessionManager {

    private static final long SESSION_TTL_MS = 30 * 60 * 1000; // 30 минут

    private final ConcurrentHashMap<String, McpSession> sessions = new ConcurrentHashMap<>();

    /**
     * Создаёт новую сессию с уникальным ID.
     */
    public McpSession createSession(String protocolVersion) {
        cleanupExpired();
        String id = UUID.randomUUID().toString();
        McpSession session = new McpSession(id, protocolVersion);
        sessions.put(id, session);
        return session;
    }

    /**
     * Создаёт сессию с указанным ID (для авто-пересоздания expired-сессий).
     * Если сессия с таким ID уже существует, она заменяется.
     */
    public McpSession createSessionWithId(String sessionId, String protocolVersion) {
        cleanupExpired();
        McpSession session = new McpSession(sessionId, protocolVersion);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * Возвращает сессию по ID или null, если не найдена / истекла.
     */
    public McpSession getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        McpSession session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }
        if (isExpired(session)) {
            sessions.remove(sessionId);
            return null;
        }
        session.touch();
        return session;
    }

    /**
     * Удаляет сессию.
     */
    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    /**
     * Количество активных сессий.
     */
    public int size() {
        return sessions.size();
    }

    private boolean isExpired(McpSession session) {
        return System.currentTimeMillis() - session.getLastAccessedAt() > SESSION_TTL_MS;
    }

    private void cleanupExpired() {
        Iterator<Map.Entry<String, McpSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            if (isExpired(it.next().getValue())) {
                it.remove();
            }
        }
    }
}
