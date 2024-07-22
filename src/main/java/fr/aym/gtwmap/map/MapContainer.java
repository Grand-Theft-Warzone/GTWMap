package fr.aym.gtwmap.map;

import fr.aym.gtwmap.utils.GtwMapConstants;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public abstract class MapContainer {
    private static MapContainer clientContainer;
    private static MapContainer serverContainer;

    public static MapContainer getInstance(boolean isClient) {
        MapContainer container = isClient ? clientContainer : serverContainer;
        if (container == null) {
            container = isClient ? new MapContainerClient() : new MapContainerServer();
        }
        return container;
    }

    protected void setThisClientInstance() {
        clientContainer = this;
    }

    protected void setThisServerInstance() {
        serverContainer = this;
    }

    protected final Map<PartPos, MapPart> tiles = new HashMap<>();

    public MapPart requestTile(int x, int z, World world, @Nullable EntityPlayer requester) {
        return requestTile(world, new PartPos(x / GtwMapConstants.TILE_SIZE, z / GtwMapConstants.TILE_SIZE), requester);
    }

    protected MapPart requestTile(World world, PartPos pos, @Nullable EntityPlayer requester) {
        if (tiles.containsKey(pos)) {
            return tiles.get(pos);
        } else {
            MapPart part = createMapPart(world, pos, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE);
            tiles.put(pos, part);
            return part;
        }
    }

    protected MapPart requestDirect(PartPos pos) {
        return tiles.get(pos);
    }

    protected void cleanTile(PartPos pos) {
        tiles.remove(pos);
    }

    protected abstract MapPart createMapPart(World world, PartPos pos, int width, int length);
}
