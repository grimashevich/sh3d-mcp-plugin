# Code Review Findings: Feature Branch vs Main

## CRITICAL ISSUES

### 1. Resource Leak in Logging (FileHandler)
In `SH3DMcpPlugin.java`, the method `setupFileLogging(PluginConfig cfg)` creates a new `java.util.logging.FileHandler` and adds it to the `com.sh3d.mcp` logger. However, this handler is **never closed or removed**.

- **Impact:** Every time the plugin is initialized (e.g., if `getActions()` is called multiple times or the plugin is reloaded within the same JVM session), a new `FileHandler` is added. This leads to:
    - **Duplicate log entries:** Each log message will be written N times to the file.
    - **File Locking:** The file lock held by the `FileHandler` is not released until the JVM exits, potentially preventing log rotation or file deletion.
- **Location:** `src/main/java/com/sh3d/mcp/plugin/SH3DMcpPlugin.java`
- **Recommendation:** Implement a mechanism to check if a handler is already attached, or keep a reference to the handler and close/remove it in the `destroy()` method of the plugin.

## MAJOR ISSUES

### 1. Uncaught Exception for Invalid Log Level
The method `setupFileLogging` calls `Level.parse(cfg.getLogLevel())`. If the configuration (file or system property) contains an invalid log level string, `Level.parse` throws an unchecked `IllegalArgumentException`. The surrounding `try-catch` block only catches `IOException`.

- **Impact:** An invalid log level configuration will cause the plugin initialization to crash with an unhandled exception, preventing the plugin from loading.
- **Location:** `src/main/java/com/sh3d/mcp/plugin/SH3DMcpPlugin.java`
- **Recommendation:** Catch `IllegalArgumentException` explicitly and fall back to a default log level (e.g., INFO) with a warning.

### 2. Environment-Dependent Test Coverage
The `PluginConfigTest` class relies on `PluginConfig.resolvePluginDir()`, which directly accesses `System.getenv("APPDATA")` and `System.getProperty("os.name")`.

- **Impact:** The tests behave differently depending on the OS they are run on. Specifically, the Windows path resolution logic is not tested on Linux/macOS, and vice versa. This leads to inconsistent test coverage across environments.
- **Location:** `src/test/java/com/sh3d/mcp/config/PluginConfigTest.java`
- **Recommendation:** Refactor `PluginConfig` to accept an environment provider/helper interface that can be mocked in tests to simulate different OS environments and environment variables.

## MINOR ISSUES

### 1. Optimistic UI Update in ServerToggleAction
The fix for the race condition in `ServerToggleAction.execute()` correctly solves the issue where the menu text didn't update during the `STARTING` phase. It does this by toggling the text based on the *intent* (captured state) rather than the *result*.

- **Impact:** If `tcpServer.start()` fails (e.g., port already in use), the menu item will incorrectly display "Stop" (implying the server is running), even though the server remains stopped.
- **Location:** `src/main/java/com/sh3d/mcp/plugin/ServerToggleAction.java`
- **Recommendation:** Consider adding a callback or listener to update the UI only when the server state actually changes to `RUNNING` or `STOPPED`, or revert the UI change if start fails.

### 2. Hardcoded Path Separators
The `resolvePluginDir` method constructs paths using `Paths.get` with hardcoded strings like `"eTeks"`, `"Sweet Home 3D"`. While generally correct for standard installations, the case sensitivity on Linux (`.eteks` vs `eTeks` in `APPDATA`) is handled by specific branches but relies on strict directory naming conventions.

- **Impact:** Low risk, but potential for issues if directory casing varies unexpectedly on some systems.
- **Location:** `src/main/java/com/sh3d/mcp/config/PluginConfig.java`

### 3. Thread Safety in TcpServer State
In `TcpServer.stop()`, the code attempts to set the state to `STOPPING` even if the current state is potentially `STOPPED` (if the CAS fails).

- **Impact:** Benign. The state eventually settles to `STOPPED`.
- **Location:** `src/main/java/com/sh3d/mcp/server/TcpServer.java`
