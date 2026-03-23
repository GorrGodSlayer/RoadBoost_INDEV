package com.roadboost.tasks;

import com.roadboost.RoadBoostPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Runs every {@code boost-check-interval} ticks.
 * For each online player, checks if the block beneath their feet is a road
 * block and, if so, applies all configured potion effects.
 */
public class BoostTask extends BukkitRunnable {

    private final RoadBoostPlugin plugin;
    private List<PotionEffect> effects;

    public BoostTask(RoadBoostPlugin plugin) {
        this.plugin = plugin;
        reloadEffects();
    }

    /** Re-parse effects from config (called on /road reload). */
    public void reloadEffects() {
        effects = new ArrayList<>();
        List<Map<?, ?>> effectList = plugin.getConfig().getMapList("effects");

        for (Map<?, ?> entry : effectList) {
            try {
                String typeName = ((String) entry.get("type")).toUpperCase();
                int amplifier = (int) entry.get("amplifier");
                int duration = (int) entry.get("duration");

                PotionEffectType type = PotionEffectType.getByName(typeName);
                if (type == null) {
                    plugin.getLogger().warning("Unknown potion effect type: " + typeName);
                    continue;
                }

                effects.add(new PotionEffect(type, duration, amplifier, true, false, false));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse an effect entry.", e);
            }
        }
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // The block under the player's feet
            Location feet = player.getLocation().clone().subtract(0, 1, 0);
            if (!plugin.getRoadManager().isRoad(feet)) continue;

            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }
        }
    }
}
