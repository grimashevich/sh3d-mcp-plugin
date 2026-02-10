# Review of fix/plugin-menu-visibility

## Summary
The changes introduce a property setting in `ServerToggleAction` to ensure the "MCP Server" menu item appears correctly in the Sweet Home 3D "Tools" menu. Additionally, a known issue regarding Mockito compatibility with JDK 24 has been documented in `TO-DOS.md`.

## Review

### CRITICAL
*   None.

### MAJOR
*   None.

### MINOR
*   **Menu Visibility Fix**: Verified that `src/main/java/com/sh3d/mcp/plugin/ServerToggleAction.java` now includes `putPropertyValue(Property.MENU, "Tools");`. This correctly instructs Sweet Home 3D to place the plugin action in the "Tools" menu. Without this, the action would not appear in the menu structure.
*   **Documentation Update**: Validated that `TO-DOS.md` includes a new entry tracking the "Mockito + JDK 24" compatibility issue, affecting 41 tests.
*   **Regression Testing**:
    *   Executed `mvn test` on OpenJDK 21 (current environment).
    *   Result: **All 81 tests passed** (0 failures, 2 skipped).
    *   Conclusion: No regressions introduced for supported JDK versions (up to JDK 21). The JDK 24 issue is tracked but not reproducible in this environment.

## Conclusion
The changes are correct and verify successfully against the current environment. The fix addresses the missing menu item issue as intended.
