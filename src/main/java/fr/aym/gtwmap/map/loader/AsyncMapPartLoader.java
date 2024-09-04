package fr.aym.gtwmap.map.loader;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.MapPart;
import fr.aym.gtwmap.utils.BlockColorConfig;
import fr.aym.gtwmap.utils.Config;
import lombok.RequiredArgsConstructor;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.awt.*;

import static fr.aym.gtwmap.map.loader.MapLoader.godMode;

@RequiredArgsConstructor
public class AsyncMapPartLoader implements Runnable {
    private final MapLoader mapLoader;
    private final MapPart target;
    private final MapPartLoadingTracker tracker;

    private Tuple<Boolean, Chunk> loadChunkAt(World world, int chunkX, int chunkZ, BlockPos chunkPos) {
        Chunk chunk;
        if (((ChunkProviderServer) world.getChunkProvider()).chunkExists(chunkX, chunkZ)) {
            chunk = world.getChunk(chunkPos);
        } else if (godMode.get().isGeneratingChunks() || world.isChunkGeneratedAt(chunkX, chunkZ)) { // ! Forcer génération chunks = mauvaise idée !
            //System.out.println("Requesting chunk GEN at " + target.getPos() + " " + chunkPos);
            return new Tuple<>(false, null);
        } else {
            chunk = null;
        }
        return new Tuple<>(true, chunk);
    }

