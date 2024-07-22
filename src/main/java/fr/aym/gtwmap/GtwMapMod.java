package fr.aym.gtwmap;

import fr.aym.acsguis.api.ACsGuiApi;
import fr.aym.gtwmap.client.gui.GuiMapTest;
import fr.aym.gtwmap.client.gui.GuiMinimap;
import fr.aym.gtwmap.common.CommonProxy;
import fr.aym.gtwmap.map.MapContainerClient;
import fr.aym.gtwmap.map.MapContainerServer;
import fr.aym.gtwmap.map.MapLoader;
import fr.aym.gtwmap.network.CS18PacketMapPart;
import fr.aym.gtwmap.network.S19PacketMapPartQuery;
import fr.aym.gtwmap.server.CommandGtwMap;
import fr.aym.gtwmap.utils.Config;
import lombok.Getter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

import static fr.aym.gtwmap.utils.GtwMapConstants.*;

@Mod(modid = ID, name = NAME, version = VERSION)
public class GtwMapMod {
    @Mod.Instance(value = ID)
    public static GtwMapMod instance;

    @Getter
    public static SimpleNetworkWrapper network;

    @SidedProxy(serverSide = "fr.aym.gtwmap.server.ServerProxy", clientSide = "fr.aym.gtwmap.client.ClientProxy")
    public static CommonProxy proxy;

    public static final Logger log = LogManager.getLogger("GtwNpcMod");

    public GtwMapMod() {
        ACsGuiApi.registerStyleSheetToPreload(GuiMapTest.STYLE);
        ACsGuiApi.registerStyleSheetToPreload(GuiMinimap.STYLE);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        log.info("Mod " + NAME + " version " + VERSION + " is running. By Aym'.");
        Config.load(event.getSuggestedConfigurationFile(), event.getSide());

        proxy.preInit();

        network = NetworkRegistry.INSTANCE.newSimpleChannel(ID + ".ch");
        network.registerMessage(CS18PacketMapPart.Handler.class, CS18PacketMapPart.class, 18, Side.CLIENT);
        network.registerMessage(S19PacketMapPartQuery.Handler.class, S19PacketMapPartQuery.class, 190, Side.SERVER);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (event.getSide().isClient()) {
            new MapContainerClient();
        } else {
            //  new MapContainerServer();
        }
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        new MapLoader(event, new MapContainerServer());
        event.registerServerCommand(new CommandGtwMap());
    }
}
