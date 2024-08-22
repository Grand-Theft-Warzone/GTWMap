package fr.aym.gtwmap.api;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.common.gps.GpsNode;
import fr.aym.gtwmap.common.gps.GpsNodes;
import fr.aym.gtwmap.common.gps.WaypointNode;
import fr.aym.gtwmap.network.BBMessageGpsNodes;
import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * API for the GtwMap mod <br>
 * This class provides methods to interact with the gps nodes and waypoints on the map
 */
public class GtwMapApi {
    /**
     * -- GETTER --
     * Get all tracked objects
     *
     * @return All tracked objects
     */
    @Getter
    private static final Set<ITrackableObject<?>> trackedObjects = new HashSet<>();

    @Setter
    @Getter
    private static boolean renderPoliceBlinking = false;

    /**
     * Add a new waypoint marker to the map
     *
     * @param name     The name of the waypoint
     * @param icon     The icon of the waypoint (will load "assets/gtwmap/textures/gpps/wp_" + icon + ".png" from the assets of the mod)
     * @param position The position of the waypoint
     * @return The created waypoint node
     */
    public static WaypointNode addWaypointMarker(String name, String icon, Vector3f position) {
        WaypointNode node = new WaypointNode(name, icon, position);
        node.create(GpsNodes.getInstance(), false);
        return node;
    }

    /**
     * Remove a waypoint marker from the map
     *
     * @param nodeId The id of the waypoint node to remove
     */
    public static void removeWaypointMarker(UUID nodeId) {
        GpsNode node = GpsNodes.getInstance().getNode(nodeId);
        if (node == null)
            return;
        node.delete(GpsNodes.getInstance(), false);
    }

    /**
     * Change the name and/or icon of a waypoint marker
     *
     * @param nodeId The id of the waypoint node to update
     * @param name   The new name of the waypoint, or null to keep the current name
     * @param icon   The new icon of the waypoint, or null to keep the current icon
     */
    public static void updateWaypointMarker(UUID nodeId, @Nullable String name, @Nullable String icon) {
        WaypointNode node = (WaypointNode) GpsNodes.getInstance().getNode(nodeId);
        if (node == null)
            return;
        if (name != null)
            node.setName(name);
        if (icon != null)
            node.setIcon(icon);
        // Send the update to all clients
        GtwMapMod.network.sendToAll(new BBMessageGpsNodes(node));
        GpsNodes.getInstance().markDirty();
    }

    /**
     * Get all GPS nodes
     *
     * @return All GPS nodes
     */
    public static Collection<GpsNode> getGpsNodes() {
        return GpsNodes.getInstance().getNodes();
    }

    /**
     * Get all waypoint nodes
     *
     * @return All waypoint nodes
     */
    public static Collection<WaypointNode> getWaypointNodes() {
        return GpsNodes.getInstance().getNodes().stream().filter(node -> node instanceof WaypointNode).map(node -> (WaypointNode) node).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Mark all waypoints as dirty, so they will be saved to disk
     */
    public static void markWaypointsDirty() {
        GpsNodes.getInstance().markDirty();
    }

    /**
     * Add a new object to show on the map, and keep track of it when pos changes
     *
     * @param object The object to track
     */
    public static void addTrackedObject(ITrackableObject<?> object) {
        if (trackedObjects.contains(object)) {
            throw new IllegalArgumentException("Object " + object + "is already tracked");
        }
        trackedObjects.add(object);
    }

    /**
     * Remove a tracked object
     *
     * @param object The object to stop tracking
     */
    public static void removeTrackedObject(Object object) {
        trackedObjects.remove(object instanceof ITrackableObject ? object : new ITrackableObject.TrackedObjectWrapper(object));
    }

    public static boolean isTracked(Object object) {
        return trackedObjects.contains(object instanceof ITrackableObject ? object : new ITrackableObject.TrackedObjectWrapper(object));
    }
}
