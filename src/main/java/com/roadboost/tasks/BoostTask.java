package com.roadboost.tasks;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.bridge.BridgeSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class BoostTask extends BukkitRunnable {

    private final RoadBoostPlugin plugin;
    private List<PotionEffect> effects;

    public BoostTask(RoadBoostPlugin plugin) {
        this.plugin = plugin;
        reloadEffects();
    }

    public void reloadEffects() {
        effects = new ArrayList<>();
        List<Map<?, ?>> effectList = plugin.getConfig().getMapList("effects");

        for (Map<?, ?> entry : effectList) {
            try {
                String typeName = ((String) entry.get("type")).toUpperCase();
                int amplifier   = (int) entry.get("amplifier");
                int duration    = (int) entry.get("duration");

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
            Location feet = player.getLocation().clone().subtract(0, 1, 0);

            boolean onRoadOrBridge = plugin.getRoadManager().isRoad(feet);

            // Also check active (not yet committed) bridge sessions
            if (!onRoadOrBridge) {
                BridgeSession activeSession = plugin.getBridgeSessionManager().get(player.getUniqueId());
                if (activeSession != null && activeSession.isActiveBridgeBlock(feet)) {
                    onRoadOrBridge = true;
                }
            }

            if (!onRoadOrBridge) continue;

            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }
        }
    }
}
