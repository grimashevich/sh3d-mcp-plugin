# Code Review: Cross-Platform Config Path Resolution

## Summary
The changes introduce cross-platform configuration file path resolution by adding a fallback for Linux/macOS using `user.home` when `APPDATA` is not available. This addresses the limitation of the previous implementation which only supported Windows. The changes also enable previously disabled tests.

## Review Findings

### CRITICAL
* **None identified.** The core logic aligns with the project requirements and provided specifications.

### MAJOR
* **Test Coverage Dependency on Environment:**
  The test `testResolveConfigPathUsesUserHomeWhenNoAppData` in `PluginConfigTest.java` relies on the execution environment.
  - If run on Windows (where `APPDATA` is present), it tests the Windows path branch.
  - If run on Linux/macOS (where `APPDATA` is absent), it tests the fallback branch.
  - It does not verify the fallback logic on Windows (simulating a missing `APPDATA`) or the precedence logic on Linux (if `APPDATA` were set).
  - **Recommendation:** Refactor `resolveConfigPath` to accept an environment/property provider (e.g., using a functional interface or a protected method that can be overridden in tests) to allow deterministic testing of all branches regardless of the OS.

### MINOR
* **Heuristic OS Detection:**
  The implementation relies on the presence of the `APPDATA` environment variable to detect Windows. While `APPDATA` is standard on Windows, this is a heuristic. A more robust approach would be to explicitly check `System.getProperty("os.name")`. However, for the scope of this plugin, the current approach is acceptable as long as the fallback behavior is desired.
* **MacOS Path Convention:**
  The fallback uses `~/.eteks/sweethome3d/plugins/` for all non-Windows systems. While this matches the Linux convention and the project's spec (`sh3d-plugin-spec.md`), standard macOS applications typically use `~/Library/Application Support/eTeks/Sweet Home 3D/plugins`.
  - **Action:** Verify if Sweet Home 3D on macOS uses the `~/.eteks` convention or the standard macOS path. If it uses the standard path, the current implementation might fail to find the config file on macOS.
* **Empty String Handling:**
  The code checks `if (appData != null)` and `if (home != null)`. If these environment variables are set to an empty string `""`, `Paths.get("")` resolves to the current working directory, which might lead to unexpected behavior (e.g., looking for `eTeks/...` in the current directory).
  - **Recommendation:** Add a check for `!appData.isEmpty()` and `!home.isEmpty()`.

### Notes
* **Thread Safety:** The implementation appears thread-safe. `PluginConfig` is immutable, and `resolveConfigPath` uses thread-safe `System` methods.
* **Visibility:** `resolveConfigPath` is package-private, which facilitates unit testing (within the same package), although the current tests do not fully exploit this for branch coverage.
* **Correctness:** The logic correctly implements the path structure differences: CamelCase `eTeks` for Windows and lowercase `.eteks` for the fallback.
