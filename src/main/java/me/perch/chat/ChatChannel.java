package me.perch.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatChannel implements Listener {
	Main plugin;

	public ChatChannel(Main instance) {
		plugin = instance;
	}

	@EventHandler(ignoreCancelled = true)
	public void chatEvent(AsyncPlayerChatEvent e) {
		String playerName = e.getPlayer().getName();

		if (plugin.currentChannel.get(playerName) == null) {
			plugin.currentChannel.put(playerName, plugin.getConfig().getString("channels.name.defaultGlobal"));
		}

		String currentChannel = plugin.currentChannel.get(playerName);
		String msg = e.getMessage();
		final String finalMsg = msg;
		Player p = e.getPlayer();

		if (currentChannel.equalsIgnoreCase("party")) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				plugin.chatChannel.formatParty(p, finalMsg);
			});
			e.setCancelled(true);
			return;
		}

		boolean isGlobalChannel = currentChannel.equals(plugin.getConfig().getString("channels.name.defaultGlobal"));

		if (isGlobalChannel) {
			if (plugin.enableGlobalChat) {
				Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
					String perm = plugin.getConfig().getString("channels.name.defaultGlobalPermission", "chatchannels.chat.global");
					plugin.chatChannel.messageChannelSender(
							p,
							finalMsg,
							perm,
							true,
							false,
							false,
							currentChannel
					);
				});
				e.setCancelled(true);
			}
		}
		else {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				String perm = plugin.getConfig().getString("channels.name." + currentChannel + ".permission");

				if (perm == null) {
					return;
				}

				plugin.chatChannel.messageChannelSender(
						p,
						finalMsg,
						perm,
						false,
						false,
						false,
						currentChannel
				);
			});
			e.setCancelled(true);
		}
	}
}