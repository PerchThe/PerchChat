package ever.green.manager;

import ever.green.Chat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ChannelManager {

    private final Chat plugin;
    private final Map<UUID, String> currentChannel = new HashMap<>();
    private final Map<UUID, Set<String>> spyChannels = new HashMap<>();

    private File dataFile;
    private YamlConfiguration dataYaml;

    public ChannelManager(Chat plugin) {
        this.plugin = plugin;
        setupDataFile();
    }

    private void setupDataFile() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml!");
            }
        }
        dataYaml = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveData() {
        try {
            dataYaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml!");
        }
    }

    public String getChannel(Player player) {
        return currentChannel.getOrDefault(player.getUniqueId(), plugin.getConfig().getString("channels.defaultGlobal", "Global"));
    }

    public void setChannel(Player player, String channelName) {
        currentChannel.put(player.getUniqueId(), channelName);
        dataYaml.set(player.getUniqueId().toString() + ".channel", channelName);
    }

    public void initializePlayer(Player player) {
        String savedChannel = dataYaml.getString(player.getUniqueId().toString() + ".channel");
        if (savedChannel == null) {
            savedChannel = plugin.getConfig().getString("channels.channelUponJoining", "Global");
            dataYaml.set(player.getUniqueId().toString() + ".channel", savedChannel);
        }
        currentChannel.put(player.getUniqueId(), savedChannel);
        spyChannels.putIfAbsent(player.getUniqueId(), new HashSet<>());
    }

    public void removePlayer(Player player) {
        currentChannel.remove(player.getUniqueId());
        spyChannels.remove(player.getUniqueId());
    }

    public boolean toggleSpy(Player player, String channelName) {
        Set<String> spies = spyChannels.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (spies.contains(channelName)) {
            spies.remove(channelName);
            return false;
        } else {
            spies.add(channelName);
            return true;
        }
    }

    public boolean isSpying(Player player, String channelName) {
        return spyChannels.getOrDefault(player.getUniqueId(), Collections.emptySet()).contains(channelName);
    }

    public List<String> getAvailableChannels() {
        if (plugin.getConfig().getConfigurationSection("channels.list") == null) return new ArrayList<>();
        return new ArrayList<>(plugin.getConfig().getConfigurationSection("channels.list").getKeys(false));
    }
}