package com.roadboost.models;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

public class RoadBlock {

    private final Location location;
    private final BlockData originalData;

    public RoadBlock(Location location, BlockData originalData) {
        this.location = location.clone();
        this.originalData = originalData.clone();
    }

    public Location getLocation() {
        return location.clone();
    }

    public BlockData getOriginalData() {
        return originalData.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoadBlock other)) return false;
        Location a = location.getBlock().getLocation();
        Location b = other.location.getBlock().getLocation();
        return a.equals(b);
    }

    @Override
    public int hashCode() {
        return location.getBlock().getLocation().hashCode();
    }
}