package com.roadboost.managers;

import com.roadboost.models.RoadBlock;
import com.roadboost.models.RoadDefinition;
import com.roadboost.models.RoadDefinition.Waypoint;
import com.roadboost.RoadBoostPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class RoadManager {

    private final RoadBoostPlugin plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    // All placed road blocks keyed by "world,x,y,z"
    private final Map<String, RoadBlock> roadBlocks = new HashMap<>();

    // Road definitions keyed by road ID
    private final Map<String, RoadDefinition> roadDefinitions = new HashMap<>();

    // Block key -> road ID for fast road-name lookup
    private final Map<String, String> blockToRoad = new HashMap<>();

    public RoadManager(RoadBoostPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "roads.yml");
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    public void loadRoads() {
        if (!dataFile.exists()) return;
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Load blocks
        List<Map<?, ?>> blockList = dataConfig.getMapList("blocks");
        for (Map<?, ?> entry : blockList) {
            try {
                String worldName = (String) entry.get("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                int x = (int) entry.get("x");
                int y = (int) entry.get("y");
                int z = (int) entry.get("z");
                String originalData = (String) entry.get("original");
                String roadId = (String) entry.get("roadId");
                Location loc = new Location(world, x, y, z);
                RoadBlock rb = new RoadBlock(loc, Bukkit.createBlockData(originalData));
                String k = key(loc);
                roadBlocks.put(k, rb);
                if (roadId != null) blockToRoad.put(k, roadId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load road block.", e);
            }
        }

        // Load definitions
        List<Map<?, ?>> defList = dataConfig.getMapList("definitions");
        for (Map<?, ?> entry : defList) {
            try {
                String id       = (String) entry.get("id");
                String from     = (String) entry.get("from");
                String to       = (String) entry.get("to");
                @SuppressWarnings("unchecked")
                List<Map<?, ?>> wps = (List<Map<?, ?>>) entry.get("waypoints");
                List<Waypoint> waypoints = new ArrayList<>();
                if (wps != null) {
                    for (Map<?, ?> wp : wps) {
                        double wx = ((Number) wp.get("x")).doubleValue();
                        double wy = ((Number) wp.get("y")).doubleValue();
                        double wz = ((Number) wp.get("z")).doubleValue();
                        String world = (String) wp.get("world");
                        Object typeObj = wp.get("type");
                        RoadDefinition.SegmentType type = RoadDefinition.SegmentType.valueOf(
                                typeObj != null ? typeObj.toString() : "ROAD");
                        waypoints.add(new Waypoint(wx, wy, wz, world, type));
                    }
                }
                roadDefinitions.put(id, new RoadDefinition(id, from, to, waypoints));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load road definition.", e);
            }
        }

        plugin.getLogger().info("Loaded " + roadBlocks.size() + " road blocks and "
                + roadDefinitions.size() + " road definitions.");
    }

    public void saveRoads() {
        if (dataConfig == null) dataConfig = new YamlConfiguration();

        // Save blocks
        List<Map<String, Object>> blockList = new ArrayList<>();
        for (Map.Entry<String, RoadBlock> entry : roadBlocks.entrySet()) {
            RoadBlock rb = entry.getValue();
            Location loc = rb.getLocation();
            if (loc.getWorld() == null) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", loc.getWorld().getName());
            map.put("x", loc.getBlockX());
            map.put("y", loc.getBlockY());
            map.put("z", loc.getBlockZ());
            map.put("original", rb.getOriginalData().getAsString());
            String roadId = blockToRoad.get(entry.getKey());
            if (roadId != null) map.put("roadId", roadId);
            blockList.add(map);
        }
        dataConfig.set("blocks", blockList);

        // Save definitions
        List<Map<String, Object>> defList = new ArrayList<>();
        for (RoadDefinition def : roadDefinitions.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", def.getId());
            map.put("from", def.getFromName());
            map.put("to", def.getToName());
            List<Map<String, Object>> wps = new ArrayList<>();
            for (Waypoint wp : def.getWaypoints()) {
                Map<String, Object> wm = new LinkedHashMap<>();
                wm.put("x", wp.x); wm.put("y", wp.y); wm.put("z", wp.z);
                wm.put("world", wp.world);
                wm.put("type", wp.type.name());
                wps.add(wm);
            }
            map.put("waypoints", wps);
            defList.add(map);
        }
        dataConfig.set("definitions", defList);

        try { dataConfig.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save roads.yml!", e); }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void addRoad(RoadDefinition def, Collection<RoadBlock> blocks) {
        for (RoadBlock rb : blocks) {
            String k = key(rb.getLocation());
            roadBlocks.put(k, rb);
            blockToRoad.put(k, def.getId());
        }
        roadDefinitions.put(def.getId(), def);
        saveRoads();
    }

    // Legacy — for bridge commit without a named road
    public void addRoadBlocks(Collection<RoadBlock> blocks) {
        for (RoadBlock rb : blocks) {
            roadBlocks.put(key(rb.getLocation()), rb);
        }
        saveRoads();
    }

    public boolean isRoad(Location loc) {
        return roadBlocks.containsKey(key(loc));
    }

    /** Returns the RoadDefinition for the block at the given location, or null. */
    public RoadDefinition getRoadAt(Location loc) {
        String roadId = blockToRoad.get(key(loc));
        if (roadId == null) return null;
        return roadDefinitions.get(roadId);
    }

    public int removeNearby(Location centre, int radius) {
        int removed = 0;
        Iterator<Map.Entry<String, RoadBlock>> it = roadBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RoadBlock> entry = it.next();
            RoadBlock rb = entry.getValue();
            Location loc = rb.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(centre.getWorld())) continue;
            if (loc.distance(centre) <= radius) {
                loc.getBlock().setBlockData(rb.getOriginalData());
                blockToRoad.remove(entry.getKey());
                it.remove();
                removed++;
            }
        }
        if (removed > 0) saveRoads();
        return removed;
    }

    public Map<String, RoadDefinition> getRoadDefinitions() { return roadDefinitions; }

    /**
     * Deletes a road by display name (e.g. "Spawn-Village").
     * Restores all original blocks and removes from BlueMap.
     * @return the deleted definition, or null if not found.
     */
    public RoadDefinition deleteRoadByName(String displayName) {
        // Find matching definition
        RoadDefinition target = null;
        for (RoadDefinition def : roadDefinitions.values()) {
            String dn = def.getFromName() + "-" + def.getToName();
            if (dn.equalsIgnoreCase(displayName)
                    || def.getDisplayName().equalsIgnoreCase(displayName)
                    || def.getId().equalsIgnoreCase(displayName)) {
                target = def;
                break;
            }
        }
        if (target == null) return null;

        String roadId = target.getId();

        // Restore all blocks belonging to this road
        Iterator<Map.Entry<String, RoadBlock>> it = roadBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RoadBlock> entry = it.next();
            if (roadId.equals(blockToRoad.get(entry.getKey()))) {
                RoadBlock rb = entry.getValue();
                rb.getLocation().getBlock().setBlockData(rb.getOriginalData());
                blockToRoad.remove(entry.getKey());
                it.remove();
            }
        }

        roadDefinitions.remove(roadId);
        saveRoads();
        return target;
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX()
                + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
