# Code Review: PlaceFurnitureHandler

## Summary
The `PlaceFurnitureHandler` implements the `place_furniture` command, allowing users to search for furniture by name and place it into the home model. The implementation relies on `HomeAccessor` for thread safety and `FurnitureCatalog` for searching.

## Findings

### [MAJOR] Error Handling for Missing Parameters
**Location:** `PlaceFurnitureHandler.java`, lines 39-40
**Description:** The handler uses `request.getFloat("x")` and `request.getFloat("y")`. These methods throw `IllegalArgumentException` if the parameters are missing. This exception is caught by the `CommandRegistry` (or `ClientHandler`) and wrapped in a generic "Internal error" response.
**Impact:** The client receives a confusing "Internal error" message for a client-side error (missing parameter), making debugging difficult for API users.
**Recommendation:** Explicitly check for the existence of required parameters (e.g., using `request.getParams().containsKey("x")`) or handle the exception within the handler to return a clear `Response.error("Missing required parameter: x")`.

### [MINOR] Thread Safety of Catalog Search
**Location:** `PlaceFurnitureHandler.java`, method `findInCatalog`
**Description:** The catalog search iterates over `FurnitureCatalog` categories and items on the server thread (non-EDT). While `FurnitureCatalog` is typically static, concurrent modification (e.g., importing a library via GUI while a command is running) could theoretically lead to `ConcurrentModificationException` or inconsistent state.
**Recommendation:** Considering the performance cost of running search on EDT, the current approach is acceptable for most use cases. However, be aware of the potential race condition. Strictly speaking, accessing model objects should be done on EDT or with proper synchronization.

### [MINOR] API Design - Angle Units
**Location:** `PlaceFurnitureHandler.java`
**Description:** The API expects `angle` in degrees and converts it to radians for the model. This is consistent with the requirements.
**Observation:** The default value is `0f`. It is good practice to document what 0 degrees represents (usually East or original model orientation).

## Verification
- **EDT Compliance:** The mutation of the home model (`home.addPieceOfFurniture`) and setting of properties (`setX`, `setY`, `setAngle`) are correctly performed within `accessor.runOnEDT`.
- **Logic:** The "case-insensitive contains" search logic is correctly implemented.
- **Tests:** `PlaceFurnitureHandlerTest` covers success paths, search logic variations, and error conditions. The test `testMissingXThrows` confirms that missing parameters result in an exception, validating the finding above.

## Conclusion
The implementation is solid and meets the core requirements. Addressing the error handling for missing parameters will significantly improve the API's usability.
