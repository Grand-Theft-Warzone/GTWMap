package fr.aym.gtwmap.network;

import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.gtwmap.client.ClientEventHandler;
import fr.aym.gtwmap.client.gui.GuiBigMap;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SCMessagePlayerList implements IMessage {
    private List<PlayerInformation> players;

    public SCMessagePlayerList() {

    }

    public SCMessagePlayerList(List<EntityPlayerMP> players) {
        this.players = players.stream().map(player -> new PlayerInformation(player.getName(), (float) player.posX, (float) player.posY, (float) player.posZ)).collect(Collectors.toList());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        players = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            PlayerInformation player = new PlayerInformation();
            player.fromBytes(buf);
            players.add(player);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(players.size());
        players.forEach(player -> player.toBytes(buf));
    }

    public static class Handler implements IMessageHandler<SCMessagePlayerList, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(SCMessagePlayerList message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (ClientEventHandler.MC.currentScreen instanceof GuiFrame.APIGuiScreen && ((GuiFrame.APIGuiScreen) ClientEventHandler.MC.currentScreen).getFrame() instanceof GuiBigMap) {
                    ((GuiBigMap) ((GuiFrame.APIGuiScreen) ClientEventHandler.MC.currentScreen).getFrame()).handlePlayerList(message.players);
                }
            });
            return null;
        }
    }

    @Getter
    public static class PlayerInformation {
        private String name;
        private float posX;
        private float posY;
        private float posZ;

        public PlayerInformation() {

        }

        public PlayerInformation(String name, float posX, float posY, float posZ) {
            this.name = name;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
        }

        public void fromBytes(ByteBuf buf) {
            name = ByteBufUtils.readUTF8String(buf);
            posX = buf.readFloat();
            posY = buf.readFloat();
            posZ = buf.readFloat();
        }

        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, name);
            buf.writeFloat(posX);
            buf.writeFloat(posY);
            buf.writeFloat(posZ);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PlayerInformation && name.equals(((PlayerInformation) obj).name);
        }
    }
}
