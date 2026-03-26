package com.roadboost.tasks;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.bridge.BridgeSession;
import com.roadboost.models.RoadDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

public class BoostTask extends BukkitRunnable {

    private final RoadBoostPlugin plugin;
    private List<PotionEffect> effects;

    // Tracks which road each player was last notified about — prevents spam
    private final Map<UUID, String> lastNotifiedRoad = new HashMap<>();

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
                if (type == null) { plugin.getLogger().warning("Unknown effect: " + typeName); continue; }
                effects.add(new PotionEffect(type, duration, amplifier, true, false, false));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to parse effect.", e);
            }
        }
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location feet = player.getLocation().clone().subtract(0, 1, 0);

            boolean onRoadOrBridge = plugin.getRoadManager().isRoad(feet);

            // Check active bridge session
            if (!onRoadOrBridge) {
                BridgeSession activeSession = plugin.getBridgeSessionManager().get(player.getUniqueId());
                if (activeSession != null && activeSession.isActiveBridgeBlock(feet)) {
                    onRoadOrBridge = true;
                }
            }

            if (!onRoadOrBridge) {
                // Player left the road — clear notification so they get it again next time
                lastNotifiedRoad.remove(player.getUniqueId());
                continue;
            }

            // Apply boost effects
            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }

            // Road name notification — only once per road
            RoadDefinition road = plugin.getRoadManager().getRoadAt(feet);
            if (road != null) {
                String lastId = lastNotifiedRoad.get(player.getUniqueId());
                if (!road.getId().equals(lastId)) {
                    lastNotifiedRoad.put(player.getUniqueId(), road.getId());
                    player.sendMessage(Component.text("You are travelling on the ", NamedTextColor.GRAY)
                            .append(Component.text(road.getDisplayName(), NamedTextColor.GOLD))
                            .append(Component.text(".", NamedTextColor.GRAY)));
                }
            }
        }
    }
}
