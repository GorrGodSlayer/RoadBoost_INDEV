package com.roadboost.managers;

import com.roadboost.models.RoadBlock;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecordingSession {

    // -------------------------------------------------------------------------
    // Vegetation set
    // -------------------------------------------------------------------------
    private static final Set<Material> VEGETATION = EnumSet.noneOf(Material.class);
    static {
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            String n = m.name();
            if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_LEAVES")
                    || n.endsWith("_SAPLING") || n.endsWith("_BUSH")
                    || n.contains("MUSHROOM") || n.contains("FUNGUS")
                    || n.contains("BAMBOO") || n.contains("CACTUS")
                    || n.contains("CORAL") || n.contains("KELP") || n.contains("SEAGRASS")) {
                VEGETATION.add(m);
            }
        }
        for (String name : new String[]{
                "SHORT_GRASS","GRASS","TALL_GRASS","FERN","LARGE_FERN","DEAD_BUSH",
                "VINE","GLOW_LICHEN","HANGING_ROOTS","MOSS_BLOCK","MOSS_CARPET",
                "AZALEA","FLOWERING_AZALEA","DANDELION","POPPY","ALLIUM",
                "AZURE_BLUET","RED_TULIP","ORANGE_TULIP","WHITE_TULIP","PINK_TULIP",
                "OXEYE_DAISY","CORNFLOWER","LILY_OF_THE_VALLEY","WITHER_ROSE",
                "SUNFLOWER","LILAC","ROSE_BUSH","PEONY","SPORE_BLOSSOM",
                "WARPED_ROOTS","NETHER_SPROUTS","CRIMSON_ROOTS",
                "SUGAR_CANE","SWEET_BERRY_BUSH"
        }) {
            try { VEGETATION.add(Material.valueOf(name)); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------
    private final Set<String> seenKeys = new HashSet<>();
    private final List<RoadBlock> recorded = new ArrayList<>();

    private final WeightedBlockPicker picker;
    private final int width;
    private final Particle particle;
    private final int corridorHeight;
    private final int forestRadius;

    public RecordingSession(WeightedBlockPicker picker, int width, Particle particle,
                            int corridorHeight, int forestRadius) {
        this.picker         = picker;
        this.width          = width;
        this.particle       = particle;
        this.corridorHeight = corridorHeight;
        this.forestRadius   = forestRadius;
    }

    // -------------------------------------------------------------------------
    // record() — called every time the player crosses a block boundary
    // -------------------------------------------------------------------------
    public void record(Player player, Block current, Block previous) {
        World world = player.getWorld();
        int px = player.getLocation().getBlockX();
        int py = player.getLocation().getBlockY() - 1;
        int pz = player.getLocation().getBlockZ();

        int dx = current.getX() - previous.getX();
        int dz = current.getZ() - previous.getZ();

        int perpX = dz != 0 ? 1 : 0;
        int perpZ = dx != 0 ? 1 : 0;

        // Forest clearing around the player
        clearForest(world, px, py, pz);

        // Place road blocks across width
        for (int offset = -width; offset <= width; offset++) {
            int bx = px + perpX * offset;
            int bz = pz + perpZ * offset;
            int by = py;

            // Carve corridor above
            clearCorridor(world, bx, by, bz);

            Block target = world.getBlockAt(bx, by, bz);
            String key = bx + "," + by + "," + bz;
            if (seenKeys.contains(key)) continue;
            seenKeys.add(key);

            BlockData original = target.getBlockData().clone();
            target.setType(picker.pick());
            recorded.add(new RoadBlock(target.getLocation(), original));

            // Particles
            if (particle != null) {
                Location particleLoc = target.getLocation().add(0.5, 1.1, 0.5);
                try {
                    if (particle == Particle.DUST) {
                        world.spawnParticle(particle, particleLoc, 4, 0.2, 0.1, 0.2, 0,
                                new Particle.DustOptions(Color.WHITE, 1.0f));
                    } else {
                        world.spawnParticle(particle, particleLoc, 4, 0.2, 0.1, 0.2, 0);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // commit() — no-op in simple mode, kept so RoadCommand compiles
    // -------------------------------------------------------------------------
    public void commit() {}

    // -------------------------------------------------------------------------
    // Hill cutting — clears solid blocks above each road block
    // -------------------------------------------------------------------------
    private void clearCorridor(World world, int bx, int by, int bz) {
        for (int dy = 1; dy <= corridorHeight; dy++) {
            Block above = world.getBlockAt(bx, by + dy, bz);
            if (above.getType().isAir()) continue;
            if (!above.getType().isSolid() && !VEGETATION.contains(above.getType())) continue;
            String key = bx + "," + (by + dy) + "," + bz;
            if (!seenKeys.contains(key)) {
                recorded.add(new RoadBlock(above.getLocation(), above.getBlockData().clone()));
                seenKeys.add(key);
            }
            above.setType(Material.AIR);
        }
    }

    // -------------------------------------------------------------------------
    // Forest clearing — removes vegetation in a circular radius
    // -------------------------------------------------------------------------
    private void clearForest(World world, int px, int py, int pz) {
        for (int rx = -forestRadius; rx <= forestRadius; rx++) {
            for (int rz = -forestRadius; rz <= forestRadius; rz++) {
                if (rx * rx + rz * rz > forestRadius * forestRadius) continue;
                int wx = px + rx;
                int wz = pz + rz;
                for (int dy = -1; dy <= 24; dy++) {
                    Block block = world.getBlockAt(wx, py + dy, wz);
                    if (!VEGETATION.contains(block.getType())) continue;
                    String key = wx + "," + (py + dy) + "," + wz;
                    if (!seenKeys.contains(key)) {
                        recorded.add(new RoadBlock(block.getLocation(), block.getBlockData().clone()));
                        seenKeys.add(key);
                    }
                    block.setType(Material.AIR);
                }
            }
        }
    }

    public List<RoadBlock> getRecorded() { return recorded; }
    public int size()                    { return recorded.size(); }
}
