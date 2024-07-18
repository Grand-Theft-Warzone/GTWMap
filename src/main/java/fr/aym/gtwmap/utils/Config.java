package fr.aym.gtwmap.utils;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;

public class Config 
{
	private static Configuration config;

	public static int[] mapDims = new int[4];

	public static void load(File file, Side side)
	{
		config = new Configuration(file);
		config.load();
		mapDims[0] = config.getInt("MapMinX", "RealTimeMap", -1000, Integer.MIN_VALUE, Integer.MAX_VALUE, "Coordonnée x minimale de la carte");
		mapDims[1] = config.getInt("MapMaxX", "RealTimeMap", 1000, Integer.MIN_VALUE, Integer.MAX_VALUE, "Coordonnée x maximale de la carte");
		mapDims[2] = config.getInt("MapMinZ", "RealTimeMap", -1500, Integer.MIN_VALUE, Integer.MAX_VALUE, "Coordonnée z minimale de la carte");
		mapDims[3] = config.getInt("MapMaxZ", "RealTimeMap", 1500, Integer.MIN_VALUE, Integer.MAX_VALUE, "Coordonnée z maximale de la carte");
		config.save();
	}
}
