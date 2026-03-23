package com.roadboost.listeners;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.managers.RecordingSession;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerMoveListener implements Listener {

    private final RoadBoostPlugin plugin;

    public PlayerMoveListener(RoadBoostPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Block from = event.getFrom().getBlock();
        Block to = event.getTo().getBlock();

        if (from.equals(to)) return;

        RecordingSession session = plugin.getSessionManager().getSession(event.getPlayer().getUniqueId());
        if (session == null) return;

        session.record(event.getPlayer(), to, from);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        RecordingSession session = plugin.getSessionManager().getSession(uuid);
        if (session == null) return;

        plugin.getSessionManager().removeSession(uuid);
        if (session.size() > 0) {
            plugin.getRoadManager().addRoadBlocks(session.getRecorded());
        }
    }
}