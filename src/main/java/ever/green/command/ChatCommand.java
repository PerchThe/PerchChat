package ever.green.command;

import com.gmail.nossr50.api.PartyAPI;
import ever.green.Chat;
import ever.green.listener.ChatListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.stream.Collectors;

public class ChatCommand implements CommandExecutor {

    private final Chat plugin;

    public ChatCommand(Chat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getMsg("invalidArgs"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                return handleListCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "spy":
                return handleSpyCommand(sender, args);
            default:
                return handleChannelSwap(sender, args);
        }
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("evergreen.chat.admin")) {
            sender.sendMessage(plugin.getMsg("noPerm"));
            return true;
        }
        plugin.reloadConfigs();
        sender.sendMessage(plugin.getMsg("reloaded"));
        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("evergreen.chat.list")) {
            sender.sendMessage(plugin.getMsg("noPerm"));
            return true;
        }

        String channels = plugin.getChannelManager().getAvailableChannels().stream()
                .filter(c -> {
                    boolean displayAll = plugin.getConfig().getBoolean("channels.list." + c + ".chlistDisplayAll", true);
                    String perm = plugin.getConfig().getString("channels.list." + c + ".permission");
                    return displayAll || perm == null || sender.hasPermission(perm);
                })
                .collect(Collectors.joining(", "));

        sender.sendMessage(plugin.getMsg("channel-list").replaceText(b -> b.matchLiteral("{channels}").replacement(channels)));
        return true;
    }

    private boolean handleSpyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMsg("invalidArgs"));
            return true;
        }

        String targetChannel = findChannel(args[1]);
        if (targetChannel == null) {
            player.sendMessage(plugin.getMsg("invalidChannel"));
            return true;
        }

        if (targetChannel.equalsIgnoreCase(plugin.getConfig().getString("channels.defaultGlobal", "Global"))) {
            player.sendMessage(plugin.getMsg("cannotSpyGlobal"));
            return true;
        }

        String spyPerm = plugin.getConfig().getString("channels.list." + targetChannel + ".spyPermission");
        if (spyPerm != null && !player.hasPermission(spyPerm)) {
            player.sendMessage(plugin.getMsg("noPerm"));
            return true;
        }

        boolean nowSpying = plugin.getChannelManager().toggleSpy(player, targetChannel);
        String msgKey = nowSpying ? "turnSpyOn" : "turnSpyOff";
        player.sendMessage(plugin.getMsg(msgKey).replaceText(b -> b.matchLiteral("{channel-name}").replacement(targetChannel)));

        return true;
    }

    private boolean handleChannelSwap(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        String targetChannelName = args[0];
        boolean hasMessage = args.length >= 2;

        if (targetChannelName.equalsIgnoreCase("party")) {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("mcMMO") || !PartyAPI.inParty(player)) {
                player.sendMessage(plugin.getMsg("notInParty"));
                return true;
            }

            if (hasMessage) {
                String message = buildMessage(args, 1);
                new ChatListener(plugin).processChat(player, message, "party");
            } else {
                plugin.getChannelManager().setChannel(player, "party");
                player.sendMessage(plugin.getMsg("partyTrue"));
            }
            return true;
        }

        String matchedChannel = findChannel(targetChannelName);
        if (matchedChannel == null) {
            player.sendMessage(plugin.getMsg("invalidChannel"));
            return true;
        }

        String perm = plugin.getConfig().getString("channels.list." + matchedChannel + ".permission");
        if (perm != null && !player.hasPermission(perm)) {
            player.sendMessage(plugin.getMsg("noPerm"));
            return true;
        }

        if (hasMessage && plugin.getConfig().getBoolean("channels.enableArgsAsMessage", true)) {
            String message = buildMessage(args, 1);
            new ChatListener(plugin).processChat(player, message, matchedChannel);
        } else {
            plugin.getChannelManager().setChannel(player, matchedChannel);
            player.sendMessage(plugin.getMsg("switchedChannel").replaceText(b -> b.matchLiteral("{channel-name}").replacement(matchedChannel)));
        }
        return true;
    }

    private String findChannel(String input) {
        for (String c : plugin.getChannelManager().getAvailableChannels()) {
            if (c.equalsIgnoreCase(input)) return c;
        }
        return null;
    }

    private String buildMessage(String[] args, int start) {
        StringBuilder msg = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            msg.append(args[i]).append(" ");
        }
        return msg.toString().trim();
    }
}