package fr.aym.gtwmap.common.gps;

import lombok.Getter;
import lombok.Setter;

import javax.vecmath.Vector3f;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

@Setter
@Getter
public class WaypointNode extends GpsNode {
    private String name;
    private String icon;
    private int iconSize;

    public WaypointNode() {
    }

    public WaypointNode(String name, String icon, Vector3f position) {
        super(position, new HashSet<>());
        this.name = name;
        this.icon = icon;
        updateIconSize();
    }

    public void setIcon(String icon) {
        this.icon = icon;
        updateIconSize();
    }

    public void updateIconSize() {
        this.iconSize = getIconSize(icon);
    }

    public static int getIconSize(String icon) {
        return icon.contains("gun") || icon.contains("bank") || icon.contains("car") || icon.equals("r_arrow") ? 50 :
                icon.contains("player_white") || icon.contains("arrow_white") ? 500 : 512;
    }

    @Override
    public Object[] getObjectsToSave() {
        return new Object[]{super.getObjectsToSave(), name, icon};
    }

    @Override
    public void populateWithSavedObjects(Object[] objects) {
        super.populateWithSavedObjects((Object[]) objects[0]);
        name = (String) objects[1];
        icon = (String) objects[2];
    }

    public GpsNode toGpsNode(GpsNodes manager, boolean isRemote) {
        GpsNode node = new GpsNode();
        node.id = id;
        node.position = position;
        node.neighbors = neighbors;
        node.bezierCurves = bezierCurves;
        node.create(manager, isRemote);
        return node;
    }

    public static WaypointNode fromGpsNode(GpsNode node, String name, String icon, GpsNodes manager, boolean isRemote) {
        WaypointNode waypointNode = new WaypointNode();
        waypointNode.id = node.getId();
        waypointNode.position = node.getPosition();
        waypointNode.neighbors = node.getNeighbors(manager);
        waypointNode.bezierCurves = node.getBezierCurves();
        waypointNode.name = name;
        waypointNode.icon = icon;
        waypointNode.create(manager, isRemote);
        return waypointNode;
    }
}
