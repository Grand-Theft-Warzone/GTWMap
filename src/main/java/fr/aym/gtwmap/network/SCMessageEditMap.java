package fr.aym.gtwmap.network;

import fr.aym.acsguis.api.ACsGuiApi;
import fr.aym.gtwmap.client.gui.GuiBigMap;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class SCMessageEditMap implements IMessage {
    @Override
    public void fromBytes(ByteBuf buf) {

    }

    @Override
    public void toBytes(ByteBuf buf) {

    }

    public static class Handler implements IMessageHandler<SCMessageEditMap, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(SCMessageEditMap message, MessageContext ctx) {
            ACsGuiApi.asyncLoadThenShowGui("admin_map", () -> new GuiBigMap(true));
            return null;
        }
    }
}
