package fr.aym.gtwmap.network;

import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.MapContainerServer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class S19PacketMapPartQuery implements IMessage {
    private int x, z;

    public S19PacketMapPartQuery() {
    }

    public S19PacketMapPartQuery(int x, int y) {
        this.x = x;
        this.z = y;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer bu = new PacketBuffer(buf);
        x = bu.readInt();
        z = bu.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer bu = new PacketBuffer(buf);
        bu.writeInt(x);
        bu.writeInt(z);
    }

    public static class Handler implements IMessageHandler<S19PacketMapPartQuery, IMessage> {
        @Override
        public IMessage onMessage(S19PacketMapPartQuery message, MessageContext ctx) {
            ctx.getServerHandler().player.server.addScheduledTask(() -> {
                if (message.x == Integer.MIN_VALUE && message.z == Integer.MAX_VALUE)
                    ((MapContainerServer) MapContainer.getINSTANCE()).removeRequester(ctx.getServerHandler().player);
                else {
                    ((MapContainerServer) MapContainer.getINSTANCE()).requestTileServer(message.x, message.z, ctx.getServerHandler().player, MapContainerServer.RELOAD_TILE);
                }
            });
            return null;
        }
    }
}
