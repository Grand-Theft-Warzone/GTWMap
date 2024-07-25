package fr.aym.gtwmap.client.gps;

import fr.aym.gtwmap.common.gps.GpsNode;
import fr.aym.gtwmap.common.gps.GpsNodes;
import lombok.Getter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

import java.util.*;

@Getter
public class GpsNavigator
{
    private GpsNode currentTarget;
    private Queue<GpsNode> route;
    private GpsNode nextNode;

    public void clear() {
        if(route != null)
            route.clear();
        currentTarget = null;
        route = null;
        nextNode = null;
    }

    public void setPath(EntityPlayer player, Vec3d destPos) {
        GpsNode destNode = GpsNodes.getInstance().findNearestNode(destPos, Collections.emptyList());
        if(destNode == null) {
            player.sendMessage(new TextComponentString("No destination point found"));
            return;
        }
        setTarget(player, destNode);
    }

    public void setTarget(EntityPlayer player, GpsNode node) {
        clear();
        GpsNodes nodes = GpsNodes.getInstance();
        List<GpsNode> blacklist = new ArrayList<>();
        int attempts = 0;
        while (currentTarget == null && attempts < 20) {
            nextNode = nodes.findNearestNode(player.getPositionVector(), blacklist);
            if(nextNode == null) {
                clear();
                player.sendMessage(new TextComponentString("No start point found"));
                return;
            }
            route = nodes.createPathToNode(nextNode, node);
            if(route == null) {
                blacklist.add(nextNode);
            } else {
                currentTarget = node;
            }
            attempts++;
        }
        if(currentTarget == null) {
            clear();
            player.sendMessage(new TextComponentString("No path found from here"));
        }
    }

    public void update(EntityPlayer player) {
        if(currentTarget == null || route == null || nextNode == null) {
            return;
        }
        if(player.ticksExisted % 20 == 0) {
            //TODO CECI EST POURRI, FAIRE UN RECALCUL DU CHEMIN VERS LE NODE +X (5 OU 6) REGULIEREMENT,
            // OU RECALCUL ENTIER SI CHEMIN PAS TROP LONG
            GpsNode nearestNode = route.stream().sorted(Comparator.comparingDouble(n -> n.getDistance(player.getPositionVector()))).findFirst().orElse(null);
            if(nearestNode != null && (nearestNode != nextNode || nearestNode.getDistance(player.getPositionVector()) < 6)) {
                // remove elements from head to nextNodes
                GpsNode test = route.poll();
                while(test != null && test != nearestNode) {
                    test = route.poll();
                }
                nextNode = route.poll();
            }
            if(nextNode == null) {
                clear();
                player.sendMessage(new TextComponentString("Arrived at destination"));
            }
        }
    }
}
