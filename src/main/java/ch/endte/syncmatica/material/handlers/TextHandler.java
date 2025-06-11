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
import java.util.*;

/**
 * 处理获取投影材料列表文本格式的API请求
 */
public class TextHandler implements HttpHandler {
    private final MaterialHttpServer server;
    private final Context context;
    private final Gson gson;
    
    public TextHandler(MaterialHttpServer server, Context context, Gson gson) {
        this.server = server;
        this.context = context;
        this.gson = gson;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 确保已加载翻译
        server.ensureTranslationsLoaded();
        
        if (!"GET".equals(exchange.getRequestMethod())) {
            server.sendTextResponse(exchange, 405, server.getTranslatedText("syncmatica.error.method_not_supported", "Method not supported"));
            return;
        }

        // 解析查询参数
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            server.sendTextResponse(exchange, 400, server.getTranslatedText("syncmatica.error.missing_id", "Missing placement ID parameter"));
            return;
        }
        
        // 更健壮地解析参数
        Map<String, String> params = server.parseQueryParams(query);
        String placementId = params.get("id");
        
        if (placementId == null || placementId.isEmpty()) {
            server.sendTextResponse(exchange, 400, server.getTranslatedText("syncmatica.error.missing_id", "Missing placement ID parameter"));
            return;
        }
        
        UUID id;
        try {
            // 尝试忽略空格并解析UUID
            placementId = placementId.trim();
            id = UUID.fromString(placementId);
        } catch (IllegalArgumentException e) {
            server.sendTextResponse(exchange, 400, server.getTranslatedText("syncmatica.error.invalid_id", "Invalid placement ID format") + ": " + e.getMessage());
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
                server.sendTextResponse(exchange, 404, server.getTranslatedText("syncmatica.error.placement_not_found", "Placement not found"));
                return;
            }

            // 尝试获取真实材料列表
            JsonObject materialData;
            try {
                materialData = server.getMaterialListFromPlacement(placement);
            } catch (Exception e) {
                materialData = server.createSampleMaterialList();
            }
            
            // 将材料数据转换为文本表格格式
            String textTable = convertMaterialsToTextTable(materialData, placement.getName(), placementId);
            
