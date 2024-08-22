package fr.aym.gtwmap.map.loader;

import com.google.common.collect.Queues;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.*;
import fr.aym.gtwmap.utils.Config;
import fr.aym.gtwmap.utils.GtwMapConstants;
import io.netty.util.internal.ConcurrentSet;
import lombok.Getter;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MapLoader {
    @Getter
    private static MapLoader instance;
    public static final int VERSION = 1;
    public static AtomicReference<EnumLoadMode> godMode = new AtomicReference<>(EnumLoadMode.LOAD_FROM_FILE);
    public static AtomicInteger amountToLoad = new AtomicInteger(0);
    public static volatile boolean makingLoadList;
    public static ICommandSender listener;

    private int threadId;
    @Getter
    private final Set<MapPart> loadingParts = new ConcurrentSet<>();
    @Getter
    private final Queue<MapPart> saveQueue = Queues.newArrayDeque();

    @Getter
    private final Queue<AsyncMapPartLoader.ChunkLoader> chunkLoaders = Queues.newArrayDeque();

    public static volatile long lastMsg;

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
                    t.setUncaughtExceptionHandler((t1, e) -> {
                        GtwMapMod.log.error("Error in thread " + t1.getName(), e);
                    });
                    return t;
                }
            }
    );

    @Getter
    private final File storage;

    public MapLoader(FMLServerStartingEvent event, MapContainerServer mapContainerServer) {
        this.storage = event.getServer().getFile("MapData");
        this.storage.mkdirs();

        instance = this;
        pool.allowCoreThreadTimeOut(true);
        pool.submit(new AsyncMapPartSaver(mapContainerServer, this));
    }

    public void loadArea(int xmin, int xmax, int zmin, int zmax, EnumLoadMode mode, boolean force, ICommandSender sender) {
        System.out.println("Mode is " + godMode + " " + listener + " " + loadingParts);
        MapContainerServer mapContainer = (MapContainerServer) MapContainer.getInstance(false);
        if (listener == null || force) {
            listener = sender;
            lastMsg = System.currentTimeMillis();
            System.out.println("Starting to load map from " + xmin + "," + zmin + " to " + xmax + ", " + zmax + " in mode " + mode + " !");
            World w = FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld();
            godMode.set(mode);
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
            makingLoadList = false;
            sender.sendMessage(new TextComponentTranslation("gtwmap.load.start", amount));
        } else {
            sender.sendMessage(new TextComponentTranslation("gtwmap.load.already_loading"));
        }
    }

    public MapPart loadPartFromFile(World world, PartPos pos) throws IOException {
        if (godMode.get().isReloadingAll()) {
            GtwMapMod.log.info("Loading {} from file: ignoring because loading mode is reloading all.", pos);
            MapPart part = new MapPartServer(world, pos, GtwMapConstants.TILE_SIZE, GtwMapConstants.TILE_SIZE);
            part.fillWithColor(Color.LIGHT_GRAY.getRGB());
            part.setDirty(true, null);
            part.refreshMapContents();
            return part;
        }
        File mapFile = new File(storage, "map_part_" + pos.xOrig + "_" + pos.zOrig + ".mapdata");
        GtwMapMod.log.info("Loading {} from file : {}", pos, mapFile.exists());
        if (mapFile.exists()) {
            Scanner sc = new Scanner(mapFile, "UTF-8");
            int version = -1, width = -1, height = -1;
            int[] data;
            boolean gray = godMode.get().isReloadingAll();
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
                    id = i;
                    int val = Integer.parseInt(sp[i]);
                    data[i] = val;
                    //if(pos.xOrig == 0 && pos.zOrig == 0)
                    //  System.out.println("Loading " + pos + " " + i + " " + val +" " + Color.CYAN.getRGB() +" //GOD// " + godMode);
                    if (val == Color.LIGHT_GRAY.getRGB() || (godMode.get().isFixingNonLoaded() && (val == Color.RED.getRGB() || val == Color.ORANGE.getRGB() || val == Color.CYAN.getRGB()))) {
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
            // System.out.println("Gray: " + gray + " // " + godMode);
            part.setDirty(gray, null);
            part.refreshMapContents();
            return part;
        } else {
            return null;
        }
    }

    public void load(MapPart part) {
        if (!loadingParts.contains(part)) {
            // System.out.println("Requesting load " + part + " " + part.getPos());
            //System.out.println("Loading part "+pos);
	    	/*if(!part.expired())
	    	{
	            for(int i = 0; i < part.getMapTextureData().length; ++i) {
	            	part.getMapTextureData()[i] = Color.lightGray.getRGB();
	            }
	    	}*/
            part.setDirty(false, null);
            part.onContentsChange();
            part.createLoadingTracker();
            loadingParts.add(part);
            amountToLoad.getAndIncrement();
            pool.submit(new AsyncMapPartLoader(this, part, part.getLoadingTracker()));
        }
    }

    protected void resumePartLoading(MapPart part) {
        //   System.out.println("Resuming load " + part + " " + part.getPos() + " " + loadingParts.contains(part));
        if (!loadingParts.contains(part)) {
            GtwMapMod.log.error("Resuming load of a part that is not loading: {}", part);
            return;
        }
        if (part.getLoadingTracker() != null) {
            part.getLoadingTracker().endChunkLoad();
        }
        pool.submit(new AsyncMapPartLoader(this, part, part.getLoadingTracker()));
    }

    public void onLoadEnd(MapPart part) {
        if (!saveQueue.contains(part)) {
            saveQueue.add(part);
        }
        loadingParts.remove(part);
        if (!makingLoadList) {
            lastMsg = System.currentTimeMillis();
            int amountToLoad = MapLoader.amountToLoad.get();
            if (amountToLoad != 0) {
                int updated = amountToLoad - loadingParts.size();
                int percent = 100 - 100 * loadingParts.size() / amountToLoad;
                System.out.println("Render in progress... Tiles fully loaded: " + percent + "% Details: " + updated + "/" + amountToLoad + " " + loadingParts.size());
                if (MapLoader.listener != null)
                    MapLoader.listener.sendMessage(new TextComponentString("Render in progress... Tiles fully loaded: " + percent + "%"));
                long totalLoaded = getLoadingParts().stream().mapToInt(p -> p.getLoadingTracker() != null ? p.getLoadingTracker().getLoadingProgress() : 0).sum();
                percent = getLoadingParts().isEmpty() ? 100 : (int) (totalLoaded / getLoadingParts().size());
                System.out.println("Average tile progress: " + percent + "%");
                if (MapLoader.listener != null)
                    MapLoader.listener.sendMessage(new TextComponentString("Average tile progress: " + percent + "%"));
                if (Config.debug) {
                    System.out.println("Working threads: " + pool.getActiveCount());
                    getLoadingParts().forEach(p -> {
                        if (p.getLoadingTracker() != null) {
                            p.getLoadingTracker().printInfos();
                        }
                    });
                }
            }
        }
    }

    public void addChunkLoading(AsyncMapPartLoader.ChunkLoader chunkLoader) {
        chunkLoaders.add(chunkLoader);
    }

    public void updateChunkLoading() {
        int i = 16;
        while (!chunkLoaders.isEmpty() && i > 0) {
            AsyncMapPartLoader.ChunkLoader chunkLoader = chunkLoaders.poll();
            //long start = System.currentTimeMillis();
            chunkLoader.run();
            // long end = System.currentTimeMillis();
            //System.out.println("Chunk loading took " + (end - start) + "ms");
            i--;
        }
    }
}
