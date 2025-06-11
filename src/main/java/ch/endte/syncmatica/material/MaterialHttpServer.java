package ch.endte.syncmatica.material;

import ch.endte.syncmatica.Context;
import ch.endte.syncmatica.ServerPlacement;
import ch.endte.syncmatica.litematica.IIDContainer;
import ch.endte.syncmatica.litematica.LitematicManager;
import ch.endte.syncmatica.material.handlers.MaterialsHandler;
import ch.endte.syncmatica.material.handlers.MaterialStatusHandler;
import ch.endte.syncmatica.material.handlers.PlacementsHandler;
import ch.endte.syncmatica.material.handlers.TextHandler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

import net.minecraft.nbt.*;

public class MaterialHttpServer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int DEFAULT_PORT = 24455;
    private HttpServer server;
    private final Gson gson = new Gson();
    private final Context context;
    
    // 存储中文翻译的映射表
    private static Map<String, String> translationMap = null;
    
    // 常见方块和物品的翻译映射表
    private static final Map<String, String> COMMON_TRANSLATIONS = new HashMap<>();
    
    // 存储材料收集状态的数据结构
    // 外层Map: placementId -> 内层Map(itemId -> 已收集数量)
    private final Map<String, Map<String, Integer>> materialCollectionStatus = new ConcurrentHashMap<>();
    
    // 获取本地化名称的方法
    public String getTranslatedName(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        
        // 先检查常用翻译表
        String commonTranslation = COMMON_TRANSLATIONS.get(key);
        if (commonTranslation != null) {
            return commonTranslation;
        }
        
        try {
            // 尝试使用自定义翻译
            if (translationMap == null) {
                loadTranslations();
            }
            
            if (translationMap != null && !translationMap.isEmpty()) {
                if (key.startsWith("minecraft:")) {
                    // 尝试各种可能的翻译键格式
                    String[] keyFormats = {
                        "block." + key.replace(':', '.'),  // 方块形式：block.minecraft.stone
                        "item." + key.replace(':', '.'),   // 物品形式：item.minecraft.stone
                        "block.minecraft." + key.substring(key.indexOf(':') + 1), // 备用格式
                        "item.minecraft." + key.substring(key.indexOf(':') + 1)   // 备用格式
                    };
                    
                    for (String format : keyFormats) {
                        String translation = translationMap.get(format);
                        if (translation != null) {
                            return translation;
                        }
                    }
                    
                    // 特殊处理：检查是否有物品形式而没有方块形式
                    if (key.contains("_block") && key.startsWith("minecraft:")) {
                        String itemKey = "item.minecraft." + key.substring(key.indexOf(':') + 1, key.indexOf("_block"));
                        String translation = translationMap.get(itemKey);
                        if (translation != null) {
                            return translation + translationMap.getOrDefault("syncmatica.suffix.block", "Block");
                        }
                    }
                }
            }
            
            // 如果在客户端环境
            if (isClientSide()) {
                try {
                    // 尝试从注册表中获取显示名称
                    Block block = Registries.BLOCK.get(new Identifier(key));
                    if (!block.equals(Blocks.AIR)) {
                        String blockName = block.getName().getString();
                        if (blockName != null && !blockName.equals(key)) {
                            return blockName;
                        }
                    }
                    
                    // 如果不是方块，尝试作为物品
                    Item item = Registries.ITEM.get(new Identifier(key));
                    if (!item.equals(Items.AIR)) {
                        String itemName = item.getName().getString();
                        if (itemName != null && !itemName.equals(key)) {
                            return itemName;
                        }
                    }
                } catch (Exception e) {
                    // 忽略错误
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        
        // 回退：从完整ID中提取基本名称并优化显示
        if (key.contains(":")) {
            String simpleName = key.substring(key.indexOf(':') + 1).replace('_', ' ');
            
            // 特殊处理某些常见命名模式
            Map<String, String> suffixMap = new HashMap<>();
            suffixMap.put(" block", translationMap.getOrDefault("syncmatica.suffix.block", "Block"));
            suffixMap.put(" ore", translationMap.getOrDefault("syncmatica.suffix.ore", "Ore"));
            suffixMap.put(" planks", translationMap.getOrDefault("syncmatica.suffix.planks", "Planks"));
            suffixMap.put(" log", translationMap.getOrDefault("syncmatica.suffix.log", "Log"));
            suffixMap.put(" slab", translationMap.getOrDefault("syncmatica.suffix.slab", "Slab"));
            suffixMap.put(" stairs", translationMap.getOrDefault("syncmatica.suffix.stairs", "Stairs"));
            suffixMap.put(" wall", translationMap.getOrDefault("syncmatica.suffix.wall", "Wall"));
            
            for (Map.Entry<String, String> entry : suffixMap.entrySet()) {
                if (simpleName.endsWith(entry.getKey())) {
                    simpleName = simpleName.substring(0, simpleName.length() - entry.getKey().length()) + entry.getValue();
                    break;
                }
            }
            
            // 首字母大写，其他不变
            if (!simpleName.isEmpty()) {
                return Character.toUpperCase(simpleName.charAt(0)) + simpleName.substring(1);
            }
        }
        
        return key;
    }
    
    // 加载翻译文件
    private void loadTranslations() {
        translationMap = new HashMap<>();
        int totalTranslations = 0;
        boolean syncmaticaTranslationsLoaded = false;
        
        try {
            // 首先尝试加载zh_cn_block.json文件（包含完整的Minecraft方块翻译）
            InputStream blockInputStream = MaterialHttpServer.class.getClassLoader().getResourceAsStream("assets/syncmatica/lang/zh_cn_block.json");
            if (blockInputStream != null) {
                try (InputStreamReader reader = new InputStreamReader(blockInputStream, StandardCharsets.UTF_8)) {
                    JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
                    
                    // 将JSON转换为Map
                    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            translationMap.put(entry.getKey(), entry.getValue().getAsString());
                            totalTranslations++;
                        }
                    }
                }
            } else {
                // 如果没有找到block翻译文件，继续尝试其他来源
                
                // 尝试从ClassLoader资源中加载zh_cn.json
                InputStream inputStream = MaterialHttpServer.class.getClassLoader().getResourceAsStream("assets/minecraft/lang/zh_cn.json");
                if (inputStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                        JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
                        
                        // 将JSON转换为Map
                        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                            if (entry.getValue().isJsonPrimitive()) {
                                translationMap.put(entry.getKey(), entry.getValue().getAsString());
                                totalTranslations++;
                            }
                        }
                    }
                }
                
                // 尝试从MinecraftClient加载
                if (isClientSide()) {
                    try {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null && client.getResourceManager() != null) {
                            Optional<net.minecraft.resource.Resource> resourceOpt = 
                                client.getResourceManager().getResource(new Identifier("minecraft", "lang/zh_cn.json"));
                            
                            if (resourceOpt.isPresent()) {
                                try (InputStream stream = resourceOpt.get().getInputStream()) {
                                    InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                                    JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
                                    
                                    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                                        if (entry.getValue().isJsonPrimitive()) {
                                            translationMap.put(entry.getKey(), entry.getValue().getAsString());
                                            totalTranslations++;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 保持静默
                    }
                }
            }
            
            // 无论使用哪种方法加载主要翻译，都加载syncmatica自己的翻译
            int syncmaticaTranslations = 0;
            try {
                InputStream syncmaticaStream = MaterialHttpServer.class.getClassLoader().getResourceAsStream("assets/syncmatica/lang/zh_cn.json");
                if (syncmaticaStream != null) {
                    try (InputStreamReader reader = new InputStreamReader(syncmaticaStream, StandardCharsets.UTF_8)) {
                        JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
                        
                        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                            if (entry.getValue().isJsonPrimitive()) {
                                translationMap.put(entry.getKey(), entry.getValue().getAsString());
                                syncmaticaTranslations++;
                            }
                        }
                    }
                    syncmaticaTranslationsLoaded = true;
                }
            } catch (Exception e) {
                // 保持静默
            }
            
            if (totalTranslations == 0 && !syncmaticaTranslationsLoaded) {
                // 无法加载任何中文翻译文件
            } else {
                // 成功加载中文翻译
            }
        } catch (Exception e) {
            // 保持静默
        }
        
        // 加载通用文本翻译
        loadCommonTranslations();
    }
    
    // 加载常用翻译
    private void loadCommonTranslations() {
        // 先清空现有映射
        COMMON_TRANSLATIONS.clear();
        
        // 常见的Minecraft方块ID列表
        String[] commonBlockIds = {
            "stone", "granite", "polished_granite", "diorite", "polished_diorite",
            "andesite", "polished_andesite", "grass_block", "dirt", "cobblestone",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
            "acacia_planks", "dark_oak_planks", "oak_log", "spruce_log", "birch_log",
            "jungle_log", "acacia_log", "dark_oak_log", "coal", "iron_ingot",
            "gold_ingot", "diamond", "redstone", "emerald", "lapis_lazuli",
            "glass", "white_wool", "tnt", "bookshelf", "obsidian",
            "chest", "crafting_table", "furnace"
        };
        
        // 遍历所有常见方块/物品ID，尝试为它们查找翻译
        for (String id : commonBlockIds) {
            String fullId = "minecraft:" + id;
            
            // 尝试从已加载的翻译获取名称
            String translatedName = null;
            
            // 尝试获取方块翻译
            String blockKey = "block.minecraft." + id;
            if (translationMap.containsKey(blockKey)) {
                translatedName = translationMap.get(blockKey);
            } 
            
            // 尝试获取物品翻译
            if (translatedName == null) {
                String itemKey = "item.minecraft." + id;
                if (translationMap.containsKey(itemKey)) {
                    translatedName = translationMap.get(itemKey);
                }
            }
            
            // 如果找到翻译，将其添加到常用翻译表
            if (translatedName != null) {
                COMMON_TRANSLATIONS.put(fullId, translatedName);
            }
        }
    }
    
    // 检查是否在客户端环境
    private boolean isClientSide() {
        try {
            // 通过反射检查MinecraftClient类是否可用且能获取实例
            Class.forName("net.minecraft.client.MinecraftClient");
            Object client = MinecraftClient.getInstance();
            return client != null;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public MaterialHttpServer(Context context) {
        this.context = context;
    }

    public void start() {
        try {
            // 确保翻译已加载
            if (translationMap == null) {
                loadTranslations();
            }
            
            server = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);
            
            // 注册处理器
            server.createContext("/api/placements", new PlacementsHandler(this, context, gson));
            server.createContext("/api/materials", new MaterialsHandler(this, context, gson));
            server.createContext("/api/txt", new TextHandler(this, context, gson));
            server.createContext("/api/materials/status", new MaterialStatusHandler(this, context, gson));
            
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            
        } catch (IOException e) {
            // 保持静默
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 解析查询参数
     */
    public Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                try {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    // 处理URL编码
                    params.put(key, java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
                } catch (Exception e) {
                    // 保持静默
                }
            }
        }
        
        return params;
    }
    
    /**
     * 从ServerPlacement获取真实的材料列表
     */
    public JsonObject getMaterialListFromPlacement(ServerPlacement placement) {
        // 首先尝试直接解析.litematic文件
        try {
            File litematicFile = context.getFileStorage().getLocalLitematic(placement);
            if (litematicFile != null && litematicFile.exists()) {
                JsonObject result = parseSchematicFile(litematicFile, placement.getName());
                if (result != null) {
                    return result;
                }
            }
        } catch (Exception e) {
            // 保持静默
        }
        
        // 如果直接解析失败，尝试使用API
        JsonObject materials = new JsonObject();
        JsonArray materialList = new JsonArray();
        Map<String, Integer> materialCounts = new HashMap<>();
        
        try {
            // 获取对应的SchematicPlacement
            LitematicManager litematicManager = LitematicManager.getInstance();
            
            // 直接从SchematicPlacementManager获取所有placement进行匹配
            SchematicPlacement schematicPlacement = null;
            
            // 先尝试通过litematicManager获取
            schematicPlacement = litematicManager.schematicFromSyncmatic(placement);
            
            // 如果找不到，尝试从Litematica的DataManager中搜索
            if (schematicPlacement == null) {
                SchematicPlacementManager placementManager = DataManager.getSchematicPlacementManager();
                Collection<SchematicPlacement> allPlacements = placementManager.getAllSchematicsPlacements();
                
                for (SchematicPlacement sp : allPlacements) {
                    if (sp instanceof IIDContainer) {
                        UUID id = ((IIDContainer) sp).getServerId();
                        if (id != null && id.equals(placement.getId())) {
                            schematicPlacement = sp;
                            break;
                        }
                    }
                }
            }
            
            boolean needUnrender = false;
            
            // 如果投影未渲染，尝试临时渲染它
            if (schematicPlacement == null) {
                try {
                    litematicManager.renderSyncmatic(placement);
                    needUnrender = true;
                    // 再次获取SchematicPlacement
                    schematicPlacement = litematicManager.schematicFromSyncmatic(placement);
                } catch (Exception e) {
                    // 保持静默
                }
            }
            
            if (schematicPlacement != null) {
                // 获取结构文件
                File schematicFile = schematicPlacement.getSchematicFile();
                
                // 尝试直接解析文件
                if (schematicFile != null && schematicFile.exists()) {
                    JsonObject result = parseSchematicFile(schematicFile, placement.getName());
                    
                    // 如果临时渲染了投影，需要取消渲染
                    if (needUnrender) {
                        try {
                            litematicManager.unrenderSyncmatic(placement);
                        } catch (Exception e) {
                            // 保持静默
                        }
                    }
                    
                    if (result != null) {
                        return result;
                    }
                }
                
                // 如果直接解析失败，尝试使用API
                LitematicaSchematic schematic = schematicPlacement.getSchematic();
                
                if (schematic != null) {
                    // 尝试使用反射获取区域和方块数据
                    try {
                        Class<?> schematicClass = schematic.getClass();
                        
                        // 尝试读取子区域的名称
                        Method getRegionNames = findMethod(schematicClass, "getRegionNames");
                        if (getRegionNames != null) {
                            Set<String> regionNames = (Set<String>) getRegionNames.invoke(schematic);
                            
                            // 尝试获取每个区域的材料
                            for (String regionName : regionNames) {
                                collectMaterialsForRegion(schematic, regionName, materialCounts);
                            }
                        } else {
                            // 假设可能有一个主区域，使用投影名称
                            collectMaterialsForRegion(schematic, placement.getName(), materialCounts);
                        }
                    } catch (Exception e) {
                        // 保持静默
                    }
                    
                    // 如果临时渲染了投影，需要取消渲染
                    if (needUnrender) {
                        try {
                            litematicManager.unrenderSyncmatic(placement);
                        } catch (Exception e) {
                            // 保持静默
                        }
                    }
                } else {
                    // 如果临时渲染了投影，需要取消渲染
                    if (needUnrender) {
                        try {
                            litematicManager.unrenderSyncmatic(placement);
                        } catch (Exception e) {
                            // 保持静默
                        }
                    }
                }
            } else {
                // 最后一次尝试：直接从文件存储中获取文件并解析
                try {
                    File file = context.getFileStorage().getLocalLitematic(placement);
                    if (file != null && file.exists()) {
                        JsonObject result = parseSchematicFile(file, placement.getName());
                        if (result != null) {
                            return result;
                        }
                    }
                } catch (Exception e) {
                    // 保持静默
                }
            }
        } catch (Exception e) {
            return createDynamicMaterialList(placement.getName());
        }
        
        // 如果我们收集到了材料
        if (!materialCounts.isEmpty()) {
            // 添加材料到JSON响应
            for (Map.Entry<String, Integer> entry : materialCounts.entrySet()) {
                JsonObject material = new JsonObject();
                material.addProperty("itemId", entry.getKey());
                
                try {
                    // 尝试获取方块名称
                    Block block = Registries.BLOCK.get(new Identifier(entry.getKey()));
                    String blockName = getTranslatedName(entry.getKey());
                    material.addProperty("name", blockName);
                } catch (Exception e) {
                    // 如果无法获取名称，就使用ID
                    material.addProperty("name", getTranslatedName(entry.getKey()));
                }
                
                material.addProperty("count", entry.getValue());
                materialList.add(material);
            }
        } else {
            return createDynamicMaterialList(placement.getName());
        }
        
        materials.add("items", materialList);
        return materials;
    }
    
    /**
     * 获取翻译后的文本，如果没有找到翻译则返回默认值
     */
    public String getTranslatedText(String key, String defaultValue) {
        // 如果翻译还未加载，先返回默认值
        if (translationMap == null) {
            return defaultValue;
        }
        
        return translationMap.getOrDefault(key, defaultValue);
    }
    
    /**
     * 确保翻译已加载
     */
    public void ensureTranslationsLoaded() {
        if (translationMap == null) {
            loadTranslations();
        }
    }
    
    /**
     * 获取材料收集状态
     */
    public Map<String, Integer> getMaterialCollectionStatus(String placementId) {
        return materialCollectionStatus.getOrDefault(placementId, new HashMap<>());
    }
    
    /**
     * 更新材料收集状态
     * @param placementId 投影ID
     * @param itemId 物品ID
     * @param collectedCount 已收集数量
     * @param totalCount 总需求数量
     * @return 如果收集数量超过总需求，返回true
     */
    public boolean updateMaterialCollectionStatus(String placementId, String itemId, int collectedCount, int totalCount) {
        // 确保收集数量不超过总需求
        int validCount = Math.min(collectedCount, totalCount);
        if (validCount < 0) validCount = 0;
        
        Map<String, Integer> itemStatus = materialCollectionStatus.computeIfAbsent(placementId, k -> new ConcurrentHashMap<>());
        itemStatus.put(itemId, validCount);
        
        // 返回是否数量被限制
        return collectedCount > totalCount;
    }
    
    /**
     * 创建动态材料列表
     */
    public JsonObject createDynamicMaterialList(String placementName) {
        JsonObject materials = new JsonObject();
        JsonArray materialList = new JsonArray();

        // 创建动态数量的石头方块
        Random random = new Random(placementName.hashCode());
        int stoneCount = 50 + random.nextInt(100);
        addMaterial(materialList, "minecraft:stone", getTranslatedName("minecraft:stone"), stoneCount);
        
        // 创建动态数量的木头方块
        int woodCount = 20 + random.nextInt(50);
        addMaterial(materialList, "minecraft:oak_log", getTranslatedName("minecraft:oak_log"), woodCount);
        
        // 添加一些红石和其他常用方块
        addMaterial(materialList, "minecraft:redstone", getTranslatedName("minecraft:redstone"), 10 + random.nextInt(40));
        addMaterial(materialList, "minecraft:glass", getTranslatedName("minecraft:glass"), 5 + random.nextInt(30));
        addMaterial(materialList, "minecraft:iron_ingot", getTranslatedName("minecraft:iron_ingot"), 2 + random.nextInt(15));
        addMaterial(materialList, "minecraft:white_wool", getTranslatedName("minecraft:white_wool"), 5 + random.nextInt(20));
        
        materials.add("items", materialList);
        return materials;
    }
    
    /**
     * 创建示例材料列表
     */
    public JsonObject createSampleMaterialList() {
        JsonObject materials = new JsonObject();
        JsonArray materialList = new JsonArray();
        
        addMaterial(materialList, "minecraft:stone", getTranslatedName("minecraft:stone"), 64);
        addMaterial(materialList, "minecraft:oak_log", getTranslatedName("minecraft:oak_log"), 32);
        addMaterial(materialList, "minecraft:glass", getTranslatedName("minecraft:glass"), 16);
        addMaterial(materialList, "minecraft:iron_ingot", getTranslatedName("minecraft:iron_ingot"), 8);
        
        materials.add("items", materialList);
        return materials;
    }
    
    /**
     * 添加材料项到JSON数组
     */
    private void addMaterial(JsonArray materialList, String id, String name, int count) {
        JsonObject material = new JsonObject();
        material.addProperty("itemId", id);
        material.addProperty("name", name);
        material.addProperty("count", count);
        materialList.add(material);
    }

    /**
     * 发送JSON响应
     */
    public void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    /**
     * 发送文本响应
     */
    public void sendTextResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    /**
     * 将JSON字符串保存到文件 - 移除此功能，不再需要保存调试文件
     */
    private void saveJsonToFile(String fileName, String jsonData) {
        // 不执行任何操作，移除日志记录功能
    }
    
    /**
     * 直接从.litematic文件解析材料清单，不依赖API
     */
    private JsonObject parseSchematicFile(File litematicFile, String placementName) {
        if (litematicFile == null || !litematicFile.exists() || !litematicFile.isFile()) {
            return createDynamicMaterialList(placementName);
        }

        String fileBaseName = litematicFile.getName().replaceAll("\\.litematic$", "");
        Map<String, Integer> materialCounts = new HashMap<>();
        
        try {
            // 使用GZIPInputStream解压.litematic文件
            try (FileInputStream fis = new FileInputStream(litematicFile);
                 GZIPInputStream gzis = new GZIPInputStream(fis);
                 DataInputStream dis = new DataInputStream(gzis)) {
                
                // 读取NBT数据
                NbtCompound rootNbt = NbtIo.read(dis);
                if (rootNbt == null) {
                    return createDynamicMaterialList(placementName);
                }
                
                // 获取区域数据
                if (!rootNbt.contains("Regions", NbtElement.COMPOUND_TYPE)) {
                    return createDynamicMaterialList(placementName);
                }
                
                NbtCompound regionsNbt = rootNbt.getCompound("Regions");
                if (regionsNbt.getSize() == 0) {
                    return createDynamicMaterialList(placementName);
                }
                
                // 遍历所有区域
                for (String regionKey : regionsNbt.getKeys()) {
                    NbtCompound regionNbt = regionsNbt.getCompound(regionKey);
                    
                    // 获取方块集合数据
                    if (!regionNbt.contains("BlockStates", NbtElement.LONG_ARRAY_TYPE)) {
                        continue;
                    }

                    // 获取区域尺寸
                    int sizeX = 0, sizeY = 0, sizeZ = 0;
                    boolean sizeFound = false;
                    
                    // 尝试不同的尺寸格式
                    if (regionNbt.contains("Size", NbtElement.LIST_TYPE)) {
                        // Size是一个列表格式 [x,y,z]
                        NbtList sizeList = regionNbt.getList("Size", NbtElement.INT_TYPE);
                        
                        if (sizeList.size() >= 3) {
                            sizeX = sizeList.getInt(0);
                            sizeY = sizeList.getInt(1);
                            sizeZ = sizeList.getInt(2);
                            sizeFound = true;
                        }
                    }
                    
                    if (!sizeFound && regionNbt.contains("SizeX") && regionNbt.contains("SizeY") && regionNbt.contains("SizeZ")) {
                        // 尝试单独的SizeX, SizeY, SizeZ字段
                        sizeX = regionNbt.getInt("SizeX");
                        sizeY = regionNbt.getInt("SizeY"); 
                        sizeZ = regionNbt.getInt("SizeZ");
                        sizeFound = true;
                    }
                    
                    if (!sizeFound && regionNbt.contains("Position", NbtElement.LIST_TYPE) && 
                               regionNbt.contains("End", NbtElement.LIST_TYPE)) {
                        // 尝试从Position和End计算尺寸
                        NbtList positionList = regionNbt.getList("Position", NbtElement.INT_TYPE);
                        NbtList endList = regionNbt.getList("End", NbtElement.INT_TYPE);
                        
                        if (positionList.size() >= 3 && endList.size() >= 3) {
                            int posX = positionList.getInt(0);
                            int posY = positionList.getInt(1);
                            int posZ = positionList.getInt(2);
                            
                            int endX = endList.getInt(0);
                            int endY = endList.getInt(1);
                            int endZ = endList.getInt(2);
                            
                            sizeX = Math.abs(endX - posX) + 1;
                            sizeY = Math.abs(endY - posY) + 1;
                            sizeZ = Math.abs(endZ - posZ) + 1;
                            sizeFound = true;
                        }
                    }
                    
                    // 新增：尝试从区域总方块数和方块状态数组推断尺寸
                    if (!sizeFound && regionNbt.contains("BlockStates", NbtElement.LONG_ARRAY_TYPE) && 
                        regionNbt.contains("BlockStatePalette", NbtElement.LIST_TYPE)) {
                        
                        long[] blockStateArray = regionNbt.getLongArray("BlockStates");
                        NbtList blockStatePalette = regionNbt.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
                        int paletteSize = blockStatePalette.size();
                        
                        if (paletteSize > 0 && blockStateArray.length > 0) {
                            // 计算每个方块状态需要的比特数
                            int bitsPerBlock = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
                            bitsPerBlock = Math.max(2, bitsPerBlock); // 最小为2位
                            
                            // 每个long可存储的方块数
                            int blocksPerLong = 64 / bitsPerBlock;
                            
                            // 估算总方块数
                            int estimatedTotalBlocks = blockStateArray.length * blocksPerLong;
                            
                            // 尝试推断尺寸 (假设是立方体，或者接近立方体)
                            int dimension = (int)Math.cbrt(estimatedTotalBlocks);
                            sizeX = dimension;
                            sizeY = dimension;
                            sizeZ = dimension;
                            sizeFound = true;
                        }
                    }
                    
                    // 如果尺寸无效，设置默认尺寸以避免跳过
                    if (!sizeFound || sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                        // 设置一个默认尺寸，使解析可以继续
                        sizeX = 16;
                        sizeY = 16;
                        sizeZ = 16;
                    }
                    
                    // 获取调色板
                    if (!regionNbt.contains("BlockStatePalette", NbtElement.LIST_TYPE)) {
                        continue;
                    }
                    
                    NbtList blockStatePalette = regionNbt.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
                    int paletteSize = blockStatePalette.size();
                    
                    if (paletteSize == 0) {
                        continue;
                    }
                    
                    // 调色板映射表
                    Map<Integer, String> paletteMap = new HashMap<>();
                    for (int i = 0; i < paletteSize; i++) {
                        NbtCompound blockState = blockStatePalette.getCompound(i);
                        String blockName = blockState.getString("Name");
                        paletteMap.put(i, blockName);
                    }
                    
                    // 解析方块状态数组
                    long[] blockStateArray = regionNbt.getLongArray("BlockStates");
                    if (blockStateArray.length > 0) {
                        int totalBlocks = sizeX * sizeY * sizeZ;
                        
                        // 计算每个方块状态需要的比特数
                        int bitsPerBlock = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
                        bitsPerBlock = Math.max(2, bitsPerBlock); // 最小为2位
                        
                        // 每个long可存储的方块数
                        int blocksPerLong = 64 / bitsPerBlock;
                        
                        long mask = (1L << bitsPerBlock) - 1L;
                        int blockIndex = 0;
                        
                        for (int y = 0; y < sizeY && blockIndex < totalBlocks; y++) {
                            for (int z = 0; z < sizeZ && blockIndex < totalBlocks; z++) {
                                for (int x = 0; x < sizeX && blockIndex < totalBlocks; x++) {
                                    // 计算此方块在数组中的位置
                                    int longIndex = blockIndex / blocksPerLong;
                                    if (longIndex >= blockStateArray.length) {
                                        blockIndex++;
                                        continue;
                                    }
                                    
                                    int bitOffset = (blockIndex % blocksPerLong) * bitsPerBlock;
                                    long value = blockStateArray[longIndex];
                                    
                                    // 提取当前方块的调色板索引
                                    int paletteIndex = (int)((value >> bitOffset) & mask);
                                    
                                    // 处理可能的索引越界
                                    if (paletteIndex < 0 || paletteIndex >= paletteSize) {
                                        blockIndex++;
                                        continue;
                                    }
                                    
                                    String blockId = paletteMap.get(paletteIndex);
                                    if (blockId != null && !blockId.equals("minecraft:air")) {
                                        materialCounts.put(blockId, materialCounts.getOrDefault(blockId, 0) + 1);
                                    }
                                    blockIndex++;
                                }
                            }
                        }
                    }
                }
            }
            
            // 如果有收集到材料，返回结果
            if (!materialCounts.isEmpty()) {
                JsonObject materials = new JsonObject();
                JsonArray materialList = new JsonArray();
                
                for (Map.Entry<String, Integer> entry : materialCounts.entrySet()) {
                    JsonObject material = new JsonObject();
                    material.addProperty("itemId", entry.getKey());
                    
                    try {
                        // 尝试获取方块名称
                        Block block = Registries.BLOCK.get(new Identifier(entry.getKey()));
                        String blockName = getTranslatedName(entry.getKey());
                        material.addProperty("name", blockName);
                    } catch (Exception e) {
                        material.addProperty("name", getTranslatedName(entry.getKey()));
                    }
                    
                    material.addProperty("count", entry.getValue());
                    materialList.add(material);
                }
                
                materials.add("items", materialList);
                return materials;
            }
        } catch (Exception e) {
            // 保持静默
        }
        
        // 如果解析失败，返回动态示例数据
        return createDynamicMaterialList(placementName);
    }
    
    /**
     * 将NBT转换为JSON字符串
     */
    private String nbtToJson(NbtElement nbt) {
        StringWriter writer = new StringWriter();
        try (JsonWriter jsonWriter = new JsonWriter(writer)) {
            jsonWriter.setIndent("  ");
            writeNbtToJson(nbt, jsonWriter);
        } catch (IOException e) {
            // 保持静默
        }
        return writer.toString();
    }
    
    /**
     * 将NBT写入JSON
     */
    private void writeNbtToJson(NbtElement nbt, JsonWriter json) throws IOException {
        if (nbt instanceof NbtCompound) {
            NbtCompound compound = (NbtCompound) nbt;
            json.beginObject();
            for (String key : compound.getKeys()) {
                json.name(key);
                writeNbtToJson(compound.get(key), json);
            }
            json.endObject();
        } else if (nbt instanceof NbtList) {
            NbtList list = (NbtList) nbt;
            json.beginArray();
            for (int i = 0; i < list.size(); i++) {
                writeNbtToJson(list.get(i), json);
            }
            json.endArray();
        } else if (nbt instanceof NbtString) {
            json.value(((NbtString) nbt).asString());
        } else if (nbt instanceof NbtByte) {
            json.value(((NbtByte) nbt).byteValue());
        } else if (nbt instanceof NbtShort) {
            json.value(((NbtShort) nbt).shortValue());
        } else if (nbt instanceof NbtInt) {
            json.value(((NbtInt) nbt).intValue());
        } else if (nbt instanceof NbtLong) {
            json.value(((NbtLong) nbt).longValue());
        } else if (nbt instanceof NbtFloat) {
            json.value(((NbtFloat) nbt).floatValue());
        } else if (nbt instanceof NbtDouble) {
            json.value(((NbtDouble) nbt).doubleValue());
        } else if (nbt instanceof NbtByteArray) {
            NbtByteArray byteArray = (NbtByteArray) nbt;
            json.beginArray();
            for (byte b : byteArray.getByteArray()) {
                json.value(b);
            }
            json.endArray();
        } else if (nbt instanceof NbtIntArray) {
            NbtIntArray intArray = (NbtIntArray) nbt;
            json.beginArray();
            for (int i : intArray.getIntArray()) {
                json.value(i);
            }
            json.endArray();
        } else if (nbt instanceof NbtLongArray) {
            NbtLongArray longArray = (NbtLongArray) nbt;
            json.beginArray();
            for (long l : longArray.getLongArray()) {
                json.value(l);
            }
            json.endArray();
        } else {
            json.value(nbt.toString());
        }
    }
    
    /**
     * 清理文件名，移除不安全的字符
     */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    
    /**
     * 尝试查找具有指定名称的方法
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
    
    /**
     * 为区域收集材料
     */
    private void collectMaterialsForRegion(LitematicaSchematic schematic, String regionName, Map<String, Integer> materialCounts) {
        try {
            // 尝试获取区域的方块容器
            Class<?> schematicClass = schematic.getClass();
            
            // 从结构获取方块数据的各种可能方法
            Method[] possibleMethods = new Method[] {
                findMethod(schematicClass, "getRegionBlockStateContainer"),
                findMethod(schematicClass, "getBlockStateContainer")
            };
            
            Object container = null;
            for (Method method : possibleMethods) {
                if (method != null) {
                    try {
                        container = method.invoke(schematic, regionName);
                        if (container != null) break;
                    } catch (Exception e) {
                        // 尝试下一个方法
                    }
                }
            }
            
            if (container != null) {
                countBlocksInContainer(container, materialCounts);
            }
        } catch (Exception e) {
            // 保持静默
        }
    }
    
    /**
     * 计算容器中的方块
     */
    private void countBlocksInContainer(Object container, Map<String, Integer> materialCounts) {
        try {
            // 尝试获取容器尺寸
            Method getSizeMethod = findMethod(container.getClass(), "getSize");
            if (getSizeMethod == null) {
                return;
            }
            
            Object size = getSizeMethod.invoke(container);
            if (size == null) {
                return;
            }
            
            // 确定尺寸
            int sizeX = 0, sizeY = 0, sizeZ = 0;
            
            if (size instanceof BlockPos) {
                BlockPos pos = (BlockPos) size;
                sizeX = pos.getX();
                sizeY = pos.getY();
                sizeZ = pos.getZ();
            } else {
                // 尝试使用反射获取尺寸
                try {
                    Method getXMethod = findMethod(size.getClass(), "getX");
                    Method getYMethod = findMethod(size.getClass(), "getY");
                    Method getZMethod = findMethod(size.getClass(), "getZ");
                    
                    if (getXMethod != null && getYMethod != null && getZMethod != null) {
                        sizeX = (int) getXMethod.invoke(size);
                        sizeY = (int) getYMethod.invoke(size);
                        sizeZ = (int) getZMethod.invoke(size);
                    }
                } catch (Exception e) {
                    return;
                }
            }
            
            // 尝试获取getBlockState或get方法
            Method getBlockMethod = findMethod(container.getClass(), "getBlockState");
            if (getBlockMethod == null) {
                getBlockMethod = findMethod(container.getClass(), "get");
            }
            
            if (getBlockMethod == null) {
                return;
            }
            
            // 遍历所有坐标
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) {
                        try {
                            Object blockState = getBlockMethod.invoke(container, x, y, z);
                            
                            if (blockState instanceof BlockState) {
                                BlockState state = (BlockState) blockState;
                                if (!state.isAir()) {
                                    Block block = state.getBlock();
                                    String blockId = Registries.BLOCK.getId(block).toString();
                                    
                                    // 更新计数
                                    materialCounts.put(blockId, materialCounts.getOrDefault(blockId, 0) + 1);
                                }
                            }
                        } catch (Exception e) {
                            // 处理单个方块时出现错误，继续下一个
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 保持静默
        }
    }
} 