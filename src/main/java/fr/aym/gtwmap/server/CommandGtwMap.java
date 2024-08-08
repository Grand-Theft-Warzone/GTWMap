package fr.aym.gtwmap.server;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.MapLoader;
import fr.aym.gtwmap.network.SCMessageEditMap;
import fr.aym.gtwmap.utils.Config;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandGtwMap extends CommandBase {
    @Override
    public String getName() {
        return "gtwmap";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/gtwmap <edit|loadmap>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    //TODO TRANSLATE
    @Override
    public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && args[0].equalsIgnoreCase("edit")) {
            GtwMapMod.network.sendTo(new SCMessageEditMap(), (EntityPlayerMP) sender);
            return;
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("loadmap")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("preload")) {
                sender.sendMessage(new TextComponentTranslation("cmd.map.preload"));
                MapLoader.getInstance().loadArea(Config.mapDims[0], Config.mapDims[1], Config.mapDims[2], Config.mapDims[3], -1, sender);
                return;
            } else if (args.length >= 2 && args[1].equalsIgnoreCase("full")) {
                sender.sendMessage(new TextComponentTranslation("cmd.map.fullload"));
                int mode = 1;
                if (args.length == 3) {
                    mode = Integer.parseInt(args[2]);
                }
                if(mode == 2) {
                    sender.sendMessage(new TextComponentTranslation("cmd.map.fullooad.mode2"));
                }
                MapLoader.getInstance().loadArea(Config.mapDims[0], Config.mapDims[1], Config.mapDims[2], Config.mapDims[3], mode, sender);
                return;
            } else if (args.length == 7 && args[1].equalsIgnoreCase("region")) {
                sender.sendMessage(new TextComponentTranslation("cmd.map.regionload"));
                MapLoader.getInstance().loadArea(Integer.parseInt(args[2]), Integer.parseInt(args[4]), Integer.parseInt(args[3]), Integer.parseInt(args[5]), Integer.parseInt(args[6]), sender);
                return;
            } else {
                sender.sendMessage(new TextComponentTranslation("cmd.map.desc.1"));
                sender.sendMessage(new TextComponentTranslation("cmd.map.desc.2"));
                sender.sendMessage(new TextComponentTranslation("cmd.map.desc.3"));
                sender.sendMessage(new TextComponentTranslation("cmd.map.desc.4"));
                return;
            }
        }
        throw new WrongUsageException(this.getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            ArrayList l = new ArrayList();
            l.add("edit");
            l.add("loadmap");
            return getListOfStringsMatchingLastWord(args, l);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("loadmap") || args[0].equalsIgnoreCase("spn"))) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("preload", "full", "region"));
        }
        return null;
    }
}