    private boolean loadChunk(World world, int xInPart, int zInPart) {
        int chunkX = xInPart + target.getPos().getInWorldX();
        int chunkZ = zInPart + target.getPos().getInWorldZ();
        BlockPos chunkPos = new BlockPos(chunkX, 0, chunkZ);
        int posXInChunk = chunkX % 16;
        int posZInChunk = chunkZ % 16;
        chunkX = chunkX >> 4;
        chunkZ = chunkZ >> 4;
        boolean chunkLoaded = false;
        Chunk chunk = null;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos pos2 = new BlockPos.MutableBlockPos();
        BlockPos pos3;

        /*System.out.println("Toppings: " + chunkX + " " + chunkZ + " " + chunk + " " + world.isChunkGeneratedAt(chunkX, chunkZ) + "\n" +
                "Chunk exists: " + ((ChunkProviderServer) world.getChunkProvider()).chunkExists(chunkX, chunkZ) + " xip " + posXInChunk + " zip " + posZInChunk + " " + target.getPos() +
                " pxc " + posXInChunk + " pzc " + posZInChunk + " " + target.getPos());*/
        for (int z = 0; (z + posZInChunk) < 16 && z + zInPart < target.getLength(); z++) {
            //long segmentStart = System.currentTimeMillis();
            for (int x = 0; (x + posXInChunk) < 16 && x + xInPart < target.getWidth(); x++) {
                int put = (zInPart + z) * target.getLength() + (xInPart + x);

                int lastPutX = tracker.getLastPut() == 0 ? -1 : tracker.getLastPut() % target.getLength();
                int lastPutZ = tracker.getLastPut() / target.getLength();
                if (x + xInPart <= lastPutX && z + zInPart <= lastPutZ) {
                    //System.out.println("Skip chunk " + x + " " + z + " " + lastPutX + " " + lastPutZ + " " + xInPart + " " + zInPart);
                    /*if (target.getMapTextureData()[put] == Color.CYAN.getRGB()) {
                        System.out.println(">>> [DABG] Skipped cyan: X " + x + " " + xInPart + " " + lastPutX + " Z " + z + " " + zInPart + " " + lastPutZ + " CHUNK " + target.getPos() + " " + chunkPos + " " + posXInChunk + " " + posZInChunk + " LP " + tracker.getLastPut() + " P " + put + ". Load mode " + godMode.get());
                    }*/
                    continue;
                }
                tracker.startBlockLoad();
                try {
                    int val = target.getMapTextureData()[put];
                    //if(pos.getX() >= 290 && pos.getX() <= 310 && pos.getZ() >= 170 && pos.getZ() <= 190)
                    //    System.out.println("Params put: " + put + " pos: " + pos + " x: " + x + " z: " + z + " xip " + xInPart + " zip " + zInPart + " pxc " + posXInChunk + " pzc " + posZInChunk);
                    if (godMode.get().isReloadingAll() || val == Color.LIGHT_GRAY.getRGB() || (godMode.get().isFixingNonLoaded() && (val == Color.RED.getRGB() || val == Color.CYAN.getRGB() || val == Color.ORANGE.getRGB()))) {
                        //long s = System.currentTimeMillis();
                        if (!chunkLoaded) {
                            tracker.startChunkTest();
                            Tuple<Boolean, Chunk> chunkTuple = loadChunkAt(world, chunkX, chunkZ, chunkPos);
                            tracker.endChunkTest();
                            chunkLoaded = chunkTuple.getFirst();
                            if (!chunkLoaded) {
                                tracker.endBlockLoad();
                                tracker.endCoreLoad(false);
                                tracker.startChunkLoad();
                                mapLoader.addChunkLoading(new ChunkLoader(chunkX, chunkZ));
                                return true;
                            }
                            chunk = chunkTuple.getSecond();
                        }
                        int color = MapColor.AIR.colorValue;
                        if (chunk != null) {
                            pos.setPos(chunkX * 16 + posXInChunk + x, 0, chunkZ * 16 + posZInChunk + z);
                            IBlockState state;
                            //TODO PAS OUF CI-DESSOUS
                            for (pos2 = pos2.setPos(pos.getX(), chunk.getTopFilledSegment() + 16, pos.getZ()); pos2.getY() >= 0; pos2.setPos(pos3)) {
                                pos3 = pos2.down();
                                state = chunk.getBlockState(pos3);
                                if (state.getBlockFaceShape(world, pos, EnumFacing.UP) != BlockFaceShape.SOLID && !state.getMaterial().isLiquid()) {
                                    continue;
                                }
                                //if (state.getMaterial().blocksMovement() /*&& !state.getBlock().isLeaves(state, world, blockpos1) && !state.getBlock().isFoliage(world, blockpos1) */|| state.getMaterial().isLiquid())
                                {
                                    color = BlockColorConfig.getBlockColor(state);//state.getMapColor(world, pos3);
                                    // this is the color that gets returned for air
                                    if (color != -8650628) {
                                        color = BlockColorConfig.getColumnColour(chunk, pos3.getX(), pos3.getY(), pos3.getZ(), state, color, getPixelHeightW(target.getMapTextureData(), put, target.getLength()), getPixelHeightN(target.getMapTextureData(), put, target.getLength()));
                                        break;
                                    }
                                }
                            }
                            if (color != MapColor.AIR.colorValue) {
                                target.putColorAt(put, getMapColorOver(1, color));
                            } else {
                                target.putColorAt(put, Color.BLACK.getRGB());
                            }
                        } else {
                            //System.out.println(">>> [DABG] SET cyan: X " + x + " " + xInPart + " " + " Z " + z + " " + zInPart + " " + " CHUNK " + target.getPos() + " " + chunkPos + " " + posXInChunk + " " + posZInChunk + " LP " + tracker.getLastPut() + " P " + put + ". Load mode " + godMode.get());
                            target.putColorAt(put, Color.CYAN.getRGB());
                        }
                    } else {
                        /*if (val == Color.CYAN.getRGB()) {
                            System.out.println(">>> [DABG] KEEP cyan: X " + x + " " + xInPart + " " + " Z " + z + " " + zInPart + " " + " CHUNK " + target.getPos() + " " + chunkPos + " " + posXInChunk + " " + posZInChunk + " LP " + tracker.getLastPut() + " P " + put + ". Load mode " + godMode.get());
                        }*/
                        target.putColorAt(put, val);
                    }

                    tracker.endBlockLoad();
                } catch (Throwable e) {
                    System.out.println("Error in map part loader");
                    e.printStackTrace();
                    System.out.println("At " + target + " " + put + " / " + target.getMapTextureData().length + " LastPos " + pos + " ChunkPos " + chunkPos + " x " + x + " " + xInPart + " z " + z + " " + zInPart);
                    throw new RuntimeException(e);
                }
            }
            if (!mapLoader.getLoadingParts().isEmpty() && System.currentTimeMillis() - MapLoader.lastMsg >= 30000) {
                MapLoader.lastMsg = System.currentTimeMillis();
                int amountToLoad = MapLoader.amountToLoad.get();
                if (amountToLoad != 0) {
                    int percent = 100 - 100 * mapLoader.getLoadingParts().size() / amountToLoad;
                    System.out.println("Render in progress... Tiles fully loaded: " + percent + "%");
                    if (MapLoader.listener != null)
                        MapLoader.listener.sendMessage(new TextComponentString("Render in progress... Tiles fully loaded: " + percent + "%"));
                    long totalLoaded = mapLoader.getLoadingParts().stream().mapToInt(p -> p.getLoadingTracker() != null ? p.getLoadingTracker().getLoadingProgress() : 0).sum();
                    percent = (int) (totalLoaded / mapLoader.getLoadingParts().size());
                    System.out.println("Average tile progress: " + percent + "%");
                    if (MapLoader.listener != null)
                        MapLoader.listener.sendMessage(new TextComponentString("Average tile progress: " + percent + "%"));
                    if (Config.debug) {
                        System.out.println("Working threads: " + mapLoader.pool.getActiveCount());
                        mapLoader.getLoadingParts().forEach(p -> {
                            if (p.getLoadingTracker() != null) {
                                p.getLoadingTracker().printInfos();
                            }
                        });
                    }
                } else {
                    System.out.println("Render in progress... ??%");
                }
            }
            /*long took = System.currentTimeMillis() - segmentStart;
            if (took > 4) {
                //System.out.println("Took " + (took) + "ms for segment " + x + " of " + target.getPos());
            }*/
        }
        return false;
    }

