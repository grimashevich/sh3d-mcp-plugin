package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandRegistryTest {

    private CommandRegistry registry;
    private AutoCloseable closeable;

    @Mock
    private HomeAccessor mockAccessor;

    @Mock
    private CommandHandler mockHandler;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        registry = new CommandRegistry();
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    @DisplayName("Should successfully register and dispatch a valid command")
    void testRegisterAndDispatch() {
        // Arrange
        String action = "testAction";
        registry.register(action, mockHandler);

        Map<String, Object> params = Collections.singletonMap("key", "value");
        Request request = new Request(action, params);
        Response expectedResponse = Response.ok(Collections.emptyMap());

        when(mockHandler.execute(request, mockAccessor)).thenReturn(expectedResponse);

        // Act
        Response response = registry.dispatch(request, mockAccessor);

        // Assert
        assertSame(expectedResponse, response);
        verify(mockHandler).execute(request, mockAccessor);
    }

    @Test
    @DisplayName("Should return error response for unknown action")
    void testDispatchUnknownAction() {
        // Arrange
        String action = "unknownAction";
        Request request = new Request(action, Collections.emptyMap());

        // Act
        Response response = registry.dispatch(request, mockAccessor);

        // Assert
        assertTrue(response.isError());
        assertEquals("Unknown action: " + action, response.getMessage());
        verifyNoInteractions(mockHandler);
    }

    @Test
    @DisplayName("Should handle CommandException thrown by handler")
    void testDispatchCommandException() {
        // Arrange
        String action = "failingAction";
        registry.register(action, mockHandler);
        Request request = new Request(action, Collections.emptyMap());
        String errorMessage = "Validation failed";

        when(mockHandler.execute(request, mockAccessor)).thenThrow(new CommandException(errorMessage));

        // Act
        Response response = registry.dispatch(request, mockAccessor);

        // Assert
        assertTrue(response.isError());
        assertEquals(errorMessage, response.getMessage());
    }

    @Test
    @DisplayName("Should handle RuntimeException thrown by handler as Internal Error")
    void testDispatchRuntimeException() {
        // Arrange
        String action = "brokenAction";
        registry.register(action, mockHandler);
        Request request = new Request(action, Collections.emptyMap());
        String errorMessage = "Unexpected error";

        when(mockHandler.execute(request, mockAccessor)).thenThrow(new RuntimeException(errorMessage));

        // Act
        Response response = registry.dispatch(request, mockAccessor);

        // Assert
        assertTrue(response.isError());
        assertTrue(response.getMessage().startsWith("Internal error: "));
        assertTrue(response.getMessage().contains(errorMessage));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when registering null action")
    void testRegisterNullAction() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(null, mockHandler));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when registering empty action")
    void testRegisterEmptyAction() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("", mockHandler));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when registering null handler")
    void testRegisterNullHandler() {
        assertThrows(IllegalArgumentException.class, () -> registry.register("action", null));
    }

    @Test
    @DisplayName("Should correctly check if handler exists")
    void testHasHandler() {
        String action = "existingAction";
        registry.register(action, mockHandler);

        assertTrue(registry.hasHandler(action));
        assertFalse(registry.hasHandler("nonExistingAction"));
    }

    @Test
    @DisplayName("Should dispatch to correct handler when multiple are registered")
    void testDispatchMultipleHandlers() {
        // Arrange
        CommandHandler handler1 = mock(CommandHandler.class);
        CommandHandler handler2 = mock(CommandHandler.class);

        registry.register("action1", handler1);
        registry.register("action2", handler2);

        Request request1 = new Request("action1", Collections.emptyMap());
        Request request2 = new Request("action2", Collections.emptyMap());

        Response response1 = Response.ok(Collections.singletonMap("id", 1));
        Response response2 = Response.ok(Collections.singletonMap("id", 2));

        when(handler1.execute(request1, mockAccessor)).thenReturn(response1);
        when(handler2.execute(request2, mockAccessor)).thenReturn(response2);

        // Act
        Response result1 = registry.dispatch(request1, mockAccessor);
        Response result2 = registry.dispatch(request2, mockAccessor);

        // Assert
        assertSame(response1, result1);
        assertSame(response2, result2);
        verify(handler1).execute(request1, mockAccessor);
        verify(handler2).execute(request2, mockAccessor);
    }
}
