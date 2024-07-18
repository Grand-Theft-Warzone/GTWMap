package fr.aym.gtwmap.server;

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
        ((MapContainerServer) MapContainer.getINSTANCE()).removeRequester(event.player);
    }

    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.world.getTotalWorldTime() % 5 == 0) {
            ((MapContainerServer) MapContainer.getINSTANCE()).update();
        }
    }

    @SubscribeEvent
    public void blockNotif(BlockEvent.NeighborNotifyEvent event) {
        ((MapContainerServer) MapContainer.getINSTANCE()).requestTileServer(event.getPos().getX(), event.getPos().getZ(), null, new Function<MapPart, Void>() {
            @Override
            public Void apply(MapPart t) {
                t.setDirty(true, event.getPos());
                return null;
            }

            @Override
            public int hashCode() {
                return event.getPos().hashCode();
            }
        });
    }
}
