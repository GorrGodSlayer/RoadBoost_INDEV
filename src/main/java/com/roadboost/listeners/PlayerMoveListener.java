package com.roadboost.listeners;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.bridge.BridgeSession;
import com.roadboost.managers.RecordingSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
        Block to   = event.getTo().getBlock();
        if (from.equals(to)) return;

        var player = event.getPlayer();
        var uuid   = player.getUniqueId();

        RecordingSession roadSession   = plugin.getSessionManager().getSession(uuid);
        BridgeSession    bridgeSession = plugin.getBridgeSessionManager().get(uuid);

        // If a bridge is active (either standalone or mid-road), handle it first
        if (bridgeSession != null) {
            boolean autoEnd = bridgeSession.onPlayerMove(player, from, to);
            if (autoEnd) {
                plugin.getBridgeSessionManager().remove(uuid);
                plugin.getBridgeCommand().commitSession(player, bridgeSession);
                player.sendMessage(Component.text(
                        "Bridge auto-completed — you reached solid ground!",
                        NamedTextColor.GREEN));
            }
            // Don't record road steps while bridge is active
            return;
        }

        // Road recording (only when no bridge is active)
        if (roadSession != null && !roadSession.isBridgeActive()) {
            roadSession.record(player, to, from);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();

        RecordingSession roadSession = plugin.getSessionManager().getSession(uuid);
        if (roadSession != null) {
            plugin.getSessionManager().removeSession(uuid);
            if (roadSession.size() > 0)
                plugin.getRoadManager().addRoadBlocks(roadSession.getRecorded());
        }

        BridgeSession bridgeSession = plugin.getBridgeSessionManager().remove(uuid);
        if (bridgeSession != null && bridgeSession.size() > 0)
            plugin.getRoadManager().addRoadBlocks(bridgeSession.getPlaced());
    }
}
