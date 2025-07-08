package me.perch.chat;

import me.clip.placeholderapi.PlaceholderAPI;
import me.perch.chat.discordsrv.ChannelListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import github.scarsz.discordsrv.DiscordSRV;

public class Main extends JavaPlugin implements Listener {
	public HashMap<String, Boolean> derp = new HashMap<>();
	public HashMap<String, String> currentChannel = new HashMap<>();
	public HashMap<String, Integer> discordChannelDelay = new HashMap<>();
	public ArrayList<String> emojis = new ArrayList<>();
	public HashMap<String, Integer> previousMessage = new HashMap<>();
	public HashMap<String, Boolean> toggledParty = new HashMap<>();
	public HashMap<String, ArrayList<String>> spyChannels = new HashMap<>();
	public MessageSender chatChannel = new MessageSender(this);
	public Commands commands = new Commands(this);
	public Configuration config = new Configuration();
	public Set<String> allKeys;
	public ArrayList<String> channels;
	public boolean hasPlaceholder = false;
	public boolean enableGlobalChat = false;
	public boolean enableArgsAsMessage = false;

	String dir = System.getProperty("user.dir");
	String directoryPathFile = dir + File.separator + "plugins" + File.separator + "ChatEmojis" + File.separator + "emojis.yml";
	public File chatEmojisFile;
	public YamlConfiguration chatEmojiData;

	public File dataFile;
	public YamlConfiguration dataYaml;

	public DiscordSRV api = DiscordSRV.getPlugin();
	public Main main = this;

	@Override
	public void onEnable() {
		// Load emoji data
		chatEmojisFile = new File(directoryPathFile);
		chatEmojiData = YamlConfiguration.loadConfiguration(chatEmojisFile);
		Set<String> keys = chatEmojiData.getKeys(true);

		for (String str : keys) {
			if (!str.endsWith(".gui-name")
					&& !str.endsWith(".check")
					&& !str.endsWith(".replacement")
					&& !str.endsWith(".creator")) {
				emojis.add(str.replace("emojis.", ""));
			}
		}

		// Subscribe to DiscordSRV events after a short delay
		Bukkit.getScheduler().runTaskLater(this, () -> {
			DiscordSRV.api.subscribe(new ChannelListener(main));
		}, 30);

		// Register events and commands
		PluginManager pm = Bukkit.getPluginManager();
		pm.registerEvents(new ChatChannel(this), this);
		pm.registerEvents(new Join(this), this);
		config.setConfig(this);

		getCommand("ch").setExecutor(new Commands(this));
		getCommand("chreload").setExecutor(new Commands(this));
		getCommand("chlist").setExecutor(new Commands(this));
		getCommand("chspy").setExecutor(new Commands(this));

		// Load channels from config
		Set<String> allKeys = getConfig().getKeys(true);
		channels = new ArrayList<>();
		for (String key : allKeys) {
			if (!key.endsWith("defaultGlobal") && !key.endsWith("defaultGlobalPermission")
					&& key.startsWith("channels.name.") && !key.endsWith(".permission") && !key.endsWith(".prefix")
					&& !key.endsWith(".sendRegardlessOfCurrentChannel") && !key.endsWith(".distanceMessage")
					&& !key.endsWith(".enableDistanceMessage") && !key.endsWith(".messageFormat")
					&& !key.endsWith(".chlistDisplayAll") && !key.endsWith(".channelExists")
					&& !key.endsWith(".defaultGlobalMessageFormat") && !key.endsWith(".enableGlobalMessageFormat")
					&& !key.endsWith(".channelUponJoining")
					&& !key.endsWith(".spyPermission")
					&& !key.endsWith(".globalChannelID")
					&& !key.endsWith(".channelID")
					&& !key.endsWith(".fromDiscordFormat")
					&& !key.endsWith(".toDiscordFormat")) {
				channels.add(key.replace("channels.name.", ""));
			}
		}
		enableGlobalChat = getConfig().getBoolean("channels.name.enableGlobalMessageFormat");
		channels.add(getConfig().getString("channels.name.defaultGlobal"));
		enableArgsAsMessage = getConfig().getBoolean("channels.name.enableArgsAsMessage");

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			hasPlaceholder = true;
		}

		// Remove non-existent channels
		int dd = channels.size() - 1;
		int holder = 0;
		while (dd != holder) {
			for (int x = 0; x != channels.size(); x++) {
				String channel = channels.get(x);
				if (!getConfig().getBoolean("channels.name." + channel + ".channelExists")
						&& !channel.equals(getConfig().getString("channels.name.defaultGlobal"))) {
					for (int x1 = 0; x1 != channels.size(); x1++) {
						if (channels.get(x).equals(channel)) {
							channels.remove(x);
							x1 = 0;
							x = 0;
						}
					}
				}
			}
			holder++;
		}

		// Set default channel and spyChannels for online players
		for (Player p : Bukkit.getOnlinePlayers()) {
			currentChannel.put(p.getName(), getConfig().getString("channels.name.defaultGlobal"));
			spyChannels.put(p.getName(), new ArrayList<>());
		}

		// Load or create data file
		dataFile = new File(this.getDataFolder(), "data.yml");
		if (!dataFile.exists()) {
			try {
				dataFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		dataYaml = YamlConfiguration.loadConfiguration(dataFile);

		// Register PlaceholderAPI expansion if available
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			new PAPI().register();
		}
	}

	@Override
	public void onDisable() {
		reloadConfig();
		saveConfig();
	}
}