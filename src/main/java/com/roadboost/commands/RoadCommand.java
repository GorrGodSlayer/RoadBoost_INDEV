package com.roadboost.commands;
import org.bukkit.Particle;
import com.roadboost.RoadBoostPlugin;
import com.roadboost.managers.RecordingSession;
import com.roadboost.managers.WeightedBlockPicker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RoadCommand implements CommandExecutor, TabCompleter {

    private final RoadBoostPlugin plugin;

    public RoadCommand(RoadBoostPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(player);
            case "stop"  -> handleStop(player);
            case "undo"  -> handleUndo(player, args);
            case "reload" -> handleReload(player);
            default -> sendHelp(player);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Sub-commands
    // -------------------------------------------------------------------------

    private void handleStart(Player player) {
        if (!player.hasPermission("roadboost.build")) {
            player.sendMessage(Component.text("You don't have permission to build roads.", NamedTextColor.RED));
            return;
        }

        if (plugin.getSessionManager().hasSession(player.getUniqueId())) {
            player.sendMessage(Component.text("You are already recording a road! Use /road stop to finish.", NamedTextColor.YELLOW));
            return;
        }

        // Build the weighted picker and create a session
        WeightedBlockPicker picker = new WeightedBlockPicker(
        plugin.getConfig().getStringList("road-blocks")
);
int width = plugin.getConfig().getInt("road-width", 1);

Particle particle = null;
String particleName = plugin.getConfig().getString("record-particle", "DUST");
if (!particleName.equalsIgnoreCase("NONE")) {
    try {
        particle = Particle.valueOf(particleName.toUpperCase());
    } catch (IllegalArgumentException ignored) {}
}

RecordingSession session = new RecordingSession(picker, width, particle);
        plugin.getSessionManager().addSession(player.getUniqueId(), session);
        player.sendMessage(Component.text("Road recording started! Walk your desired path, then run ", NamedTextColor.GREEN)
                .append(Component.text("/road stop", NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void handleStop(Player player) {
        if (!player.hasPermission("roadboost.build")) {
            player.sendMessage(Component.text("You don't have permission to build roads.", NamedTextColor.RED));
            return;
        }

        RecordingSession session = plugin.getSessionManager().removeSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(Component.text("You are not recording a road. Use /road start first.", NamedTextColor.RED));
            return;
        }

        if (session.size() == 0) {
            player.sendMessage(Component.text("No blocks were recorded — try walking further before stopping.", NamedTextColor.YELLOW));
            return;
        }

        // Commit the road
        plugin.getRoadManager().addRoadBlocks(session.getRecorded());

        // Visual / audio feedback
        playPlaceEffects(player);

        player.sendMessage(Component.text("Road created! " + session.size() + " block(s) placed.", NamedTextColor.GREEN));
    }

    private void handleUndo(Player player, String[] args) {
        if (!player.hasPermission("roadboost.build")) {
            player.sendMessage(Component.text("You don't have permission to remove roads.", NamedTextColor.RED));
            return;
        }

        int radius = plugin.getConfig().getInt("undo-radius", 5);

        // Allow optional override: /road undo 10
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid radius. Usage: /road undo [radius]", NamedTextColor.RED));
                return;
            }
        }

        Location feet = player.getLocation();
        int removed = plugin.getRoadManager().removeNearby(feet, radius);

        if (removed == 0) {
            player.sendMessage(Component.text("No road blocks found within " + radius + " block(s) of you.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Removed " + removed + " road block(s) within " + radius + " block(s) and restored originals.", NamedTextColor.GREEN));
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("roadboost.admin")) {
            player.sendMessage(Component.text("You don't have permission to reload the config.", NamedTextColor.RED));
            return;
        }

        plugin.reloadConfig();
        plugin.getBoostTask().reloadEffects();
        player.sendMessage(Component.text("RoadBoost config reloaded.", NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void playPlaceEffects(Player player) {
        String particleName = plugin.getConfig().getString("place-particle", "HAPPY_VILLAGER");
        String soundName    = plugin.getConfig().getString("place-sound", "BLOCK_NOTE_BLOCK_PLING");

        if (!particleName.equalsIgnoreCase("NONE")) {
            try {
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                player.getWorld().spawnParticle(particle, player.getLocation(), 20, 1, 1, 1);
            } catch (IllegalArgumentException ignored) {}
        }

        if (!soundName.equalsIgnoreCase("NONE")) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1f, 1f);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- RoadBoost Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/road start", NamedTextColor.YELLOW)
                .append(Component.text(" - Begin recording your path as a road.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/road stop", NamedTextColor.YELLOW)
                .append(Component.text(" - Stop recording and place the road.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/road undo [radius]", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove road blocks near you and restore originals.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/road reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload the config (admin only).", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop", "undo", "reload");
        }
        return List.of();
    }
}
