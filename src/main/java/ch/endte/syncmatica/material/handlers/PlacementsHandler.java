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
import java.util.Map;

/**
 * 处理获取所有投影列表的API请求
 */
public class PlacementsHandler implements HttpHandler {
    private final MaterialHttpServer server;
    private final Context context;
    private final Gson gson;
    
    public PlacementsHandler(MaterialHttpServer server, Context context, Gson gson) {
        this.server = server;
        this.context = context;
        this.gson = gson;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            server.sendResponse(exchange, 405, server.getTranslatedText("syncmatica.error.method_not_supported", "Method not supported"));
            return;
        }

        try {
            Collection<ServerPlacement> placements = context.getSyncmaticManager().getAll();
            JsonArray placementsArray = new JsonArray();

            for (ServerPlacement placement : placements) {
                JsonObject placementJson = new JsonObject();
                placementJson.addProperty("id", placement.getId().toString());
                placementJson.addProperty("name", placement.getName());
                placementJson.addProperty("dimension", placement.getDimension());
                placementJson.addProperty("posX", placement.getPosition().getX());
                placementJson.addProperty("posY", placement.getPosition().getY());
                placementJson.addProperty("posZ", placement.getPosition().getZ());
                placementJson.addProperty("owner", placement.getOwner().getName());
                placementsArray.add(placementJson);
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.add("placements", placementsArray);

            server.sendResponse(exchange, 200, gson.toJson(response));
        } catch (Exception e) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("error", e.getMessage());
            server.sendResponse(exchange, 500, gson.toJson(errorResponse));
        }
    }
} 