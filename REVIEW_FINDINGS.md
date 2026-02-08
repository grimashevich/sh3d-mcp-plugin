# Code Review Findings

## Critical

### 1. Missing Core Functionality
**File:** `src/main/java/com/sh3d/mcp/command/CreateWallsHandler.java` (lines 24-42), `PlaceFurnitureHandler.java` (lines 24-40), `GetStateHandler.java` (lines 22-42), `ListFurnitureCatalogHandler.java` (lines 21-40)
**Description:** The command handlers for `create_walls`, `place_furniture`, `get_state`, and `list_furniture_catalog` are stubbed out and throw `UnsupportedOperationException`. The plugin only supports `ping`.
**Suggested Fix:** Implement the logic for these handlers as described in the `TODO` comments and `ARCHITECTURE.md`. Ensure all model mutations use `HomeAccessor.runOnEDT()`.

### 2. Missing/Fake Tests
**File:** `src/test/java/com/sh3d/mcp/command/CommandRegistryTest.java` (lines 21-35)
**Description:** The tests for command dispatching (`testRegisterAndDispatch`, `testDispatchUnknownAction`) have empty bodies or are commented out. There are no unit tests for the unimplemented handlers.
**Suggested Fix:** Implement unit tests for `CommandRegistry` and for each command handler once they are implemented.

## Major

### 3. Package Name Violation (ADR-006)
**File:** All source files, `pom.xml`
**Description:** The project uses the package `com.sh3d.mcp` (e.g., `SH3DMcpPlugin.java` line 1), but `ARCHITECTURE.md` (ADR-006) explicitly specifies `com.eteks.sweethome3d.mcp` to indicate it is a plugin for SH3D.
**Suggested Fix:** Rename packages to `com.eteks.sweethome3d.mcp` to match the architecture decision, or update ADR if the decision has changed (unlikely given the reasoning).

### 4. ServerToggleAction State De-sync
**File:** `src/main/java/com/sh3d/mcp/plugin/ServerToggleAction.java` (line 20)
**Description:** `ServerToggleAction` initializes `running = false` in the constructor. However, `SH3DMcpPlugin` might start the server automatically if `autoStart` is true. This leads to the menu showing "Start" when the server is already running.
**Suggested Fix:** Initialize `running` state in `ServerToggleAction` by checking `tcpServer.isRunning()`. Also, consider observing the server state if it changes unexpectedly (e.g., error).

## Minor

### 5. TcpServer Start Race Condition & Error Logging
**File:** `src/main/java/com/sh3d/mcp/server/TcpServer.java` (lines 42, 122-130)
**Description:**
1. `start()` creates a thread which sets state to `RUNNING`. There is a window where `start()` returns but `isRunning()` is false.
2. If `ServerSocket` constructor fails (e.g. port in use), the `catch` block checks `if (state.get() == ServerState.RUNNING)` which might be false (it is `STARTING` or `STOPPED` depending on race), potentially swallowing the error log.
**Suggested Fix:**
1. Set state to `STARTING` in `start()`, then to `RUNNING` in the thread (as done).
2. Improve error logging in `acceptLoop`: log the exception if state is `STARTING` or `RUNNING`.

### 6. HomeAccessor Missing Dependency
**File:** `src/main/java/com/sh3d/mcp/bridge/HomeAccessor.java` (lines 16-20)
**Description:** `ARCHITECTURE.md` specifies `UndoableEditSupport` in `HomeAccessor`, but it is missing in the implementation.
**Suggested Fix:** Add `UndoableEditSupport` to `HomeAccessor` constructor and fields, even if not used yet, to match the architecture and prepare for future undo support.

### 7. JSON Parser Robustness
**File:** `src/main/java/com/sh3d/mcp/protocol/JsonProtocol.java` (lines 156-340)
**Description:** The manual JSON parser is minimal. While sufficient for the spec, ensure it handles edge cases like empty objects/arrays and specific escape sequences correctly (tests seem to cover some).
**Suggested Fix:** Ensure comprehensive tests for the JSON parser, specifically for edge cases.

## Review Summary
The project scaffolding is good and follows the 5-layer architecture. However, the core business logic is completely missing, and the package structure deviates from the design document. The tests are currently passing only because they cover the infrastructure and `ping` command, while ignoring the missing features.
