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

		// If in party chat, handle that first and exit.
		if (plugin.toggledParty.get(playerName) != null && plugin.toggledParty.get(playerName)) {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				Player p = e.getPlayer();
				// Only call formatParty, do NOT send its return value!
				plugin.chatChannel.formatParty(p, e.getMessage());
			});
			e.setCancelled(true);
			return;
		}

		// Determine the current channel for this player
		String currentChannel = plugin.currentChannel.get(playerName);

		// Check if the player is in the default global channel
		boolean isGlobalChannel = currentChannel.equals(plugin.getConfig().getString("channels.name.defaultGlobal"));

		if (isGlobalChannel) {
			if (plugin.enableGlobalChat) {
				Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
					String perm = plugin.getConfig().getString("channels.name.defaultGlobalPermission", "chatchannels.chat.global");
					plugin.chatChannel.messageChannelSender(
							e.getPlayer(),
							e.getMessage(),
							perm,
							true,   // isGlobal
							false,  // fromCommand
							false,  // overrideToggle
							currentChannel // pass the channel name!
					);
				});
				e.setCancelled(true);
			}
		} else {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				String perm = plugin.getConfig().getString("channels.name." + currentChannel + ".permission");
				if (perm == null) {
					return;
				}
				plugin.chatChannel.messageChannelSender(
						e.getPlayer(),
						e.getMessage(),
						perm,
						false,  // isGlobal
						false,  // fromCommand
						false,  // overrideToggle
						currentChannel // pass the channel name!
				);
			});
			e.setCancelled(true);
		}
	}
}