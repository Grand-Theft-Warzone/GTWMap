package fr.aym.gtwmap.client;

import fr.aym.gtwmap.client.gui.GuiBigMap;
import fr.aym.gtwmap.common.CommonProxy;
import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.PartPos;
import fr.aym.gtwmap.server.ServerEventHandler;
import fr.aym.gtwmap.utils.GtwMapConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        super.preInit();

        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());

        ClientRegistry.registerKeyBinding(ClientEventHandler.openMap);
    }

    @Override
    public void handleCS18(PartPos pos, int[] blockData, int currentlyLoading) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            int x = pos.xOrig * GtwMapConstants.TILE_SIZE;
            int z = pos.zOrig * GtwMapConstants.TILE_SIZE;
            MapContainer.getInstance(true).requestTile(x, z, Minecraft.getMinecraft().world, null).feedWidthBlockData(blockData);
            GuiBigMap.loadingTiles = currentlyLoading;
        });
    }
}
