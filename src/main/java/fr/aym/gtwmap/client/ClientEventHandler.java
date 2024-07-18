package fr.aym.gtwmap.client;

import fr.aym.gtwmap.client.gui.GuiBigMap;
import fr.aym.gtwmap.client.gui.GuiMapTest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
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
                Minecraft.getMinecraft().displayGuiScreen(new GuiMapTest().getGuiScreen());
            }
            /* map texture png if (Keyboard.isKeyDown(Keyboard.KEY_L)) {
                Minecraft.getMinecraft().displayGuiScreen(new GuiBigMap(new ScaledResolution(MC)).getGuiScreen());
            }*/
        }
    }
}
