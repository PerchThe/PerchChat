package me.perch.chat;

import me.clip.placeholderapi.PlaceholderAPI;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageSender {

	Main plugin;

	public MessageSender(Main instance) {
		this.plugin = instance;
	}

	private static final char COLOR_CHAR = '\u00A7';
	private static final Map<UUID, Integer> localWarningCounter = new HashMap<>();
	private static final Map<UUID, Integer> partyWarningCounter = new HashMap<>();
	private static final Pattern PTAG_TOKEN = Pattern.compile("⟦PTAG:([^⟧]+)⟧");

	private boolean shouldSendLocalWarning(Player player) {
		UUID uuid = player.getUniqueId();
		int count = localWarningCounter.getOrDefault(uuid, 0) + 1;
		localWarningCounter.put(uuid, count);
		return count % 50 == 1;
	}

	private boolean shouldSendPartyWarning(Player player) {
		UUID uuid = player.getUniqueId();
		int count = partyWarningCounter.getOrDefault(uuid, 0) + 1;
		partyWarningCounter.put(uuid, count);
		return count % 50 == 1;
	}

	private Component renderBaseComponent(String legacyStringWithTokens) {
		if (plugin.emojiManager != null) return plugin.emojiManager.render(legacyStringWithTokens);
		return LegacyComponentSerializer.legacySection().deserialize(legacyStringWithTokens);
	}

	private Component resolvePerchTagComponentById(String uniqueId) {
		try {
			Plugin pl = Bukkit.getPluginManager().getPlugin("PerchTags");
			if (pl == null || !pl.isEnabled()) return Component.empty();

			Method m = pl.getClass().getMethod("getTagComponentById", String.class);
			Object out = m.invoke(pl, uniqueId);
			if (out instanceof Component c) return c;
			return Component.empty();
		} catch (Throwable ignored) {
			return Component.empty();
		}
	}

	private void sendWithHover(Player to, String legacyStringWithTokens) {
		Component comp = renderBaseComponent(legacyStringWithTokens);

		Matcher m = PTAG_TOKEN.matcher(legacyStringWithTokens);
		if (m.find()) {
			m.reset();
			while (m.find()) {
				String full = m.group(0);
				String id = m.group(1);

				Component tagComp = resolvePerchTagComponentById(id);
				comp = comp.replaceText(TextReplacementConfig.builder()
						.matchLiteral(full)
						.replacement(tagComp)
						.build());
			}
		}

		to.sendMessage(comp);
	}

	public String translateHexColorCodes(String message) {
		final Pattern hexPattern = Pattern.compile("&" + "#" + "([A-Fa-f0-9]{6})");
		Matcher matcher = hexPattern.matcher(message);
		StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
		while (matcher.find()) {
			String group = matcher.group(1);
			matcher.appendReplacement(buffer, COLOR_CHAR + "x"
					+ COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
					+ COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
					+ COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5));
		}
		return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
	}

	private void sendToDiscordOnce(Player p, String channelName, boolean isGlobal, boolean fromCommand, String message) {
		if (!Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) return;
		String targetChannel = isGlobal ? "global" : channelName.toLowerCase();
		github.scarsz.discordsrv.DiscordSRV.getPlugin().processChatMessage(p, message, targetChannel, false);
	}

	public String format(String channelPrefix, Player p, String message, boolean isGlobal, boolean fromCommand, String channelName) {
		String configuredGlobal = plugin.getConfig().getString("channels.name.defaultGlobal");
		boolean global = isGlobal || (configuredGlobal != null && channelName != null && channelName.equalsIgnoreCase(configuredGlobal));

		if (channelPrefix == null) {
			channelPrefix = plugin.getConfig().getString("channels.name." + channelName + ".prefix", "");
		}

		String format = plugin.getConfig().getString("channels.name." + channelName + ".messageFormat", "");
		if (global) {
			format = plugin.getConfig().getString("channels.name.defaultGlobalMessageFormat", "");
			channelPrefix = "";
		}

		if (plugin.hasPlaceholder) {
			if (format != null) format = PlaceholderAPI.setPlaceholders(p, format);
			if (channelPrefix != null) channelPrefix = PlaceholderAPI.setPlaceholders(p, channelPrefix);
		}

		String playerName = p.getDisplayName();
		if (playerName == null) playerName = "";

		String msg = message;
		if (msg == null) msg = "";

		String out = format.replace("{channel-prefix}", channelPrefix != null ? channelPrefix : "")
				.replace("{player}", playerName)
				.replace("{message}", msg)
				.replace("[i]", "[item]");

		return translateHexColorCodes(out);
	}

	public void formatParty(Player p, String message) {
		String format = plugin.getConfig().getString("partyFormat", "");

		if (plugin.hasPlaceholder && format != null) {
			format = PlaceholderAPI.setPlaceholders(p, format);
		}

		String partyName = com.gmail.nossr50.api.PartyAPI.getPartyName(p);
		if (partyName == null) partyName = "";

		String playerName = p.getDisplayName();
		if (playerName == null) playerName = "";

		String msg = message;
		if (msg == null) msg = "";

		String out = format.replace("{party-name}", partyName)
				.replace("{player}", playerName)
				.replace("{message}", msg)
				.replace("[i]", "[item]");

		String finalMessage = translateHexColorCodes(out);

		boolean someoneReceived = false;

		try {
			for (Player member : com.gmail.nossr50.api.PartyAPI.getOnlineMembers(p)) {
				sendWithHover(member, finalMessage);
				if (!member.equals(p)) someoneReceived = true;
			}

			String partyID = plugin.getConfig().getString("partyID");
			if (partyID != null && !partyID.isEmpty()) {
				if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
					github.scarsz.discordsrv.DiscordSRV.getPlugin().processChatMessage(p, msg, "party", false);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (!someoneReceived && shouldSendPartyWarning(p)) {
			p.sendMessage(ChatColor.RED + "No party member is online to see your message");
		}
	}

	public void messageChannelSender(Player player, String message, String permission, boolean isGlobal, boolean fromCommand, boolean overrideToggle, String channelName) {

		String msg = message;
		if (msg == null) msg = "";
		final String finalMessage = msg;

		if (PerchTagsBridge.isCapturing(player)) {
			return;
		}

		if ("party".equalsIgnoreCase(channelName)) {
			formatParty(player, finalMessage);
			return;
		}

		if (isGlobal) {
			String formattedMsg = ChatColor.translateAlternateColorCodes('&',
					format("", player, finalMessage, true, fromCommand, channelName));

			sendToDiscordOnce(player, channelName, true, fromCommand, finalMessage);

			UUID senderUUID = player.getUniqueId();
			Collection<? extends Player> online = Bukkit.getOnlinePlayers();
			DataStore gpData = GriefPrevention.instance.dataStore;

			Thread.startVirtualThread(() -> {
				for (Player p : online) {
					if (!p.hasPermission(permission)) continue;
					if (gpData.getPlayerData(p.getUniqueId()).ignoredPlayers.containsKey(senderUUID)) continue;
					sendWithHover(p, formattedMsg);
				}
			});

			Bukkit.getLogger().info(formattedMsg);
			return;
		}

		sendToDiscordOnce(player, channelName, false, fromCommand, finalMessage);

		String prefixChannel = plugin.getConfig().getString("channels.name." + channelName + ".prefix", "");

		for (Player p : Bukkit.getOnlinePlayers()) {
			plugin.spyChannels.computeIfAbsent(p.getName(), k -> new java.util.ArrayList<>());

			if (plugin.spyChannels.get(p.getName()).contains(channelName)) {
				String fm = ChatColor.translateAlternateColorCodes('&',
						format(prefixChannel, player, finalMessage, false, fromCommand, channelName));
				sendWithHover(p, fm);
				continue;
			}

			if (!p.hasPermission(permission)) continue;

			boolean enableDistance = plugin.getConfig().getBoolean("channels.name." + channelName + ".enableDistanceMessage");
			if (!enableDistance) {
				boolean sendRegardless = plugin.getConfig().getBoolean("channels.name." + channelName + ".sendRegardlessOfCurrentChannel");
				if (sendRegardless || channelName.equals(plugin.currentChannel.get(p.getName()))) {
					String fm = ChatColor.translateAlternateColorCodes('&',
							format(prefixChannel, player, finalMessage, false, fromCommand, channelName));
					sendWithHover(p, fm);
				}
			} else {
				double dis = plugin.getConfig().getDouble("channels.name." + channelName + ".distanceMessage");

				Bukkit.getScheduler().runTask(plugin, () -> {
					boolean localSomeoneReceived = false;

					for (Entity entity : player.getNearbyEntities(dis, dis, dis)) {
						if (!(entity instanceof Player)) continue;
						Player near = (Player) entity;

						plugin.spyChannels.computeIfAbsent(near.getName(), k -> new java.util.ArrayList<>());
						if (plugin.spyChannels.get(near.getName()).contains(channelName)) continue;

						boolean sendRegardless = plugin.getConfig().getBoolean("channels.name." + channelName + ".sendRegardlessOfCurrentChannel");
						if (sendRegardless || channelName.equals(plugin.currentChannel.get(near.getName()))) {
							String fmNear = ChatColor.translateAlternateColorCodes('&',
									format(prefixChannel, player, finalMessage, false, fromCommand, channelName));
							sendWithHover(near, fmNear);
							if (!near.equals(player)) localSomeoneReceived = true;
						}
					}

					boolean sendRegardless = plugin.getConfig().getBoolean("channels.name." + channelName + ".sendRegardlessOfCurrentChannel");
					if (sendRegardless || channelName.equals(plugin.currentChannel.get(player.getName()))) {
						String fmSelf = ChatColor.translateAlternateColorCodes('&',
								format(prefixChannel, player, finalMessage, false, fromCommand, channelName));
						sendWithHover(player, fmSelf);
					}

					String logMsg = ChatColor.translateAlternateColorCodes('&',
							format(prefixChannel, player, finalMessage, false, fromCommand, channelName));
					Bukkit.getLogger().info(logMsg);

					if (!localSomeoneReceived && shouldSendLocalWarning(player)) {
						player.sendMessage(ChatColor.RED + "No one is nearby to see your message");
					}
				});

				return;
			}
		}

		String logMsg = ChatColor.translateAlternateColorCodes('&',
				format(prefixChannel, player, finalMessage, false, fromCommand, channelName));
		Bukkit.getLogger().info(logMsg);
	}
}