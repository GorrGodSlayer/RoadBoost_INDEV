package com.roadboost.bridge;

import com.roadboost.models.RoadBlock;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

public class BridgeSession {

    /** Block types considered vegetation — cleared before a module is pasted. */
    private static final Set<org.bukkit.Material> VEGETATION = EnumSet.noneOf(org.bukkit.Material.class);
    static {
        // Logs and wood
        for (org.bukkit.Material m : org.bukkit.Material.values()) {
            String n = m.name();
            if (!m.isBlock()) continue;
            if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_LEAVES")
                    || n.endsWith("_SAPLING") || n.endsWith("_BUSH")
                    || n.contains("MUSHROOM") || n.contains("FUNGUS")
                    || n.contains("NYLIUM") || n.contains("WARPED") || n.contains("CRIMSON")) {
                VEGETATION.add(m);
            }
        }
        // Grass, plants, flowers, vines
        try {
            VEGETATION.add(org.bukkit.Material.SHORT_GRASS);
        } catch (Exception ignored) {
            try { VEGETATION.add(org.bukkit.Material.valueOf("GRASS")); } catch (Exception ignored2) {}
        }
        try { VEGETATION.add(org.bukkit.Material.TALL_GRASS); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.FERN); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.LARGE_FERN); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.DEAD_BUSH); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.VINE); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.GLOW_LICHEN); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.HANGING_ROOTS); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.MOSS_BLOCK); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.MOSS_CARPET); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.AZALEA); } catch (Exception ignored) {}
        try { VEGETATION.add(org.bukkit.Material.FLOWERING_AZALEA); } catch (Exception ignored) {}
        // All flowers and small plants
        for (org.bukkit.Material m : org.bukkit.Material.values()) {
            if (!m.isBlock()) continue;
            String n = m.name();
            if (n.endsWith("_FLOWER") || n.endsWith("_TULIP") || n.endsWith("_ORCHID")
                    || n.contains("DANDELION") || n.contains("POPPY") || n.contains("ALLIUM")
                    || n.contains("BLUET") || n.contains("DAISY") || n.contains("CORNFLOWER")
                    || n.contains("LILY") || n.contains("ROSE") || n.contains("SUNFLOWER")
                    || n.contains("LILAC") || n.contains("PEONY") || n.contains("SPORE_BLOSSOM")
                    || n.contains("BAMBOO") || n.contains("CACTUS") || n.contains("SUGAR_CANE")
                    || n.contains("KELP") || n.contains("SEAGRASS") || n.contains("CORAL")) {
                VEGETATION.add(m);
            }
        }
    }

    private final Clipboard schematic;
    private final int lockedY;
    private final Particle particle;
    private final Logger logger;

    private final List<RoadBlock> placed = new ArrayList<>();
    private final Set<String> placedKeys = new HashSet<>();

    private int dirX = 0;
    private int dirZ = 1;
    private boolean directionLocked = false;

    private final int schemSizeX;
    private final int schemSizeY;
    private final int schemSizeZ;
    private int moduleLength;

    private int forwardSteps = 0;
    private int nextStampAt;
    private int stampedModules = 0;

    private final int originX;
    private final int originZ;

    public BridgeSession(Clipboard schematic, int lockedY, Particle particle,
                         int startX, int startZ, Logger logger) {
        this.schematic = schematic;
        this.lockedY   = lockedY;
        this.particle  = particle;
        this.originX   = startX;
        this.originZ   = startZ;
        this.logger    = logger;

        BlockVector3 dims = schematic.getDimensions();
        this.schemSizeX = dims.getBlockX();
        this.schemSizeY = dims.getBlockY();
        this.schemSizeZ = dims.getBlockZ();
        this.moduleLength = schemSizeZ;
        this.nextStampAt = moduleLength - 1;

        logger.info("[Bridge] Schematic size: " + schemSizeX + "x" + schemSizeY + "x" + schemSizeZ);
    }

    public void stampFirst(Player player) {
        pasteModule(player.getWorld(), originX, originZ, dirX, dirZ);
        stampedModules = 1;
        nextStampAt = moduleLength - 1;
        logger.info("[Bridge] Module 0 stamped at origin. moduleLength=" + moduleLength + " nextStampAt=" + nextStampAt);
        spawnParticles(player.getWorld(), originX, originZ);
    }

    public boolean onPlayerMove(Player player, Block from, Block to) {
        World world = player.getWorld();
        int px = player.getLocation().getBlockX();
        int pz = player.getLocation().getBlockZ();
        int stepX = to.getX() - from.getX();
        int stepZ = to.getZ() - from.getZ();

        // Lock direction on first step
        if (!directionLocked) {
            if (stepX == 0 && stepZ == 0) return false;

            if (Math.abs(stepX) >= Math.abs(stepZ)) {
                dirX = (int) Math.signum(stepX);
                dirZ = 0;
            } else {
                dirX = 0;
                dirZ = (int) Math.signum(stepZ);
            }
            directionLocked = true;
            moduleLength = (dirZ != 0) ? schemSizeZ : schemSizeX;
            nextStampAt = moduleLength - 1;

            logger.info("[Bridge] Direction locked: dirX=" + dirX + " dirZ=" + dirZ
                    + " moduleLength=" + moduleLength + " nextStampAt=" + nextStampAt);

            // Redo module 0 with correct direction
            for (RoadBlock rb : placed) {
                rb.getLocation().getBlock().setBlockData(rb.getOriginalData());
            }
            placed.clear();
            placedKeys.clear();
            stampedModules = 0;
            forwardSteps = 0;

            pasteModule(world, originX, originZ, dirX, dirZ);
            stampedModules = 1;
            spawnParticles(world, originX, originZ);
            return false;
        }

        // Only count forward steps on locked axis
        int axisStep = (dirZ != 0) ? stepZ * dirZ : stepX * dirX;
        if (axisStep <= 0) return false;

        forwardSteps += axisStep;

        logger.info("[Bridge] forwardSteps=" + forwardSteps + " nextStampAt=" + nextStampAt
                + " stampedModules=" + stampedModules);

        // Auto-end: natural solid ground
        Block underFeet = world.getBlockAt(px, lockedY - 1, pz);
        if (underFeet.getType().isSolid()
                && !placedKeys.contains(key(px, lockedY - 1, pz))) {
            logger.info("[Bridge] Auto-end triggered at " + px + "," + pz);
            return true;
        }

        // Stamp next module at seam
        if (forwardSteps >= nextStampAt) {
            int anchorX = originX + dirX * (stampedModules * moduleLength);
            int anchorZ = originZ + dirZ * (stampedModules * moduleLength);
            logger.info("[Bridge] Stamping module " + stampedModules + " at anchor "
                    + anchorX + "," + anchorZ);
            pasteModule(world, anchorX, anchorZ, dirX, dirZ);
            spawnParticles(world, anchorX, anchorZ);
            stampedModules++;
            nextStampAt += moduleLength;
            logger.info("[Bridge] Next stamp at forwardSteps=" + nextStampAt);
        }

        return false;
    }

    public boolean isActiveBridgeBlock(Location loc) {
        return placedKeys.contains(key(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    /**
     * Clears all vegetation in the column footprint of the module PLUS a generous
     * height above lockedY, so custom trees don't poke through the bridge.
     */
    private void clearVegetation(World world, int anchorX, int anchorZ, int dx, int dz) {
        int clearHeightAbove = 32; // configurable via bridge-clear-height in config

        for (int sx = 0; sx < schemSizeX; sx++) {
            for (int sz = 0; sz < schemSizeZ; sz++) {
                int relX = sx - (schemSizeX - 1);
                int[] rotated = rotate(relX, sz, dx, dz);
                int worldX = anchorX + rotated[0];
                int worldZ = anchorZ + rotated[1];

                // Clear from lockedY upward
                for (int dy = 0; dy <= clearHeightAbove; dy++) {
                    int worldY = lockedY + dy;
                    if (worldY > world.getMaxHeight()) break;
                    Block block = world.getBlockAt(worldX, worldY, worldZ);
                    if (VEGETATION.contains(block.getType())) {
                        // Save original so it can be restored on /road undo
                        placed.add(new RoadBlock(block.getLocation(), block.getBlockData().clone()));
                        placedKeys.add(key(worldX, worldY, worldZ));
                        block.setType(org.bukkit.Material.AIR);
                    }
                }
            }
        }
    }

    private void pasteModule(World world, int anchorX, int anchorZ, int dx, int dz) {
        // Clear trees and vegetation before placing blocks
        clearVegetation(world, anchorX, anchorZ, dx, dz);
        BlockVector3 min = schematic.getMinimumPoint();

        for (int sy = 0; sy < schemSizeY; sy++) {
            int worldY = lockedY - (schemSizeY - 1 - sy);
            if (worldY < world.getMinHeight() || worldY > world.getMaxHeight()) continue;

            for (int sx = 0; sx < schemSizeX; sx++) {
                for (int sz = 0; sz < schemSizeZ; sz++) {

                    int relX = sx - (schemSizeX - 1);
                    int[] rotated = rotate(relX, sz, dx, dz);
                    int worldX = anchorX + rotated[0];
                    int worldZ = anchorZ + rotated[1];

                    if (worldY < lockedY) {
                        Block below = world.getBlockAt(worldX, worldY - 1, worldZ);
                        if (below.getType().isSolid()
                                && !placedKeys.contains(key(worldX, worldY - 1, worldZ))) {
                            continue;
                        }
                    }

                    BlockVector3 schemPos = BlockVector3.at(
                            min.getBlockX() + sx,
                            min.getBlockY() + sy,
                            min.getBlockZ() + sz
                    );
                    BlockState state = schematic.getBlock(schemPos);
                    String blockId = state.getBlockType().getId();
                    if (blockId.contains("air")) continue;

                    Material mat = Material.matchMaterial(
                            blockId.replace("minecraft:", "").toUpperCase()
                    );
                    if (mat == null || !mat.isBlock()) continue;

                    Block target = world.getBlockAt(worldX, worldY, worldZ);
                    String k = key(worldX, worldY, worldZ);
                    if (placedKeys.contains(k)) continue;

                    placed.add(new RoadBlock(target.getLocation(), target.getBlockData().clone()));
                    target.setType(mat);
                    placedKeys.add(k);
                }
            }
        }
    }

    private int[] rotate(int relX, int relZ, int dx, int dz) {
        if (dz ==  1) return new int[]{ relX,  relZ};
        if (dz == -1) return new int[]{-relX, -relZ};
        if (dx ==  1) return new int[]{ relZ, -relX};
        if (dx == -1) return new int[]{-relZ,  relX};
        return new int[]{relX, relZ};
    }

    private void spawnParticles(World world, int x, int z) {
        if (particle == null) return;
        Location loc = new Location(world, x + 0.5, lockedY + 1.5, z + 0.5);
        try { world.spawnParticle(particle, loc, 12, 1.0, 0.1, 1.0, 0); }
        catch (Exception ignored) {}
    }

    private String key(int x, int y, int z) { return x + "," + y + "," + z; }

    public int getLockedY()            { return lockedY; }
    public List<RoadBlock> getPlaced() { return placed; }
    public int size()                  { return placed.size(); }
}
