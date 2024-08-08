package fr.aym.gtwmap.map;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.utils.BlockColorConfig;
import lombok.RequiredArgsConstructor;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.awt.*;

import static fr.aym.gtwmap.map.MapLoader.godMode;

@RequiredArgsConstructor
public class AsyncMapPartLoader implements Runnable {
    private final MapLoader mapLoader;
    private final MapPart target;

    private boolean loadChunk(World world, int xInPart, int zInPart) {
        int chunkX = xInPart + target.getPos().getInWorldX();
        int chunkZ = zInPart + target.getPos().getInWorldZ();
        BlockPos chunkPos = new BlockPos(chunkX, 0, chunkZ);
        int posXInChunk = chunkX % 16;
        int posZInChunk = chunkZ % 16;
        chunkX = chunkX >> 4;
        chunkZ = chunkZ >> 4;
        Chunk chunk;
        if (((ChunkProviderServer) world.getChunkProvider()).chunkExists(chunkX, chunkZ)) {
            chunk = world.getChunk(chunkPos);
        } else if (godMode == 2 || world.isChunkGeneratedAt(chunkX, chunkZ)) { // ! Forcer génération chunks = mauvaise idée !
            //System.out.println("Requesting chunk GEN at " + target.getPos() + " " + chunkPos);
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(new ChunkLoader(chunkX, chunkZ));
            return true;
        } else {
            chunk = null;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos pos2 = new BlockPos.MutableBlockPos();
        BlockPos pos3;
        /*System.out.println("Toppings: " + chunkX + " " + chunkZ + " " + chunk + " " + world.isChunkGeneratedAt(chunkX, chunkZ) + "\n" +
                "Chunk exists: " + ((ChunkProviderServer) world.getChunkProvider()).chunkExists(chunkX, chunkZ) + " xip " + posXInChunk + " zip " + posZInChunk + " " + target.getPos() +
                " pxc " + posXInChunk + " pzc " + posZInChunk + " " + target.getPos());*/
        for (int x = 0; (x + posXInChunk) < 16; x++) {
            long segmentStart = System.currentTimeMillis();
            for (int z = 0; (z + posZInChunk) < 16; z++) {
                int put = (zInPart + z) * target.getLength() + (xInPart + x);
                int val = target.getMapTextureData()[put];
                pos.setPos(chunkX*16 + posXInChunk + x, 0, chunkZ*16 + posZInChunk + z);
                //if(pos.getX() >= 290 && pos.getX() <= 310 && pos.getZ() >= 170 && pos.getZ() <= 190)
                //    System.out.println("Params put: " + put + " pos: " + pos + " x: " + x + " z: " + z + " xip " + xInPart + " zip " + zInPart + " pxc " + posXInChunk + " pzc " + posZInChunk);
                if (godMode == 2 || val == Color.LIGHT_GRAY.getRGB() || (godMode >= 1 && (val == Color.RED.getRGB() || val == Color.CYAN.getRGB() || val == Color.ORANGE.getRGB()))) {
                    long s = System.currentTimeMillis();
                    int color = MapColor.AIR.colorValue;
                    if (chunk != null) {
                        IBlockState state;
                        //TODO PAS OUF CI-DESSOUS
                        for (pos2 = pos2.setPos(pos.getX(), chunk.getTopFilledSegment() + 16, pos.getZ()); pos2.getY() >= 0; pos2.setPos(pos3)) {
                            pos3 = pos2.down();
                            state = chunk.getBlockState(pos3);
                            if(!state.isTopSolid()) {
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
                            target.getMapTextureData()[put] = getMapColorOver(1, color);
                        } else {
                            target.getMapTextureData()[put] = Color.BLACK.getRGB();
                        }
                    } else {
                        target.getMapTextureData()[put] = Color.CYAN.getRGB();
                    }
                }
                /*
                Not accurate because of parallelism
                if (!mapLoader.getLoadingParts().isEmpty() && System.currentTimeMillis() - MapLoader.lastMsg >= 30000) {
                    MapLoader.lastMsg = System.currentTimeMillis();
                    if (MapLoader.amountToLoad != 0) {
                        int percent = 100 - 100 * mapLoader.getLoadingParts().size() / MapLoader.amountToLoad;
                        System.out.println("Render in progress... " + percent + "%");
                        if (MapLoader.listener != null)
                            MapLoader.listener.sendMessage(new TextComponentString("Render in progress... " + percent + "%"));
                        percent = 100 * put / (target.getWidth() * target.getLength());
                        System.out.println("Tile percent... " + percent + "%");
                        if (MapLoader.listener != null)
                            MapLoader.listener.sendMessage(new TextComponentString("Tile percent... " + percent + "%"));
                    } else {
                        System.out.println("Render in progress... ??%");
                    }
                }*/
            }
            long took = System.currentTimeMillis() - segmentStart;
            if (took > 4) {
                //System.out.println("Took " + (took) + "ms for segment " + x + " of " + target.getPos());
            }
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
        try {
            World world = FMLCommonHandler.instance().getMinecraftServerInstance().getEntityWorld();
            //System.out.println("Updating part " + target.getPos() + " from " + target.getPos().getInWorldX() + " " + target.getPos().getInWorldZ());
            for (int x = 0; x < target.getWidth(); x += 16) {
                long segmentStart = System.currentTimeMillis();
                for (int z = 0; z < target.getLength(); z += 16) {
                    if (loadChunk(world, x, z)) {
                        return; // Async chunk loading started
                    }
                }
                long took = System.currentTimeMillis() - segmentStart;
                if (took > 4) {
                    // System.out.println("Took " + (took) + "ms for chunk segment " + x + " of " + target.getPos());
                }
            }
            target.onContentsChange();
            mapLoader.onLoadEnd(target);
        } catch (Exception e) {
            GtwMapMod.log.fatal("Error in map part loader", e);
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
        }

        @Override
        public void run() {
            target.getWorld().getChunkProvider().provideChunk(x, y);
            //System.out.println("Loaded chk "+x+ " "+y +" "+part.getPos());
            //loadQueue.add(part);
            mapLoader.resumePartLoading(target);
        }
    }
}
