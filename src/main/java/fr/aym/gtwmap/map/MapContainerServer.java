package fr.aym.gtwmap.map;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.network.CS18PacketMapPart;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MapContainerServer extends MapContainer implements Function<PartPos, Void> {
    private Map<PartPos, List<EntityPlayerMP>> requests = new ConcurrentHashMap<>();
    private Map<EntityPlayer, Set<PartPos>> requestsRevesed = new ConcurrentHashMap<>();
    private Map<PartPos, Set<EntityPlayerMP>> pendingUpdates = new ConcurrentHashMap<>();

    @Getter
    private static Map<PartPos, Set<Function<MapPart, Void>>> loadQueue = new ConcurrentHashMap<>();

    public static final Function<MapPart, Void> RELOAD_TILE = t -> {
        t.updateMapContents();
        return null;
    };
    public static final Function<MapPart, Void> T2 = t -> {
        System.out.println("Applyed request of " + t.getPos());
        return null;
    };

    public static void updateQueue(World world, MapContainerServer mapContainer) {
        if (!loadQueue.isEmpty()) {
            Entry<PartPos, Set<Function<MapPart, Void>>> entry = loadQueue.entrySet().iterator().next();
            if (mapContainer.requestDirect(entry.getKey()) == null) {
                try {
                    MapPart part = MapLoader.loadPartAt(world, entry.getKey(), mapContainer);
                    if (part != null) {
                        mapContainer.tiles.put(entry.getKey(), part);
                        for (Function<MapPart, Void> func : entry.getValue()) {
                            if (func != null)
                                func.apply(part);
                        }
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            MapPart part = mapContainer.requestTile(world, entry.getKey(), mapContainer, null);
            for (Function<MapPart, Void> func : entry.getValue())
                func.apply(part);
            loadQueue.remove(entry.getKey());
        }
    }

    @Override
    public MapPart requestTile(int x, int z, World world, EntityPlayer requester) {
        /*if (x < 0)
            x -= 400;
        if (z < 0)
            z -= 400;*/
        PartPos pos = new PartPos(x / TILE_WIDTH, z / TILE_HEIGHT);

        if (requester != null) {
            if (!requests.containsKey(pos)) {
                List list = new ArrayList<>();
                list.add(requester);
                requests.put(pos, list);
                //System.out.println("Add target: "+requester+" "+pos);
            } else
                requests.get(pos).add((EntityPlayerMP) requester);

            if (!requestsRevesed.containsKey(requester)) {
                Set list = new HashSet<>();
                list.add(pos);
                requestsRevesed.put(requester, list);
            } else
                requestsRevesed.get(requester).add(pos);
        }

        if (requestDirect(pos) == null) {
            try {
                MapPart part = MapLoader.loadPartAt(world, pos, this);
                if (part != null) {
                    tiles.put(pos, part);
                    return part;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return super.requestTile(world, pos, this, null);
    }

    public void requestTileServer(int x, int z, EntityPlayer requester, Function<MapPart, Void> action) {
        /*if (x < 0)
            x -= 400;
        if (z < 0)
            z -= 400;*/
        PartPos pos = new PartPos(x / TILE_WIDTH, z / TILE_HEIGHT);

        if (requester != null) {
            if (!requests.containsKey(pos)) {
                List list = new ArrayList<>();
                list.add(requester);
                requests.put(pos, list);
                //System.out.println("Add target: "+requester+" "+pos);
            } else
                requests.get(pos).add((EntityPlayerMP) requester);

            if (!requestsRevesed.containsKey(requester)) {
                Set list = new HashSet<>();
                list.add(pos);
                requestsRevesed.put(requester, list);
            } else
                requestsRevesed.get(requester).add(pos);
        }
        if (!loadQueue.containsKey(pos)) {
            Set<Function<MapPart, Void>> set = new HashSet();
            set.add(action);
            loadQueue.put(pos, set);
        } else {
            loadQueue.get(pos).add(action);
        }
    }

    @Override
    public Void apply(PartPos t) {
        if (requests.containsKey(t)) {
            Set list;
            if (!pendingUpdates.containsKey(t)) {
                list = new HashSet<>();
                pendingUpdates.put(t, list);
            } else
                list = pendingUpdates.get(t);
            list.addAll(requests.get(t));
        }
        return null;
    }

    public void update() {
        Iterator<Entry<PartPos, Set<EntityPlayerMP>>> it = pendingUpdates.entrySet().iterator();
        for (int i = 0; it.hasNext(); i++) {
            Entry<PartPos, Set<EntityPlayerMP>> e = it.next();
            MapPart part = requestDirect(e.getKey());
            if (part == null) {
                GtwMapMod.log.error("Part has been removed before update: {}", e.getKey());
                continue;
            }
            CS18PacketMapPart pkt = new CS18PacketMapPart(requestDirect(e.getKey()));
            for (EntityPlayerMP player : e.getValue()) {
                //System.out.println("Send to: "+player+" "+e.getKey());
                GtwMapMod.getNetwork().sendTo(pkt, player);
            }
        }
        pendingUpdates.clear();
    }

    public void removeRequester(EntityPlayer player) {
        //pendingUpdates.remove(player);
        Set<PartPos> pos = requestsRevesed.remove(player);
        if (pos != null) {
            for (PartPos p : pos) {
                requests.get(p).remove(player);
            }
        }
    }
}
