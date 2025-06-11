package ch.endte.syncmatica.material.handlers;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.material.MaterialHttpServer;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 处理更新材料收集状态的API请求
 */
public class MaterialStatusHandler implements HttpHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private final MaterialHttpServer server;
    private final Context context;
    private final Gson gson;
    
    public MaterialStatusHandler(MaterialHttpServer server, Context context, Gson gson) {
        this.server = server;
        this.context = context;
        this.gson = gson;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 确保已加载翻译
        server.ensureTranslationsLoaded();
        
        // 处理OPTIONS请求（CORS预检请求）
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        // 只接受POST请求
        if (!"POST".equals(exchange.getRequestMethod())) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("error", server.getTranslatedText("syncmatica.error.method_not_supported", "Method not supported"));
            server.sendResponse(exchange, 405, gson.toJson(errorResponse));
            return;
        }

        // 读取请求体
        StringBuilder requestBody = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }
        
        try {
            // 解析JSON请求
            JsonObject request = gson.fromJson(requestBody.toString(), JsonObject.class);
            
            // 验证必须字段
            if (!request.has("placementId") || !request.has("itemId") || !request.has("count")) {
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", server.getTranslatedText("syncmatica.error.missing_fields", "Missing required fields"));
                server.sendResponse(exchange, 400, gson.toJson(errorResponse));
                return;
            }
            
            String placementId = request.get("placementId").getAsString();
            String itemId = request.get("itemId").getAsString();
            int collectedCount = request.get("count").getAsInt();
            
            // 验证count是否为正数
            if (collectedCount < 0) {
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", server.getTranslatedText("syncmatica.error.invalid_count", "Count must be non-negative"));
                server.sendResponse(exchange, 400, gson.toJson(errorResponse));
                return;
            }
            
            // 验证placementId是有效的UUID
            UUID id;
            try {
                id = UUID.fromString(placementId.trim());
            } catch (IllegalArgumentException e) {
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", server.getTranslatedText("syncmatica.error.invalid_id", "Invalid placement ID format"));
                server.sendResponse(exchange, 400, gson.toJson(errorResponse));
                return;
            }
            
            // 验证投影存在
            boolean placementExists = false;
            Collection<ServerPlacement> placements = context.getSyncmaticManager().getAll();
            for (ServerPlacement p : placements) {
                if (p.getId().equals(id)) {
                    placementExists = true;
                    break;
                }
            }
            
            if (!placementExists) {
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("error", server.getTranslatedText("syncmatica.error.placement_not_found", "Placement not found"));
                server.sendResponse(exchange, 404, gson.toJson(errorResponse));
                return;
            }
            
            // 查找物品总数量
            int totalCount = 0;
            try {
                // 获取投影物品数据
                JsonObject materialData = null;
                Collection<ServerPlacement> allPlacements = context.getSyncmaticManager().getAll();
                for (ServerPlacement p : allPlacements) {
                    if (p.getId().equals(id)) {
                        try {
                            materialData = server.getMaterialListFromPlacement(p);
                            break;
                        } catch (Exception e) {
                            // 保持静默
                        }
                    }
                }
                
                if (materialData != null && materialData.has("items")) {
                    JsonArray items = materialData.getAsJsonArray("items");
                    for (int i = 0; i < items.size(); i++) {
                        JsonObject item = items.get(i).getAsJsonObject();
                        if (item.has("itemId") && item.has("count") && item.get("itemId").getAsString().equals(itemId)) {
                            totalCount = item.get("count").getAsInt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // 出错时默认为高数值，让用户可以更新
                totalCount = Integer.MAX_VALUE;
            }
            
            // 如果找不到物品，使用传入的数量作为上限
            if (totalCount == 0) {
                totalCount = collectedCount;
            }
            
            // 更新收集状态
            boolean wasLimited = server.updateMaterialCollectionStatus(placementId, itemId, collectedCount, totalCount);
            
            // 构建响应
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", server.getTranslatedText("syncmatica.success.status_updated", "Material collection status updated"));
            response.addProperty("placementId", placementId);
            response.addProperty("itemId", itemId);
            response.addProperty("requestedCount", collectedCount);
            response.addProperty("actualCount", Math.min(collectedCount, totalCount));
            response.addProperty("totalCount", totalCount);
            response.addProperty("wasLimited", wasLimited);
            
            server.sendResponse(exchange, 200, gson.toJson(response));
            
            // 记录状态变更到日志
            LOGGER.info("Material collection status updated - Placement: " + placementId + ", Item: " + itemId + ", Count: " + Math.min(collectedCount, totalCount) + "/" + totalCount);
        } catch (Exception e) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("error", e.getMessage());
            server.sendResponse(exchange, 500, gson.toJson(errorResponse));
        }
    }
} 