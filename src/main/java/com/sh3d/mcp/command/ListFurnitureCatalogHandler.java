package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

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
        // TODO: реализовать
        // 1. String query = request.getString("query")   — опционально
        // 2. String category = request.getString("category") — опционально
        // 3. FurnitureCatalog catalog = accessor.getFurnitureCatalog();
        // 4. List<Object> results = new ArrayList<>();
        //    for (FurnitureCategory cat : catalog.getCategories()) {
        //      if (category != null && !cat.getName().toLowerCase().contains(category.toLowerCase()))
        //        continue;
        //      for (CatalogPieceOfFurniture piece : cat.getFurniture()) {
        //        if (query != null && !piece.getName().toLowerCase().contains(query.toLowerCase()))
        //          continue;
        //        Map<String, Object> item = new LinkedHashMap<>();
        //        item.put("name", piece.getName());
        //        item.put("category", cat.getName());
        //        item.put("width", piece.getWidth());
        //        item.put("depth", piece.getDepth());
        //        item.put("height", piece.getHeight());
        //        results.add(item);
        //      }
        //    }
        // 5. Response.ok(Map.of("furniture", results))
        throw new UnsupportedOperationException("TODO: implement list_furniture_catalog");
    }
}
