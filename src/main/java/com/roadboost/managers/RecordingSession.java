package com.roadboost.managers;

import com.roadboost.models.RoadBlock;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class RecordingSession {

    private final LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
    private final List<RoadBlock> recorded = new ArrayList<>();

    private final WeightedBlockPicker picker;
    private final int width;
    private final Particle particle;

    public RecordingSession(WeightedBlockPicker picker, int width, Particle particle) {
        this.picker = picker;
        this.width = width;
        this.particle = particle;
    }

    public void record(Player player, Block current, Block previous) {
        // Always use the block directly under the player's feet
        Block underPlayer = player.getWorld().getBlockAt(
            player.getLocation().getBlockX(),
            player.getLocation().getBlockY() - 1,
            player.getLocation().getBlockZ()
        );

        int dx = current.getX() - previous.getX();
        int dz = current.getZ() - previous.getZ();

        int perpX = dz != 0 ? 1 : 0;
        int perpZ = dx != 0 ? 1 : 0;

        for (int offset = -width; offset <= width; offset++) {
            int bx = underPlayer.getX() + perpX * offset;
            int bz = underPlayer.getZ() + perpZ * offset;
            int by = underPlayer.getY();

            Block target = player.getWorld().getBlockAt(bx, by, bz);
            String key = bx + "," + by + "," + bz;

            if (seenKeys.contains(key)) continue;
            seenKeys.add(key);

            BlockData original = target.getBlockData().clone();
            target.setType(picker.pick());

            recorded.add(new RoadBlock(target.getLocation(), original));

            // Spawn particles above each placed block
            if (particle != null) {
                Location particleLoc = target.getLocation().add(0.5, 1.1, 0.5);
                if (particle == Particle.DUST) {
    Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.WHITE, 1.0f);
    player.getWorld().spawnParticle(particle, particleLoc, 4, 0.2, 0.1, 0.2, 0, dust);
} else {
    player.getWorld().spawnParticle(particle, particleLoc, 4, 0.2, 0.1, 0.2, 0);
}
            }
        }
    }

    public List<RoadBlock> getRecorded() {
        return recorded;
    }

    public int size() {
        return recorded.size();
    }
}