package fr.aym.gtwmap.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class MapContainerClient extends MapContainer implements Function<PartPos, Void>
{
	private Map<PartPos, DynamicTexture> textures = new HashMap<>();
	private Map<PartPos, ResourceLocation> locations = new HashMap<>();
	private Minecraft mc = Minecraft.getMinecraft();
	
	@Override
	public MapPart requestTile(int x, int z, World world, EntityPlayer requester)
	{
		/*if(x < 0)
			x-=400;
		if(z < 0)
			z-=400;*/
		//System.out.println("RQ " + x + " " + z +" /400 = " + (x/400) + " " + (z/400));
		PartPos pos = new PartPos(x/TILE_WIDTH, z/TILE_HEIGHT);
		DynamicTexture mapTexture = null;
		if(textures.containsKey(pos))
		{
			mapTexture = textures.get(pos);
		}
		else
		{
			//System.out.println(">> CREATE " + pos + " " + textures.get(pos) + " " + locations.get(pos));
	        mapTexture = new DynamicTexture(TILE_WIDTH, TILE_HEIGHT);
	        locations.put(pos, mc.getTextureManager().getDynamicTextureLocation("dynmap:"+pos, mapTexture));
	        textures.put(pos, mapTexture);
		}
		//System.out.println(">> REQUEST " + pos + " " + textures.get(pos) + " " + locations.get(pos));
		return super.requestTile(world, pos, this, mapTexture.getTextureData());
	}

	@Override
	public Void apply(PartPos t) {
		//System.out.println(">> REFRESH " + t + " " + textures.get(t) + " " + locations.get(t));
		textures.get(t).updateDynamicTexture();
		return null;
	}
	
	public ResourceLocation getLocation(PartPos pos) {
		//System.out.println(">> GET " + pos + " " + textures.get(pos) + " " + locations.get(pos));
		return locations.get(pos);
	}

	public void dirtyAll() {
		for(MapPart part : tiles.values())
			part.setDirty(true, null);
	}
}
