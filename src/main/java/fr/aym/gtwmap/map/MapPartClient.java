package fr.aym.gtwmap.map;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

@Getter
@SideOnly(Side.CLIENT)
public class MapPartClient extends MapPart {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final ResourceLocation location;
    private final DynamicTexture mapTexture;

    public MapPartClient(World world, PartPos pos, int width, int length, DynamicTexture textureData) {
        super(world, pos, width, length, textureData.getTextureData());
        this.mapTexture = textureData;
        this.location = mc.getTextureManager().getDynamicTextureLocation("dynmap:" + pos, textureData);
    }

    @Override
    public void onContentsChange() {
        mapTexture.updateDynamicTexture();
    }
}
