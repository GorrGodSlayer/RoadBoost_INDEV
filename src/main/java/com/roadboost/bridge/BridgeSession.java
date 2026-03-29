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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Bridge session.
 *
 * Surface detection:
 *   Scans the center column (middleX, z=0) of the schematic from top to bottom
 *   to find the highest solid block. That Y index becomes the "surface row".
 *   When pasting, the surface row is aligned to lockedY so the player always
 *   walks on the correct surface regardless of decorations above.
 *
 * Horizontal alignment:
 *   The middle X of the schematic is centered on the player's position.
 *
 * Stamping:
 *   forwardSteps counts only steps along the locked axis in the locked direction.
 *   nextStampAt advances by moduleLength each time a module is stamped.
 */
public class BridgeSession {

    // -------------------------------------------------------------------------
    // Vegetation materials — cleared before each module is pasted
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
                "SUNFLOWER","LILAC","ROSE_BUSH","PEONY","SPORE_BLOSSOM","WARPED_ROOTS",
                "NETHER_SPROUTS","CRIMSON_ROOTS","SUGAR_CANE","SWEET_BERRY_BUSH"
        }) {
            try { VEGETATION.add(Material.valueOf(name)); } catch (Exception ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------
    private final Clipboard schematic;
    private final int lockedY;
    private final Particle particle;
    private final Logger logger;
    private final int clearHeight;
    private final int moduleLengthOverride;

    private final List<RoadBlock> placed = new ArrayList<>();
    private final Set<String> placedKeys = new HashSet<>();

    private int dirX = 0;
    private int dirZ = 1;
    private boolean directionLocked = false;

    private final int schemSizeX;
    private final int schemSizeY;
    private final int schemSizeZ;
    private int moduleLength;

    /**
     * The Y index within the schematic of the walkable surface.
     * Detected by scanning the center column (middleX, z=0) top-to-bottom
     * for the highest solid block.
     */
    private final int surfaceSchemY;

    /**
     * Half the schematic width, used to center on the player.
     * centerOffset = schemSizeX / 2
     */
    private final int centerOffsetX;

    private int forwardSteps = 0;
    private int nextStampAt;
    private int stampedModules = 0;

    private final int originX;
    private final int originZ;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public BridgeSession(Clipboard schematic, int lockedY, Particle particle,
                         int startX, int startZ, float yaw, Logger logger, int clearHeight, int moduleLengthOverride) {
        this.schematic   = schematic;
        this.lockedY     = lockedY;
        this.particle    = particle;
        this.originX     = startX;
        this.originZ     = startZ;
        this.logger      = logger;
        this.clearHeight = clearHeight;
        this.moduleLengthOverride = moduleLengthOverride;

        // Lock direction immediately from player's yaw
        // Yaw: 0/-360=south, -90/270=east, 90/-270=west, 180/-180=north
        // Normalize yaw to 0-360
        float normalizedYaw = ((yaw % 360) + 360) % 360;
        if (normalizedYaw >= 315 || normalizedYaw < 45) {
            dirX = 0; dirZ = 1;  // south
        } else if (normalizedYaw >= 45 && normalizedYaw < 135) {
            dirX = -1; dirZ = 0; // west
        } else if (normalizedYaw >= 135 && normalizedYaw < 225) {
            dirX = 0; dirZ = -1; // north
        } else {
            dirX = 1; dirZ = 0;  // east
        }
        directionLocked = true;

        BlockVector3 dims = schematic.getDimensions();
        this.schemSizeX = dims.getBlockX();
        this.schemSizeY = dims.getBlockY();
        this.schemSizeZ = dims.getBlockZ();
        // Direction already locked — set moduleLength correctly
        // sz always maps to the forward axis regardless of rotation direction
        // (rotate() maps sz to worldX when going east/west, to worldZ when going north/south)
        // So module length is always schemSizeZ. Config override takes priority if set.
        this.moduleLength = (moduleLengthOverride > 0) ? moduleLengthOverride : schemSizeZ;
        this.nextStampAt  = moduleLength - 1;

        // Center offset: middle of width
        this.centerOffsetX = schemSizeX / 2;

        // Detect walkable surface in center column
        this.surfaceSchemY = detectSurface();

        logger.info("[Bridge] Schematic: " + schemSizeX + "x" + schemSizeY + "x" + schemSizeZ + " dir=(" + dirX + "," + dirZ + ") moduleLength=" + moduleLength
                + "  surfaceSchemY=" + surfaceSchemY
                + "  centerOffsetX=" + centerOffsetX);
    }

    /**
     * Scans the center column (middleX, z=0) from top to bottom.
     * Returns the Y index of the highest solid block found.
     * Falls back to (schemSizeY - 1) if nothing solid is found.
     */
    private int detectSurface() {
        int midX = schemSizeX / 2;
        BlockVector3 min = schematic.getMinimumPoint();

        for (int sy = schemSizeY - 1; sy >= 0; sy--) {
            BlockVector3 pos = BlockVector3.at(
                    min.getBlockX() + midX,
                    min.getBlockY() + sy,
                    min.getBlockZ() // z=0, first slice
            );
            BlockState state = schematic.getBlock(pos);
            String id = state.getBlockType().getId();
            if (!id.contains("air")) {
                // Check if the block above this one is air (open top = walkable)
                if (sy < schemSizeY - 1) {
                    BlockVector3 above = BlockVector3.at(
                            min.getBlockX() + midX,
                            min.getBlockY() + sy + 1,
                            min.getBlockZ()
                    );
                    String aboveId = schematic.getBlock(above).getBlockType().getId();
                    if (aboveId.contains("air")) {
                        return sy;
                    }
                } else {
                    return sy; // top of schematic is solid
                }
            }
        }
        return schemSizeY - 1; // fallback
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Stamps module 0 immediately at the player's feet. */
    public void stampFirst(Player player) {
        pasteModule(player.getWorld(), originX, originZ, dirX, dirZ);
        stampedModules = 1;
        nextStampAt = moduleLength - 1;
        logger.info("[Bridge] Module 0 stamped. moduleLength=" + moduleLength
                + " nextStampAt=" + nextStampAt);
        spawnParticles(player.getWorld(), originX, originZ);
    }

    /**
     * Called each time the player crosses a block boundary.
     * @return true if the session should auto-end.
     */
    public boolean onPlayerMove(Player player, Block from, Block to) {
        World world = player.getWorld();
        int px = player.getLocation().getBlockX();
        int pz = player.getLocation().getBlockZ();
        int stepX = to.getX() - from.getX();
        int stepZ = to.getZ() - from.getZ();

        // --- Only count steps along locked axis in locked direction ---
        int axisStep = (dirZ != 0) ? stepZ * dirZ : stepX * dirX;
        if (axisStep <= 0) return false;

        forwardSteps += axisStep;

        logger.info("[Bridge] forwardSteps=" + forwardSteps
                + " nextStampAt=" + nextStampAt
                + " stampedModules=" + stampedModules);

        // --- Stamp next module when player reaches the seam (BEFORE auto-end check) ---
        if (forwardSteps >= nextStampAt) {
            int anchorX = originX + dirX * (stampedModules * moduleLength);
            int anchorZ = originZ + dirZ * (stampedModules * moduleLength);
            logger.info("[Bridge] Stamping module " + stampedModules
                    + " at anchor " + anchorX + "," + anchorZ);
            pasteModule(world, anchorX, anchorZ, dirX, dirZ);
            spawnParticles(world, anchorX, anchorZ);
            stampedModules++;
            nextStampAt += moduleLength;
            logger.info("[Bridge] Next stamp at forwardSteps=" + nextStampAt);
        }

        // --- Auto-end: player stepped onto solid ground that is NOT a bridge block ---
        // Checked AFTER stamping so newly placed blocks are in placedKeys already
        int feetY = player.getLocation().getBlockY() - 1;
        Block underFeet = world.getBlockAt(px, feetY, pz);
        logger.info("[Bridge] underFeet=" + underFeet.getType()
                + " feetY=" + feetY + " isBridgeBlock=" + placedKeys.contains(key(px, feetY, pz)));
        if (underFeet.getType().isSolid()
                && !placedKeys.contains(key(px, feetY, pz))) {
            logger.info("[Bridge] Auto-end at " + px + "," + pz + " feetY=" + feetY);
            return true;
        }

        return false;
    }

    /** True if this location is part of the active (not yet committed) bridge. */
    public boolean isActiveBridgeBlock(Location loc) {
        return placedKeys.contains(key(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    // -------------------------------------------------------------------------
    // Vegetation clearing
    // -------------------------------------------------------------------------

    private void clearVegetation(World world, int anchorX, int anchorZ, int dx, int dz) {
        for (int sx = 0; sx < schemSizeX; sx++) {
            for (int sz = 0; sz < schemSizeZ; sz++) {
                int relX = sx - centerOffsetX; // centered
                int[] rotated = rotate(relX, sz, dx, dz);
                int worldX = anchorX + rotated[0];
                int worldZ = anchorZ + rotated[1];

                for (int dy = -1; dy <= clearHeight; dy++) {
                    int worldY = (lockedY - 1) + dy;
                    if (worldY > world.getMaxHeight()) break;
                    Block block = world.getBlockAt(worldX, worldY, worldZ);
                    if (VEGETATION.contains(block.getType())) {
                        String k = key(worldX, worldY, worldZ);
                        if (!placedKeys.contains(k)) {
                            placed.add(new RoadBlock(block.getLocation(), block.getBlockData().clone()));
                            placedKeys.add(k);
                        }
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Module pasting
    // -------------------------------------------------------------------------

    private void pasteModule(World world, int anchorX, int anchorZ, int dx, int dz) {
        clearVegetation(world, anchorX, anchorZ, dx, dz);

        BlockVector3 min = schematic.getMinimumPoint();

        for (int sy = 0; sy < schemSizeY; sy++) {
            // Align: surface row of schematic maps to lockedY - 1 (block under feet)
            int worldY = (lockedY - 1) + (sy - surfaceSchemY);
            if (worldY < world.getMinHeight() || worldY > world.getMaxHeight()) continue;

            for (int sx = 0; sx < schemSizeX; sx++) {
                for (int sz = 0; sz < schemSizeZ; sz++) {

                    // Center horizontally: middle of schematic = anchor
                    int relX = sx - centerOffsetX;
                    int[] rotated = rotate(relX, sz, dx, dz);
                    int worldX = anchorX + rotated[0];
                    int worldZ = anchorZ + rotated[1];

                    // Bottom clip: don't place below natural solid terrain
                    if (worldY < lockedY - 1) {
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

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int[] rotate(int relX, int relZ, int dx, int dz) {
        if (dz ==  1) return new int[]{ relX,  relZ};
        if (dz == -1) return new int[]{-relX, -relZ};
        if (dx ==  1) return new int[]{ relZ, -relX};
        if (dx == -1) return new int[]{-relZ,  relX};
        return new int[]{relX, relZ};
    }

    private void spawnParticles(World world, int x, int z) {
        if (particle == null) return;
        Location loc = new Location(world, x + 0.5, lockedY + 0.5, z + 0.5);
        try { world.spawnParticle(particle, loc, 12, 1.0, 0.1, 1.0, 0); }
        catch (Exception ignored) {}
    }

    private String key(int x, int y, int z) { return x + "," + y + "," + z; }

    public int getLockedY()            { return lockedY; }
    public List<RoadBlock> getPlaced() { return placed; }
    public List<org.bukkit.Location> getCenterLineWaypoints() { return new java.util.ArrayList<>(); }
    public int size()                  { return placed.size(); }
}
