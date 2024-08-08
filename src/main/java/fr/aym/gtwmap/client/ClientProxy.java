package fr.aym.gtwmap.client;

import fr.aym.gtwmap.common.CommonProxy;
import fr.aym.gtwmap.server.ServerEventHandler;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        super.preInit();

        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());
    }
}
