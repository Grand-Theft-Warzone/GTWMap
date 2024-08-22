package fr.aym.gtwmap.map.loader;

import fr.aym.gtwmap.map.MapPart;
import lombok.Getter;
import net.minecraft.util.math.ChunkPos;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Queue;

import static fr.aym.gtwmap.map.loader.MapLoader.godMode;

public class MapPartLoadingTracker {
    private final MapPart mapPart;
    private final Queue<ChunkPos> chunksToLoad;
    @Getter
    private int lastPut;

    private long loadStartTime;
    private long chunkLoadStart, totalChunkLoadTime;
    private long coreChunkLoadStart, totalCoreChunkLoadTime;
    private long coreLoadStart, totalCoreLoadTime;
    private long chunkTestStart, totalChunkTestTime;
    private long blockLoadStart, totalBlockLoadTime;
    private long lastUpdateTime;

    private int loadChunkTimes;
    private int loadBlockTimes;

    private int state = -1;

    //private final List<Integer> donePut;

    public MapPartLoadingTracker(MapPart mapPart) {
        this.mapPart = mapPart;
        this.loadStartTime = System.currentTimeMillis();
        lastUpdateTime = loadStartTime;
        /*donePut = new ArrayList<>(mapPart.getMapTextureData().length);
        for (int i = 0; i < mapPart.getMapTextureData().length; i++) {
            donePut.add(i);
        }*/
        chunksToLoad = new ArrayDeque<>();
    }

    public int getLoadingProgress() {
        return lastPut * 100 / mapPart.getMapTextureData().length;
    }

    public void setLastPut(int lastPut) {
        this.lastPut = lastPut;
        /*if (!donePut.contains(lastPut)) {
            //System.out.println("Error, last put already done : " + lastPut + ". On " + mapPart.getPos());
        } else {
            donePut.remove((Integer) lastPut);
        }*/
    }

    public boolean hasRemainingChunks() {
        return !chunksToLoad.isEmpty();
    }

    public ChunkPos getNextChunkToLoad() {
        return chunksToLoad.element();
    }

    public void removeNextChunkToLoad() {
        chunksToLoad.remove();
    }

    public void startChunkLoad() {
        chunkLoadStart = System.currentTimeMillis();
        lastUpdateTime = chunkLoadStart;
        state = 10;
    }

    public void endChunkLoad() {
        lastUpdateTime = System.currentTimeMillis();
        totalChunkLoadTime += lastUpdateTime - chunkLoadStart;
        loadChunkTimes++;
        state = 13;
    }

    public void startCoreChunkLoad() {
        coreChunkLoadStart = System.currentTimeMillis();
        lastUpdateTime = coreChunkLoadStart;
        state = 11;
    }

    public void endCoreChunkLoad() {
        lastUpdateTime = System.currentTimeMillis();
        totalCoreChunkLoadTime += lastUpdateTime - coreChunkLoadStart;
        state = 12;
    }

    public void printInfos() {
        System.out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=");
        System.out.println("Map part " + mapPart.getPos() + " loading during " + (System.currentTimeMillis() - loadStartTime) + "ms");
        System.out.println("Chunk load time : " + totalChunkLoadTime + "ms (" + loadChunkTimes + " times)");
        System.out.println("Core chunk load time : " + totalCoreChunkLoadTime + "ms");
        System.out.println("Core load time : " + totalCoreLoadTime + "ms. Progress : " + getLoadingProgress() + "%");
        System.out.println("Chunk test time : " + totalChunkTestTime + "ms. " + "Block load time : " + totalBlockLoadTime + "ms (" + loadBlockTimes + " times)");
        System.out.println("Last update was: " + (System.currentTimeMillis() - lastUpdateTime) + "ms ago. Current state : " + state);
        System.out.println("========================================================================================================");
    }

    public void startCoreLoad() {
        if (state == -1) {
            if (MapLoader.godMode.get().isReloadingAll()) {
                for (int zInPart = 0; zInPart < mapPart.getLength(); zInPart += 16) {
                    for (int xInPart = 0; xInPart < mapPart.getWidth(); xInPart += 16) {
                        chunksToLoad.add(new ChunkPos(xInPart, zInPart));
                    }
                }
            } else {
                for (int zInPart = 0; zInPart < mapPart.getLength(); zInPart += 16) {
                    int chunkZ = zInPart + mapPart.getPos().getInWorldZ();
                    int posZInChunk = chunkZ % 16;
                    for (int xInPart = 0; xInPart < mapPart.getWidth(); xInPart += 16) {
                        int chunkX = xInPart + mapPart.getPos().getInWorldX();
                        int posXInChunk = chunkX % 16;
                        label1:
                        for (int z = 0; (z + posZInChunk) < 16 && z + zInPart < mapPart.getLength(); z++) {
                            for (int x = 0; (x + posXInChunk) < 16 && x + xInPart < mapPart.getWidth(); x++) {
                                int put = (zInPart + z) * mapPart.getLength() + (xInPart + x);
                                int val = mapPart.getMapTextureData()[put];
                                if (val == Color.LIGHT_GRAY.getRGB() || (godMode.get().isFixingNonLoaded() && (val == Color.RED.getRGB() || val == Color.CYAN.getRGB() || val == Color.ORANGE.getRGB()))) {
                                    chunksToLoad.add(new ChunkPos(xInPart, zInPart));
                                    continue label1;
                                }
                            }
                        }
                    }
                }
            }
        }
        coreLoadStart = System.currentTimeMillis();
        lastUpdateTime = coreLoadStart;
        state = 1;
    }

    public void endCoreLoad(boolean fullEnd) {
        lastUpdateTime = System.currentTimeMillis();
        totalCoreLoadTime += lastUpdateTime - coreLoadStart;
        state = 0;
       /*if (fullEnd) {
            System.out.println("[DABG] Not done puts: " + donePut.size() + " : " + donePut + " on " + mapPart.getPos());
        }*/
    }

    public void startBlockLoad() {
        blockLoadStart = System.currentTimeMillis();
        lastUpdateTime = blockLoadStart;
        state = 4;
    }

    public void endBlockLoad() {
        lastUpdateTime = System.currentTimeMillis();
        totalBlockLoadTime += lastUpdateTime - blockLoadStart;
        loadBlockTimes++;
        state = 5;
    }

    public void startChunkTest() {
        lastUpdateTime = System.currentTimeMillis();
        chunkTestStart = lastUpdateTime;
        state = 2;
    }

    public void endChunkTest() {
        lastUpdateTime = System.currentTimeMillis();
        totalChunkTestTime += lastUpdateTime - chunkTestStart;
        state = 3;
    }
}
