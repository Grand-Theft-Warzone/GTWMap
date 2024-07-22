package fr.aym.gtwmap.map;

import fr.aym.gtwmap.GtwMapMod;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.commons.io.output.FileWriterWithEncoding;

import java.io.File;
import java.io.IOException;

@RequiredArgsConstructor
public class AsyncMapPartSaver implements Runnable {
    private final MapContainerServer mapContainer;
    private final MapLoader mapLoader;

    @Override
    public void run() {
        try {
            MinecraftServer srv = FMLCommonHandler.instance().getMinecraftServerInstance();
            while (srv.isServerRunning()) {
                if (mapLoader.getSaveQueue().isEmpty() && mapContainer.getLoadQueue().isEmpty()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        System.out.println("S8 state");
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (!mapLoader.getSaveQueue().isEmpty()) {
                    MapPart part = mapLoader.getSaveQueue().remove();
                    File mapFile = new File(mapLoader.getStorage(), "map_part_" + part.getPos().xOrig + "_" + part.getPos().zOrig + ".mapdata");
                    try {
                        mapFile.createNewFile();
                        FileWriterWithEncoding wri = new FileWriterWithEncoding(mapFile, "UTF-8");
                        wri.write(MapLoader.VERSION + ";" + part.getWidth() + ";" + part.getLength() + System.getProperty("line.separator"));
                        for (int i = 0; i < part.getMapTextureData().length - 1; i++) {
                            wri.write(part.getMapTextureData()[i] + ";");
                        }
                        wri.write("" + part.getMapTextureData()[part.getMapTextureData().length - 1]);
                        wri.flush();
                        wri.close();
                        GtwMapMod.log.info("Saved map part {}", part.getPos());
                    } catch (IOException e) {
                        GtwMapMod.log.error("Error saving map part {} : {}", part.getPos(), e);
                    }
                }
                if (!mapContainer.getLoadQueue().isEmpty()) {
                    System.out.println("Processing loads...");
                    mapContainer.updateQueue(srv.getEntityWorld());
                }
            }
        } catch (Exception e) {
            GtwMapMod.log.fatal("Error in map part saver", e);
            throw new RuntimeException(e);
        }
    }
}
