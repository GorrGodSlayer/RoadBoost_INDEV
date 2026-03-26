package com.roadboost.commands;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.bridge.BridgeSession;
import com.roadboost.managers.RecordingSession;
import com.roadboost.models.RoadBlock;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BridgeCommand implements CommandExecutor, TabCompleter {

    private final RoadBoostPlugin plugin;

    public BridgeCommand(RoadBoostPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("roadboost.build")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(player, args);
            case "stop"  -> handleStop(player);
            case "undo"  -> handleUndo(player, args);
            case "list"  -> handleList(player);
            default      -> sendHelp(player);
        }
        return true;
    }

    private void handleStart(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /bridge start <schematic>", NamedTextColor.RED)); return;
        }
        if (plugin.getBridgeSessionManager().has(player.getUniqueId())) {
            player.sendMessage(Component.text("Already building a bridge!", NamedTextColor.YELLOW)); return;
        }

        String schemName = args[1];
        Clipboard clipboard = plugin.getSchematicLoader().load(schemName);
        if (clipboard == null) {
            player.sendMessage(Component.text("Schematic '" + schemName + "' not found. Use /bridge list.", NamedTextColor.RED)); return;
        }

        int lockedY = player.getLocation().getBlockY();
        float yaw = player.getLocation().getYaw();

        Particle particle = null;
        String particleName = plugin.getConfig().getString("bridge-record-particle", "END_ROD");
        if (!particleName.equalsIgnoreCase("NONE")) {
            try { particle = Particle.valueOf(particleName.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        int clearHeight = plugin.getConfig().getInt("bridge-clear-height", 32);
        int moduleLengthOverride = plugin.getConfig().getInt("bridge-module-length", 0);

        BridgeSession session = new BridgeSession(clipboard, lockedY, particle,
                player.getLocation().getBlockX(), player.getLocation().getBlockZ(),
                yaw, plugin.getLogger(), clearHeight, moduleLengthOverride);
        session.stampFirst(player);
        plugin.getBridgeSessionManager().add(player.getUniqueId(), session);

        // If a road session is active, mark bridge as active on it
        RecordingSession roadSession = plugin.getSessionManager().getSession(player.getUniqueId());
        if (roadSession != null) {
            roadSession.setBridgeActive(true);
            player.sendMessage(Component.text("Bridge started as part of road from ",
                    NamedTextColor.GREEN)
                    .append(Component.text(roadSession.getFromName(), NamedTextColor.YELLOW))
                    .append(Component.text(". Run /bridge stop when done.", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("Bridge recording started! Walk your path. Run /bridge stop or reach land.", NamedTextColor.GREEN));
        }
        player.sendMessage(Component.text("Y locked at " + lockedY + ".", NamedTextColor.GRAY));
    }

    private void handleStop(Player player) {
        BridgeSession session = plugin.getBridgeSessionManager().remove(player.getUniqueId());
        if (session == null) {
            player.sendMessage(Component.text("Not building a bridge.", NamedTextColor.RED)); return;
        }
        commitSession(player, session);
    }

    public void commitSession(Player player, BridgeSession session) {
        if (session.size() == 0) {
            player.sendMessage(Component.text("No bridge blocks placed.", NamedTextColor.YELLOW));
        } else {
            // Check if a road session is active — merge into it
            RecordingSession roadSession = plugin.getSessionManager().getSession(player.getUniqueId());
            if (roadSession != null) {
                // Extract center-line waypoints from bridge placed blocks (simplified: use placed list)
                List<Location> bridgeWaypoints = extractWaypoints(session);
                roadSession.mergeBridge(session.getPlaced(), bridgeWaypoints);
                player.sendMessage(Component.text("Bridge merged into road! " + session.size()
                        + " block(s) placed. Continue walking to extend the road.", NamedTextColor.GREEN));
            } else {
                // Standalone bridge — commit directly to RoadManager
                plugin.getRoadManager().addRoadBlocks(session.getPlaced());
                player.sendMessage(Component.text("Bridge complete! " + session.size()
                        + " block(s) placed.", NamedTextColor.GREEN));
            }
        }
    }

    /** Extracts a center-line waypoint list from the bridge session.
     *  Uses the bridge's recorded forward steps — one point per step, at the anchor position.
     */
    private List<Location> extractWaypoints(BridgeSession session) {
    List<Location> result = new ArrayList<>();
    int lockedY = session.getLockedY() - 1;

    java.util.TreeMap<Integer, java.util.List<Location>> byForward = new java.util.TreeMap<>();
    for (RoadBlock rb : session.getPlaced()) {
        Location loc = rb.getLocation();
        if (loc.getBlockY() != lockedY) continue;
        byForward.computeIfAbsent(loc.getBlockX(), k -> new ArrayList<>()).add(loc);
    }

    if (byForward.size() <= 1) {
        byForward.clear();
        for (RoadBlock rb : session.getPlaced()) {
            Location loc = rb.getLocation();
            if (loc.getBlockY() != lockedY) continue;
            byForward.computeIfAbsent(loc.getBlockZ(), k -> new ArrayList<>()).add(loc);
        }
    }

    for (java.util.List<Location> group : byForward.values()) {
        group.sort((a, b) -> Integer.compare(a.getBlockZ(), b.getBlockZ()));
        result.add(group.get(group.size() / 2));
    }

    return result;
}

    private void handleUndo(Player player, String[] args) {
        int radius = plugin.getConfig().getInt("bridge-undo-radius", 5);
        if (args.length >= 2) {
            try { radius = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid radius.", NamedTextColor.RED)); return;
            }
        }
        int removed = plugin.getRoadManager().removeNearby(player.getLocation(), radius);
        if (removed == 0)
            player.sendMessage(Component.text("No bridge blocks found within " + radius + " blocks.", NamedTextColor.YELLOW));
        else
            player.sendMessage(Component.text("Removed " + removed + " block(s) and restored originals.", NamedTextColor.GREEN));
    }

    private void handleList(Player player) {
        Set<String> names = plugin.getSchematicLoader().getAvailableNames();
        if (names.isEmpty()) {
            player.sendMessage(Component.text("No schematics found in plugins/RoadBoost/schematics/.", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("Available schematics:", NamedTextColor.GOLD));
        for (String name : names)
            player.sendMessage(Component.text("  - " + name, NamedTextColor.YELLOW));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- Bridge Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/bridge start <schematic>", NamedTextColor.YELLOW)
                .append(Component.text(" - Begin walking a bridge.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bridge stop", NamedTextColor.YELLOW)
                .append(Component.text(" - Finish the bridge.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bridge undo [radius]", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove bridge blocks near you and restore originals.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bridge list", NamedTextColor.YELLOW)
                .append(Component.text(" - List available schematics.", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("start", "stop", "undo", "list");
        if (args.length == 2 && args[0].equalsIgnoreCase("start"))
            return new ArrayList<>(plugin.getSchematicLoader().getAvailableNames());
        return List.of();
    }
}
