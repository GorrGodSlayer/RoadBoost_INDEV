package com.roadboost.managers;

import com.roadboost.RoadBoostPlugin;
import com.roadboost.models.RoadBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Owns the master set of all road blocks across all worlds.
 * Persists data to roads.yml so roads survive server restarts.
 */
public class RoadManager {

    private final RoadBoostPlugin plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    // Keyed by "world,x,y,z" for O(1) lookups
    private final Map<String, RoadBlock> roadBlocks = new HashMap<>();

    public RoadManager(RoadBoostPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "roads.yml");
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public void loadRoads() {
        if (!dataFile.exists()) return;
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        List<Map<?, ?>> list = dataConfig.getMapList("roads");
        for (Map<?, ?> entry : list) {
            try {
                String worldName = (String) entry.get("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                int x = (int) entry.get("x");
                int y = (int) entry.get("y");
                int z = (int) entry.get("z");
                String originalDataStr = (String) entry.get("original");

                Location loc = new Location(world, x, y, z);
                var originalData = Bukkit.createBlockData(originalDataStr);
                RoadBlock rb = new RoadBlock(loc, originalData);
                roadBlocks.put(key(loc), rb);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load a road block entry.", e);
            }
        }

        plugin.getLogger().info("Loaded " + roadBlocks.size() + " road blocks.");
    }

    public void saveRoads() {
        if (dataConfig == null) dataConfig = new YamlConfiguration();

        List<Map<String, Object>> list = new ArrayList<>();
        for (RoadBlock rb : roadBlocks.values()) {
            Location loc = rb.getLocation();
            if (loc.getWorld() == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", loc.getWorld().getName());
            entry.put("x", loc.getBlockX());
            entry.put("y", loc.getBlockY());
            entry.put("z", loc.getBlockZ());
            entry.put("original", rb.getOriginalData().getAsString());
            list.add(entry);
        }

        dataConfig.set("roads", list);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save roads.yml!", e);
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Register a collection of road blocks (called when a player stops recording). */
    public void addRoadBlocks(Collection<RoadBlock> blocks) {
        for (RoadBlock rb : blocks) {
            roadBlocks.put(key(rb.getLocation()), rb);
        }
        saveRoads();
    }

    /** Returns true if the given block location is part of a road. */
    public boolean isRoad(Location loc) {
        return roadBlocks.containsKey(key(loc));
    }

    /**
     * Remove all road blocks within {@code radius} of {@code centre},
     * restoring original blocks.
     * @return Number of blocks removed.
     */
    public int removeNearby(Location centre, int radius) {
        int removed = 0;
        Iterator<Map.Entry<String, RoadBlock>> it = roadBlocks.entrySet().iterator();

        while (it.hasNext()) {
            RoadBlock rb = it.next().getValue();
            Location loc = rb.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(centre.getWorld())) continue;

            double dist = loc.distance(centre);
            if (dist <= radius) {
                Block block = loc.getBlock();
                block.setBlockData(rb.getOriginalData());
                it.remove();
                removed++;
            }
        }

        if (removed > 0) saveRoads();
        return removed;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
