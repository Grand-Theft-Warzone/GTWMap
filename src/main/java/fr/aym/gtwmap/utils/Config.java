package fr.aym.gtwmap.utils;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;

public class Config 
{
	private static Configuration config;

	public static boolean debug = false;
	public static int[] mapDims = new int[4];
	public static int mapSaveIntervalSeconds = 120;
	public static boolean showNonFullBlocks = true;

	public static void load(File file, Side side)
	{
		config = new Configuration(file);
		config.load();
		mapDims[0] = config.getInt("MapMinX", "RealTimeMap", -6527, Integer.MIN_VALUE, Integer.MAX_VALUE, "Min x coordinate of the map");
		mapDims[1] = config.getInt("MapMaxX", "RealTimeMap", 4863, Integer.MIN_VALUE, Integer.MAX_VALUE, "Max x coordinate of the map");
		mapDims[2] = config.getInt("MapMinZ", "RealTimeMap", -8959, Integer.MIN_VALUE, Integer.MAX_VALUE, "Min z coordinate of the map");
		mapDims[3] = config.getInt("MapMaxZ", "RealTimeMap", 2303, Integer.MIN_VALUE, Integer.MAX_VALUE, "Max z coordinate of the map");
		debug = config.getBoolean("Debug", "RealTimeMap", false, "Enable debug mode");
		mapSaveIntervalSeconds = config.getInt("MapSaveIntervalSeconds", "RealTimeMap", 120, 1, Integer.MAX_VALUE, "Interval between map saves in seconds, if there is less than 4 parts to save.");
		showNonFullBlocks = config.getBoolean("ShowNonFullBlocks", "MapRendering", true, "Show non full blocks on the map (shows road lines, fences...). Needs map re-render when changed.");
		config.save();
	}
}
