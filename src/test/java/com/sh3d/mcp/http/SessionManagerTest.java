package com.sh3d.mcp.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    // === createSession ===

    @Test
    void testCreateSessionReturnsValidSession() {
        McpSession session = sessionManager.createSession("2025-03-26");

        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertFalse(session.getSessionId().isEmpty());
        assertEquals("2025-03-26", session.getProtocolVersion());
    }

    @Test
    void testCreateSessionGeneratesUniqueIds() {
        McpSession session1 = sessionManager.createSession("2025-03-26");
        McpSession session2 = sessionManager.createSession("2025-03-26");

        assertNotEquals(session1.getSessionId(), session2.getSessionId());
    }

    @Test
    void testCreateSessionIdIsUuidFormat() {
        McpSession session = sessionManager.createSession("2025-03-26");
        String id = session.getSessionId();
        // UUID format: 8-4-4-4-12 hex chars
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Session ID should be UUID format, got: " + id);
    }

    @Test
    void testCreateSessionIncreasesSize() {
        assertEquals(0, sessionManager.size());
        sessionManager.createSession("2025-03-26");
        assertEquals(1, sessionManager.size());
        sessionManager.createSession("2025-03-26");
        assertEquals(2, sessionManager.size());
    }

    // === getSession ===

    @Test
    void testGetSessionReturnsExistingSession() {
        McpSession created = sessionManager.createSession("2025-03-26");
        McpSession found = sessionManager.getSession(created.getSessionId());

        assertNotNull(found);
        assertEquals(created.getSessionId(), found.getSessionId());
        assertEquals(created.getProtocolVersion(), found.getProtocolVersion());
    }

    @Test
    void testGetSessionReturnsNullForUnknownId() {
        assertNull(sessionManager.getSession("nonexistent-id"));
    }

    @Test
    void testGetSessionReturnsNullForNullId() {
        assertNull(sessionManager.getSession(null));
    }

    @Test
    void testGetSessionTouchesSession() throws Exception {
        McpSession session = sessionManager.createSession("2025-03-26");
        long initialAccess = session.getLastAccessedAt();

        // Small delay to ensure time advances
        Thread.sleep(15);

        McpSession found = sessionManager.getSession(session.getSessionId());
        assertNotNull(found);
        assertTrue(found.getLastAccessedAt() >= initialAccess,
                "getSession should touch the session, updating lastAccessedAt");
    }

    // === removeSession ===

    @Test
    void testRemoveSessionRemovesExistingSession() {
        McpSession session = sessionManager.createSession("2025-03-26");
        assertEquals(1, sessionManager.size());

        sessionManager.removeSession(session.getSessionId());
        assertEquals(0, sessionManager.size());
        assertNull(sessionManager.getSession(session.getSessionId()));
    }

    @Test
    void testRemoveSessionIgnoresUnknownId() {
        sessionManager.createSession("2025-03-26");
        assertEquals(1, sessionManager.size());

        // Should not throw or affect existing sessions
        sessionManager.removeSession("nonexistent-id");
        assertEquals(1, sessionManager.size());
    }

    @Test
    void testRemoveSessionIgnoresNullId() {
        sessionManager.createSession("2025-03-26");
        assertEquals(1, sessionManager.size());

        // Should not throw
        sessionManager.removeSession(null);
        assertEquals(1, sessionManager.size());
    }

    @Test
    void testRemoveSessionOnlyRemovesSpecified() {
        McpSession session1 = sessionManager.createSession("2025-03-26");
        McpSession session2 = sessionManager.createSession("2025-03-26");
        assertEquals(2, sessionManager.size());

        sessionManager.removeSession(session1.getSessionId());
        assertEquals(1, sessionManager.size());
        assertNull(sessionManager.getSession(session1.getSessionId()));
        assertNotNull(sessionManager.getSession(session2.getSessionId()));
    }

    // === size ===

    @Test
    void testSizeEmptyManager() {
        assertEquals(0, sessionManager.size());
    }

    @Test
    void testSizeAfterCreateAndRemove() {
        McpSession s1 = sessionManager.createSession("2025-03-26");
        McpSession s2 = sessionManager.createSession("2025-03-26");
        McpSession s3 = sessionManager.createSession("2025-03-26");
        assertEquals(3, sessionManager.size());

        sessionManager.removeSession(s2.getSessionId());
        assertEquals(2, sessionManager.size());

        sessionManager.removeSession(s1.getSessionId());
        sessionManager.removeSession(s3.getSessionId());
        assertEquals(0, sessionManager.size());
    }

    // === Session expiration ===

    @Test
    void testExpiredSessionIsRemovedOnGet() throws Exception {
        McpSession session = sessionManager.createSession("2025-03-26");
        String sessionId = session.getSessionId();

        // Simulate expiration by setting lastAccessedAt far in the past via reflection
        setLastAccessedAt(session, System.currentTimeMillis() - 31 * 60 * 1000); // 31 minutes ago

        McpSession found = sessionManager.getSession(sessionId);
        assertNull(found, "Expired session should not be returned by getSession");
    }

    @Test
    void testExpiredSessionIsCleanedUpOnCreate() throws Exception {
        McpSession old = sessionManager.createSession("2025-03-26");
        assertEquals(1, sessionManager.size());

        // Expire the old session
        setLastAccessedAt(old, System.currentTimeMillis() - 31 * 60 * 1000);

        // Creating a new session triggers cleanupExpired()
        sessionManager.createSession("2025-03-26");

        // Old expired session should be removed, only the new one remains
        assertEquals(1, sessionManager.size());
        assertNull(sessionManager.getSession(old.getSessionId()));
    }

    @Test
    void testNonExpiredSessionSurvivesCleanup() throws Exception {
        McpSession fresh = sessionManager.createSession("2025-03-26");
        McpSession old = sessionManager.createSession("2025-03-26");

        // Expire only the old session
        setLastAccessedAt(old, System.currentTimeMillis() - 31 * 60 * 1000);

        // Trigger cleanup
        sessionManager.createSession("2025-03-26");

        // Fresh session still accessible, old is gone
        assertNotNull(sessionManager.getSession(fresh.getSessionId()));
        assertNull(sessionManager.getSession(old.getSessionId()));
    }

    /**
     * Sets lastAccessedAt via reflection to simulate time passage.
     */
    private void setLastAccessedAt(McpSession session, long timeMillis) throws Exception {
        Field field = McpSession.class.getDeclaredField("lastAccessedAt");
        field.setAccessible(true);
        field.set(session, timeMillis);
    }
}
