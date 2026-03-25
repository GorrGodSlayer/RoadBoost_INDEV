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
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Tracks a player walking a bridge path.
 *
 * Rules:
 * - The Y level is locked to where the player started.
 * - Each new block column the player enters stamps the schematic
 *   horizontally, aligned so the player's position is the RIGHT edge.
 * - The schematic is clipped at the bottom by existing terrain —
 *   blocks are only placed where there is currently air (or non-solid blocks).
 * - The session ends automatically when the player returns to natural ground
 *   at the same Y level, or manually via /bridge stop.
 */
public class BridgeSession {

    private final Clipboard schematic;
    private final int lockedY;
    private final Particle particle;

    private final List<RoadBlock> placed = new ArrayList<>();
    private final LinkedHashSet<String> seenColumns = new LinkedHashSet<>();

    // Schematic dimensions
    private final int schemWidth;  // X size
    private final int schemHeight; // Y size
    private final int schemDepth;  // Z size

    public BridgeSession(Clipboard schematic, int lockedY, Particle particle) {
        this.schematic = schematic;
        this.lockedY = lockedY;
        this.particle = particle;

        BlockVector3 dims = schematic.getDimensions();
        this.schemWidth  = dims.getBlockX();
        this.schemHeight = dims.getBlockY();
        this.schemDepth  = dims.getBlockZ();
    }

    /**
     * Called each time the player enters a new block column.
     * @return true if the bridge should auto-end (player back on solid ground at lockedY)
     */
    public boolean stamp(Player player, Block currentBlock, Block previousBlock) {
        World world = player.getWorld();
        int px = player.getLocation().getBlockX();
        int pz = player.getLocation().getBlockZ();

        // Check if player is back on natural terrain at the same Y — auto-end signal
        Block underFeet = world.getBlockAt(px, lockedY - 1, pz);
        if (underFeet.getType().isSolid() && !isOurBlock(underFeet.getLocation())) {
            return true; // signal to end session
        }

        String colKey = px + "," + pz;
        if (seenColumns.contains(colKey)) return false;
        seenColumns.add(colKey);

        // Determine movement direction for orientation
        int dx = currentBlock.getX() - previousBlock.getX();
        int dz = currentBlock.getZ() - previousBlock.getZ();

        // Stamp the schematic at this column
        pasteSchematic(world, px, pz, dx, dz);

        // Particle effect
        if (particle != null) {
            Location particleLoc = new Location(world, px + 0.5, lockedY + 1.2, pz + 0.5);
            try {
                world.spawnParticle(particle, particleLoc, 6, 0.3, 0.1, 0.3, 0);
            } catch (Exception ignored) {}
        }

        return false;
    }

    /**
     * Pastes the schematic with:
     * - Player position = RIGHT edge (max X of schematic)
     * - Top of schematic = lockedY (surface the player walks on)
     * - Bottom clipped by terrain (skip blocks where terrain is solid)
     *
     * The schematic is rotated to face the direction of travel.
     */
    private void pasteSchematic(World world, int px, int pz, int dx, int dz) {
        BlockVector3 origin = schematic.getOrigin();
        BlockVector3 min = schematic.getMinimumPoint();

        // Iterate every block in the schematic
        for (int sy = 0; sy < schemHeight; sy++) {
            int worldY = lockedY - (schemHeight - 1 - sy); // top of schem = lockedY

            if (worldY < world.getMinHeight() || worldY > world.getMaxHeight()) continue;

            for (int sx = 0; sx < schemWidth; sx++) {
                for (int sz = 0; sz < schemDepth; sz++) {

                    // Rotate offset based on movement direction
                    int[] rotated = rotate(sx - (schemWidth - 1), sz, dx, dz);
                    int worldX = px + rotated[0];
                    int worldZ = pz + rotated[1];

                    // Clip bottom: don't place if terrain below is solid
                    Block terrainCheck = world.getBlockAt(worldX, worldY - 1, worldZ);
                    if (worldY < lockedY && terrainCheck.getType().isSolid()) continue;

                    // Get the schematic block
                    BlockVector3 schemPos = BlockVector3.at(
                            min.getBlockX() + sx,
                            min.getBlockY() + sy,
                            min.getBlockZ() + sz
                    );
                    BlockState state = schematic.getBlock(schemPos);
                    Material mat = Material.matchMaterial(
                            state.getBlockType().getId().replace("minecraft:", "").toUpperCase()
                    );
                    if (mat == null || mat == Material.AIR) continue;

                    Block target = world.getBlockAt(worldX, worldY, worldZ);
                    String key = worldX + "," + worldY + "," + worldZ;

                    if (isOurBlock(target.getLocation())) continue;

                    // Save original and place
                    placed.add(new RoadBlock(target.getLocation(), target.getBlockData().clone()));
                    target.setType(mat);
                    seenColumns.add(key); // also mark as seen so road-detection works
                }
            }
        }
    }

    /**
     * Rotates a schematic offset (relX, relZ) to align with movement direction.
     * dx/dz is the normalised movement vector.
     */
    private int[] rotate(int relX, int relZ, int dx, int dz) {
        // Normalize
        if (dx != 0) dx = dx / Math.abs(dx);
        if (dz != 0) dz = dz / Math.abs(dz);

        // Facing +Z (south): no rotation needed
        // Facing -Z (north): rotate 180
        // Facing +X (east):  rotate 90 CW
        // Facing -X (west):  rotate 90 CCW
        if (dz == 1)       return new int[]{ relX,  relZ};  // south
        if (dz == -1)      return new int[]{-relX, -relZ};  // north
        if (dx == 1)       return new int[]{ relZ, -relX};  // east
        if (dx == -1)      return new int[]{-relZ,  relX};  // west
        return new int[]{relX, relZ};
    }

    private boolean isOurBlock(Location loc) {
        String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        for (RoadBlock rb : placed) {
            Location rl = rb.getLocation();
            if (rl.getBlockX() == loc.getBlockX() &&
                rl.getBlockY() == loc.getBlockY() &&
                rl.getBlockZ() == loc.getBlockZ()) return true;
        }
        return false;
    }

    public int getLockedY() { return lockedY; }
    public List<RoadBlock> getPlaced() { return placed; }
    public int size() { return placed.size(); }
}
