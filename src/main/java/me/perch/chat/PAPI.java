package me.perch.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PAPI extends PlaceholderExpansion {
	public Main plugin;

	@Override
	public String onPlaceholderRequest(Player p, String identifier) {
		if (p == null) {
			return "";
		}
		if (identifier.equals("channel")) {
			String channel = plugin.dataYaml.getString(p.getUniqueId() + ".channel");
			if (channel == null) {
				return "None";
			}
			// If party, try to show party name
			if (channel.equalsIgnoreCase("party")) {
				return "Party";
			}
			// Capitalize first letter, rest lowercase
			return channel.substring(0, 1).toUpperCase() + channel.substring(1).toLowerCase();
		}
		return null;
	}

	@Override
	public boolean canRegister() {
		return (plugin = (Main) Bukkit.getPluginManager().getPlugin(getRequiredPlugin())) != null;
	}

	@Override
	public String getAuthor() {
		return plugin.getDescription().getAuthors().toString();
	}

	@Override
	public String getIdentifier() {
		return "PerchChat";
	}

	@Override
	public String getVersion() {
		return plugin.getDescription().getVersion();
	}

	@Override
	public String getRequiredPlugin() {
		return "PerchChat";
	}
}