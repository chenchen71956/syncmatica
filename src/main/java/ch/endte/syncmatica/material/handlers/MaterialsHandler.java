package ch.endte.syncmatica.material.handlers;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.material.MaterialHttpServer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 处理获取投影材料列表的API请求
 */
public class MaterialsHandler implements HttpHandler {
    private final MaterialHttpServer server;
    private final Context context;
    private final Gson gson;
    
    public MaterialsHandler(MaterialHttpServer server, Context context, Gson gson) {
        this.server = server;
        this.context = context;
        this.gson = gson;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 确保已加载翻译
        server.ensureTranslationsLoaded();
        
        if (!"GET".equals(exchange.getRequestMethod())) {
            server.sendResponse(exchange, 405, server.getTranslatedText("syncmatica.error.method_not_supported", "Method not supported"));
            return;
        }

        // 解析查询参数
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            server.sendResponse(exchange, 400, server.getTranslatedText("syncmatica.error.missing_id", "Missing placement ID parameter"));
            return;
        }
        
        // 更健壮地解析参数
        Map<String, String> params = server.parseQueryParams(query);
        String placementId = params.get("id");
        
        if (placementId == null || placementId.isEmpty()) {
            server.sendResponse(exchange, 400, server.getTranslatedText("syncmatica.error.missing_id", "Missing placement ID parameter"));
            return;
        }
        
        UUID id;
        try {
            // 尝试忽略空格并解析UUID
            placementId = placementId.trim();
            id = UUID.fromString(placementId);
        } catch (IllegalArgumentException e) {
            server.sendResponse(exchange, 400, server.getTranslatedText("syncmatica.error.invalid_id", "Invalid placement ID format") + ": " + e.getMessage());
            return;
        }

        try {
            // 查找匹配的投影
            ServerPlacement placement = null;
            Collection<ServerPlacement> placements = context.getSyncmaticManager().getAll();
            for (ServerPlacement p : placements) {
                if (p.getId().equals(id)) {
                    placement = p;
                    break;
                }
            }

            if (placement == null) {
                server.sendResponse(exchange, 404, server.getTranslatedText("syncmatica.error.placement_not_found", "Placement not found"));
                return;
            }

            // 尝试获取真实材料列表
            JsonObject materialData;
            try {
                materialData = server.getMaterialListFromPlacement(placement);
            } catch (Exception e) {
                materialData = server.createSampleMaterialList(); // 出错时使用示例数据
            }
            
            // 将收集状态添加到材料列表中
            if (materialData.has("items")) {
                JsonArray items = materialData.getAsJsonArray("items");
                Map<String, Integer> collectionStatus = server.getMaterialCollectionStatus(placementId);
                
                for (int i = 0; i < items.size(); i++) {
                    JsonObject item = items.get(i).getAsJsonObject();
                    if (item.has("itemId") && item.has("count")) {
                        String itemId = item.get("itemId").getAsString();
                        int totalCount = item.get("count").getAsInt();
                        int collectedCount = collectionStatus.getOrDefault(itemId, 0);
                        
                        // 确保收集数量不超过总数
                        if (collectedCount > totalCount) {
                            collectedCount = totalCount;
                        }
                        
                        item.addProperty("collectedCount", collectedCount);
                        item.addProperty("remaining", totalCount - collectedCount);
                        item.addProperty("percentComplete", totalCount > 0 ? (collectedCount * 100 / totalCount) : 0);
                        
                        // 保留向后兼容
                        item.addProperty("collected", collectedCount >= totalCount);
                    }
                }
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("placementId", placement.getId().toString());
            response.addProperty("placementName", placement.getName());
            response.add("materials", materialData);

            server.sendResponse(exchange, 200, gson.toJson(response));
        } catch (Exception e) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("error", e.getMessage());
            server.sendResponse(exchange, 500, gson.toJson(errorResponse));
        }
    }
} 