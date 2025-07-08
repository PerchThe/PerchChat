package me.perch.chat;

public class Configuration {
	Main plugin;
	
	public void setConfig(Main instance) {
		this.plugin = instance;
		plugin.saveDefaultConfig();
		plugin.getConfig().addDefault("channel-list", "&8[&3CH&8] &3Known channels are: {channels}");
		plugin.getConfig().addDefault("reloaded", "&8[&3CH&8] &3Reloaded Configuration");
		plugin.getConfig().addDefault("invalidArgs", "&8[&3CH&8] &3Invalid arguments!");
		plugin.getConfig().addDefault("noPerm", "&8[&3CH&8] &3No Permission");
		plugin.getConfig().addDefault("invalidChannel", "&8[&3CH&8] &3Invalid channel!");
		plugin.getConfig().addDefault("switchedChannel", "&8[&3CH&8] &3Channel set to &2{channel-name}");
		plugin.getConfig().addDefault("turnSpyOff", "&8[&3CH&8] &3No longer spying on the &2{channel-name}");
		plugin.getConfig().addDefault("turnSpyOn", "&8[&3CH&8] &3Now spying on the channel &2{channel-name}");
		plugin.getConfig().addDefault("cannotSpyGlobal", "&8[&3CH&8] &3Cannot spy on the GLOBAl channel... since its well... global");
		plugin.getConfig().addDefault("partyFormat", "&8[&3CH&8] {party-name} {player} &8--> &3{message}");
		plugin.getConfig().addDefault("partyFalse", "&8[&3CH&8] No longer talking in party");
		plugin.getConfig().addDefault("partyTrue", "&8[&3CH&8] Now talking in party");
		plugin.getConfig().addDefault("notInParty", "&8[&3CH&8] Your message sends to no one... as you are not in a party...");
		plugin.getConfig().addDefault("partyDiscordFormat", "&8[&3CH&8] {party-name} {player} -> {messahe}");
		plugin.getConfig().addDefault("partyID", "");
		plugin.getConfig().addDefault("channels.name", "testChannel");
		plugin.getConfig().addDefault("channels.name.defaultGlobal", "global");
		plugin.getConfig().addDefault("channels.name.channelUponJoining", "global");
		plugin.getConfig().addDefault("channels.name.defaultGlobalPermission", "ch.defaultGlobal.use");
		plugin.getConfig().addDefault("channels.name.enableGlobalMessageFormat", false);
		plugin.getConfig().addDefault("channels.name.defaultGlobalMessageFormat", "&8{&2Global&8}&f {player} &8--> &3{message}");
		plugin.getConfig().addDefault("channels.name.globalChannelID", "");
		plugin.getConfig().addDefault("channels.name.toGlobalChannelDiscord", "");

		plugin.getConfig().addDefault("channels.name.testChannel.permission", "ch.use.testChannel");
		plugin.getConfig().addDefault("channels.name.testChannel.spyPermission", "ch.spy.testChannel");
		plugin.getConfig().addDefault("channels.name.testChannel.prefix", "&8[&4testChannel&8]");
		plugin.getConfig().addDefault("channels.name.testChannel.sendRegardlessOfCurrentChannel", true);
		plugin.getConfig().addDefault("channels.name.testChannel.enableDistanceMessage", false);
		plugin.getConfig().addDefault("channels.name.testChannel.distanceMessage", 25);
		plugin.getConfig().addDefault("channels.name.testChannel.messageFormat", "{channel-prefix} {player} &8--> &3{message}");
		plugin.getConfig().addDefault("channels.name.testChannel.toDiscordFormat", "{channel-prefix} {player} &8--> &3{message}");
		plugin.getConfig().addDefault("channels.name.testChannel.fromDiscordFormat", "&cDiscord >> user &8--> &3{message}");
		plugin.getConfig().addDefault("channels.name.testChannel.chlistDisplayAll", true);
		plugin.getConfig().addDefault("channels.name.testChannel.channelExists", true);
		plugin.getConfig().addDefault("channels.name.testChannel.channelID", "");
		plugin.getConfig().addDefault("channels.name.enableArgsAsMessage", false);
		plugin.getConfig().options().copyDefaults(true);
		plugin.saveConfig();
	}
}
