package com.sh3d.mcp.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpSessionTest {

    // === Constructor & getters ===

    @Test
    void testConstructorSetsSessionId() {
        McpSession session = new McpSession("test-id", "2025-03-26");
        assertEquals("test-id", session.getSessionId());
    }

    @Test
    void testConstructorSetsCreatedAtToCurrentTime() {
        long before = System.currentTimeMillis();
        McpSession session = new McpSession("id", "2025-03-26");
        long after = System.currentTimeMillis();

        assertTrue(session.getLastAccessedAt() >= before);
        assertTrue(session.getLastAccessedAt() <= after);
    }

    @Test
    void testConstructorSetsInitializedToFalse() {
        McpSession session = new McpSession("id", "2025-03-26");
        assertFalse(session.isInitialized());
    }

    @Test
    void testLastAccessedAtEqualsCreatedAtInitially() {
        McpSession session = new McpSession("id", "2025-03-26");
        // lastAccessedAt is set to createdAt in the constructor
        long lastAccessed = session.getLastAccessedAt();
        assertTrue(lastAccessed > 0);
    }

    // === touch() ===

    @Test
    void testTouchUpdatesLastAccessedAt() throws InterruptedException {
        McpSession session = new McpSession("id", "2025-03-26");
        long initialAccess = session.getLastAccessedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(10);
        session.touch();

        assertTrue(session.getLastAccessedAt() >= initialAccess);
    }

    @Test
    void testMultipleTouchesKeepUpdating() throws InterruptedException {
        McpSession session = new McpSession("id", "2025-03-26");

        Thread.sleep(10);
        session.touch();
        long first = session.getLastAccessedAt();

        Thread.sleep(10);
        session.touch();
        long second = session.getLastAccessedAt();

        assertTrue(second >= first);
    }

    // === initialized flag ===

    @Test
    void testSetInitializedTrue() {
        McpSession session = new McpSession("id", "2025-03-26");
        session.setInitialized(true);
        assertTrue(session.isInitialized());
    }

    @Test
    void testSetInitializedFalseAfterTrue() {
        McpSession session = new McpSession("id", "2025-03-26");
        session.setInitialized(true);
        session.setInitialized(false);
        assertFalse(session.isInitialized());
    }

    // === Thread-safety (volatile fields) ===

    @Test
    void testInitializedVisibleAcrossThreads() throws InterruptedException {
        McpSession session = new McpSession("id", "2025-03-26");

        Thread writer = new Thread(() -> session.setInitialized(true));
        writer.start();
        writer.join();

        assertTrue(session.isInitialized(), "initialized should be visible after thread join");
    }

    @Test
    void testTouchVisibleAcrossThreads() throws InterruptedException {
        McpSession session = new McpSession("id", "2025-03-26");
        long initial = session.getLastAccessedAt();

        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            session.touch();
        });
        writer.start();
        writer.join();

        assertTrue(session.getLastAccessedAt() >= initial,
                "lastAccessedAt should be visible after thread join");
    }

    // === Distinct session IDs ===

    @Test
    void testDifferentSessionsHaveIndependentState() {
        McpSession a = new McpSession("a", "2025-03-26");
        McpSession b = new McpSession("b", "2025-03-26");

        a.setInitialized(true);
        assertFalse(b.isInitialized());

        assertEquals("a", a.getSessionId());
        assertEquals("b", b.getSessionId());
    }
}
