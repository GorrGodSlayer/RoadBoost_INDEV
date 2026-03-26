package com.roadboost.commands;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.managers.RecordingSession;
import com.roadboost.managers.WeightedBlockPicker;
import com.roadboost.models.RoadDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    public RoadCommand(RoadBoostPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "start"  -> handleStart(player, args);
            case "stop"   -> handleStop(player, args);
            case "undo"   -> handleUndo(player, args);
            case "reload" -> handleReload(player);
            default       -> sendHelp(player);
        }
        return true;
    }

    // -------------------------------------------------------------------------

    private void handleStart(Player player, String[] args) {
        if (!player.hasPermission("roadboost.build")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /road start <from-name>", NamedTextColor.RED)); return;
        }
        if (plugin.getSessionManager().hasSession(player.getUniqueId())) {
            player.sendMessage(Component.text("Already recording! Use /road stop <to-name>.", NamedTextColor.YELLOW)); return;
        }
        if (plugin.getBridgeSessionManager().has(player.getUniqueId())) {
            player.sendMessage(Component.text("Finish your bridge first with /bridge stop.", NamedTextColor.YELLOW)); return;
        }

        String fromName = args[1];

        WeightedBlockPicker picker = new WeightedBlockPicker(
                plugin.getConfig().getStringList("road-blocks"));
        int width          = plugin.getConfig().getInt("road-width", 1);
        int corridorHeight = plugin.getConfig().getInt("road-corridor-height", 4);
        int forestRadius   = plugin.getConfig().getInt("road-forest-radius", 5);

        Particle particle = null;
        String particleName = plugin.getConfig().getString("record-particle", "HAPPY_VILLAGER");
        if (!particleName.equalsIgnoreCase("NONE")) {
            try { particle = Particle.valueOf(particleName.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        RecordingSession session = new RecordingSession(
                picker, width, particle, corridorHeight, forestRadius, fromName);
        plugin.getSessionManager().addSession(player.getUniqueId(), session);

        player.sendMessage(Component.text("Road recording started from ", NamedTextColor.GREEN)
                .append(Component.text(fromName, NamedTextColor.YELLOW))
                .append(Component.text(". Walk your path. You can run /bridge start <schematic> mid-road.", NamedTextColor.GREEN)));
    }

    private void handleStop(Player player, String[] args) {
        if (!player.hasPermission("roadboost.build")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /road stop <to-name>", NamedTextColor.RED)); return;
        }

        RecordingSession session = plugin.getSessionManager().removeSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(Component.text("Not recording a road. Use /road start <from-name>.", NamedTextColor.RED)); return;
        }
        if (session.isBridgeActive()) {
            player.sendMessage(Component.text("Finish your bridge first with /bridge stop.", NamedTextColor.YELLOW)); return;
        }
        if (session.size() == 0) {
            player.sendMessage(Component.text("No blocks recorded — walk further before stopping.", NamedTextColor.YELLOW)); return;
        }

        String toName = args[1];
        RoadDefinition def = session.buildDefinition(toName);

        session.commit();
        plugin.getRoadManager().addRoad(def, session.getRecorded());

        // Update BlueMap
        if (plugin.getBlueMapIntegration() != null) {
            plugin.getBlueMapIntegration().updateRoad(def);
        }

        playPlaceEffects(player);

        player.sendMessage(Component.text("Road ", NamedTextColor.GREEN)
                .append(Component.text(def.getDisplayName(), NamedTextColor.GOLD))
                .append(Component.text(" created! " + session.getRecorded().size()
                        + " block(s) placed.", NamedTextColor.GREEN)));
    }

    private void handleUndo(Player player, String[] args) {
        if (!player.hasPermission("roadboost.build")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return;
        }
        int radius = plugin.getConfig().getInt("undo-radius", 5);
        if (args.length >= 2) {
            try { radius = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid radius.", NamedTextColor.RED)); return;
            }
        }
        int removed = plugin.getRoadManager().removeNearby(player.getLocation(), radius);
        if (removed == 0)
            player.sendMessage(Component.text("No road blocks found within " + radius + " blocks.", NamedTextColor.YELLOW));
        else
            player.sendMessage(Component.text("Removed " + removed + " block(s) and restored originals.", NamedTextColor.GREEN));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("roadboost.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return;
        }
        plugin.reloadConfig();
        plugin.getBoostTask().reloadEffects();
        player.sendMessage(Component.text("RoadBoost config reloaded.", NamedTextColor.GREEN));
    }

    private void playPlaceEffects(Player player) {
        String particleName = plugin.getConfig().getString("place-particle", "HAPPY_VILLAGER");
        String soundName    = plugin.getConfig().getString("place-sound", "BLOCK_NOTE_BLOCK_PLING");
        if (!particleName.equalsIgnoreCase("NONE")) {
            try { player.getWorld().spawnParticle(
                    Particle.valueOf(particleName.toUpperCase()), player.getLocation(), 20, 1, 1, 1); }
            catch (Exception ignored) {}
        }
        if (!soundName.equalsIgnoreCase("NONE")) {
            try { player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, 1f); }
            catch (Exception ignored) {}
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- RoadBoost Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/road start <from>", NamedTextColor.YELLOW)
                .append(Component.text(" - Begin recording from a named point.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/road stop <to>", NamedTextColor.YELLOW)
                .append(Component.text(" - Stop and name the destination.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/road undo [radius]", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove road blocks near you.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/road reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload config.", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("start", "stop", "undo", "reload");
        return List.of();
    }
}
