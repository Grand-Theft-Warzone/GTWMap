package fr.aym.gtwmap.server;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.MapContainerServer;
import fr.aym.gtwmap.map.MapPart;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.function.Function;

public class ServerEventHandler {
    @SubscribeEvent
    public void playerDisconnectedFromServer(PlayerEvent.PlayerLoggedOutEvent event) {
        ((MapContainerServer) MapContainer.getInstance(false)).removeRequester(event.player);
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world.getTotalWorldTime() % 5 == 0) {
            ((MapContainerServer) MapContainer.getInstance(false)).update();
        }
    }

    @SubscribeEvent
    public void blockNotif(BlockEvent.NeighborNotifyEvent event) {
        //if(true)
          //  return;
        ((MapContainerServer) MapContainer.getInstance(false)).requestTileLoading(event.getPos().getX(), event.getPos().getZ(), null).whenComplete((p, e) -> {
            if(e != null) {
                GtwMapMod.log.error("Error marking map part as dirty at {}", event.getPos(), e);
            }
            if(p != null) {
                p.setDirty(true, event.getPos());
            }
        });
    }
}
