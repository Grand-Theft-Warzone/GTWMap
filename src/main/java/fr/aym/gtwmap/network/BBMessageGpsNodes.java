package fr.aym.gtwmap.network;

import fr.aym.acsguis.api.ACsGuiApi;
import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.acslib.utils.packetserializer.ISerializablePacket;
import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.client.ClientEventHandler;
import fr.aym.gtwmap.client.gui.GuiBigMap;
import fr.aym.gtwmap.client.gui.GuiMinimap;
import fr.aym.gtwmap.common.gps.GpsNode;
import fr.aym.gtwmap.common.gps.GpsNodes;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.joml.Vector2f;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BBMessageGpsNodes implements ISerializablePacket {
    private Action action;
    private List<UUID> ids;
    private Collection<GpsNode> nodes;
    private List<Vector2f> bezierCurvePoints;

    public BBMessageGpsNodes() {
    }

    public BBMessageGpsNodes(GpsNode node) {
        this.action = Action.ADD;
        this.nodes = Collections.singletonList(node);
    }

    public BBMessageGpsNodes(Collection<GpsNode> nodes) {
        this.action = Action.ADD_ALL;
        this.nodes = nodes;
    }

    public BBMessageGpsNodes(Action action, List<UUID> ids) {
        this.action = action;
        this.ids = ids;
    }

    public BBMessageGpsNodes(List<UUID> ids, List<Vector2f> bezierCurvePoints) {
        this.action = Action.LINK_NODES;
        this.ids = ids;
        this.bezierCurvePoints = bezierCurvePoints;
    }

    @Override
    public Object[] getObjectsToSave() {
        return action == Action.ADD || action == Action.ADD_ALL ? new Object[]{action, nodes} : action == Action.LINK_NODES ? new Object[]{action, ids, bezierCurvePoints} : new Object[]{action, ids};
    }

    @Override
    public void populateWithSavedObjects(Object[] objects) {
        action = (Action) objects[0];
        if (action != Action.ADD_ALL && action != Action.ADD)
            ids = (List<UUID>) objects[1];
        else
            nodes = (List<GpsNode>) objects[1];
        if (action == Action.LINK_NODES)
            bezierCurvePoints = (List<Vector2f>) objects[2];
    }

    public static class HandlerClient implements IMessageHandler<BBMessageGpsNodes, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(BBMessageGpsNodes message, MessageContext ctx) {
            //System.out.println("Received nodes from server : " + message.action + " " + message.nodeType + " " + message.ids + " " + message.nodes);
            GpsNodes manager = GpsNodes.getInstance();
            Minecraft.getMinecraft().addScheduledTask(() -> {
                switch (message.action) {
                    case ADD:
                    case ADD_ALL:
                        message.nodes.forEach(node -> node.create(manager, false));
                        if (ClientEventHandler.MC.currentScreen instanceof GuiFrame.APIGuiScreen && ((GuiFrame.APIGuiScreen) ClientEventHandler.MC.currentScreen).getFrame() instanceof GuiBigMap) {
                            ((GuiBigMap) ((GuiFrame.APIGuiScreen) ClientEventHandler.MC.currentScreen).getFrame()).refreshGpsNodeComponents();
                        }
                        if(ACsGuiApi.getDisplayHudGui() != null && ACsGuiApi.getDisplayHudGui().getFrame() instanceof GuiMinimap) {
                            ((GuiMinimap) ACsGuiApi.getDisplayHudGui().getFrame()).refreshGpsNodeComponents();
                        }
                        break;
                    case REMOVE:
                        message.ids.forEach(id -> {
                            if (manager.hasNode(id))
                                manager.getNode(id).delete(manager, false);
                            else
                                GtwMapMod.log.warn("Cannot remove node: node does not exist: " + id);
                        });
                        break;
                    case LINK_NODES:
                        if (manager.hasNode(message.ids.get(0)) && manager.hasNode(message.ids.get(1)))
                            manager.getNode(message.ids.get(0)).addNeighbor(manager, manager.getNode(message.ids.get(1)), message.bezierCurvePoints, false);
                        else
                            GtwMapMod.log.warn("Cannot link nodes: one of the nodes does not exist: " + message.ids.get(0) + " or " + message.ids.get(1) + " : " + manager.getNode(message.ids.get(0)) + " " + manager.getNode(message.ids.get(1)));
                        break;
                    case UNLINK_NODES:
                        if (manager.hasNode(message.ids.get(0)) && manager.hasNode(message.ids.get(1)))
                            manager.getNode(message.ids.get(0)).removeNeighbor(manager, manager.getNode(message.ids.get(1)), false);
                        else
                            GtwMapMod.log.warn("Cannot unlink nodes: one of the nodes does not exist: " + message.ids.get(0) + " or " + message.ids.get(1) + " : " + manager.getNode(message.ids.get(0)) + " " + manager.getNode(message.ids.get(1)));
                        break;
                }
            });
            return null;
        }
    }

    public static class HandlerServer implements IMessageHandler<BBMessageGpsNodes, IMessage> {
        @Override
        public IMessage onMessage(BBMessageGpsNodes message, MessageContext ctx) {
            if (!ctx.getServerHandler().player.canUseCommand(4, "gtwnpc.pathnodes")) {
                ctx.getServerHandler().player.sendMessage(new TextComponentString("You don't have the permission to modify npc nodes !"));
                GtwMapMod.log.warn(ctx.getServerHandler().player.getName() + " tried to modify npc nodes without permission !");
                return null;
            }
            GpsNodes manager = GpsNodes.getInstance();
            switch (message.action) {
                case ADD:
                    message.nodes.forEach(node -> node.create(manager, false));
                    GtwMapMod.network.sendToAll(new BBMessageGpsNodes(message.nodes));
                    break;
                case REMOVE:
                    message.ids.forEach(id -> {
                        if (manager.hasNode(id))
                            manager.getNode(id).delete(manager, false);
                        else
                            GtwMapMod.log.warn("Cannot remove node: node does not exist: " + id);
                    });
                    GtwMapMod.network.sendToAll(new BBMessageGpsNodes(message.action, message.ids));
                    break;
                case ADD_ALL:
                    throw new IllegalArgumentException("Cannot add all nodes from client");
                case LINK_NODES:
                    if (manager.hasNode(message.ids.get(0)) && manager.hasNode(message.ids.get(1)))
                        manager.getNode(message.ids.get(0)).addNeighbor(manager, manager.getNode(message.ids.get(1)), message.bezierCurvePoints, false);
                    else
                        GtwMapMod.log.warn("Cannot link nodes: one of the nodes does not exist: " + message.ids.get(0) + " or " + message.ids.get(1) + " : " + manager.getNode(message.ids.get(0)) + " " + manager.getNode(message.ids.get(1)));
                    GtwMapMod.network.sendToAll(new BBMessageGpsNodes(message.ids, message.bezierCurvePoints));
                    break;
                case UNLINK_NODES:
                    if (manager.hasNode(message.ids.get(0)) && manager.hasNode(message.ids.get(1)))
                        manager.getNode(message.ids.get(0)).removeNeighbor(manager, manager.getNode(message.ids.get(1)), false);
                    else
                        GtwMapMod.log.warn("Cannot unlink nodes: one of the nodes does not exist: " + message.ids.get(0) + " or " + message.ids.get(1) + " : " + manager.getNode(message.ids.get(0)) + " " + manager.getNode(message.ids.get(1)));
                    GtwMapMod.network.sendToAll(new BBMessageGpsNodes(message.action, message.ids));
                    break;
            }
            return null;
        }
    }

    public enum Action {
        ADD,
        REMOVE,
        ADD_ALL,
        LINK_NODES,
        UNLINK_NODES
    }
}
