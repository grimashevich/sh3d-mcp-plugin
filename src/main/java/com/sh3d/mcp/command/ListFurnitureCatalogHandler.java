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
public class ListFurnitureCatalogHandler implements CommandHandler {

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String query = request.getString("query");
        String categoryFilter = request.getString("category");

        FurnitureCatalog catalog = accessor.getFurnitureCatalog();
        List<Object> results = new ArrayList<>();

        for (FurnitureCategory cat : catalog.getCategories()) {
            if (categoryFilter != null
                    && !cat.getName().toLowerCase().contains(categoryFilter.toLowerCase())) {
                continue;
            }
            for (CatalogPieceOfFurniture piece : cat.getFurniture()) {
                if (query != null
                        && !piece.getName().toLowerCase().contains(query.toLowerCase())) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", piece.getName());
                item.put("category", cat.getName());
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
}