            // 发送文本响应
            server.sendTextResponse(exchange, 200, textTable);
        } catch (Exception e) {
            server.sendTextResponse(exchange, 500, server.getTranslatedText("syncmatica.error.processing_request", "Error processing request") + ": " + e.getMessage());
        }
    }
    
    private String convertMaterialsToTextTable(JsonObject materialData, String placementName, String placementId) {
        StringBuilder sb = new StringBuilder();
        sb.append(server.getTranslatedText("syncmatica.text.placement_name", "Placement name") + ": ").append(placementName).append("\n\n");
        
        // 获取材料收集状态
        Map<String, Integer> collectionStatus = server.getMaterialCollectionStatus(placementId);
        
        // 定义列宽常量 (显示宽度，非字符数)
        final int nameColWidth = 30;  // 材料名称列
        final int countColWidth = 6;  // 数量列
        final int collectedColWidth = 12; // 已收集列
        final int idColWidth = 24;    // 物品ID列
        
        // 获取表头文本
        String nameHeader = server.getTranslatedText("syncmatica.text.material_name", "Material name");
        String countHeader = server.getTranslatedText("syncmatica.text.count", "Count");
        String collectedHeader = server.getTranslatedText("syncmatica.text.collected", "Collected");
        String idHeader = server.getTranslatedText("syncmatica.text.item_id", "Item ID");
        
        // 构建表头行
        sb.append(padRight(nameHeader, nameColWidth))
          .append(" | ")
          .append(padCenter(countHeader, countColWidth))
          .append(" | ")
          .append(padCenter(collectedHeader, collectedColWidth))
          .append(" | ")
          .append(padLeft(idHeader, idColWidth))
          .append("\n");
        
        // 构建分隔线
        sb.append(repeatChar('-', nameColWidth))
          .append("-+-")
          .append(repeatChar('-', countColWidth))
          .append("-+-")
          .append(repeatChar('-', collectedColWidth))
          .append("-+-")
          .append(repeatChar('-', idColWidth))
          .append("\n");
        
        if (materialData.has("items")) {
            JsonArray items = materialData.getAsJsonArray("items");
            List<MaterialItem> materialItems = new ArrayList<>();
            
            // 收集所有材料项目
            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();
                String name = item.has("name") ? item.get("name").getAsString() : server.getTranslatedText("syncmatica.text.unknown", "Unknown");
                int count = item.has("count") ? item.get("count").getAsInt() : 0;
                String id = item.has("itemId") ? item.get("itemId").getAsString() : server.getTranslatedText("syncmatica.text.unknown", "Unknown");
                
                // 使用物品ID获取中文翻译
                String translatedName = server.getTranslatedName(id);
                if (translatedName.equals(id) && !name.equals(id)) {
                    // 如果翻译和ID相同但name不同，使用name
                    translatedName = name;
                }
                
                materialItems.add(new MaterialItem(translatedName, count, id));
            }
            
            // 按数量降序排序
            materialItems.sort((a, b) -> Integer.compare(b.count, a.count));
            
            // 生成表格行
            for (MaterialItem item : materialItems) {
                // 截断过长的名称
                String name = truncateToDisplayWidth(item.name, nameColWidth);
                
                // 获取收集状态
                int collectedCount = collectionStatus.getOrDefault(item.id, 0);
                if (collectedCount > item.count) collectedCount = item.count;
                
                // 计算百分比
                int percent = item.count > 0 ? (collectedCount * 100 / item.count) : 0;
                
                // 格式化为 "已收集/总数 (百分比%)"
                String collectedStr = String.format("%d/%d (%d%%)", collectedCount, item.count, percent);
                
                // 构建数据行
                sb.append(padRight(name, nameColWidth))
                  .append(" | ")
                  .append(padLeft(String.valueOf(item.count), countColWidth))
                  .append(" | ")
                  .append(padCenter(collectedStr, collectedColWidth))
                  .append(" | ")
                  .append(item.id)
                  .append("\n");
            }
            
            // 添加总计行
            int totalItems = materialItems.stream().mapToInt(item -> item.count).sum();
            int totalCollected = 0;
            
            for (MaterialItem item : materialItems) {
                int collectedCount = collectionStatus.getOrDefault(item.id, 0);
                if (collectedCount > item.count) collectedCount = item.count;
                totalCollected += collectedCount;
            }
            
            int overallPercent = totalItems > 0 ? (totalCollected * 100 / totalItems) : 0;
            
            // 添加底部分隔线
            sb.append(repeatChar('-', nameColWidth))
              .append("-+-")
              .append(repeatChar('-', countColWidth))
              .append("-+-")
              .append(repeatChar('-', collectedColWidth))
              .append("-+-")
              .append(repeatChar('-', idColWidth))
              .append("\n");
            
            // 添加总计信息
            sb.append(String.format("%s: %d %s, %d %s, %s: %d/%d (%d%%)\n", 
                server.getTranslatedText("syncmatica.text.total", "Total"),
                totalItems,
                server.getTranslatedText("syncmatica.text.materials", "materials"),
                materialItems.size(),
                server.getTranslatedText("syncmatica.text.different_types", "different types"),
                server.getTranslatedText("syncmatica.text.collected", "Collected"),
                totalCollected,
                totalItems,
                overallPercent));
        } else {
            sb.append(server.getTranslatedText("syncmatica.text.no_materials", "No material data found") + "\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 重复字符指定次数
     */
    private String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
    
    /**
     * 计算字符串的显示宽度（考虑中文字符占用两个位置）
     */
    private int calculateDisplayWidth(String str) {
        if (str == null) return 0;
        
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // 中文字符和其他全角字符的Unicode范围
            if (c >= '\u4e00' && c <= '\u9fa5' || c >= '\uff00' && c <= '\uffef') {
                width += 2; // 中文和全角字符算2个宽度
            } else {
                width += 1; // 英文和其他字符算1个宽度
            }
        }
        return width;
    }
    
    /**
     * 截断字符串至指定显示宽度
     */
    private String truncateToDisplayWidth(String str, int maxWidth) {
        if (str == null) return "";
        if (calculateDisplayWidth(str) <= maxWidth) return str;
        
        StringBuilder result = new StringBuilder();
        int width = 0;
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int charWidth = (c >= '\u4e00' && c <= '\u9fa5' || c >= '\uff00' && c <= '\uffef') ? 2 : 1;
            
            // 如果加上当前字符会超出最大宽度-3（为"..."预留空间），则停止
            if (width + charWidth > maxWidth - 3) {
                result.append("...");
                break;
            }
            
            result.append(c);
            width += charWidth;
        }
        
        return result.toString();
    }
    
    /**
     * 右对齐填充字符串至指定显示宽度
     */
    private String padLeft(String str, int width) {
        int displayWidth = calculateDisplayWidth(str);
        if (displayWidth >= width) return str;
        
        return repeatChar(' ', width - displayWidth) + str;
    }
    
    /**
     * 左对齐填充字符串至指定显示宽度
     */
    private String padRight(String str, int width) {
        int displayWidth = calculateDisplayWidth(str);
        if (displayWidth >= width) return str;
        
        return str + repeatChar(' ', width - displayWidth);
    }
    
    /**
     * 居中对齐填充字符串至指定显示宽度
     */
    private String padCenter(String str, int width) {
        int displayWidth = calculateDisplayWidth(str);
        if (displayWidth >= width) return str;
        
        int leftPad = (width - displayWidth) / 2;
        int rightPad = width - displayWidth - leftPad;
        
        return repeatChar(' ', leftPad) + str + repeatChar(' ', rightPad);
    }
    
    private class MaterialItem {
        String name;
        int count;
        String id;
        
        MaterialItem(String name, int count, String id) {
            this.name = name;
            this.count = count;
            this.id = id;
        }
    }
} 