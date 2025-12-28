package me.perch.chat.discordsrv;

import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePostProcessEvent;
import me.perch.chat.Main;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ChannelListener {

	private final Main plugin;

	public ChannelListener(Main instance) {
		this.plugin = instance;
	}

	@Subscribe
	public void onDiscordToMinecraftPostProcess(DiscordGuildMessagePostProcessEvent event) {
		if (event.getAuthor() != null && event.getAuthor().isBot()) return;

		String channelId = event.getChannel() != null ? event.getChannel().getId() : null;
		if (channelId == null) return;

		String globalId = plugin.getConfig().getString("channels.name.globalChannelID");
		if (globalId != null && globalId.equals(channelId)) return;

		for (String ch : plugin.channels) {
			String defaultGlobal = plugin.getConfig().getString("channels.name.defaultGlobal");
			if (defaultGlobal != null && ch.equalsIgnoreCase(defaultGlobal)) continue;

			String cfgId = plugin.getConfig().getString("channels.name." + ch + ".channelID");
			if (cfgId == null || cfgId.isBlank()) continue;

			if (!cfgId.equals(channelId)) continue;

			event.setCancelled(true);

			String fmt = plugin.getConfig().getString("channels.name." + ch + ".fromDiscordFormat", "{user}: {message}");
			String user = event.getMember() != null ? event.getMember().getEffectiveName() : "Discord";
			String msg = event.getMessage() != null ? event.getMessage().getContentDisplay() : "";

			String built = ChatColor.translateAlternateColorCodes('&',
					fmt.replace("{user}", user).replace("{message}", msg)
			);

			String perm = plugin.getConfig().getString("channels.name." + ch + ".permission");
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (perm == null || perm.isBlank() || p.hasPermission(perm)) {
					p.sendMessage(built);
				}
			}
			return;
		}
	}
}
