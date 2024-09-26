package fr.aym.gtwmap.client;

import fr.aym.gtwmap.utils.BlockColorConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;

import java.io.File;

public class CommandMapRegen extends CommandBase {
    @Override
    public String getName() {
        return "mapcolorsregen";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/mapcolorsregen";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        sender.sendMessage(new TextComponentTranslation("cmd.map.regen_colors_start"));
        BlockColorConfig.init(new File("MapData", "colors.cfg"), true);
        sender.sendMessage(new TextComponentTranslation("cmd.map.regen_colors_end"));
        if(server == null) {
            sender.sendMessage(new TextComponentTranslation("cmd.map.regen_colors_server"));
        }
    }
}
