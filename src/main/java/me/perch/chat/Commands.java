package me.perch.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.gmail.nossr50.api.PartyAPI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

public class Commands implements CommandExecutor {
	Main plugin;

	public Commands(Main instance) {
		this.plugin = instance;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		switch (cmd.getName().toLowerCase()) {
			case "ch":
				return handleChannelCommand(sender, args);
			case "chlist":
				return handleChannelListCommand(sender);
			case "chspy":
				return handleChannelSpyCommand(sender, args);
			case "chreload":
				return handleChannelReloadCommand(sender);
		}
		return true;
	}

	private boolean handleChannelCommand(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be run by a player.");
			return true;
		}
		Player player = (Player) sender;

		if (!player.hasPermission("ch.use.channels")) {
			player.sendMessage(msg("noPerm"));
			return true;
		}

		if (args.length == 0) {
			player.sendMessage(msg("invalidArgs"));
			return true;
		}

		String targetChannelName = args[0];
		boolean hasMessage = args.length >= 2;

		if (targetChannelName.equalsIgnoreCase("party")) {
			if (!PartyAPI.inParty(player)) {
				player.sendMessage(msg("notInParty"));
				return true;
			}
			if (hasMessage) {
				String message = buildMessage(args, 1);
				// FIX: Just call formatParty, do not try to use a return value or send again
				plugin.chatChannel.formatParty(player, message);
			} else {
				boolean isToggled = plugin.toggledParty.getOrDefault(player.getName(), false);
				plugin.toggledParty.put(player.getName(), !isToggled);
				savePlayerData(player, "party", !isToggled);
				String messageKey = !isToggled ? "partyTrue" : "partyFalse";
				player.sendMessage(msg(messageKey));
			}
			return true;
		}

		String matchedChannel = findChannel(targetChannelName);
		if (matchedChannel == null) {
			player.sendMessage(msg("invalidChannel"));
			return true;
		}

		boolean isGlobal = matchedChannel.equalsIgnoreCase(plugin.getConfig().getString("channels.name.defaultGlobal"));
		String permission = isGlobal ? plugin.getConfig().getString("channels.name.defaultGlobalPermission")
				: plugin.getConfig().getString("channels.name." + matchedChannel + ".permission");

		if (permission == null || !player.hasPermission(permission)) {
			player.sendMessage(msg("noPerm"));
			return true;
		}

