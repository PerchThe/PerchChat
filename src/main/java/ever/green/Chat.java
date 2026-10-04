package ever.green;

import ever.green.command.AnnounceCommand;
import ever.green.command.ChatCommand;
import ever.green.command.ChannelCommand;
import ever.green.hook.DiscordSRVHook;
import ever.green.hook.PapiExpansion;
import ever.green.listener.*;
import ever.green.manager.ActiveMessageManager;
import ever.green.manager.ChannelManager;
import ever.green.util.FormatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Chat extends JavaPlugin {

    private static Chat instance;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    private ChannelManager channelManager;
    private ActiveMessageManager activeMessageManager;
    private DiscordSRVHook discordHook;
    private Emojis emojiManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        createMessagesConfig();

        this.channelManager = new ChannelManager(this);
        this.activeMessageManager = new ActiveMessageManager(this);
        this.discordHook = new DiscordSRVHook(this);

        this.emojiManager = new Emojis(this);
        this.emojiManager.reload();

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PapiExpansion(this).register();
        }

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new SystemListener(this), this);
        pm.registerEvents(new SleepListener(this), this);

        getCommand("evergreenchat").setExecutor(new ChatCommand(this));

        AnnounceCommand announceCommand = new AnnounceCommand(this);
        getCommand("messagechat").setExecutor(announceCommand);
        getCommand("messageactionbar").setExecutor(announceCommand);
        getCommand("messagebossbar").setExecutor(announceCommand);
        getCommand("dismiss").setExecutor(announceCommand);

        // Inject config commands natively into the server
        registerDynamicCommands();

        getLogger().info("EvergreenChat has been successfully enabled! Glorp");
    }

    @Override
    public void onDisable() {
        if (activeMessageManager != null) activeMessageManager.shutdown();
        if (channelManager != null) channelManager.saveData();
        if (discordHook != null) discordHook.unregister();
        getLogger().info("EvergreenChat is shutting down.");
    }

    private void registerDynamicCommands() {
        CommandMap commandMap = getServer().getCommandMap();

        // Register Party Commands
        for (String alias : getConfig().getStringList("channels.party.commands")) {
            commandMap.register("evergreenchat", new ChannelCommand(alias, "party"));
        }

        // Register Channel Commands
        for (String channel : channelManager.getAvailableChannels()) {
            for (String alias : getConfig().getStringList("channels.list." + channel + ".commands")) {
                commandMap.register("evergreenchat", new ChannelCommand(alias, channel));
            }
        }
    }

    public static Chat getInstance() { return instance; }

    public ChannelManager getChannelManager() { return channelManager; }
    public ActiveMessageManager getActiveMessageManager() { return activeMessageManager; }
    public DiscordSRVHook getDiscordHook() { return discordHook; }
    public Emojis getEmojiManager() { return emojiManager; }
    public FileConfiguration getMessagesConfig() { return messagesConfig; }

    public void reloadConfigs() {
        reloadConfig();
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        if (emojiManager != null) emojiManager.reload();
        registerDynamicCommands(); // Re-register commands if config changes!
    }

    private void createMessagesConfig() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            messagesFile.getParentFile().mkdirs();
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public Component getMsg(String path) {
        return FormatUtil.format(getRawMsg(path));
    }

    public String getRawMsg(String path) {
        String prefix = messagesConfig.getString("prefix", "");
        String raw = messagesConfig.getString(path, "&cMessage missing: " + path);
        return raw.replace("{prefix}", prefix);
    }
}