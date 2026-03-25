package com.roadboost.listeners;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.bridge.BridgeSession;
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
        Block to   = event.getTo().getBlock();
        if (from.equals(to)) return;

        var player = event.getPlayer();
        var uuid   = player.getUniqueId();

        // --- Road recording ---
        RecordingSession roadSession = plugin.getSessionManager().getSession(uuid);
        if (roadSession != null) {
            roadSession.record(player, to, from);
            return;
        }

        // --- Bridge recording ---
        BridgeSession bridgeSession = plugin.getBridgeSessionManager().get(uuid);
        if (bridgeSession != null) {
            boolean autoEnd = bridgeSession.onPlayerMove(player, from, to);
            if (autoEnd) {
                plugin.getBridgeSessionManager().remove(uuid);
                plugin.getBridgeCommand().commitSession(player, bridgeSession);
                player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                        "Bridge auto-completed — you reached solid ground!",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN
                    )
                );
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();

        // Commit road session on disconnect
        RecordingSession roadSession = plugin.getSessionManager().getSession(uuid);
        if (roadSession != null) {
            plugin.getSessionManager().removeSession(uuid);
            if (roadSession.size() > 0) {
                plugin.getRoadManager().addRoadBlocks(roadSession.getRecorded());
            }
        }

        // Commit bridge session on disconnect
        BridgeSession bridgeSession = plugin.getBridgeSessionManager().remove(uuid);
        if (bridgeSession != null && bridgeSession.size() > 0) {
            plugin.getRoadManager().addRoadBlocks(bridgeSession.getPlaced());
        }
    }
}
