package com.sh3d.mcp.command;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработчик команды "list_furniture_catalog".
 * Возвращает содержимое каталога мебели с фильтрацией.
 *
 * <pre>
 * Параметры:
 *   query    — поисковый запрос по имени (string, опционально)
 *   category — фильтр по категории (string, опционально)
 *
 * Ответ:
 *   furniture — массив [{name, category, width, depth, height}, ...]
 *
 * EDT: не требуется (каталог read-only, thread-safe)
 * </pre>
 */
public class ListFurnitureCatalogHandler implements CommandHandler, CommandDescriptor {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String query = request.getString("query");
        String categoryFilter = request.getString("category");
        String lowerQuery = query != null ? query.toLowerCase() : null;
        String lowerCategory = categoryFilter != null ? categoryFilter.toLowerCase() : null;

        FurnitureCatalog catalog = accessor.getFurnitureCatalog();
        List<Object> results = new ArrayList<>();

        for (FurnitureCategory cat : catalog.getCategories()) {
            String catName = cat.getName();
            if (catName == null) {
                continue;
            }
            if (lowerCategory != null
                    && !catName.toLowerCase().contains(lowerCategory)) {
                continue;
            }
            for (CatalogPieceOfFurniture piece : cat.getFurniture()) {
                String pieceName = piece.getName();
                if (pieceName == null) {
                    continue;
                }
                if (lowerQuery != null
                        && !pieceName.toLowerCase().contains(lowerQuery)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", pieceName);
                item.put("category", catName);
                item.put("width", round2(piece.getWidth()));
                item.put("depth", round2(piece.getDepth()));
                item.put("height", round2(piece.getHeight()));
                results.add(item);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("furniture", results);
        return Response.ok(data);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public String getToolName() {
        return "list_catalog";
    }

    @Override
    public String getDescription() {
        return "Lists available furniture in the Sweet Home 3D catalog. "
                + "Can filter by name query and/or category. "
                + "Returns furniture names, categories, and dimensions. "
                + "Use this to find the correct furniture name before placing it.";
    }

    @Override
    public Map<String, Object> getSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", prop("string", "Search query for furniture name"));
        properties.put("category", prop("string", "Filter by category name"));
        schema.put("properties", properties);

        return schema;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }
}
