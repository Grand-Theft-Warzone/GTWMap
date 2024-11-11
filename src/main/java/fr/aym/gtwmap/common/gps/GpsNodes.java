package fr.aym.gtwmap.common.gps;

import fr.aym.acslib.utils.nbtserializer.ISerializable;
import fr.aym.acslib.utils.nbtserializer.NBTSerializer;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.utils.GtwMapConstants;
import lombok.Getter;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = GtwMapConstants.ID)
public class GpsNodes extends WorldSavedData implements ISerializable {
    @Getter
    private static GpsNodes instance;

    private final Map<UUID, GpsNode> nodes = new ConcurrentHashMap<>();

    public GpsNodes(String name) {
        super(name);
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Object[] getObjectsToSave() {
        return new Object[]{nodes};
    }

    @Override
    public void populateWithSavedObjects(Object[] objects) {
        nodes.clear();
        nodes.putAll((Map<UUID, GpsNode>) objects[0]);
    }

    public GpsNode getNode(UUID id) {
        return nodes.get(id);
    }

    public Collection<GpsNode> getNodes() {
        return nodes.values();
    }

    public Collection<GpsNode> getNodesWithinAABB(AxisAlignedBB aabb) {
        return nodes.values().stream().filter(gpsNode -> gpsNode.isIn(aabb)).collect(java.util.stream.Collectors.toList());
    }

    public void addNode(GpsNode gpsNode) {
        nodes.put(gpsNode.getId(), gpsNode);
        markDirty();
    }

    public void removeNode(GpsNode gpsNode) {
        // This DOES NOT remove the links with other nodes
        nodes.remove(gpsNode.getId());
        markDirty();
    }

    public boolean hasNode(UUID id) {
        return nodes.containsKey(id);
    }

    public GpsNode findNearestNode(Vec3d around, List<GpsNode> avoidNodes) {
        return nodes.values().stream().filter(n -> !avoidNodes.contains(n)).min(Comparator.comparingDouble(gpsNode -> gpsNode.getDistance(around))).orElse(null);
    }

    public Set<GpsNode> findNodesInRadius(Vec3d around, List<GpsNode> avoidNodes, int radius) {
        return nodes.values().stream().filter(n -> !avoidNodes.contains(n) && n.getDistance(around) < radius).collect(Collectors.toSet());
    }

    public List<GpsNode> createPathToNode(GpsNode startNode, GpsNode end) {
        //TOO REWORK
        // System.out.println("Start node : " + startNode + " from " + startPos);
        Queue<GpsRouteNode> openSet = new PriorityQueue<>();
        Map<GpsNode, GpsRouteNode> allNodes = new HashMap<>();
        GpsRouteNode start = new GpsRouteNode(startNode, null, 0d, startNode.getDistance(end));
        openSet.add(start);
        allNodes.put(startNode, start);
        while (!openSet.isEmpty()) {
            GpsRouteNode next = openSet.poll();
            if (next.getCurrent().equals(end)) {
                List<GpsNode> route = new LinkedList<>();
                GpsRouteNode current = next;
                do {
                    route.add(0, current.getCurrent());
                    current = allNodes.get(current.getPrevious());
                } while (current != null);
                //Reverse
                return route;
            }
            next.getCurrent().getNeighbors(this).forEach(connection -> {
                GpsRouteNode nextNode = allNodes.getOrDefault(connection, new GpsRouteNode(connection));
                allNodes.put(connection, nextNode);
                double newScore = next.getRouteScore() + next.getCurrent().getDistance(connection);
                if (newScore < nextNode.getRouteScore()) {
                    nextNode.setPrevious(next.getCurrent());
                    nextNode.setRouteScore(newScore);
                    nextNode.setEstimatedScore(newScore + connection.getDistance(end));
                    openSet.add(nextNode);
                }
            });
        }
        //System.out.println("No path found from " + startNode + " to " + end);
        return null;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTSerializer.unserialize(nbt.getCompoundTag("nodes"), this);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTBase base = NBTSerializer.serialize(this);
        compound.setTag("nodes", base);
        return compound;
    }

    @SubscribeEvent
    public static void load(WorldEvent.Load event) {
        if (event.getWorld().provider.getDimensionType() == DimensionType.OVERWORLD) {
            if (!event.getWorld().isRemote) {
                try {
                    instance = (GpsNodes) event.getWorld().getPerWorldStorage().getOrLoadData(GpsNodes.class, "GtwMapNodes");
                } catch (Exception e) {
                    instance = null;
                    GtwMapMod.log.fatal("Cannot load saved gps nodes !", e);
                }
                if (instance == null) {
                    instance = new GpsNodes("GtwMapNodes");
                    event.getWorld().getPerWorldStorage().setData("GtwMapNodes", instance);
                }
            } else if (instance == null) {
                instance = new GpsNodes("ClientGtwMapNodes");
            }
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        if (event.getWorld().provider.getDimensionType() == DimensionType.OVERWORLD && instance != null && (!event.getWorld().isRemote || FMLCommonHandler.instance().getMinecraftServerInstance() == null)) {
            instance.nodes.clear();
            instance = null;
        }
    }
}
