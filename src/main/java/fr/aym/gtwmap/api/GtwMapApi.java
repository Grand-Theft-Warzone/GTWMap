package fr.aym.gtwmap.api;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.common.gps.GpsNode;
import fr.aym.gtwmap.common.gps.GpsNodes;
import fr.aym.gtwmap.common.gps.WaypointNode;
import fr.aym.gtwmap.network.BBMessageGpsNodes;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;
import java.util.Collection;
import java.util.UUID;

/**
 * API for the GtwMap mod <br>
 * This class provides methods to interact with the gps nodes and waypoints on the map
 */
public class GtwMapApi {
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
}
