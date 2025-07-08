package me.perch.chat.discordsrv;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import me.perch.chat.Main;
import net.md_5.bungee.api.ChatColor;

public class ChannelListener {
	
	Main plugin;
	
	public ChannelListener(Main instance) {
		this.plugin = instance;
	}
	
	@Subscribe
	public void discordMessageReceived(DiscordGuildMessageReceivedEvent event) {
		// Example of logging a message sent in Discord
		if (event.getChannel().getId().equals(plugin.getConfig().getString("channels.name.globalChannelID")) || event.getAuthor().isBot() ||  event.getMessage().toString().split(":")[2].equalsIgnoreCase("pl")) {
			return;
		}
		outloop:
		for (String str : plugin.channels) {
			if (!str.equals(plugin.getConfig().getString("channels.name.defaultGlobal"))) {
				if (event.getChannel().getId().toString().equals(plugin.getConfig().getString("channels.name." + str + ".channelID"))) {
					String message;
					message = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("channels.name."+ str + ".fromDiscordFormat")).replace("{user}", event.getMember().getEffectiveName()).replace("{message}", "" + event.getMessage().getContentDisplay());
					for(Player p : Bukkit.getOnlinePlayers()) {
						if(p.hasPermission(plugin.getConfig().getString("channels.name." + str + ".permission")))
							p.sendMessage(message);
					}
					break outloop;
				}
			}
		}
		
	}
	
}
