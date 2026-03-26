package com.roadboost.bluemap;

import com.roadboost.models.RoadDefinition;
import com.roadboost.models.RoadDefinition.Waypoint;
import com.roadboost.models.RoadDefinition.SegmentType;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import com.flowpowered.math.vector.Vector3d;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles all BlueMap marker rendering for roads and bridges.
 *
 * Roads appear as a single line.
 * Bridge segments appear as two parallel lines offset left/right.
 */
public class BlueMapIntegration {

    private static final String MARKER_SET_ID    = "roadboost_roads";
    private static final String MARKER_SET_LABEL = "Roads & Bridges";

    private final JavaPlugin plugin;
    private final float roadLineWidth;
    private final float bridgeLineWidth;
    private final double bridgeParallelOffset;
    private final int roadColor;
    private final int bridgeColor;

    public BlueMapIntegration(JavaPlugin plugin) {
        this.plugin              = plugin;
        this.roadLineWidth       = (float) plugin.getConfig().getDouble("bluemap-road-line-width", 3.0);
        this.bridgeLineWidth     = (float) plugin.getConfig().getDouble("bluemap-bridge-line-width", 2.0);
        this.bridgeParallelOffset = plugin.getConfig().getDouble("bluemap-bridge-parallel-offset", 2.5);

        // Parse hex colors from config
        this.roadColor   = parseColor(plugin.getConfig().getString("bluemap-road-color",   "#A0A0A0"));
        this.bridgeColor = parseColor(plugin.getConfig().getString("bluemap-bridge-color",  "#8B6914"));
    }

    /**
     * Adds or updates markers for a road on all BlueMap maps for the road's world.
     */
    public void updateRoad(RoadDefinition road) {
        BlueMapAPI.getInstance().ifPresentOrElse(api -> {
            try {
                String worldName = road.getWaypoints().isEmpty()
                        ? null : road.getWaypoints().get(0).world;
                if (worldName == null) return;

                for (BlueMapMap map : api.getMaps()) {
                    String bmWorldId = map.getWorld().getId();
                    if (!bmWorldId.equals(worldName) && !bmWorldId.endsWith(":" + worldName) && !bmWorldId.contains(worldName)) continue;

                    MarkerSet set = map.getMarkerSets()
                            .computeIfAbsent(MARKER_SET_ID,
                                    id -> MarkerSet.builder().label(MARKER_SET_LABEL).build());

                    // Split waypoints into contiguous road and bridge segments
                    List<List<Waypoint>> roadSegments   = splitByType(road.getWaypoints(), SegmentType.ROAD);
                    List<List<Waypoint>> bridgeSegments = splitByType(road.getWaypoints(), SegmentType.BRIDGE);

                    // Draw road segments as single lines
                    int ri = 0;
                    for (List<Waypoint> seg : roadSegments) {
                        if (seg.size() < 2) continue;
                        String markerId = road.getId() + "_road_" + ri++;
                        LineMarker marker = LineMarker.builder()
                                .label(road.getDisplayName())
                                .line(toLine(seg))
                                .lineWidth((int) roadLineWidth)
                                .lineColor(new Color(roadColor))
                                .depthTestEnabled(false)
                                .build();
                        set.getMarkers().put(markerId, marker);
                    }

                    // Draw bridge segments as two parallel lines
                    int bi = 0;
                    for (List<Waypoint> seg : bridgeSegments) {
                        if (seg.size() < 2) continue;

                        // Left line
                        String leftId = road.getId() + "_bridge_L_" + bi;
                        LineMarker left = LineMarker.builder()
                                .label(road.getDisplayName() + " (Bridge)")
                                .line(toParallelLine(seg, -bridgeParallelOffset))
                                .lineWidth((int) bridgeLineWidth)
                                .lineColor(new Color(bridgeColor))
                                .depthTestEnabled(false)
                                .build();
                        set.getMarkers().put(leftId, left);

                        // Right line
                        String rightId = road.getId() + "_bridge_R_" + bi++;
                        LineMarker right = LineMarker.builder()
                                .label(road.getDisplayName() + " (Bridge)")
                                .line(toParallelLine(seg, +bridgeParallelOffset))
                                .lineWidth((int) bridgeLineWidth)
                                .lineColor(new Color(bridgeColor))
                                .depthTestEnabled(false)
                                .build();
                        set.getMarkers().put(rightId, right);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update BlueMap markers for road: " + road.getId(), e);
            }
        }, () -> plugin.getLogger().info("[BlueMap] BlueMap not available — skipping marker update."));
    }

    /** Removes all markers for a road. */
    public void removeRoad(RoadDefinition road) {
        BlueMapAPI.getInstance().ifPresent(api -> {
            for (BlueMapMap map : api.getMaps()) {
                MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
                if (set == null) continue;
                set.getMarkers().entrySet().removeIf(e -> e.getKey().startsWith(road.getId() + "_"));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Deduplicates a waypoint list — keeps only one point per unique XZ position.
     * This prevents bridge module stamps from creating a grid of overlapping lines.
     */
    private List<Waypoint> deduplicateByXZ(List<Waypoint> all) {
        List<Waypoint> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Waypoint wp : all) {
            String k = (int)wp.x + "," + (int)wp.z;
            if (seen.add(k)) result.add(wp);
        }
        return result;
    }

    /**
     * Splits a deduplicated waypoint list into contiguous runs of the given type,
     * including one overlapping point at each boundary for seamless joins.
     */
    private List<List<Waypoint>> splitByType(List<Waypoint> all, SegmentType type) {
        List<Waypoint> deduped = deduplicateByXZ(all);
        List<List<Waypoint>> result = new ArrayList<>();
        List<Waypoint> current = null;

        for (int i = 0; i < deduped.size(); i++) {
            Waypoint wp = deduped.get(i);
            if (wp.type == type) {
                if (current == null) {
                    current = new ArrayList<>();
                    if (i > 0) current.add(deduped.get(i - 1));
                }
                current.add(wp);
            } else {
                if (current != null) {
                    current.add(wp);
                    result.add(current);
                    current = null;
                }
            }
        }
        if (current != null) result.add(current);
        return result;
    }

    private Line toLine(List<Waypoint> waypoints) {
        Vector3d[] points = waypoints.stream()
                .map(w -> new Vector3d(w.x + 0.5, w.y + 1.0, w.z + 0.5))
                .toArray(Vector3d[]::new);
        return new Line(points);
    }

    /**
     * Creates a parallel line offset perpendicular to the path direction.
     * Offset is in the XZ plane only.
     */
    private Line toParallelLine(List<Waypoint> waypoints, double offset) {
        Vector3d[] points = new Vector3d[waypoints.size()];
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint cur = waypoints.get(i);
            // Compute direction from neighbours
            Waypoint prev = waypoints.get(Math.max(0, i - 1));
            Waypoint next = waypoints.get(Math.min(waypoints.size() - 1, i + 1));

            double dx = next.x - prev.x;
            double dz = next.z - prev.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len == 0) { dx = 1; dz = 0; } else { dx /= len; dz /= len; }

            // Perpendicular: rotate 90°
            double perpX = -dz * offset;
            double perpZ =  dx * offset;

            points[i] = new Vector3d(cur.x + 0.5 + perpX, cur.y + 1.0, cur.z + 0.5 + perpZ);
        }
        return new Line(points);
    }

    private int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return (int) Long.parseLong(hex, 16) | 0xFF000000; // ensure full alpha
        } catch (NumberFormatException e) {
            return 0xFFA0A0A0;
        }
    }
}
