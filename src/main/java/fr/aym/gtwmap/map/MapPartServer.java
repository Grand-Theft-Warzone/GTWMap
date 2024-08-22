package fr.aym.gtwmap.map;

import net.minecraft.world.World;

public class MapPartServer extends MapPart {
    public MapPartServer(World world, PartPos pos, int width, int length) {
        super(world, pos, width, length, null);
    }

    @Override
    public void onContentsChange() {
        ((MapContainerServer) MapContainer.getInstance(false)).onContentsChange(this);
    }
}
