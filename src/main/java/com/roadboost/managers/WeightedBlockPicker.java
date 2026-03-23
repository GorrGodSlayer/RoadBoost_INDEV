package com.roadboost.managers;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Parses the "road-blocks" config list (format: MATERIAL:WEIGHT) and
 * returns random materials according to their configured weights.
 */
public class WeightedBlockPicker {

    private final List<Material> pool = new ArrayList<>();
    private final Random random = new Random();

    /**
     * @param entries List of strings in the format "MATERIAL:WEIGHT"
     */
    public WeightedBlockPicker(List<String> entries) {
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;

            Material mat = Material.matchMaterial(parts[0].trim().toUpperCase());
            if (mat == null || !mat.isBlock()) continue;

            int weight;
            try {
                weight = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            for (int i = 0; i < weight; i++) {
                pool.add(mat);
            }
        }

        // Fallback so the pool is never empty
        if (pool.isEmpty()) {
            pool.add(Material.COBBLESTONE);
        }
    }

    /** Returns a random Material from the weighted pool. */
    public Material pick() {
        return pool.get(random.nextInt(pool.size()));
    }
}
