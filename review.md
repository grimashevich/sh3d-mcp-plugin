# Code Review: fix/plugin-action-enabled vs main

## CRITICAL
- `src/main/java/com/sh3d/mcp/plugin/ServerToggleAction.java`:
    - Added `setEnabled(true)` to the constructor.
    - **Reasoning**: The `PluginAction` defaults to disabled if `Property.ENABLED` is not set. This prevented the user from interacting with the menu item to start/stop the MCP server, effectively rendering the plugin unusable from the UI. This fix explicitly enables the action.

## MAJOR
- None

## MINOR
- None
