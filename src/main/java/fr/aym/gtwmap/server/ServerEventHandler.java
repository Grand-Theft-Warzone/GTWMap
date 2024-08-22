package fr.aym.gtwmap.server;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.common.gps.GpsNodes;
import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.MapContainerServer;
import fr.aym.gtwmap.network.BBMessageGpsNodes;
import fr.aym.gtwmap.utils.TaskScheduler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ServerEventHandler {
    @SubscribeEvent
    public void onConnected(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        TaskScheduler.schedule(new TaskScheduler.ScheduledTask((short) 20) { // Wait for world load on client side
            @Override
            public void run() {
                GtwMapMod.network.sendTo(new BBMessageGpsNodes(GpsNodes.getInstance().getNodes()), (EntityPlayerMP) event.player);
            }
        });
    }

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
        int x = event.getPos().getX();
        int z = event.getPos().getZ();
        if (x < 0)
            x -= 399;
        if (z < 0)
            z -= 399;
        ((MapContainerServer) MapContainer.getInstance(false)).requestTileLoading(x, z, null).whenComplete((p, e) -> {
            if (e != null) {
                GtwMapMod.log.error("Error marking map part as dirty at {}", event.getPos(), e);
            }
            if (p != null) {
                p.setDirty(true, event.getPos());
            }
        });
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
            World world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
            if (world == null)
                return;
            TaskScheduler.tick();
        }
    }
}
