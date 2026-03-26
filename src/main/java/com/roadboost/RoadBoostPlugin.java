package com.roadboost;

import com.roadboost.bluemap.BlueMapIntegration;
import com.roadboost.bridge.BridgeSessionManager;
import com.roadboost.bridge.SchematicLoader;
import com.roadboost.commands.BridgeCommand;
import com.roadboost.commands.RoadCommand;
import com.roadboost.listeners.PlayerMoveListener;
import com.roadboost.managers.RoadManager;
import com.roadboost.managers.SessionManager;
import com.roadboost.tasks.BoostTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class RoadBoostPlugin extends JavaPlugin {

    private RoadManager roadManager;
    private SessionManager sessionManager;
    private BridgeSessionManager bridgeSessionManager;
    private SchematicLoader schematicLoader;
    private BoostTask boostTask;
    private BridgeCommand bridgeCommand;
    private BlueMapIntegration blueMapIntegration;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        roadManager          = new RoadManager(this);
        sessionManager       = new SessionManager();
        bridgeSessionManager = new BridgeSessionManager();

        String folderName = getConfig().getString("bridge-schematics-folder", "schematics");
        schematicLoader = new SchematicLoader(new File(getDataFolder(), folderName), getLogger());

        roadManager.loadRoads();

        // BlueMap integration — use onEnable callback so API is guaranteed ready
        if (getServer().getPluginManager().getPlugin("BlueMap") != null) {
            blueMapIntegration = new BlueMapIntegration(this);
            de.bluecolored.bluemap.api.BlueMapAPI.onEnable(api -> {
                getLogger().info("BlueMap API ready — road markers enabled.");
                // Re-register all existing roads on BlueMap enable/reload
                for (com.roadboost.models.RoadDefinition def : roadManager.getRoadDefinitions().values()) {
                    blueMapIntegration.updateRoad(def);
                }
            });
        } else {
            getLogger().info("BlueMap not found — map markers disabled.");
        }

        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        RoadCommand roadCommand = new RoadCommand(this);
        var rc = getCommand("road");
        if (rc != null) { rc.setExecutor(roadCommand); rc.setTabCompleter(roadCommand); }

        bridgeCommand = new BridgeCommand(this);
        var bc = getCommand("bridge");
        if (bc != null) { bc.setExecutor(bridgeCommand); bc.setTabCompleter(bridgeCommand); }

        boostTask = new BoostTask(this);
        long interval = getConfig().getLong("boost-check-interval", 20L);
        boostTask.runTaskTimer(this, interval, interval);

        getLogger().info("RoadBoost enabled.");
    }

    @Override
    public void onDisable() {
        for (var entry : sessionManager.getAllSessions().entrySet()) {
            var s = entry.getValue();
            if (s.size() > 0) roadManager.addRoadBlocks(s.getRecorded());
        }
        for (var entry : bridgeSessionManager.allMap().entrySet()) {
            var s = entry.getValue();
            if (s.size() > 0) roadManager.addRoadBlocks(s.getPlaced());
        }
        boostTask.cancel();
        getLogger().info("RoadBoost disabled.");
    }

    public RoadManager getRoadManager()                   { return roadManager; }
    public SessionManager getSessionManager()             { return sessionManager; }
    public BridgeSessionManager getBridgeSessionManager() { return bridgeSessionManager; }
    public SchematicLoader getSchematicLoader()           { return schematicLoader; }
    public BoostTask getBoostTask()                       { return boostTask; }
    public BridgeCommand getBridgeCommand()               { return bridgeCommand; }
    public BlueMapIntegration getBlueMapIntegration()     { return blueMapIntegration; }
}
