package fr.aym.gtwmap.map;

import com.google.common.collect.Queues;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.utils.GtwMapConstants;
import io.netty.util.internal.ConcurrentSet;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.*;

public class MapLoader {
    @Getter
    private static MapLoader instance;
    public static final int VERSION = 1;
    public static volatile int godMode = 0;
    public static volatile int amountToLoad = 0;
    public static volatile boolean makingLoadList;
    public static ICommandSender listener;

    private int threadId;
    @Getter
    private final Set<MapPart> loadingParts = new ConcurrentSet<>();
    @Getter
    private final Queue<MapPart> saveQueue = Queues.newArrayDeque();
    private int phase = 0; //TODO REMOVE

    public final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            10, // core pool size
            10, // maximum pool size
            60L, TimeUnit.SECONDS, // keep-alive time for idle threads
            new LinkedBlockingQueue<>(), // bounded queue to queue tasks
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    if (r instanceof AsyncMapPartSaver)
                        t.setName("MapSaver#" + threadId);
                    else
                        t.setName("MapRenderer#" + threadId);
                    threadId++;
                    return t;
                }
            }
    );

    private final MapContainerServer mapContainerServer;
    @Getter
    private final File storage;

    public MapLoader(FMLServerStartingEvent event, MapContainerServer mapContainerServer) {
        this.mapContainerServer = mapContainerServer;
        this.storage = event.getServer().getFile("MapData");
        this.storage.mkdirs();

        instance = this;
        pool.allowCoreThreadTimeOut(true);
        pool.submit(new AsyncMapPartSaver(mapContainerServer, this));
    }

    public void loadArea(int xmin, int xmax, int zmin, int zmax, int mode, ICommandSender sender) {
        System.out.println("Mode is " + godMode + " " + listener + " " + loadingParts + " " + phase);
        //listener = null;
        MapContainerServer mapContainer = (MapContainerServer) MapContainer.getInstance(false);
        if (listener == null) {
            listener = sender;
            System.out.println("Starting to load map from " + xmin + "," + zmin + " to " + xmax + ", " + zmax + " in mode " + mode + " !");
            World w = FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld();
            godMode = mode;
            makingLoadList = true;
            xmin = (xmin < 0 ? xmin - GtwMapConstants.TILE_SIZE : xmin) / GtwMapConstants.TILE_SIZE;
            zmin = (zmin < 0 ? zmin - GtwMapConstants.TILE_SIZE : zmin) / GtwMapConstants.TILE_SIZE;
            xmax = (xmax < 0 ? xmax - GtwMapConstants.TILE_SIZE : xmax) / GtwMapConstants.TILE_SIZE;
            zmax = (zmax < 0 ? zmax - GtwMapConstants.TILE_SIZE : zmax) / GtwMapConstants.TILE_SIZE;
            int amount = 0;
            for (int x = xmin; x <= xmax; x++) {
                for (int z = zmin; z <= zmax; z++) {
                    PartPos pos = new PartPos(x, z);
                    System.out.println("Requesting " + pos + " " + x + " " + z);
                    mapContainer.cleanTile(pos);
                    mapContainer.requestTileLoading(x * GtwMapConstants.TILE_SIZE, z * GtwMapConstants.TILE_SIZE, null);
                    amount++;
                }
            }
            godMode = mode; //Do again beacause of async things
            makingLoadList = false;
            sender.sendMessage(new TextComponentString(amount + " map parts are loading..."));
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Erreur : un rendu est déjà en cours d'éxécution !"));
        }
    }

    protected MapPart loadPartFromFile(World world, PartPos pos) throws IOException {
        File mapFile = new File(storage, "map_part_" + pos.xOrig + "_" + pos.zOrig + ".mapdata");
        GtwMapMod.log.info("Loading {} from file : {}", pos, mapFile.exists());
        if (mapFile.exists()) {
            Scanner sc = new Scanner(mapFile, "UTF-8");
            int version = -1, width = -1, height = -1;
            int[] data;
            boolean gray = godMode == 2;
            int id = -1;
            MapPart part;
            try {
                String line = sc.nextLine();
                String[] sp = line.split(";");
                version = Integer.parseInt(sp[0]);
                width = Integer.parseInt(sp[1]);
                height = Integer.parseInt(sp[2]);

                part = new MapPartServer(world, pos, width, height);
                data = part.getMapTextureData();
                line = sc.nextLine();
                sp = line.split(";");
                for (int i = 0; i < data.length; i++) {
                    if (godMode == 2)
                        data[i] = Color.LIGHT_GRAY.getRGB();
                    else {
                        id = i;
                        int val = Integer.parseInt(sp[i]);
                        data[i] = val;
                        //if(pos.xOrig == 0 && pos.zOrig == 0)
                        //  System.out.println("Loading " + pos + " " + i + " " + val +" " + Color.CYAN.getRGB() +" //GOD// " + godMode);
                        if (val == Color.LIGHT_GRAY.getRGB() || (godMode == 1 && (val == Color.RED.getRGB() || val == Color.CYAN.getRGB() || val == Color.ORANGE.getRGB())))
                            gray = true;
                    }
                }
            } catch (Exception e) {
                System.err.println("Invalid file structure : " + mapFile + " : " + e + " at " + id);
                e.printStackTrace();
                width = GtwMapConstants.TILE_SIZE;
                height = GtwMapConstants.TILE_SIZE;
                part = new MapPartServer(world, pos, width, height);
                part.fillWithColor(Color.RED.getRGB());
                gray = true;
            }
            sc.close();
            System.out.println("Gray: " + gray + " // " + godMode);
            if (!gray)
                part.setDirty(false, null);
            else
                part.setDirty(true, null);
            part.refreshMapContents();
            return part;
        } else {
            return null;
        }
    }

    protected void load(MapPart part) {
        if (!loadingParts.contains(part)) {
            System.out.println("Requesting load " + part + " " + part.getPos());
            //System.out.println("Loading part "+pos);
	    	/*if(!part.expired())
	    	{
	            for(int i = 0; i < part.getMapTextureData().length; ++i) {
	            	part.getMapTextureData()[i] = Color.lightGray.getRGB();
	            }
	    	}*/
            part.setDirty(false, null);
            part.onContentsChange();
            loadingParts.add(part);
            amountToLoad++;
            pool.submit(new AsyncMapPartLoader(this, part));
        }
    }

    protected void resumePartLoading(MapPart part) {
        if (loadingParts.contains(part)) {
            pool.submit(new AsyncMapPartLoader(this, part));
        }
    }

    public void onLoadEnd(MapPart part) {
        saveQueue.add(part);
        loadingParts.remove(part);
        if (!makingLoadList) {
            int updated = MapLoader.amountToLoad - loadingParts.size();
            if (MapLoader.amountToLoad != 0) {
                int percent = 100 - 100 * loadingParts.size() / MapLoader.amountToLoad;
                System.out.println("Render in progress... " + percent + "% Details: " + updated + "/" + MapLoader.amountToLoad + " " + loadingParts.size());
                if (MapLoader.listener != null)
                    MapLoader.listener.sendMessage(new TextComponentString("Render in progress... " + percent + "%"));
            }
        }
    }
}
