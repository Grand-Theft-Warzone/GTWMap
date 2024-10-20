package fr.aym.gtwmap.map.loader;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.MapContainerServer;
import fr.aym.gtwmap.map.MapPart;
import fr.aym.gtwmap.utils.Config;
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
    private long lastSave;

    private boolean shouldSave() {
        return mapLoader.getSaveQueue().size() > 4 || (!mapLoader.getSaveQueue().isEmpty() && System.currentTimeMillis() - lastSave > Config.mapSaveIntervalSeconds * 1000L);
    }

    @Override
    public void run() {
        try {
            MinecraftServer srv = FMLCommonHandler.instance().getMinecraftServerInstance();
            while (srv.isServerRunning()) {
                boolean shouldSave = shouldSave();
                if (!shouldSave && mapContainer.getLoadQueue().isEmpty()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (shouldSave) {
                    save();
                }
                if (!mapContainer.getLoadQueue().isEmpty()) {
                    mapContainer.updateQueue(srv.getEntityWorld());
                }
            }
            if(!mapLoader.getSaveQueue().isEmpty()) {
                GtwMapMod.log.debug("Saving remaining map parts...");
                save();
            }
        } catch (Exception e) {
            GtwMapMod.log.fatal("Error in map part saver", e);
            throw new RuntimeException(e);
        }
    }

    private void save() {
        lastSave = System.currentTimeMillis();
        try {
            while (!mapLoader.getSaveQueue().isEmpty()) {
                MapPart part = mapLoader.getSaveQueue().remove();
                File mapFile = new File(mapLoader.getStorage(), "map_part_" + part.getPos().xOrig + "_" + part.getPos().zOrig + ".mapdata");
                mapFile.createNewFile();
                FileWriterWithEncoding wri = new FileWriterWithEncoding(mapFile, "UTF-8");
                wri.write(MapLoader.VERSION + ";" + part.getWidth() + ";" + part.getLength() + System.getProperty("line.separator"));
                for (int i = 0; i < part.getMapTextureData().length - 1; i++) {
                    wri.write(part.getMapTextureData()[i] + ";");
                }
                wri.write("" + part.getMapTextureData()[part.getMapTextureData().length - 1]);
                wri.flush();
                wri.close();
                //GtwMapMod.log.info("Saved map part {}", part.getPos());
            }
        } catch (IOException e) {
            GtwMapMod.log.error("Error saving map parts", e);
        }
    }
}
