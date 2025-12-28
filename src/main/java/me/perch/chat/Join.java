package me.perch.chat;

import java.io.IOException;
import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class Join implements Listener {
	Main plugin;

	public Join(Main instance) {
		this.plugin = instance;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		plugin.spyChannels.put(p.getName(), new ArrayList<>());

		String channel = plugin.dataYaml.getString(p.getUniqueId().toString() + ".channel");
		if (channel == null) {
			channel = plugin.getConfig().getString("channels.name.channelUponJoining");
			plugin.dataYaml.set(p.getUniqueId().toString() + ".channel", channel);
			try { plugin.dataYaml.save(plugin.dataFile); } catch (IOException e) {}
		}
		plugin.currentChannel.put(p.getName(), channel);

		boolean toggledParty = plugin.dataYaml.getBoolean(p.getUniqueId().toString() + ".inParty", false);
		plugin.toggledParty.put(p.getName(), toggledParty);
	}


	// The invalid event handler has been removed!
}