    static int getPixelHeightN(int[] pixels, int offset, int scanSize) {
        return (offset >= scanSize) ? ((pixels[offset - scanSize] >> 24) & 0xff) : -1;
    }

    static int getPixelHeightW(int[] pixels, int offset, int scanSize) {
        return ((offset & (scanSize - 1)) >= 1) ? ((pixels[offset - 1] >> 24) & 0xff) : -1;
    }

    @Override
    public void run() {
        // System.out.println("Threading Loading part " + target.getPos());
        try {
            if (tracker == null) {
                throw new RuntimeException("Tracker is null");
            }
            //System.out.println("Press F is loading");
            tracker.startCoreLoad();
            World world = FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld();
            //System.out.println("Updating part " + target.getPos() + " from " + target.getPos().getInWorldX() + " " + target.getPos().getInWorldZ());
            int count = 0;
            long segmentStart = System.currentTimeMillis();
            while (tracker.hasRemainingChunks()) {
                ChunkPos chunkPos = tracker.getNextChunkToLoad();
                if (loadChunk(world, chunkPos.x, chunkPos.z)) {
                    return; // Async chunk loading started
                }
                tracker.removeNextChunkToLoad();
                if (!Config.debug) {
                    continue;
                }
                count++;
                if (count % 25 != 0) {
                    continue;
                }
                long took = System.currentTimeMillis() - segmentStart;
                segmentStart = System.currentTimeMillis();
                if (took <= 4) {
                    continue;
                }
                System.out.println("Took " + (took) + "ms for chunk segment " + chunkPos.z + " of " + target.getPos());
            }
            if (Config.debug) {
                System.out.println("Load end");
            }
            target.onContentsChange();
            mapLoader.onLoadEnd(target);
            tracker.endCoreLoad(true);
            //System.out.println("Loaded part " + target.getPos());
        } catch (Throwable e) {
            GtwMapMod.log.fatal("Error in map part loader", e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private int getMapColorOver(int index, int colorValue) {
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

    public class ChunkLoader implements Runnable {
        private final int x;
        private final int y;

        public ChunkLoader(int x, int y) {
            this.x = x;
            this.y = y;
            //   System.out.println("CREATE ChunkLoader " + x + " " + y);
        }

        @Override
        public void run() {
            //  System.out.println("Loading chunk " + x + " " + y);
            if (tracker != null) {
                tracker.startCoreChunkLoad();
            }
            target.getWorld().getChunkProvider().provideChunk(x, y);
            if (tracker != null) {
                tracker.endCoreChunkLoad();
            }
            //    System.out.println("Loaded chk "+x+ " "+y +" "+target.getPos());
            //loadQueue.add(part);
            mapLoader.resumePartLoading(target);
        }
    }
}
