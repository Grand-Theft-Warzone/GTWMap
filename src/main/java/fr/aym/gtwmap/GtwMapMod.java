package fr.aym.gtwmap;

import fr.aym.acsguis.api.ACsGuiApi;
import fr.aym.acslib.utils.nbtserializer.NBTDataSerializer;
import fr.aym.acslib.utils.nbtserializer.NBTSerializer;
import fr.aym.acslib.utils.packetserializer.PacketDataSerializer;
import fr.aym.acslib.utils.packetserializer.PacketSerializer;
import fr.aym.gtwmap.client.gui.GuiBigMap;
import fr.aym.gtwmap.client.gui.GuiMinimap;
import fr.aym.gtwmap.common.CommonProxy;
import fr.aym.gtwmap.map.MapContainerClient;
import fr.aym.gtwmap.map.MapContainerServer;
import fr.aym.gtwmap.map.loader.MapLoader;
import fr.aym.gtwmap.network.*;
import fr.aym.gtwmap.server.CommandGtwMap;
import fr.aym.gtwmap.utils.BlockColorConfig;
import fr.aym.gtwmap.utils.Config;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2f;

import java.io.File;
import java.util.Set;

import static fr.aym.gtwmap.utils.GtwMapConstants.*;

@Mod(modid = ID, name = NAME, version = VERSION)
public class GtwMapMod {
    @Mod.Instance(value = ID)
    public static GtwMapMod instance;

    @Getter
    public static SimpleNetworkWrapper network;

    @SidedProxy(serverSide = "fr.aym.gtwmap.server.ServerProxy", clientSide = "fr.aym.gtwmap.client.ClientProxy")
    public static CommonProxy proxy;

    public static final Logger log = LogManager.getLogger("GtwMapMod");

    public GtwMapMod() {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            ACsGuiApi.registerStyleSheetToPreload(GuiBigMap.STYLE);
            ACsGuiApi.registerStyleSheetToPreload(GuiMinimap.STYLE);
        }

        NBTSerializer.addCustomSerializer(new NBTDataSerializer<Vector2f>() {
            @Override
            public Class<Vector2f> objectType() {
                return Vector2f.class;
            }

            @Override
            public void serialize(NBTTagCompound nbtTagCompound, Vector2f vector2f) {
                nbtTagCompound.setFloat("x", vector2f.x);
                nbtTagCompound.setFloat("y", vector2f.y);
            }

            @Override
            public Vector2f unserialize(NBTTagCompound nbtTagCompound) {
                return new Vector2f(nbtTagCompound.getFloat("x"), nbtTagCompound.getFloat("y"));
            }
        });

        PacketSerializer.addCustomSerializer(new PacketDataSerializer<Vector2f>() {
            @Override
            public Class<Vector2f> objectType() {
                return Vector2f.class;
            }

            @Override
            public void serialize(ByteBuf byteBuf, Vector2f vector2f) {
                byteBuf.writeFloat(vector2f.x);
                byteBuf.writeFloat(vector2f.y);
            }

            @Override
            public Vector2f unserialize(ByteBuf byteBuf) {
                return new Vector2f(byteBuf.readFloat(), byteBuf.readFloat());
            }
        });

        NBTSerializer.addCustomSerializer(new NBTDataSerializer<Object[]>() {
            @Override
            public Class<Object[]> objectType() {
                return Object[].class;
            }

            @Override
            public void serialize(NBTTagCompound nbtTagCompound, Object[] objects) {
                NBTTagCompound list = new NBTTagCompound();
                for (int i = 0; i < objects.length; i++) {
                    NBTBase nbtBase = NBTSerializer.serialize(objects[i]);
                    list.setTag(String.valueOf(i), nbtBase);
                }
                nbtTagCompound.setTag("list", list);
            }

            @Override
            public Object[] unserialize(NBTTagCompound nbtTagCompound) {
                NBTTagCompound list = nbtTagCompound.getCompoundTag("list");
                Set<String> keys = list.getKeySet();
                Object[] objects = new Object[keys.size()];
                for (String key : keys) {
                    objects[Integer.parseInt(key)] = NBTSerializer.unserialize(list.getTag(key));
                }
                return objects;
            }
        });

        PacketSerializer.addCustomSerializer(new PacketDataSerializer<Object[]>() {
            @Override
            public Class<Object[]> objectType() {
                return Object[].class;
            }

            @Override
            public void serialize(ByteBuf byteBuf, Object[] objects) {
                byteBuf.writeInt(objects.length);
                for (Object object : objects) {
                    PacketSerializer.serialize(byteBuf, object);
                }
            }

            @Override
            public Object[] unserialize(ByteBuf byteBuf) {
                int length = byteBuf.readInt();
                Object[] objects = new Object[length];
                for (int i = 0; i < length; i++) {
                    objects[i] = PacketSerializer.unserialize(byteBuf);
                }
                return objects;
            }
        });
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        log.info("Mod " + NAME + " version " + VERSION + " is running. By Aym'.");
        Config.load(event.getSuggestedConfigurationFile(), event.getSide());

        proxy.preInit();

        network = NetworkRegistry.INSTANCE.newSimpleChannel(ID + ".ch");
        network.registerMessage(CS18PacketMapPart.Handler.class, CS18PacketMapPart.class, 18, Side.CLIENT);
        network.registerMessage(S19PacketMapPartQuery.Handler.class, S19PacketMapPartQuery.class, 190, Side.SERVER);
        network.registerMessage(BBMessageGpsNodes.HandlerClient.class, BBMessageGpsNodes.class, 191, Side.CLIENT);
        network.registerMessage(BBMessageGpsNodes.HandlerServer.class, BBMessageGpsNodes.class, 192, Side.SERVER);
        network.registerMessage(SCMessageEditMap.Handler.class, SCMessageEditMap.class, 193, Side.CLIENT);
        network.registerMessage(SCMessagePlayerList.Handler.class, SCMessagePlayerList.class, 194, Side.CLIENT);
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

    @Mod.EventHandler
    public void onLoadEnd(FMLLoadCompleteEvent event) {
        BlockColorConfig.init(new File("MapData", "colors.cfg"), false);
    }
}
