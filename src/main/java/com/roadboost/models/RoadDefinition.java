package com.roadboost.models;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a completed road with a name and ordered path.
 * Waypoints are the center-line locations recorded as the player walked.
 * Each waypoint has a type: ROAD or BRIDGE (for BlueMap rendering).
 */
public class RoadDefinition {

    public enum SegmentType { ROAD, BRIDGE }

    public static class Waypoint {
        public final double x, y, z;
        public final String world;
        public final SegmentType type;

        public Waypoint(Location loc, SegmentType type) {
            this.x     = loc.getX();
            this.y     = loc.getY();
            this.z     = loc.getZ();
            this.world = loc.getWorld().getName();
            this.type  = type;
        }

        public Waypoint(double x, double y, double z, String world, SegmentType type) {
            this.x = x; this.y = y; this.z = z;
            this.world = world;
            this.type  = type;
        }
    }

    private final String id;          // unique ID used as file/marker key
    private final String fromName;    // start point name (e.g. "Spawn")
    private final String toName;      // end point name (e.g. "Village")
    private final List<Waypoint> waypoints;

    public RoadDefinition(String id, String fromName, String toName) {
        this.id        = id;
        this.fromName  = fromName;
        this.toName    = toName;
        this.waypoints = new ArrayList<>();
    }

    public RoadDefinition(String id, String fromName, String toName, List<Waypoint> waypoints) {
        this.id        = id;
        this.fromName  = fromName;
        this.toName    = toName;
        this.waypoints = new ArrayList<>(waypoints);
    }

    public void addWaypoint(Location loc, SegmentType type) {
        waypoints.add(new Waypoint(loc, type));
    }

    public String getId()             { return id; }
    public String getFromName()       { return fromName; }
    public String getToName()         { return toName; }
    public String getDisplayName()    { return fromName + "-" + toName + " Road"; }
    public List<Waypoint> getWaypoints() { return waypoints; }
}
