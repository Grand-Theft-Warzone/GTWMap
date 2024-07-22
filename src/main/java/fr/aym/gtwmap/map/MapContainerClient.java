package fr.aym.gtwmap.map;

import fr.aym.gtwmap.utils.GtwMapConstants;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class MapContainerClient extends MapContainer {
    public MapContainerClient() {
        setThisClientInstance();
    }

    @Override
    public MapPart requestTile(int x, int z, World world, EntityPlayer requester) {
        PartPos pos = new PartPos(x / GtwMapConstants.TILE_SIZE, z / GtwMapConstants.TILE_SIZE);
        return super.requestTile(world, pos, requester);
    }

    @Override
    protected MapPart createMapPart(World world, PartPos pos, int width, int length) {
        return new MapPartClient(world, pos, width, length, new DynamicTexture(width, length));
    }

    public void dirtyAll() {
        for (MapPart part : tiles.values())
            part.setDirty(true, null);
    }
}
