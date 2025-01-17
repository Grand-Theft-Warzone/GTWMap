package fr.aym.gtwmap.network;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.client.gui.GuiBigMap;
import fr.aym.gtwmap.map.MapContainer;
import fr.aym.gtwmap.map.MapPart;
import fr.aym.gtwmap.map.PartPos;
import fr.aym.gtwmap.utils.GtwMapConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class CS18PacketMapPart implements IMessage {
    private PartPos pos;
    private int[] blockData;
    private int currentlyLoading;

    public CS18PacketMapPart() {
    }

    public CS18PacketMapPart(MapPart part, int currentlyLoading) {
        this.pos = part.getPos();
        this.blockData = part.getMapTextureData();
        this.currentlyLoading = currentlyLoading;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer bu = new PacketBuffer(buf);
        pos = new PartPos(bu.readInt(), bu.readInt());
        blockData = bu.readVarIntArray();
        currentlyLoading = bu.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer bu = new PacketBuffer(buf);
        bu.writeInt(pos.xOrig);
        bu.writeInt(pos.zOrig);
        bu.writeVarIntArray(blockData);
        bu.writeInt(currentlyLoading);
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<CS18PacketMapPart, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(CS18PacketMapPart message, MessageContext ctx) {
            GtwMapMod.proxy.handleCS18(message.pos, message.blockData, message.currentlyLoading);
            return null;
        }
    }
}
