package fr.aym.gtwmap.map;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.network.CS18PacketMapPart;
import fr.aym.gtwmap.utils.GtwMapConstants;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MapContainerServer extends MapContainer {
    private final Map<PartPos, List<EntityPlayerMP>> requests = new ConcurrentHashMap<>();
    private final Map<EntityPlayer, Set<PartPos>> requestsReversed = new ConcurrentHashMap<>();
    private final Map<PartPos, Set<EntityPlayerMP>> pendingUpdates = new ConcurrentHashMap<>();

    @Getter
    private final Map<PartPos, CompletableFuture<MapPart>> loadQueue = new ConcurrentHashMap<>();

    public MapContainerServer() {
        setThisServerInstance();
    }

    //TODO STATIC ???
    public void updateQueue(World world) {
        while (!loadQueue.isEmpty()) {
            Entry<PartPos, CompletableFuture<MapPart>> entry = loadQueue.entrySet().iterator().next();
            MapPart part = requestDirect(entry.getKey());
            if (part == null) {
                try {
                    part = MapLoader.getInstance().loadPartFromFile(world, entry.getKey());
                    if (part != null) {
                        tiles.put(entry.getKey(), part);
                        entry.getValue().complete(part);
                        continue;
                    }
                } catch (IOException e) {
                    GtwMapMod.log.fatal("Error loading map part at {} {} from queue", entry.getKey(), e);
                }
            }
            if (part == null)
                part = requestTile(world, entry.getKey(), null);
            entry.getValue().complete(part);
            loadQueue.remove(entry.getKey());
        }
    }

    @Override
    protected MapPart requestTile(World world, PartPos pos, @Nullable EntityPlayer requester) {
        if (requester != null) {
            if (!requests.containsKey(pos)) {
                List<EntityPlayerMP> list = new ArrayList<>();
                list.add((EntityPlayerMP) requester);
                requests.put(pos, list);
            } else {
                requests.get(pos).add((EntityPlayerMP) requester);
            }
            if (!requestsReversed.containsKey(requester)) {
                Set<PartPos> list = new HashSet<>();
                list.add(pos);
                requestsReversed.put(requester, list);
            } else {
                requestsReversed.get(requester).add(pos);
            }
        }
        MapPart part = requestDirect(pos);
        if (part != null) {
            return part;
        }
        try {
            part = MapLoader.getInstance().loadPartFromFile(world, pos);
            if (part != null) {
                tiles.put(pos, part);
                return part;
            }
        } catch (IOException e) {
            GtwMapMod.log.fatal("Error loading map part at {} {}", pos, e);
        }
        return super.requestTile(world, pos, requester);
    }

    public CompletableFuture<MapPart> requestTileLoading(int x, int z, EntityPlayer requester) {
        PartPos pos = new PartPos(x / GtwMapConstants.TILE_SIZE, z / GtwMapConstants.TILE_SIZE);
        if (requester != null) {
            if (!requests.containsKey(pos)) {
                List<EntityPlayerMP> list = new ArrayList<>();
                list.add((EntityPlayerMP) requester);
                requests.put(pos, list);
            } else {
                requests.get(pos).add((EntityPlayerMP) requester);
            }
            if (!requestsReversed.containsKey(requester)) {
                Set<PartPos> list = new HashSet<>();
                list.add(pos);
                requestsReversed.put(requester, list);
            } else {
                requestsReversed.get(requester).add(pos);
            }
        }
        MapPart part = requestDirect(pos);
        CompletableFuture<MapPart> future = new CompletableFuture<>();
        if(part != null) {
            part.refreshMapContents();
            future.complete(part);
            return future;
        }
        loadQueue.put(pos, future);
        return future;
    }

    public void update() {
        for (Entry<PartPos, Set<EntityPlayerMP>> e : pendingUpdates.entrySet()) {
            MapPart part = requestDirect(e.getKey());
            if (part == null) {
                GtwMapMod.log.warn("Part has been removed before update: {}", e.getKey());
                continue;
            }
            CS18PacketMapPart pkt = new CS18PacketMapPart(part, MapLoader.getInstance().getLoadingParts().size());
            for (EntityPlayerMP player : e.getValue()) {
                GtwMapMod.getNetwork().sendTo(pkt, player);
            }
        }
        pendingUpdates.clear();
        if (MapLoader.getInstance().getLoadingParts().isEmpty() && getLoadQueue().isEmpty()) {
            if (MapLoader.godMode != 0) {
                System.out.println("Render end !");
                MapLoader.godMode = 0;
            }
            if (MapLoader.listener != null) {
                MapLoader.listener.sendMessage(new TextComponentString("Opération terminée !"));
                MapLoader.listener = null;
            }
            MapLoader.amountToLoad = 0;
        }
    }

    public void removeRequester(EntityPlayer player) {
        //pendingUpdates.remove(player);
        System.out.println("REQUESTER REMOVE " + player);
        Set<PartPos> pos = requestsReversed.remove(player);
        if (pos != null) {
            for (PartPos p : pos) {
                requests.get(p).remove(player);
            }
        }
    }

    @Override
    protected MapPart createMapPart(World world, PartPos pos, int width, int length) {
        MapPart part = new MapPartServer(world, pos, width, length);
        part.refreshMapContents(); // will load the map part
        return part;
    }

    public void onContentsChange(MapPart part) {
        PartPos t = part.getPos();
        System.out.println("Contents change " + t + " " + requests.containsKey(t) + " " + requests);
        if (requests.containsKey(t)) {
            Set<EntityPlayerMP> list;
            if (!pendingUpdates.containsKey(t)) {
                list = new HashSet<>();
                pendingUpdates.put(t, list);
            } else {
                list = pendingUpdates.get(t);
            }
            list.addAll(requests.get(t));
            System.out.println("Pending updates are now " + pendingUpdates);
        }
    }
}
