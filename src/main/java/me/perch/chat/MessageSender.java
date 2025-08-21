package me.perch.chat;

import me.clip.placeholderapi.PlaceholderAPI;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.gmail.nossr50.api.PartyAPI;

import github.scarsz.discordsrv.api.events.GameChatMessagePostProcessEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.GuildChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;

import net.md_5.bungee.api.ChatColor;

// Paper / Adventure imports
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.chat.ChatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MessageSender {

	Main plugin;

	public MessageSender(Main instance) {
		this.plugin = instance;
	}

	private final String creditForHexSnippet = "Elementeral";
	private static final char COLOR_CHAR = '\u00A7';

	private static final Map<UUID, Integer> localWarningCounter = new HashMap<>();
	private static final Map<UUID, Integer> partyWarningCounter = new HashMap<>();

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


	// NEW: sends as real chat, hidden when "Commands Only" is enabled
	private void sendAsChat(Player recipient, Player sender, String legacySectionFormatted) {
		final Component comp = LegacyComponentSerializer.legacySection().deserialize(legacySectionFormatted);
		recipient.sendMessage(comp, ChatType.CHAT.bind(sender.name())); // binds the chat type to the sender
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

	public String formatToDiscord(String channelPrefix, Player p, String message, boolean isGlobal,
								  boolean fromCommand, String channelName) {

		if (channelPrefix == null) {
			channelPrefix = plugin.getConfig()
					.getString("channels.name." + channelName + ".prefix");
			if (channelPrefix == null) channelPrefix = "";
		}
		String format = plugin.getConfig()
				.getString("channels.name." + channelName + ".toDiscordFormat");
		if (format == null) format = "";
		if (isGlobal) {
			// Use toGlobalChannelDiscord for global messages to Discord
			format = plugin.getConfig()
					.getString("channels.name.toGlobalChannelDiscord");
			if (format == null) format = "{player}: {message}";
			channelPrefix = "";
		}
		String playerName = p.getDisplayName();
		if (playerName == null) playerName = "";
		String msg = message;
		if (msg == null) msg = "";

		String format1 = format.replace("{channel-prefix}", channelPrefix);
		String format2 = format1.replace("{player}", playerName);
		String format3 = format2.replace("{message}", msg);
		format3 = format3.replace("[i]", "[item]");
		for (String str : plugin.emojis) {
			String check = plugin.chatEmojiData.getString("emojis." + str + ".check");
			String replacement = plugin.chatEmojiData.getString("emojis." + str + ".replacement");
			if (check != null && replacement != null) {
				if (format3.contains(check)) {
					format3 = format3.replace(check, replacement);
				}
			}
		}

		if (plugin.hasPlaceholder) {
			format3 = (PlaceholderAPI.setPlaceholders(p.getPlayer(), format3));
		}

		return translateHexColorCodes(format3);
	}

	@SuppressWarnings("deprecation")
	public void formatParty(Player p, String message) {
		String format = plugin.getConfig().getString("partyFormat");
		if (format == null) format = "";

		String partyName = PartyAPI.getPartyName(p);
		if (partyName == null) partyName = "";

		String playerName = p.getDisplayName();
		if (playerName == null) playerName = "";

		String msg = message;
		if (msg == null) msg = "";

		String format1 = format.replace("{party-name}", partyName);
		String format2 = format1.replace("{player}", playerName);
		String format3 = format2.replace("{message}", msg);

		for (String str : plugin.emojis) {
			String check = plugin.chatEmojiData.getString("emojis." + str + ".check");
			String replacement = plugin.chatEmojiData.getString("emojis." + str + ".replacement");
			if (check != null && replacement != null) {
				if (format3.contains(check)) {
					format3 = format3.replace(check, replacement);
				}
			}
		}
		format3 = format3.replace("[i]", "[item]");

		if (plugin.hasPlaceholder) {
			format3 = PlaceholderAPI.setPlaceholders(p.getPlayer(), format3);
		}

		String finalMessage = translateHexColorCodes(format3);

		boolean someoneReceived = false;

		try {
			for (Player member : PartyAPI.getOnlineMembers(p)) {
				// Send to ALL party members, including the sender
				member.sendMessage(finalMessage);
				if (!member.equals(p)) someoneReceived = true;
			}

			String partyID = plugin.getConfig().getString("partyID");
			if (partyID != null && !partyID.equals("")) {
				String str = plugin.getConfig().getString("partyDiscordFormat");
				if (str == null) str = "";
				String format11 = str.replace("{party-name}", partyName);
				String format22 = format11.replace("{player}", playerName);
				String format33 = format22.replace("{message}", msg);

				GuildChannel channel = plugin.api.getMainGuild().getGuildChannelById(partyID);
				TextChannel txtChannel = (channel instanceof TextChannel) ? (TextChannel) channel : null;

				if (txtChannel != null) {
					String sendDiscordMessage = format33.replaceAll("§[0-9A-FK-ORa-fk-orx]", "");
					GameChatMessagePreProcessEvent preEvent = plugin.api.api.callEvent(
							new GameChatMessagePreProcessEvent(partyID, sendDiscordMessage, p)
					);

					GameChatMessagePostProcessEvent postEvent = plugin.api.api.callEvent(
							new GameChatMessagePostProcessEvent(partyID, sendDiscordMessage, p, preEvent.isCancelled())
					);

					txtChannel.sendMessage(preEvent.getMessage()).queue();
				} else {
					Bukkit.getLogger().warning("[SoaromaCH] Party Discord channel ID " + partyID + " is not a valid text channel or does not exist!");
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		Bukkit.getLogger().info(finalMessage);

		if (!someoneReceived && shouldSendPartyWarning(p)) {
			p.sendMessage(ChatColor.RED + "No party member is online to see your message");
		}
	}

	public String format(String channelPrefix, Player p, String message, boolean isGlobal, boolean fromCommand, String channelName) {
		if (channelPrefix == null) {
			channelPrefix = plugin.getConfig().getString("channels.name." + channelName + ".prefix");
			if (channelPrefix == null) channelPrefix = "";
		}
		String format = plugin.getConfig().getString("channels.name." + channelName + ".messageFormat");
		if (format == null) format = "";
		if (isGlobal) {
			format = plugin.getConfig().getString("channels.name.defaultGlobalMessageFormat");
			if (format == null) format = "";
			channelPrefix = "";
		}

		String playerName = p.getDisplayName();
		if (playerName == null) playerName = "";
		String msg = message;
		if (msg == null) msg = "";

		String format1 = format.replace("{channel-prefix}", channelPrefix);
		String format2 = format1.replace("{player}", playerName);
		String format3 = format2.replace("{message}", msg);
		for (String str : plugin.emojis) {
			String check = plugin.chatEmojiData.getString("emojis." + str + ".check");
			String replacement = plugin.chatEmojiData.getString("emojis." + str + ".replacement");
			if (check != null && replacement != null) {
				if (format3.contains(check)) {
					format3 = format3.replace(check, replacement);
				}
			}
		}
		format3 = format3.replace("[i]", "[item]");

		if (plugin.hasPlaceholder) {
			format3 = (PlaceholderAPI.setPlaceholders(p.getPlayer(), format3));
		}
		try {
			// --- Local/Other Channels ---
			if (!isGlobal) {
				if (plugin.previousMessage.get(p.getName()) == 0) {
					String channelID = plugin.getConfig().getString("channels.name." + channelName + ".channelID");
					if (channelID != null && !channelID.equals(" ")) {
						GuildChannel channel = plugin.api.getMainGuild().getGuildChannelById(channelID);
						TextChannel txtChannel = (channel instanceof TextChannel) ? (TextChannel) channel : null;

						if (txtChannel != null) {
							// Use formatToDiscord for Discord messages!
							String sendDiscordMessage = formatToDiscord(channelPrefix, p, msg, isGlobal, fromCommand, channelName);

							// Optionally strip Minecraft color codes for Discord
							sendDiscordMessage = sendDiscordMessage
									.replaceAll("&#[A-Fa-f0-9]{6}", "")
									.replaceAll("§[0-9A-FK-ORa-fk-orx]", "");

							GameChatMessagePreProcessEvent preEvent = plugin.api.api.callEvent(
									new GameChatMessagePreProcessEvent(channelID, sendDiscordMessage, p)
							);

							GameChatMessagePostProcessEvent postEvent = plugin.api.api.callEvent(
									new GameChatMessagePostProcessEvent(channelID, sendDiscordMessage, p, preEvent.isCancelled())
							);

							txtChannel.sendMessage(preEvent.getMessage()).queue();
						} else {
							Bukkit.getLogger().warning("[SoaromaCH] Discord channel ID " + channelID + " is not a valid text channel or does not exist!");
						}
					}
				}
			}

			// --- Global Channel: Always send to Discord if isGlobal is true ---
			if (isGlobal) {
				if (plugin.previousMessage.get(p.getName()) == 0) {
					String globalChannelID = plugin.getConfig().getString("channels.name.globalChannelID");
					if (globalChannelID != null && !globalChannelID.equals(" ")) {
						GuildChannel channel = plugin.api.getMainGuild().getGuildChannelById(globalChannelID);
						TextChannel txtChannel = (channel instanceof TextChannel) ? (TextChannel) channel : null;

						if (txtChannel != null) {
							// Use formatToDiscord for Discord messages!
							String sendDiscordMessage = formatToDiscord(channelPrefix, p, msg, true, fromCommand, channelName);

							// Optionally strip Minecraft color codes for Discord
							sendDiscordMessage = sendDiscordMessage
									.replaceAll("&#[A-Fa-f0-9]{6}", "")
									.replaceAll("§[0-9A-FK-ORa-fk-orx]", "");

							GameChatMessagePreProcessEvent preEvent = plugin.api.api.callEvent(
									new GameChatMessagePreProcessEvent(globalChannelID, sendDiscordMessage, p)
							);

							GameChatMessagePostProcessEvent postEvent = plugin.api.api.callEvent(
									new GameChatMessagePostProcessEvent(globalChannelID, sendDiscordMessage, p, preEvent.isCancelled())
							);

							txtChannel.sendMessage(preEvent.getMessage()).queue();
						} else {
							Bukkit.getLogger().warning("[SoaromaCH] Global Discord channel ID " + globalChannelID + " is not a valid text channel or does not exist!");
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		plugin.previousMessage.put(p.getName(), 1);
		return translateHexColorCodes(format3);
	}

	public void messageChannelSender(Player player, String message, String permission, boolean isGlobal,
									 boolean fromCommand, boolean overrideToggle, String channelName) {

		// --- Fix: Only handle party chat via formatParty ---
		if ("Party".equalsIgnoreCase(channelName) || (!overrideToggle && plugin.toggledParty.get(player.getName()) != null && plugin.toggledParty.get(player.getName()))) {
			formatParty(player, message);
			return;
		}

		String prefixChannel = null;

		for (Player p : Bukkit.getOnlinePlayers()) {
			String name = p.getName();
			if (plugin.currentChannel.get(name) == null) {
				plugin.currentChannel.put(name, plugin.getConfig().getString("channels.name.defaultGlobal"));
			}
			if (plugin.spyChannels.get(p.getName()).contains(channelName)) {
				String channelPerm = plugin.getConfig().getString("channels.name." + channelName + ".permission");
				if (permission != null && permission.equals(channelPerm)) {
					prefixChannel = plugin.getConfig().getString("channels.name." + channelName + ".prefix");
					if (prefixChannel == null) prefixChannel = "";
				}
				p.sendMessage(ChatColor.translateAlternateColorCodes('&', format(prefixChannel, player,
						message, isGlobal, fromCommand, channelName)));
				continue;
			}

			if (p.hasPermission(permission)) {
				// --- GLOBAL branch: send as real CHAT (hidden by client "Commands Only")
				if (isGlobal) {
					String globalPrefix = plugin.getConfig().getString("channels.name." + channelName + ".prefix");
					if (globalPrefix == null) globalPrefix = "";
					String rendered = ChatColor.translateAlternateColorCodes('&',
							format(globalPrefix, player, message, true, fromCommand, channelName));
					sendAsChat(p, player, rendered);
					continue;
				}

				boolean enableDistance = plugin.getConfig().getBoolean("channels.name." + channelName + ".enableDistanceMessage");
				if (!enableDistance) {
					String channelPerm = plugin.getConfig().getString("channels.name." + channelName + ".permission");
					if (permission != null && permission.equals(channelPerm)) {
						prefixChannel = plugin.getConfig().getString("channels.name." + channelName + ".prefix");
						if (prefixChannel == null) prefixChannel = "";
					}
					boolean sendRegardless = plugin.getConfig().getBoolean("channels.name." + channelName + ".sendRegardlessOfCurrentChannel");
					if (sendRegardless) {
						p.sendMessage(ChatColor.translateAlternateColorCodes('&', format(prefixChannel, player, message,
								isGlobal, fromCommand, channelName)));
					} else {
						if (plugin.currentChannel.get(p.getName()) != null &&
								plugin.currentChannel.get(p.getName()).equals(channelName)) {
							p.sendMessage(ChatColor.translateAlternateColorCodes('&', format(prefixChannel, player,
									message, isGlobal, fromCommand, channelName)));
						}
					}
				} else {
					Double dis = plugin.getConfig().getDouble(
							"channels.name." + channelName + ".distanceMessage");
					Bukkit.getScheduler().runTask(plugin, new Runnable() {
						@Override
						public void run() {
							String prefix = null;
							boolean localSomeoneReceived = false;
							for (Entity entity : player.getNearbyEntities(dis, dis, dis)) {
								if (entity instanceof Player) {
									Player playerd = (Player) entity;
									if (plugin.spyChannels.get(playerd.getName()) != null) {
										if (plugin.spyChannels.get(playerd.getName()).contains(channelName)) {
											continue;
										}
									}
									String channelPerm = plugin.getConfig().getString("channels.name." + channelName + ".permission");
									if (permission != null && permission.equals(channelPerm)) {
										prefix = plugin.getConfig().getString("channels.name." + channelName + ".prefix");
										if (prefix == null) prefix = "";
									}
									boolean sendRegardless = plugin.getConfig().getBoolean("channels.name." + channelName + ".sendRegardlessOfCurrentChannel");
									if (sendRegardless) {
										String renderedNearby = ChatColor.translateAlternateColorCodes('&',
												format(prefix, player, message, isGlobal, fromCommand, channelName));
										if (isGlobal) {
											sendAsChat(playerd, player, renderedNearby);
										} else {
											playerd.sendMessage(renderedNearby);
										}
										if (!playerd.equals(player)) localSomeoneReceived = true;
									} else {
										if (plugin.currentChannel.get(playerd.getName()) != null &&
												plugin.currentChannel.get(playerd.getName()).equals(channelName)) {
											String renderedNearby = ChatColor.translateAlternateColorCodes('&',
													format(plugin.getConfig().getString("channels.name."
																	+ channelName + ".prefix"),
															player, message, isGlobal, fromCommand, channelName));
											if (isGlobal) {
												sendAsChat(playerd, player, renderedNearby);
											} else {
												playerd.sendMessage(renderedNearby);
											}
											if (!playerd.equals(player)) localSomeoneReceived = true;
										}
									}
								}
							}
							String channelPerm = plugin.getConfig().getString("channels.name." + channelName + ".permission");
							if (permission != null && permission.equals(channelPerm)) {
								prefix = plugin.getConfig().getString("channels.name." + channelName + ".prefix");
								if (prefix == null) prefix = "";
							}
							boolean sendRegardless = plugin.getConfig().getBoolean("channels.name." + channelName + ".sendRegardlessOfCurrentChannel");
							String renderedSelf = ChatColor.translateAlternateColorCodes('&',
									format(prefix, player, message, isGlobal, fromCommand, channelName));
							if (sendRegardless) {
								if (isGlobal) {
									sendAsChat(player, player, renderedSelf);
								} else {
									player.sendMessage(renderedSelf);
								}
							} else {
								if (plugin.currentChannel.get(player.getName()) != null &&
										plugin.currentChannel.get(player.getName()).equals(channelName)) {
									if (isGlobal) {
										sendAsChat(player, player, renderedSelf);
									} else {
										player.sendMessage(renderedSelf);
									}
								}
							}
							Bukkit.getLogger().info(ChatColor.translateAlternateColorCodes('&', format(plugin
									.getConfig().getString("channels.name." + channelName + ".prefix"), player, message, isGlobal, fromCommand, channelName)));

							if (!localSomeoneReceived && shouldSendLocalWarning(player)) {
								player.sendMessage(ChatColor.RED + "No one is nearby to see your message");
							}
						}
					});
					plugin.previousMessage.put(player.getName(), 0);
					return;
				}
			}
		}
		plugin.previousMessage.put(player.getName(), 0);
		Bukkit.getLogger().info(ChatColor.translateAlternateColorCodes('&', format(prefixChannel, player, message,
				isGlobal, false, channelName)));
	}
}
