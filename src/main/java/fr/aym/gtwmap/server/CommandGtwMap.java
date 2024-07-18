package fr.aym.gtwmap.server;

import fr.aym.gtwmap.map.MapLoader;
import fr.aym.gtwmap.utils.Config;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandGtwMap extends CommandBase {
    public static boolean DEBUG;

    @Override
    public String getName() {
        return "gtwmap";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/gtwmap <loadmap>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
            DEBUG = !DEBUG;
            sender.sendMessage(new TextComponentString("Debug is on : " + DEBUG));
            return;
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("loadmap")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("preload")) {
                sender.sendMessage(new TextComponentString("Chargement de toute la carte, cela prendra un certain temps..."));
                MapLoader.loadArea(Config.mapDims[0], Config.mapDims[1], Config.mapDims[2], Config.mapDims[3], -1, sender);
                return;
            } else if (args.length == 2 && args[1].equalsIgnoreCase("full")) {
                sender.sendMessage(new TextComponentString("Rendu de toute la carte, cela prendra un certain temps..."));
                MapLoader.loadArea(Config.mapDims[0], Config.mapDims[1], Config.mapDims[2], Config.mapDims[3], 1, sender);
                return;
            } else if (args.length == 7 && args[1].equalsIgnoreCase("region")) {
                sender.sendMessage(new TextComponentString("Rendu de la carte à partir de ces coordonnées, cela prendra un certain temps..."));
                MapLoader.loadArea(Integer.parseInt(args[2]), Integer.parseInt(args[4]), Integer.parseInt(args[3]), Integer.parseInt(args[5]), Integer.parseInt(args[6]), sender);
                return;
            } else {
                sender.sendMessage(new TextComponentString("==== RealTimeMap commands : ===="));
                sender.sendMessage(new TextComponentString("- '/gtwmap loadmap preload' : charge la carte qui a déjà été rendue dans la mémoire afin de de la visualiser plus rapidement, utilise les coordinnées de la config"));
                sender.sendMessage(new TextComponentString("- '/gtwmap loadmap full' : réalise le rendu de toute la carte (opération assez longue) à part des coordonnées spécifiées dans la config"));
                sender.sendMessage(new TextComponentString("- '/gtwmap loadmap region <xmin> <zmin> <xmax> <zmax> <mode>' : réalise le rendu de la carte à partir des coordonnées données en argument. mode 1 : mode normal, met à jour les chunks incomplets;"
                        + " mode 2 : supprime et recharge tout"));
                return;
            }
        }
        throw new WrongUsageException(this.getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            ArrayList l = new ArrayList();
            l.add("loadmap");
            return getListOfStringsMatchingLastWord(args, l);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("loadmap") || args[0].equalsIgnoreCase("spn"))) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("preload", "full", "region"));
        }
        return null;
    }
}