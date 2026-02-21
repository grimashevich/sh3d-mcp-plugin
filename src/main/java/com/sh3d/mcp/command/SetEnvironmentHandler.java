package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomeTexture;
import com.eteks.sweethome3d.model.TexturesCatalog;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import static com.sh3d.mcp.command.FormatUtil.colorToHex;
import static com.sh3d.mcp.command.FormatUtil.round2;
import static com.sh3d.mcp.command.SchemaUtil.enumProp;
import static com.sh3d.mcp.command.SchemaUtil.nullableProp;
import static com.sh3d.mcp.command.SchemaUtil.prop;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "set_environment".
 * Настраивает окружение 3D-сцены: земля, небо, освещение, прозрачность стен, режим рисования.
 *
 * <pre>
 * Все параметры опциональные — изменяются только указанные.
 * Цвета: '#RRGGBB' (не nullable, т.к. API принимает int).
 * Текстуры: имя из каталога или null для удаления.
 * EDT: мутации через runOnEDT().
 * </pre>
 */
public class SetEnvironmentHandler implements CommandHandler, CommandDescriptor {

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "groundColor", "groundTexture", "skyColor", "skyTexture",
            "lightColor", "ceilingLightColor",
            "wallsAlpha", "drawingMode", "allLevelsVisible"
    );

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        Map<String, Object> params = request.getParams();

        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. Supported: "
                    + "groundColor, groundTexture, skyColor, skyTexture, "
                    + "lightColor, ceilingLightColor, wallsAlpha, drawingMode, allLevelsVisible");
        }

        // --- Parse colors outside EDT ---
        ColorParser.ColorResult groundColorResult = ColorParser.parseRequired(params, "groundColor");
        if (groundColorResult != null && groundColorResult.hasError()) {
            return Response.error(groundColorResult.error);
        }

        ColorParser.ColorResult skyColorResult = ColorParser.parseRequired(params, "skyColor");
        if (skyColorResult != null && skyColorResult.hasError()) {
            return Response.error(skyColorResult.error);
        }

        ColorParser.ColorResult lightColorResult = ColorParser.parseRequired(params, "lightColor");
        if (lightColorResult != null && lightColorResult.hasError()) {
            return Response.error(lightColorResult.error);
        }

        ColorParser.ColorResult ceilingLightColorResult = ColorParser.parseRequired(params, "ceilingLightColor");
        if (ceilingLightColorResult != null && ceilingLightColorResult.hasError()) {
            return Response.error(ceilingLightColorResult.error);
        }

        // --- Parse wallsAlpha ---
        Float wallsAlpha = null;
        boolean hasWallsAlpha = params.containsKey("wallsAlpha");
        if (hasWallsAlpha) {
            wallsAlpha = request.getFloat("wallsAlpha");
            if (wallsAlpha < 0f || wallsAlpha > 1f) {
                return Response.error("wallsAlpha must be between 0.0 and 1.0, got " + wallsAlpha);
            }
        }

        // --- Parse drawingMode ---
        HomeEnvironment.DrawingMode drawingMode = null;
        boolean hasDrawingMode = params.containsKey("drawingMode");
        if (hasDrawingMode) {
            String modeStr = request.getString("drawingMode");
            if (modeStr == null) {
                return Response.error("drawingMode cannot be null. Expected: FILL, OUTLINE, FILL_AND_OUTLINE");
            }
            try {
                drawingMode = HomeEnvironment.DrawingMode.valueOf(modeStr);
            } catch (IllegalArgumentException e) {
                return Response.error("Invalid drawingMode: '" + modeStr
                        + "'. Expected: FILL, OUTLINE, FILL_AND_OUTLINE");
            }
        }

        // --- Parse allLevelsVisible ---
        Boolean allLevelsVisible = request.getBoolean("allLevelsVisible");
        boolean hasAllLevelsVisible = params.containsKey("allLevelsVisible");

        // --- Find textures in catalog (outside EDT) ---
        HomeTexture groundTexture = null;
        boolean hasGroundTexture = params.containsKey("groundTexture");
        boolean clearGroundTexture = false;
        if (hasGroundTexture) {
            String texName = request.getString("groundTexture");
            if (texName == null) {
                clearGroundTexture = true;
            } else {
                if (accessor.getUserPreferences() == null) {
                    return Response.error("Texture catalog is not available");
                }
                TexturesCatalog catalog = accessor.getTexturesCatalog();
                String category = request.getString("groundTextureCategory");
                CatalogSearchUtil.TextureSearchResult texResult =
                        CatalogSearchUtil.findTexture(catalog, texName, category);
                if (!texResult.isFound()) {
                    return Response.error("Ground texture not found: '" + texName + "'"
                            + (category != null ? " in category '" + category + "'" : "")
                            + ". Use list_textures_catalog to browse available textures");
                }
                groundTexture = new HomeTexture(texResult.getFound());
            }
        }

        HomeTexture skyTexture = null;
        boolean hasSkyTexture = params.containsKey("skyTexture");
        boolean clearSkyTexture = false;
        if (hasSkyTexture) {
            String texName = request.getString("skyTexture");
            if (texName == null) {
                clearSkyTexture = true;
            } else {
                if (accessor.getUserPreferences() == null) {
                    return Response.error("Texture catalog is not available");
                }
                TexturesCatalog catalog = accessor.getTexturesCatalog();
                String category = request.getString("skyTextureCategory");
                CatalogSearchUtil.TextureSearchResult texResult =
                        CatalogSearchUtil.findTexture(catalog, texName, category);
                if (!texResult.isFound()) {
                    return Response.error("Sky texture not found: '" + texName + "'"
                            + (category != null ? " in category '" + category + "'" : "")
                            + ". Use list_textures_catalog to browse available textures");
                }
                skyTexture = new HomeTexture(texResult.getFound());
            }
        }

        // --- Capture for lambda ---
        final boolean hasGroundColor = groundColorResult != null;
        final int finalGroundColor = hasGroundColor ? groundColorResult.value : 0;
        final boolean hasSkyColor = skyColorResult != null;
        final int finalSkyColor = hasSkyColor ? skyColorResult.value : 0;
        final boolean hasLightColor = lightColorResult != null;
        final int finalLightColor = hasLightColor ? lightColorResult.value : 0;
        final boolean hasCeilingLightColor = ceilingLightColorResult != null;
        final int finalCeilingLightColor = hasCeilingLightColor ? ceilingLightColorResult.value : 0;
        final float finalWallsAlpha = hasWallsAlpha ? wallsAlpha : 0f;
        final HomeEnvironment.DrawingMode finalDrawingMode = drawingMode;
        final boolean finalAllLevelsVisible = allLevelsVisible != null && allLevelsVisible;
        final HomeTexture finalGroundTexture = groundTexture;
        final boolean doClearGroundTexture = clearGroundTexture;
        final HomeTexture finalSkyTexture = skyTexture;
        final boolean doClearSkyTexture = clearSkyTexture;

        // --- EDT mutations ---
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            HomeEnvironment env = home.getEnvironment();

            if (hasGroundColor) {
                env.setGroundColor(finalGroundColor);
            }
            if (hasGroundTexture) {
                env.setGroundTexture(doClearGroundTexture ? null : finalGroundTexture);
            }
            if (hasSkyColor) {
                env.setSkyColor(finalSkyColor);
            }
            if (hasSkyTexture) {
                env.setSkyTexture(doClearSkyTexture ? null : finalSkyTexture);
            }
            if (hasLightColor) {
                env.setLightColor(finalLightColor);
            }
            if (hasCeilingLightColor) {
                env.setCeillingLightColor(finalCeilingLightColor);
            }
            if (hasWallsAlpha) {
                env.setWallsAlpha(finalWallsAlpha);
            }
            if (hasDrawingMode) {
                env.setDrawingMode(finalDrawingMode);
            }
            if (hasAllLevelsVisible) {
                env.setAllLevelsVisible(finalAllLevelsVisible);
            }

            return buildResponse(env);
        });

        return Response.ok(data);
    }

    private static Map<String, Object> buildResponse(HomeEnvironment env) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groundColor", colorToHex(env.getGroundColor()));
        result.put("groundTexture", textureName(env.getGroundTexture()));
        result.put("skyColor", colorToHex(env.getSkyColor()));
        result.put("skyTexture", textureName(env.getSkyTexture()));
        result.put("lightColor", colorToHex(env.getLightColor()));
        result.put("ceilingLightColor", colorToHex(env.getCeillingLightColor()));
        result.put("wallsAlpha", round2(env.getWallsAlpha()));
        result.put("drawingMode", env.getDrawingMode().name());
        result.put("allLevelsVisible", env.isAllLevelsVisible());
        return result;
    }

    // --- Utilities ---

    private static String textureName(HomeTexture texture) {
        return texture != null ? texture.getName() : null;
    }

    // --- Descriptor ---

    @Override
    public String getDescription() {
        return "Configures the 3D scene environment: ground/sky colors and textures, "
                + "lighting, wall transparency, and drawing mode. All parameters are optional — "
                + "only provided settings are changed. Use get_state to see current environment values. "
                + "Ground and sky can have either a solid color or a texture from list_textures_catalog "
                + "(texture overrides color in 3D view). "
                + "Set groundTexture/skyTexture to null to remove the texture and revert to solid color. "
                + "wallsAlpha controls wall transparency in 3D: 0.0 = fully opaque (default), "
                + "1.0 = fully transparent (useful for seeing inside rooms). "
                + "Default ground color is '#D0CC9B' (beige), default sky is '#CCE4FC' (light blue).";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("groundColor", prop("string",
                "Ground color as '#RRGGBB' (default '#D0CC9B' beige)"));
        properties.put("groundTexture", nullableProp("string",
                "Ground texture name from list_textures_catalog, or null to remove texture"));
        properties.put("groundTextureCategory", prop("string",
                "Category to disambiguate ground texture name"));
        properties.put("skyColor", prop("string",
                "Sky color as '#RRGGBB' (default '#CCE4FC' light blue)"));
        properties.put("skyTexture", nullableProp("string",
                "Sky texture name from list_textures_catalog, or null to remove texture"));
        properties.put("skyTextureCategory", prop("string",
                "Category to disambiguate sky texture name"));
        properties.put("lightColor", prop("string",
                "Main light color as '#RRGGBB'. Affects 3D rendering brightness and tone"));
        properties.put("ceilingLightColor", prop("string",
                "Ceiling light color as '#RRGGBB'. Affects ceiling illumination in 3D"));
        properties.put("wallsAlpha", prop("number",
                "Wall transparency: 0.0 (fully opaque, default) to 1.0 (fully transparent)"));
        properties.put("drawingMode", enumProp(
                "2D plan drawing mode: how surfaces are rendered on the plan",
                Arrays.asList("FILL", "OUTLINE", "FILL_AND_OUTLINE")));
        properties.put("allLevelsVisible", prop("boolean",
                "Whether all levels/floors are visible simultaneously in the plan"));

        schema.put("properties", properties);
        schema.put("required", Collections.emptyList());
        return schema;
    }

}
