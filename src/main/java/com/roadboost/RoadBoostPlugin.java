package com.roadboost;

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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Managers
        this.roadManager          = new RoadManager(this);
        this.sessionManager       = new SessionManager();
        this.bridgeSessionManager = new BridgeSessionManager();

        // Schematic loader
        String folderName = getConfig().getString("bridge-schematics-folder", "schematics");
        File schematicsFolder = new File(getDataFolder(), folderName);
        this.schematicLoader = new SchematicLoader(schematicsFolder, getLogger());

        // Load persisted roads
        roadManager.loadRoads();

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // Commands
        RoadCommand roadCommand = new RoadCommand(this);
        var roadCmd = getCommand("road");
        if (roadCmd != null) {
            roadCmd.setExecutor(roadCommand);
            roadCmd.setTabCompleter(roadCommand);
        }

        this.bridgeCommand = new BridgeCommand(this);
        var bridgeCmd = getCommand("bridge");
        if (bridgeCmd != null) {
            bridgeCmd.setExecutor(bridgeCommand);
            bridgeCmd.setTabCompleter(bridgeCommand);
        }

        // Boost task
        this.boostTask = new BoostTask(this);
        long interval = getConfig().getLong("boost-check-interval", 20L);
        boostTask.runTaskTimer(this, interval, interval);

        getLogger().info("RoadBoost enabled.");
    }

    @Override
    public void onDisable() {
        // Commit open road sessions
        for (var entry : sessionManager.getAllSessions().entrySet()) {
            var s = entry.getValue();
            if (s.size() > 0) roadManager.addRoadBlocks(s.getRecorded());
        }
        // Commit open bridge sessions
        for (var entry : bridgeSessionManager.allMap().entrySet()) {
            var s = entry.getValue();
            if (s.size() > 0) roadManager.addRoadBlocks(s.getPlaced());
        }
        boostTask.cancel();
        getLogger().info("RoadBoost disabled.");
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public RoadManager getRoadManager()                 { return roadManager; }
    public SessionManager getSessionManager()           { return sessionManager; }
    public BridgeSessionManager getBridgeSessionManager() { return bridgeSessionManager; }
    public SchematicLoader getSchematicLoader()         { return schematicLoader; }
    public BoostTask getBoostTask()                     { return boostTask; }
    public BridgeCommand getBridgeCommand()             { return bridgeCommand; }
}
