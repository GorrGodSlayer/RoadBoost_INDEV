package com.roadboost;

import com.roadboost.commands.RoadCommand;
import com.roadboost.listeners.PlayerMoveListener;
import com.roadboost.managers.RoadManager;
import com.roadboost.managers.SessionManager;
import com.roadboost.tasks.BoostTask;
import org.bukkit.plugin.java.JavaPlugin;

public class RoadBoostPlugin extends JavaPlugin {

    private RoadManager roadManager;
    private SessionManager sessionManager;
    private BoostTask boostTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Managers
        this.roadManager    = new RoadManager(this);
        this.sessionManager = new SessionManager();

        // Load persisted road data
        roadManager.loadRoads();

        // Listeners
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // Commands
        RoadCommand roadCommand = new RoadCommand(this);
        var cmd = getCommand("road");
        if (cmd != null) {
            cmd.setExecutor(roadCommand);
            cmd.setTabCompleter(roadCommand);
        }

        // Boost task
        this.boostTask = new BoostTask(this);
        long interval = getConfig().getLong("boost-check-interval", 20L);
        boostTask.runTaskTimer(this, interval, interval);

        getLogger().info("RoadBoost enabled.");
    }

    @Override
    public void onDisable() {
        // Commit any sessions still open (e.g. server shut down mid-recording)
        for (var entry : sessionManager.getAllSessions().entrySet()) {
            var session = entry.getValue();
            if (session.size() > 0) {
                roadManager.addRoadBlocks(session.getRecorded());
            }
        }
        boostTask.cancel();
        getLogger().info("RoadBoost disabled.");
    }

    public RoadManager getRoadManager()       { return roadManager; }
    public SessionManager getSessionManager() { return sessionManager; }
    public BoostTask getBoostTask()           { return boostTask; }
}
