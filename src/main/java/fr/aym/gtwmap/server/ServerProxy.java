package fr.aym.gtwmap.server;

import fr.aym.gtwmap.common.CommonProxy;
import net.minecraftforge.common.MinecraftForge;

public class ServerProxy extends CommonProxy {
    @Override
    public void preInit() {
        super.preInit();
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());
    }
}
