package fr.aym.gtwmap.client;

import fr.aym.acsguis.api.ACsGuiApi;
import fr.aym.gtwmap.api.GtwMapApi;
import fr.aym.gtwmap.api.ITrackableObject;
import fr.aym.gtwmap.client.gps.GpsNavigator;
import fr.aym.gtwmap.client.gui.GuiBigMap;
import fr.aym.gtwmap.client.gui.GuiMinimap;
import fr.aym.gtwmap.utils.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.passive.EntityPig;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class ClientEventHandler {
    public static final Minecraft MC = Minecraft.getMinecraft();

    public static final GpsNavigator gpsNavigator = new GpsNavigator();

    //TODO TRANSLATE
    public static KeyBinding openMap = new KeyBinding("Open map", Keyboard.KEY_M, "GtwMap");

    @SubscribeEvent
    public void onKeyTyped(InputEvent.KeyInputEvent event) {
        if (MC.world != null) {
            if (openMap.isPressed()) {
                ACsGuiApi.asyncLoadThenShowGui("client_map", () -> new GuiBigMap(false));
            }
            if (Config.debug && Keyboard.isKeyDown(Keyboard.KEY_P)) {
                if (ACsGuiApi.getDisplayHudGui() != null) {
                    ACsGuiApi.closeHudGui();
                } else {
                    ACsGuiApi.asyncLoadThenShowHudGui("minimap", GuiMinimap::new);
                }
            }
            /* map texture png if (Keyboard.isKeyDown(Keyboard.KEY_L)) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiBigMap(new ScaledResolution(MC)).getGuiScreen());
            }*/
        }
    }

    @SubscribeEvent
    public void clientConnected(EntityJoinWorldEvent event) {
        if (event.getEntity() == MC.player) {
            gpsNavigator.clear();
            GtwMapApi.getTrackedObjects().clear();
            ACsGuiApi.asyncLoadThenShowHudGui("minimap", GuiMinimap::new);
        }
        /*if (event.getEntity() instanceof EntityPig && event.getEntity().world.isRemote) {
            System.out.println("Adding pig on map " + event.getEntity()+ " "+event.getEntity().hashCode());
            GtwMapApi.addTrackedObject(new ITrackableObject.TrackedEntity(event.getEntity(), "Police", "player_white") {
                @Override
                public int renderPoliceCircleAroundRadius() {
                    return 30;
                }
            });
        }*/
    }

    @SubscribeEvent
    public void clientTickEvent(TickEvent.ClientTickEvent event) {
        if(MC.player != null) {
            gpsNavigator.update(MC.player);
        }
    }
}
