package fr.aym.gtwmap.server;

import fr.aym.gtwmap.GtwMapMod;
import fr.aym.gtwmap.map.loader.EnumLoadMode;
import fr.aym.gtwmap.map.loader.MapLoader;
import fr.aym.gtwmap.network.SCMessageEditMap;
import fr.aym.gtwmap.utils.Config;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
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

    @Override
    public void execute(MinecraftServer srv, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && args[0].equalsIgnoreCase("edit")) {
            GtwMapMod.network.sendTo(new SCMessageEditMap(), (EntityPlayerMP) sender);
            return;
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("loadmap")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("preload")) {
                sender.sendMessage(new TextComponentTranslation("cmd.map.preload"));
                MapLoader.getInstance().loadArea(Config.mapDims[0], Config.mapDims[1], Config.mapDims[2], Config.mapDims[3], EnumLoadMode.LOAD_NON_LOADED_ZONES, false, sender);
                return;
            } else if (args.length >= 2 && args[1].equalsIgnoreCase("full")) {
                sender.sendMessage(new TextComponentTranslation("cmd.map.fullload"));
                EnumLoadMode mode = EnumLoadMode.LOAD_NON_LOADED_ZONES;
                boolean force = false;
                if (args.length >= 3) {
                    int modeI = Integer.parseInt(args[2]);
                    if (modeI < 0 || modeI > EnumLoadMode.values().length) {
                        throw new WrongUsageException("Invalid mode");
                    }
                    mode = EnumLoadMode.values()[modeI];
                    if (modeI == 3) {
                        sender.sendMessage(new TextComponentTranslation("cmd.map.fullooad.mode3"));
                    }
                }
                if (args.length >= 4) {
                    force = Boolean.parseBoolean(args[3]);
                    if (force) {
                        sender.sendMessage(new TextComponentTranslation("cmd.map.fullooad.force"));
                    }
                }
                MapLoader.getInstance().loadArea(Config.mapDims[0], Config.mapDims[1], Config.mapDims[2], Config.mapDims[3], mode, force, sender);
                return;
            } else if (args.length >= 7 && args[1].equalsIgnoreCase("region")) {
                sender.sendMessage(new TextComponentTranslation("cmd.map.regionload"));
                boolean force = false;
                if (args.length >= 8) {
                    force = Boolean.parseBoolean(args[7]);
                    if (force) {
                        sender.sendMessage(new TextComponentTranslation("cmd.map.fullooad.force"));
                    }
                }
                int modeI = Integer.parseInt(args[6]);
                if (modeI < 0 || modeI > EnumLoadMode.values().length) {
                    throw new WrongUsageException("Invalid mode");
                }
                EnumLoadMode mode = EnumLoadMode.values()[modeI];
                MapLoader.getInstance().loadArea(Integer.parseInt(args[2]), Integer.parseInt(args[4]), Integer.parseInt(args[3]), Integer.parseInt(args[5]), mode, force, sender);
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
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos
            targetPos) {
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