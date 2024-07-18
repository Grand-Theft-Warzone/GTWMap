package fr.aym.gtwmap.network;

import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.MapPart;
import fr.aym.gtwmap.map.PartPos;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class CS18PacketMapPart implements IMessage {
    private PartPos pos;
    private int[] blockData;

    public CS18PacketMapPart() {
    }

    public CS18PacketMapPart(MapPart part) {
        //System.out.println("Sending update of "+part);
        this.pos = part.getPos();
        this.blockData = part.getMapTextureData();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer bu = new PacketBuffer(buf);
        pos = new PartPos(bu.readInt(), bu.readInt());
        blockData = bu.readVarIntArray();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer bu = new PacketBuffer(buf);
        bu.writeInt(pos.xOrig);
        bu.writeInt(pos.zOrig);
        bu.writeVarIntArray(blockData);
    }

    public static class Handler implements IMessageHandler<CS18PacketMapPart, IMessage> {
        @Override
        public IMessage onMessage(CS18PacketMapPart message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                int x = message.pos.xOrig * 400;
                int z = message.pos.zOrig * 400;
                /*if (x < 0)
                    x += 400;
                if (z < 0)
                    z += 400;*/
                MapContainer.getINSTANCE().requestTile(x, z, Minecraft.getMinecraft().world, null).feedWidthBlockData(message.blockData);
            });
            return null;
        }
    }
}
