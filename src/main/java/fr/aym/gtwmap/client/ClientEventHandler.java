package fr.aym.gtwmap.client;

import fr.aym.acsguis.api.ACsGuiApi;
import fr.aym.gtwmap.client.gui.GuiMapTest;
import fr.aym.gtwmap.client.gui.GuiMinimap;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class ClientEventHandler {
    public static final Minecraft MC = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onKeyTyped(InputEvent.KeyInputEvent event) {
        if (MC.world != null) {
            if (Keyboard.isKeyDown(Keyboard.KEY_O)) { // Vraie map dynamique
                ACsGuiApi.asyncLoadThenShowGui("map_test", GuiMapTest::new);
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_P)) { // Vraie mini-map dynamique
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
        if (event.getEntity() == MC.player && ACsGuiApi.getDisplayHudGui() == null) {
            ACsGuiApi.asyncLoadThenShowHudGui("minimap", GuiMinimap::new);
        }
    }
}
