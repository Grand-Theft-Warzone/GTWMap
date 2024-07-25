package fr.aym.gtwmap.common.gps;

import fr.aym.acslib.utils.nbtserializer.ISerializable;
import fr.aym.acslib.utils.packetserializer.ISerializablePacket;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.network.BBMessageGpsNodes;
import lombok.Getter;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import javax.vecmath.Vector3f;
import java.util.*;
import java.util.stream.Collectors;

public class GpsNode implements ISerializable, ISerializablePacket {
    @Getter
    protected UUID id;
    @Getter
    protected Vector3f position;
    protected Set<GpsNode> neighbors;
    protected Set<UUID> neighborsIds;

    public GpsNode() {
    }

    public GpsNode(Vector3f position, Set<GpsNode> neighbors) {
        this.position = position;
        this.neighbors = neighbors;
        this.id = UUID.randomUUID();
    }

    public Set<GpsNode> getNeighbors(GpsNodes manager) {
        if (neighbors == null)
            resolveNeighbors(manager);
        return neighbors;
    }

    protected void resolveNeighbors(GpsNodes manager) {
        neighbors = neighborsIds.stream().map(manager::getNode).collect(Collectors.toSet());
        neighbors.removeIf(Objects::isNull);
        neighborsIds.clear();
        neighborsIds = null;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Object[] getObjectsToSave() {
        if (neighborsIds != null) // Nodes not resolved yet
            return new Object[]{id, position.x, position.y, position.z, neighborsIds};
        return new Object[]{id, position.x, position.y, position.z, neighbors.stream().map(GpsNode::getId).collect(Collectors.toList())};
    }

    @Override
    public void populateWithSavedObjects(Object[] objects) {
        id = (UUID) objects[0];
        position = new Vector3f((float) objects[1], (float) objects[2], (float) objects[3]);
        neighborsIds = new HashSet<>((List<UUID>) objects[4]);
        if (neighbors != null) {
            neighbors.clear();
            neighbors = null;
        }
    }

    public AxisAlignedBB getBoundingBox() {
        //TODO CACHE THE VALUE
        return new AxisAlignedBB(position.x - 0.5, position.y - 0.5, position.z - 0.5, position.x + 0.5, position.y + 0.5, position.z + 0.5);
    }

    public void create(GpsNodes manager, boolean isRemote) {
        if (isRemote)
            GtwMapMod.network.sendToServer(new BBMessageGpsNodes(this));
        else {
            manager.addNode(this);
        }
    }

    public void delete(GpsNodes manager, boolean isRemote) {
        if (isRemote)
            GtwMapMod.network.sendToServer(new BBMessageGpsNodes(BBMessageGpsNodes.Action.REMOVE, Collections.singletonList(getId())));
        else {
            // TODO ASYNC JOB
            for (GpsNode node : manager.getNodes()) {
                if (node.neighbors == null)
                    node.neighborsIds.remove(id);
                else
                    node.neighbors.remove(this);
            }
            manager.removeNode(this);
        }
    }

    public void addNeighbor(GpsNodes manager, GpsNode pointedNode, boolean isRemote) {
        if (isRemote)
            GtwMapMod.network.sendToServer(new BBMessageGpsNodes(BBMessageGpsNodes.Action.LINK_NODES, Arrays.asList(this.getId(), pointedNode.getId())));
        else {
            getNeighbors(manager).add(pointedNode);
            manager.markDirty();
        }
    }

    public void removeNeighbor(GpsNodes manager, GpsNode pointedNode, boolean isRemote) {
        if (isRemote)
            GtwMapMod.network.sendToServer(new BBMessageGpsNodes(BBMessageGpsNodes.Action.UNLINK_NODES, Arrays.asList(this.getId(), pointedNode.getId())));
        else {
            getNeighbors(manager).remove(pointedNode);
            manager.markDirty();
        }
    }

    public boolean isIn(AxisAlignedBB box) {
        return position.x > box.minX && position.x < box.maxX && position.y > box.minY && position.y < box.maxY && position.z > box.minZ && position.z < box.maxZ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GpsNode node = (GpsNode) o;
        return Objects.equals(id, node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public float getDistance(Vec3d around) {
        return (float) around.subtract(position.x, position.y, position.z).length();
    }

    public double getDistance(GpsNode node) {
        return getDistance(node.getPosition());
    }

    @Override
    public String toString() {
        return "PathNode{" +
                "id=" + id +
                ", position=" + position +
                ", neighbors=" + (neighbors == null ? null : neighbors.stream().map(n -> "N{id=" + n.getId() + ", pos=" + n.getPosition()).collect(Collectors.toList())) +
                ", neighborsIds=" + neighborsIds +
                '}';
    }

    public double getDistance(Vector3f position) {
        return Math.sqrt(Math.pow(position.x - this.position.x, 2) + Math.pow(position.y - this.position.y, 2) + Math.pow(position.z - this.position.z, 2));
    }
}
