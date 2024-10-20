package fr.aym.gtwmap.client.gps;

import fr.aym.acsguis.api.ACsGuiApi;
import fr.aym.acsguis.component.panel.GuiFrame;
import fr.aym.gtwmap.client.ClientEventHandler;
import fr.aym.gtwmap.client.gui.GuiBigMap;
import fr.aym.gtwmap.client.gui.GuiMinimap;
import fr.aym.gtwmap.common.gps.GpsNode;
import fr.aym.gtwmap.common.gps.GpsNodes;
import fr.aym.gtwmap.common.gps.WaypointNode;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class GpsNavigator {
    private WaypointNode customWaypoint;

    private GpsNode currentTarget;
    private List<GpsNode> route;
    private GpsNode nextNode;

    private float lastPlayerX, lastPlayerZ;

    public void clear() {
        if (route != null)
            route.clear();
        currentTarget = null;
        route = null;
        nextNode = null;
    }

    public void setTargetPos(EntityPlayer player, Vector3f destPos) {
        WaypointNode customWaypoint = new WaypointNode("Custom destination", "marker", destPos);
        GpsNode destNode = GpsNodes.getInstance().findNearestNode(new Vec3d(destPos.x, destPos.y, destPos.z), Collections.emptyList());
        if (destNode == null) {
            player.sendMessage(new TextComponentString("No destination point found"));
            return;
        }
        setCustomWaypoint(customWaypoint);
        setTargetNode(player, destNode);
    }

    public void setCustomWaypoint(WaypointNode customWaypoint) {
        this.customWaypoint = customWaypoint;
        if (ClientEventHandler.MC.currentScreen instanceof GuiFrame.APIGuiScreen && ((GuiFrame.APIGuiScreen) ClientEventHandler.MC.currentScreen).getFrame() instanceof GuiBigMap) {
            ((GuiBigMap) ((GuiFrame.APIGuiScreen) ClientEventHandler.MC.currentScreen).getFrame()).refreshCustomMarker();
        }
        ACsGuiApi.getDisplayHudGuis().stream().filter(hud -> hud.getFrame() instanceof GuiMinimap).findFirst().ifPresent(hud -> ((GuiMinimap) hud.getFrame()).refreshCustomMarker());
    }

    public void setTargetNode(EntityPlayer player, GpsNode node) {
        clear();
        if ((customWaypoint != null ? customWaypoint : node).getDistance(player.getPositionVector()) < 5) {
            player.sendMessage(new TextComponentString("Arrived at destination"));
            setCustomWaypoint(null);
            return;
        }
        GpsNodes nodes = GpsNodes.getInstance();
        List<GpsNode> blacklist = new ArrayList<>();
        int attempts = 0;
        while (currentTarget == null && attempts < 20) {
            nextNode = nodes.findNearestNode(player.getPositionVector(), blacklist);
            if (nextNode == null) {
                clear();
                player.sendMessage(new TextComponentString("No start point found"));
                return;
            }
            route = nodes.createPathToNode(nextNode, node);
            if (route == null) {
                blacklist.add(nextNode);
            } else {
                currentTarget = node;
            }
            attempts++;
        }
        if (currentTarget == null || route == null) {
            clear();
            player.sendMessage(new TextComponentString("No path found from here"));
            setCustomWaypoint(null);
            return;
        } else if (customWaypoint != null) {
            route.add(customWaypoint);
        }
        GpsNode firstNode = route.get(0);
        if (route.size() > 1) {
            GpsNode secondNode = route.get(1);
            if (secondNode.getDistance(player.getPositionVector()) < secondNode.getDistance(firstNode)) {
                route.remove(0);
            }
        }
        GpsNode playerPos = new GpsNode(new Vector3f((float) player.posX, (float) player.posY, (float) player.posZ), null);
        route.add(0, playerPos);
    }

    public void update(EntityPlayer player) {
        if (currentTarget == null || route == null || nextNode == null) {
            return;
        }
        if (player.ticksExisted % 20 == 0 && (lastPlayerX != player.posX || lastPlayerZ != player.posZ)) {
            GpsNode currentTargetCopy = currentTarget;
            setTargetNode(player, currentTargetCopy);

            lastPlayerX = (float) player.posX;
            lastPlayerZ = (float) player.posZ;
        }
    }
}