		if (hasMessage && plugin.enableArgsAsMessage) {
			String message = buildMessage(args, 1);
			String previousChannel = plugin.currentChannel.get(player.getName());
			plugin.currentChannel.put(player.getName(), matchedChannel);
			// Pass matchedChannel as parameter!
			plugin.chatChannel.messageChannelSender(player, message, permission, isGlobal, true, true, matchedChannel);
			Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
				plugin.currentChannel.put(player.getName(), previousChannel);
			}, 2);
		} else {
			plugin.currentChannel.put(player.getName(), matchedChannel);
			plugin.toggledParty.put(player.getName(), false);
			savePlayerData(player, matchedChannel, false);
			player.sendMessage(msg("switchedChannel").replace("{channel-name}", matchedChannel));
		}
		return true;
	}

	private boolean handleChannelListCommand(CommandSender sender) {
		if (!sender.hasPermission("ch.list")) {
			sender.sendMessage(msg("noPerm"));
			return true;
		}
		String channelsString = plugin.channels.stream()
				.filter(channel -> {
					boolean displayAll = plugin.getConfig().getBoolean("channels.name." + channel + ".chlistDisplayAll", true);
					String permissionNode = plugin.getConfig().getString("channels.name." + channel + ".permission");
					return displayAll || permissionNode == null || sender.hasPermission(permissionNode);
				})
				.collect(Collectors.joining(", "));
		sender.sendMessage(msg("channel-list").replace("{channels}", channelsString));
		return true;
	}

	private boolean handleChannelSpyCommand(CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) return true;
		Player player = (Player) sender;

		if (!player.hasPermission("ch.use.spy")) {
			player.sendMessage(msg("noPerm"));
			return true;
		}
		if (args.length == 0) {
			player.sendMessage(msg("invalidArgs"));
			return true;
		}

		String targetChannel = findChannel(args[0]);
		if (targetChannel == null) {
			player.sendMessage(msg("invalidChannel"));
			return true;
		}

		if (targetChannel.equalsIgnoreCase(plugin.getConfig().getString("channels.name.defaultGlobal"))) {
			player.sendMessage(msg("cannotSpyGlobal"));
			return true;
		}

		if (!player.hasPermission(plugin.getConfig().getString("channels.name." + targetChannel + ".spyPermission"))) {
			player.sendMessage(msg("noPerm"));
			return true;
		}

		ArrayList<String> spyList = plugin.spyChannels.computeIfAbsent(player.getName(), k -> new ArrayList<>());
		if (spyList.contains(targetChannel)) {
			spyList.remove(targetChannel);
			player.sendMessage(msg("turnSpyOff").replace("{channel-name}", targetChannel));
		} else {
			spyList.add(targetChannel);
			player.sendMessage(msg("turnSpyOn").replace("{channel-name}", targetChannel));
		}
		return true;
	}

	private boolean handleChannelReloadCommand(CommandSender sender) {
		if (!sender.hasPermission("ch.reload")) {
			sender.sendMessage(msg("noPerm"));
			return true;
		}

		plugin.saveDefaultConfig();
		plugin.reloadConfig();
		plugin.saveConfig();
		Set<String> allKeys = plugin.getConfig().getKeys(true);
		plugin.channels = new ArrayList<String>();
		for (String key : allKeys) {
			if (!key.endsWith("defaultGlobal") && !key.endsWith("defaultGlobalPermission")
					&& key.startsWith("channels.name.") && !key.endsWith(".permission") && !key.endsWith(".prefix")
					&& !key.endsWith(".sendRegardlessOfCurrentChannel") && !key.endsWith(".distanceMessage")
					&& !key.endsWith(".enableDistanceMessage") && !key.endsWith(".messageFormat")
					&& !key.endsWith(".chlistDisplayAll") && !key.endsWith(".channelExists")
					&& !key.endsWith(".defaultGlobalMessageFormat") && !key.endsWith(".enableGlobalMessageFormat")
					&& !key.endsWith(".channelUponJoining") && !key.endsWith(".spyPermission")) {
				plugin.channels.add(key.replace("channels.name.", ""));
			}
		}
		plugin.enableGlobalChat = plugin.getConfig().getBoolean("channels.name.enableGlobalMessageFormat");
		plugin.channels.add(plugin.getConfig().getString("channels.name.defaultGlobal"));
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			plugin.hasPlaceholder = true;
		}
		int dd = plugin.channels.size() - 1;
		int holder = 0;
		while (dd != holder) {
			for (int x = 0; x != plugin.channels.size(); x++) {
				String channel = plugin.channels.get(x);
				if (!plugin.getConfig().getBoolean("channels.name." + channel + ".channelExists")
						&& !channel.equals(plugin.getConfig().getString("channels.name.defaultGlobal"))) {
					for (int x1 = 0; x1 != plugin.channels.size(); x1++) {
						if (plugin.channels.get(x).equals(channel)) {
							plugin.channels.remove(x);
							x1 = 0;
							x = 0;
						}
					}
				}
			}
			holder++;
		}
		for (Player p : Bukkit.getOnlinePlayers()) {
			plugin.currentChannel.put(p.getName(), plugin.getConfig().getString("channels.name.defaultGlobal"));
		}

		sender.sendMessage(msg("reloaded"));
		return true;
	}

	private String findChannel(String channelName) {
		for (String c : plugin.channels) {
			if (c.equalsIgnoreCase(channelName)) {
				return c;
			}
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

	private void savePlayerData(Player player, String channel, boolean inParty) {
		plugin.dataYaml.set(player.getUniqueId().toString() + ".channel", channel);
		plugin.dataYaml.set(player.getUniqueId().toString() + ".inParty", inParty);
		try {
			plugin.dataYaml.save(plugin.dataFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String msg(String key) {
		return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(key, ""));
	}
}