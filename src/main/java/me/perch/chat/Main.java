package me.perch.chat;

import me.perch.chat.discordsrv.ChannelListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import github.scarsz.discordsrv.DiscordSRV;

public class Main extends JavaPlugin implements Listener {
	public HashMap<String, Boolean> derp = new HashMap<>();
	public HashMap<String, String> currentChannel = new HashMap<>();
	public HashMap<String, Integer> discordChannelDelay = new HashMap<>();
	public ArrayList<String> emojis = new ArrayList<>();
	public HashMap<String, Integer> previousMessage = new HashMap<>();
	public HashMap<String, ArrayList<String>> spyChannels = new HashMap<>();
	public MessageSender chatChannel = new MessageSender(this);
	public Commands commands = new Commands(this);
	public Configuration config = new Configuration();
	public ArrayList<String> channels;
	public boolean hasPlaceholder = false;
	public boolean enableGlobalChat = false;
	public boolean enableArgsAsMessage = false;

	public Emojis emojiManager;

	public File dataFile;
	public YamlConfiguration dataYaml;

	public DiscordSRV api = DiscordSRV.getPlugin();
	public Main main = this;

	private PAPI papiExpansion;

	@Override
	public void onEnable() {
		emojiManager = new Emojis(this);
		emojiManager.reload();
		emojis = new ArrayList<>();

		Bukkit.getScheduler().runTaskLater(this, () -> {
			try {
				DiscordSRV.api.subscribe(new ChannelListener(main));
			} catch (Throwable t) {
				getLogger().warning("Failed to subscribe to DiscordSRV API: " + t.getMessage());
			}
		}, 30L);

		PluginManager pm = Bukkit.getPluginManager();
		pm.registerEvents(new ChatChannel(this), this);
		pm.registerEvents(new Join(this), this);
		pm.registerEvents(this, this);
		config.setConfig(this);

		if (getCommand("ch") != null) getCommand("ch").setExecutor(new Commands(this));
		if (getCommand("chreload") != null) getCommand("chreload").setExecutor(new Commands(this));
		if (getCommand("chlist") != null) getCommand("chlist").setExecutor(new Commands(this));
		if (getCommand("chspy") != null) getCommand("chspy").setExecutor(new Commands(this));

		channels = new ArrayList<>();
		for (String key : getConfig().getKeys(true)) {
			if (!key.endsWith("defaultGlobal")
					&& !key.endsWith("defaultGlobalPermission")
					&& key.startsWith("channels.name.")
					&& !key.endsWith(".permission")
					&& !key.endsWith(".prefix")
					&& !key.endsWith(".sendRegardlessOfCurrentChannel")
					&& !key.endsWith(".distanceMessage")
					&& !key.endsWith(".enableDistanceMessage")
					&& !key.endsWith(".messageFormat")
					&& !key.endsWith(".chlistDisplayAll")
					&& !key.endsWith(".channelExists")
					&& !key.endsWith(".defaultGlobalMessageFormat")
					&& !key.endsWith(".enableGlobalMessageFormat")
					&& !key.endsWith(".channelUponJoining")
					&& !key.endsWith(".spyPermission")
					&& !key.endsWith(".globalChannelID")
					&& !key.endsWith(".channelID")
					&& !key.endsWith(".fromDiscordFormat")
					&& !key.endsWith(".toDiscordFormat")) {
				channels.add(key.replace("channels.name.", ""));
			}
		}

		enableGlobalChat = getConfig().getBoolean("channels.name.enableGlobalMessageFormat", false);
		String defaultGlobal = getConfig().getString("channels.name.defaultGlobal");
		if (defaultGlobal != null && !defaultGlobal.isEmpty()) {
			channels.add(defaultGlobal);
		}
		enableArgsAsMessage = getConfig().getBoolean("channels.name.enableArgsAsMessage", false);

		hasPlaceholder = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

		for (int i = 0; i < channels.size(); i++) {
			String ch = channels.get(i);
			boolean exists = getConfig().getBoolean("channels.name." + ch + ".channelExists", false);
			if (!exists && (defaultGlobal == null || !ch.equals(defaultGlobal))) {
				channels.remove(i);
				i--;
			}
		}

		for (Player p : Bukkit.getOnlinePlayers()) {
			if (defaultGlobal != null) {
				currentChannel.put(p.getName(), defaultGlobal);
			}
			spyChannels.put(p.getName(), new ArrayList<>());
		}

		dataFile = new File(this.getDataFolder(), "data.yml");
		if (!dataFile.exists()) {
			try {
				if (!this.getDataFolder().exists()) {
					this.getDataFolder().mkdirs();
				}
				dataFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		dataYaml = YamlConfiguration.loadConfiguration(dataFile);

		tryRegisterPapi();
		Bukkit.getScheduler().runTaskLater(this, this::tryRegisterPapi, 20L);
	}

	@Override
	public void onDisable() {
		tryUnregisterPapi();
		reloadConfig();
		saveConfig();
	}

	@EventHandler
	public void onPluginEnable(PluginEnableEvent event) {
		if (event.getPlugin() != null && "PlaceholderAPI".equalsIgnoreCase(event.getPlugin().getName())) {
			Bukkit.getScheduler().runTaskLater(this, this::tryRegisterPapi, 1L);
		}
	}

	private void tryRegisterPapi() {
		try {
			if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
				if (papiExpansion != null) {
					try {
						papiExpansion.unregister();
					} catch (Throwable ignored) {
					}
					papiExpansion = null;
				}
				papiExpansion = new PAPI();
				boolean ok = papiExpansion.register();
				if (ok) {
					getLogger().info("PlaceholderAPI expansion registered.");
					hasPlaceholder = true;
				} else {
					getLogger().warning("Failed to register PlaceholderAPI expansion.");
				}
			} else {
				getLogger().info("PlaceholderAPI not detected; skipping expansion registration.");
				hasPlaceholder = false;
			}
		} catch (NoClassDefFoundError | Exception e) {
			getLogger().warning("Could not register PlaceholderAPI expansion: " + e.getMessage());
		}
	}

	private void tryUnregisterPapi() {
		if (papiExpansion != null) {
			try {
				papiExpansion.unregister();
				getLogger().info("PlaceholderAPI expansion unregistered.");
			} catch (Throwable t) {
			} finally {
				papiExpansion = null;
			}
		}
	}
}
