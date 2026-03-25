package com.roadboost.commands;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.bridge.BridgeSession;
import com.sk89q.worldedit.extent.clipboard.Clipboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    public BridgeCommand(RoadBoostPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("roadboost.build")) {
            player.sendMessage(Component.text("You don't have permission to build bridges.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(player, args);
            case "stop"  -> handleStop(player);
            case "list"  -> handleList(player);
            default -> sendHelp(player);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-commands
    // -------------------------------------------------------------------------

    private void handleStart(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /bridge start <schematic>", NamedTextColor.RED));
            player.sendMessage(Component.text("Use /bridge list to see available schematics.", NamedTextColor.GRAY));
            return;
        }

        if (plugin.getBridgeSessionManager().has(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already building a bridge! Use /bridge stop to finish.", NamedTextColor.YELLOW));
            return;
        }

        // Also check road session
        if (plugin.getSessionManager().hasSession(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already recording a road! Use /road stop first.", NamedTextColor.YELLOW));
            return;
        }

        String schemName = args[1];
        Clipboard schematicData = plugin.getSchematicLoader().load(schemName);
        if (schematicData == null) {
            player.sendMessage(Component.text("Schematic '" + schemName + "' not found. Use /bridge list to see available schematics.", NamedTextColor.RED));
            return;
        }

        int lockedY = player.getLocation().getBlockY();

        Particle particle = null;
        String particleName = plugin.getConfig().getString("bridge-record-particle", "END_ROD");
        if (!particleName.equalsIgnoreCase("NONE")) {
            try { particle = Particle.valueOf(particleName.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        int startX = player.getLocation().getBlockX();
        int startZ = player.getLocation().getBlockZ();
        float yaw = player.getLocation().getYaw();
int clearHeight = plugin.getConfig().getInt("bridge-clear-height", 32);
int moduleLengthOverride = plugin.getConfig().getInt("bridge-module-length", 0);
BridgeSession session = new BridgeSession(schematicData, lockedY, particle, startX, startZ, yaw, plugin.getLogger(), clearHeight, moduleLengthOverride);
        session.stampFirst(player);
        plugin.getBridgeSessionManager().add(player.getUniqueId(), session);

        player.sendMessage(Component.text("Bridge recording started using schematic '", NamedTextColor.GREEN)
                .append(Component.text(schemName, NamedTextColor.YELLOW))
                .append(Component.text("'! Walk your path. The bridge will auto-complete when you reach solid ground, or use ", NamedTextColor.GREEN))
                .append(Component.text("/bridge stop", NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Your Y level is locked at " + lockedY + ".", NamedTextColor.GRAY));
    }

    private void handleStop(Player player) {
        BridgeSession session = plugin.getBridgeSessionManager().remove(player.getUniqueId());
        if (session == null) {
            player.sendMessage(Component.text("You are not building a bridge. Use /bridge start <schematic> first.", NamedTextColor.RED));
            return;
        }

        commitSession(player, session);
    }

    private void handleList(Player player) {
        Set<String> names = plugin.getSchematicLoader().getAvailableNames();
        if (names.isEmpty()) {
            player.sendMessage(Component.text("No schematics found. Place .schem or .schematic files in the plugins/RoadBoost/schematics/ folder.", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("Available bridge schematics:", NamedTextColor.GOLD));
        for (String name : names) {
            player.sendMessage(Component.text("  - " + name, NamedTextColor.YELLOW));
        }
    }

    // -------------------------------------------------------------------------
    // Shared commit logic (also called from PlayerMoveListener on auto-end)
    // -------------------------------------------------------------------------

    public void commitSession(Player player, BridgeSession session) {
        if (session.size() == 0) {
            player.sendMessage(Component.text("No bridge blocks were placed.", NamedTextColor.YELLOW));
            return;
        }

        plugin.getRoadManager().addRoadBlocks(session.getPlaced());
        player.sendMessage(Component.text("Bridge complete! " + session.size() + " block(s) placed.", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- Bridge Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/bridge start <schematic>", NamedTextColor.YELLOW)
                .append(Component.text(" - Begin walking a bridge using the named schematic.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bridge stop", NamedTextColor.YELLOW)
                .append(Component.text(" - Finish the bridge manually.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bridge list", NamedTextColor.YELLOW)
                .append(Component.text(" - List available schematic modules.", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("start", "stop", "list");
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return new ArrayList<>(plugin.getSchematicLoader().getAvailableNames());
        }
        return List.of();
    }
}
