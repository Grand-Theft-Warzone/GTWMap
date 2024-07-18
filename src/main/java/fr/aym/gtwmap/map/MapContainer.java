package fr.aym.gtwmap.map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class MapContainer 
{
	public static final int TILE_WIDTH = 400, TILE_HEIGHT = 400;
	private static ThreadLocal<MapContainer> INSTANCE = new ThreadLocal<>();

	public static MapContainer getINSTANCE() {
		return INSTANCE.get();
	}

	public MapContainer() {
		INSTANCE.set(this);
	}
	
	protected final Map<PartPos, MapPart> tiles = new HashMap<>();

	public MapPart requestTile(int x, int z, World world, EntityPlayer requester)
	{
		/*if(x < 0)
			x-=400;
		if(z < 0)
			z-=400;*/
		return requestTile(world, new PartPos(x/400, z/400), null, null);
	}
	protected MapPart requestTile(World world, PartPos pos, @Nullable Function<PartPos, Void> reloadCallable, int[] textureData)
	{
		if(tiles.containsKey(pos))
		{
			//System.out.println("Requested tile "+pos);
			return tiles.get(pos);
		}
		else
		{
			//System.out.println("Loading tile "+pos);
			MapPart part = new MapPart(world, pos, TILE_WIDTH, TILE_HEIGHT, reloadCallable, textureData);
			tiles.put(pos, part);
			return part;
		}
	}
	
	protected MapPart requestDirect(PartPos pos)
	{
		return tiles.get(pos);
	}
	protected void cleanTile(PartPos pos)
	{
		tiles.remove(pos);
	}
}
