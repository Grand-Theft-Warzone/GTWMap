package fr.aym.gtwmap.map;

import com.google.common.collect.Queues;
import lombok.RequiredArgsConstructor;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.commons.io.output.FileWriterWithEncoding;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class MapLoader {
    private static File storage;
    private static final int VERSION = 1;
    private static int godMode = 0;
    private static int amountToLoad = 0;
    private static long lastMsg;
    private static ICommandSender listener;

    public static void init(FMLServerStartingEvent event, MapContainerServer mapContainerServer) {
        storage = event.getServer().getFile("MapData");
        storage.mkdirs();

        loadExecutor = pool.submit(new AsyncLoader());
        saveExecutor = pool.submit(new AsyncSaver(mapContainerServer));
    }

    public static void loadArea(int xmin, int xmax, int zmin, int zmax, int mode, ICommandSender sender) {
        System.out.println("Mode is " + godMode + " " + listener + " " + loadingMens + " " + loadQueue + " " + phase);
        listener = null;
        if (listener == null) {
            listener = sender;
            System.out.println("Starting to load map from " + xmin + "," + zmin + " to " + xmax + ", " + zmax + " in mode " + mode + " !");
            World w = FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld();
            godMode = mode;
            for (int x = xmin; x < xmax; x = x + 400) {
                for (int z = zmin; z < zmax; z = z + 400) {
                    int x2 = x;
                    int z2 = z;
                    if (x2 < 0)
                        x2 -= 400;
                    if (z2 < 0)
                        z2 -= 400;
                    MapContainer.getINSTANCE().cleanTile(new PartPos(x2 / 400, z2 / 400));
                    ((MapContainerServer) MapContainer.getINSTANCE()).requestTileServer(x, z, null, godMode == -1 ? MapContainerServer.T2 : MapContainerServer.RELOAD_TILE);
                }
            }
            godMode = mode; //Do again beacause of async things
        } else
            sender.sendMessage(new TextComponentString("Erreur : un rendu est déjà en cours d'éxécution !"));
    }

    protected static MapPart loadPartAt(World world, PartPos pos, MapContainerServer mapContainer) throws IOException {
        File mapFile = new File(storage, "map_part_" + pos.xOrig + "_" + pos.zOrig + ".mapdata");
        System.out.println("Loading " + pos + " from file : " + mapFile.exists());
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

                part = new MapPart(world, pos, width, height, mapContainer, null);
                data = part.getMapTextureData();
                line = sc.nextLine();
                sp = line.split(";");
                for (int i = 0; i < data.length; i++) {
                    if (godMode == 2)
                        data[i] = Color.lightGray.getRGB();
                    else {
                        id = i;
                        int val = Integer.parseInt(sp[i]);
                        data[i] = val;
                        if (val == Color.lightGray.getRGB() || (godMode == 1 && val == Color.red.getRGB()))
                            gray = true;
                    }
                }
            } catch (Exception e) {
                System.err.println("Invalid file structure : " + mapFile + " : " + e + " at " + id);
                e.printStackTrace();
                width = MapContainer.TILE_WIDTH;
                height = MapContainer.TILE_HEIGHT;
                part = new MapPart(world, pos, width, height, mapContainer, null);
                data = part.getMapTextureData();
                for (int i = 0; i < data.length; i++) {
                    data[i] = Color.RED.getRGB();
                }
                gray = true;
            }
            sc.close();
            if (!gray)
                part.setDirty(false, null);
            return part;
        } else
            return null;
    }

    protected static void load(MapPart part) {
        if (!loadingMens.contains(part)) {
            System.out.println("Requesting load " + part + " " + part.getPos());
            //System.out.println("Loading part "+pos);
	    	/*if(!part.expired())
	    	{
	            for(int i = 0; i < part.getMapTextureData().length; ++i) {
	            	part.getMapTextureData()[i] = Color.lightGray.getRGB();
	            }
	    	}*/
            part.onChange();
            loadingMens.add(part);
            loadQueue.add(part);
            amountToLoad++;
        }
    }

    private static int threadId;
    public static Queue<MapPart> loadQueue = Queues.newArrayDeque();
    public static Queue<MapPart> saveQueue = Queues.newArrayDeque();
    public static Set<MapPart> loadingMens = new HashSet();
    private static MapPart loading;
    private static int phase = 0;

    public static ExecutorService pool = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            if (r instanceof AsyncSaver)
                t.setName("MapSaver#" + threadId);
            else
                t.setName("MapRenderer#" + threadId);
            threadId++;
            return t;
        }
    });
    private static Future<?> loadExecutor;
    private static Future<?> saveExecutor;

    @RequiredArgsConstructor
    public static class AsyncSaver implements Runnable {
        private final MapContainerServer mapContainer;

        @Override
        public void run() {
            try {
                MinecraftServer srv = FMLCommonHandler.instance().getMinecraftServerInstance();
                while (srv.isServerRunning()) {
					/*if(!loadingMens.isEmpty() && System.currentTimeMillis()-lastMsg >= 30000)
					{
						lastMsg = System.currentTimeMillis();
						if(amountToLoad != 0)
						{
							int percent = 100 - 100*loadingMens.size()/amountToLoad;
							System.out.println("Render in progress... "+percent+"%");
						}
						else
							System.out.println("Render in progress... ??%");
					}*/
                    if (saveQueue.isEmpty() && MapContainerServer.getLoadQueue().isEmpty()) {
                        try {
                            System.out.println("S20 state");
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            System.out.println("S8 state");
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (!saveQueue.isEmpty()) {
                        MapPart part = saveQueue.remove();
                        File mapFile = new File(storage, "map_part_" + part.getPos().xOrig + "_" + part.getPos().zOrig + ".mapdata");
                        try {
                            mapFile.createNewFile();
                            FileWriterWithEncoding wri = new FileWriterWithEncoding(mapFile, "UTF-8");
                            wri.write(VERSION + ";" + part.getWidth() + ";" + part.getHeigth() + System.getProperty("line.separator"));
                            for (int i = 0; i < part.getMapTextureData().length - 1; i++) {
                                wri.write(part.getMapTextureData()[i] + ";");
                            }
                            wri.write("" + part.getMapTextureData()[part.getMapTextureData().length - 1]);
                            wri.flush();
                            wri.close();
                            System.out.println("Saved map part " + part.getPos());
                        } catch (IOException e) {
                            System.err.println("Error saving map part " + part.getPos() + " : " + e);
                            e.printStackTrace();
                        }
                    }
                    if (!MapContainerServer.getLoadQueue().isEmpty()) {
                        System.out.println("Processing loads...");
                        MapContainerServer.updateQueue(srv.getEntityWorld(), mapContainer);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public static class AsyncLoader implements Runnable {
        @Override
        public void run() {
            try {
                MinecraftServer srv = FMLCommonHandler.instance().getMinecraftServerInstance();
                while (srv.isServerRunning()) {
                    if (loadQueue.isEmpty() && phase == 0 && MapContainerServer.getLoadQueue().isEmpty()) {
                        amountToLoad = 0;
                        if (godMode != 0) {
                            System.out.println("Render end !");
                            godMode = 0;
                        }
                        if (listener != null) {
                            listener.sendMessage(new TextComponentString("Opération terminée !"));
                            listener = null;
                        }
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    World world = FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld();
                    if (loading == null && !loadQueue.isEmpty()) {
                        loading = loadQueue.remove();
                        phase = 1;
                    }
                    if (phase == 1) {
                        MapPart part = loading;
                        //System.out.println("Updating part "+part.pos+" from "+part.pos.getInWorldX()+" "+part.pos.getInWorldZ());
                        boolean exitLoops = false;
                        for (int x = 0; x < part.getWidth(); x++) {
                            if (exitLoops)
                                break;
                            for (int z = 0; z < part.getHeigth(); z++) {
                                int put = z * part.getHeigth() + x;
                                if (part.getMapTextureData()[put] == Color.lightGray.getRGB()) {
                                    BlockPos pos = new BlockPos(x + part.getPos().getInWorldX(), 0, z + part.getPos().getInWorldZ());
                                    //pos = world.getTopSolidOrLiquidBlock(pos);

                                    MapColor color = null;
                                    if (((ChunkProviderServer) world.getChunkProvider()).chunkExists(x + part.getPos().getInWorldX() >> 4, z + part.getPos().getInWorldZ() >> 4)) {
                                        Chunk chunk = world.getChunk(pos);
                                        BlockPos blockpos;
                                        BlockPos blockpos1;

                                        IBlockState state = null;
                                        for (blockpos = new BlockPos(pos.getX(), chunk.getTopFilledSegment() + 16, pos.getZ()); blockpos.getY() >= 0; blockpos = blockpos1) {
                                            blockpos1 = blockpos.down();
                                            state = chunk.getBlockState(blockpos1);

                                            //if (state.getMaterial().blocksMovement() /*&& !state.getBlock().isLeaves(state, world, blockpos1) && !state.getBlock().isFoliage(world, blockpos1) */|| state.getMaterial().isLiquid())
                                            {
                                                color = state.getMapColor(world, blockpos1);
                                                if (color != MapColor.AIR)
                                                    break;
                                            }
                                        }
                                        if (color != null && color != MapColor.AIR) {
                                            part.getMapTextureData()[put] = getMapColorOver(1, color.colorValue);
                                        } else
                                            part.getMapTextureData()[put] = Color.BLACK.getRGB();
                                    } else if (world.isChunkGeneratedAt(x + part.getPos().getInWorldX() >> 4, z + part.getPos().getInWorldZ() >> 4)) // ! Forcer génération chunks = mauvaise idée !
                                    {
                                        phase = 2;
                                        FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(new ChunkLoader(part, x + part.getPos().getInWorldX() >> 4, z + part.getPos().getInWorldZ() >> 4));
                                        exitLoops = true;
                                        break;
                                    } else
                                        part.getMapTextureData()[put] = Color.CYAN.getRGB();
                                    if (!loadingMens.isEmpty() && System.currentTimeMillis() - lastMsg >= 30000) {
                                        lastMsg = System.currentTimeMillis();
                                        if (amountToLoad != 0) {
                                            int percent = 100 - 100 * loadingMens.size() / amountToLoad;
                                            System.out.println("Render in progress... " + percent + "%");
                                            if (listener != null)
                                                listener.sendMessage(new TextComponentString("Render in progress... " + percent + "%"));
                                            percent = 100 * put / (part.getWidth() * part.getHeigth());
                                            System.out.println("Tile percent... " + percent + "%");
                                            if (listener != null)
                                                listener.sendMessage(new TextComponentString("Tile percent... " + percent + "%"));
                                        } else
                                            System.out.println("Render in progress... ??%");
                                    }
                                }
                            }
                        }
                        if (exitLoops)
                            continue;
                        loadingMens.remove(part);
                        loading = null;
                        phase = 0;
                        System.out.println("Updated " + loadingMens.size() + " " + loadQueue.size() + " left " + part.getPos());
                        if (amountToLoad != 0) {
                            int percent = 100 - 100 * loadingMens.size() / amountToLoad;
                            System.out.println("Render in progress... " + percent + "%");
                            if (listener != null)
                                listener.sendMessage(new TextComponentString("Render in progress... " + percent + "%"));
                            lastMsg = System.currentTimeMillis();
                        }

                        saveQueue.add(part);
                        part.onChange();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public static class ChunkLoader implements Runnable {
        private final MapPart part;
        private final int x;
        private final int y;

        public ChunkLoader(MapPart part, int x, int y) {
            this.part = part;
            this.x = x;
            this.y = y;
        }

        @Override
        public void run() {
            part.getWorld().getChunkProvider().provideChunk(x, y);
            //System.out.println("Loaded chk "+x+ " "+y +" "+part.getPos());
            //loadQueue.add(part);
            phase = 1;
        }
    }

    private static int getMapColorOver(int index, int colorValue) {
        int i = 220;

        if (index == 3) {
            i = 135;
        }

        if (index == 2) {
            i = 255;
        }

        if (index == 1) {
            i = 220;
        }

        if (index == 0) {
            i = 180;
        }

        int j = (colorValue >> 16 & 255) * i / 255;
        int k = (colorValue >> 8 & 255) * i / 255;
        int l = (colorValue & 255) * i / 255;
        return -16777216 | j << 16 | k << 8 | l;
    }
}
