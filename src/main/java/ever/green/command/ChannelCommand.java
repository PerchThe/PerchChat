package ever.green.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ChannelCommand extends Command {

    private final String targetChannel;

    public ChannelCommand(String name, String targetChannel) {
        super(name);
        this.targetChannel = targetChannel;
        this.description = "EvergreenChat shortcut for " + targetChannel;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        // Reconstruct the command to pass through our secure /ech command
        StringBuilder newCommand = new StringBuilder("ech " + targetChannel);
        for (String arg : args) {
            newCommand.append(" ").append(arg);
        }
        player.performCommand(newCommand.toString());
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        // Returning an empty list tells Minecraft to natively tab-complete online player names!
        return java.util.Collections.emptyList();
    }
}