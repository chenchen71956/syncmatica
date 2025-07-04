package ch.endte.syncmatica;

import ch.endte.syncmatica.communication.CommunicationManager;
import ch.endte.syncmatica.communication.FeatureSet;
import ch.endte.syncmatica.extended_core.PlayerIdentifierProvider;
import ch.endte.syncmatica.material.MaterialServerFeature;
import ch.endte.syncmatica.service.DebugService;
import ch.endte.syncmatica.service.IService;
import ch.endte.syncmatica.service.JsonConfiguration;
import ch.endte.syncmatica.service.QuotaService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Context {

    private final IFileStorage files;
    private final CommunicationManager comMan;
    private final SyncmaticManager synMan;
    private FeatureSet fs = null;
    private final boolean server;
    private final boolean integratedServer;
    private final File litematicFolder;
    private final File worldFolder;
    private boolean isStarted = false;
    private final QuotaService quota;
    private final DebugService debugService;
    private final PlayerIdentifierProvider playerIdentifierProvider;
    private final MaterialServerFeature materialServerFeature;
    private final List<Feature> features = new ArrayList<>();


    public Context(
            final IFileStorage fs,
            final CommunicationManager comMan,
            final SyncmaticManager synMan,
            final File litematicFolder
    ) {
        this(fs, comMan, synMan, false, litematicFolder, false, null);
    }

    public Context(
            final IFileStorage fs,
            final CommunicationManager comMan,
            final SyncmaticManager synMan,
            final boolean isServer,
            final File litematicFolder,
            final boolean integrated,
            final File worldFolder
    ) {
        files = fs;
        fs.setContext(this);
        this.comMan = comMan;
        comMan.setContext(this);
        this.synMan = synMan;
        synMan.setContext(this);
        server = isServer;
        if (isServer) {
            quota = new QuotaService();
            features.add(Feature.QUOTA);
        } else {
            quota = null;
        }
        playerIdentifierProvider = new PlayerIdentifierProvider(this);
        debugService = new DebugService();
        features.add(Feature.DEBUG);
        features.add(Feature.CORE);
        features.add(Feature.FEATURE);
        features.add(Feature.MODIFY);
        features.add(Feature.MESSAGE);
        features.add(Feature.CORE_EX);
        
        materialServerFeature = new MaterialServerFeature();
        
        this.litematicFolder = litematicFolder;
        litematicFolder.mkdirs();
        integratedServer = integrated;
        this.worldFolder = worldFolder;
        loadConfiguration();
    }

    public PlayerIdentifierProvider getPlayerIdentifierProvider() {
        return playerIdentifierProvider;
    }

    public IFileStorage getFileStorage() {
        return files;
    }

    public CommunicationManager getCommunicationManager() {
        return comMan;
    }

    public SyncmaticManager getSyncmaticManager() {
        return synMan;
    }

    public QuotaService getQuotaService() {
        return quota;
    }

    public DebugService getDebugService() {
        return debugService;
    }

    public MaterialServerFeature getMaterialServerFeature() {
        return materialServerFeature;
    }

    public FeatureSet getFeatureSet() {
        if (fs == null) {
            generateFeatureSet();
        }
        return fs;
    }

    public boolean isServer() {
        return server;
    }

    public boolean isIntegratedServer() {
        return integratedServer;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public File getLitematicFolder() {
        return litematicFolder;
    }

    private void generateFeatureSet() {
        fs = new FeatureSet(features);
    }

    public void startup() {
        startupServices();
        isStarted = true;
        synMan.startup();
        
        materialServerFeature.init(this);
        if (materialServerFeature.isReady()) {
            materialServerFeature.onEnable();
        }
    }

    public void shutdown() {
        shutdownServices();
        isStarted = false;
        synMan.shutdown();
        
        materialServerFeature.onDisable();
    }

    public boolean checkPartnerVersion(final String version) {
        return !version.equals("0.0.1");
    }

    public File getConfigFolder() {
        if (isServer() && isIntegratedServer()) {

            return new File(worldFolder, Syncmatica.MOD_ID);
        }
        return new File(new File("."), "config" + File.separator + Syncmatica.MOD_ID);
    }

    public File getConfigFile() {
        return new File(getConfigFolder(), "config.json");
    }

    public File getAndCreateConfigFile() throws IOException {
        getConfigFolder().mkdirs();
        final File configFile = getConfigFile();
        configFile.createNewFile();
        return configFile;
    }

    public void loadConfiguration() {
        boolean attemptToLoad = false;
        JsonObject configuration;
        try {
            configuration = new Gson().fromJson(new BufferedReader(new FileReader(getConfigFile())), JsonObject.class);
            attemptToLoad = true;
        } catch (final Exception ignored) {
            configuration = new JsonObject();
        }
        boolean needsRewrite = false;
        if (isServer()) {
            needsRewrite = loadConfigurationForService(quota, configuration, attemptToLoad);
        }
        needsRewrite |= loadConfigurationForService(debugService, configuration, attemptToLoad);
        if (needsRewrite) {
            try (
                    final Writer writer = new BufferedWriter(new FileWriter(getAndCreateConfigFile()))
            ) {
                final Gson gson = new GsonBuilder().setPrettyPrinting().create();
                final String jsonString = gson.toJson(configuration);
                writer.write(jsonString);
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Boolean loadConfigurationForService(final IService service, final JsonObject configuration, final boolean attemptToLoad) {
        final String configKey = service.getConfigKey();
        JsonObject serviceJson = null;
        JsonConfiguration serviceConfiguration = null;
        boolean started = false;

        if (attemptToLoad && configuration.has(configKey)) {
            try {
                serviceJson = configuration.getAsJsonObject(configKey);
                if (serviceJson != null) {
                    serviceConfiguration = new JsonConfiguration(serviceJson);
                    service.configure(serviceConfiguration);
                    started = true;
                    if (!serviceConfiguration.hadError()) {
                        return false;
                    }
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
        if (serviceJson == null) {
            serviceJson = new JsonObject();
            configuration.add(configKey, serviceJson);
        }
        if (serviceConfiguration == null) {
            serviceConfiguration = new JsonConfiguration(serviceJson);
        }
        service.getDefaultConfiguration(serviceConfiguration);
        if (!started) {
            service.configure(serviceConfiguration);
        }
        return true;
    }

    private void startupServices() {
        if (quota != null) {
            quota.startup();
        }
        debugService.startup();
    }

    private void shutdownServices() {
        if (quota != null) {
            quota.shutdown();
        }
        debugService.shutdown();
    }

    public static class DuplicateContextAssignmentException extends RuntimeException {
        private static final long serialVersionUID = -5147544661160756303L;

        public DuplicateContextAssignmentException(final String reason) {
            super(reason);
        }
    }

    public static class ContextMismatchException extends RuntimeException {
        private static final long serialVersionUID = 2769376183212635479L;

        public ContextMismatchException(final String reason) {
            super(reason);
        }
    }
}
