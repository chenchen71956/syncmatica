package ch.endte.syncmatica.material;

import ch.endte.syncmatica.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 材料HTTP服务器功能，提供基于Web的API来获取Syncmatica结构的材料列表
 */
public class MaterialServerFeature {
    private static final Logger LOGGER = LogManager.getLogger();
    private MaterialHttpServer materialHttpServer;
    protected Context context;

    public void init(Context context) {
        this.context = context;
        materialHttpServer = new MaterialHttpServer(context);
    }

    /**
     * 启动材料HTTP服务器
     * @return 是否成功启动
     */
    public boolean onEnable() {
        try {
            materialHttpServer.start();
            return true;
        } catch (Exception e) {
            LOGGER.error("无法启动材料HTTP服务器", e);
            return false;
        }
    }

    /**
     * 停止材料HTTP服务器
     */
    public void onDisable() {
        if (materialHttpServer != null) {
            try {
                materialHttpServer.stop();
            } catch (Exception e) {
                LOGGER.error("停止材料HTTP服务器时出错", e);
            }
        }
    }

    public MaterialHttpServer getMaterialServer() {
        return materialHttpServer;
    }

    public boolean isReady() {
        return context != null && context.isStarted();
    }

    public String getName() {
        return "MaterialServer";
    }
} 