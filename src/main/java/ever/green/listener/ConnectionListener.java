package ever.green.listener;

import ever.green.Chat;
import ever.green.util.FormatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Random;

public class ConnectionListener implements Listener {

    private final Chat plugin;
    private final Random random = new Random();

    public ConnectionListener(Chat plugin) {
        this.plugin = plugin;
    }

    private String getRandomMessage(ConfigurationSection section, String path) {
        if (section == null) return null;
        if (section.isList(path)) {
            List<String> list = section.getStringList(path);
            return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
        } else if (section.isString(path)) {
            return section.getString(path);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Initialize player channel data instantly on tick 0
        plugin.getChannelManager().initializePlayer(player);

        String mode = plugin.getConfig().getString("join_messages.mode", "CUSTOM").toUpperCase();

        // 2. Instantly wipe the vanilla message right now on tick 0 so it never shows up
        if (mode.equals("DISABLED") || mode.equals("CUSTOM")) {
            event.joinMessage(null);
        }

        // 3. Schedule custom message system to execute 5 ticks (0.25 seconds) later.
        // This tiny delay guarantees LuckPerms and Essentials have time to load their data into PlaceholderAPI!
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            if (mode.equals("CUSTOM")) {
                if (!player.hasPlayedBefore() && plugin.getConfig().getBoolean("join_messages.first-join.enabled")) {
                    if (plugin.getConfig().isList("join_messages.first-join.format")) {
                        for (String line : plugin.getConfig().getStringList("join_messages.first-join.format")) {
                            if (line != null) Bukkit.broadcast(FormatUtil.parse(player, line));
                        }
                    } else {
                        String line = plugin.getConfig().getString("join_messages.first-join.format");
                        if (line != null) Bukkit.broadcast(FormatUtil.parse(player, line));
                    }
                } else {
                    String msg = null;
                    ConfigurationSection custom = plugin.getConfig().getConfigurationSection("join_messages.custom_messages");
                    if (custom != null) {
                        for (String key : custom.getKeys(false)) {
                            if (player.hasPermission("evergreen.join." + key)) {
                                msg = getRandomMessage(custom, key);
                                break;
                            }
                        }
                    }
                    if (msg == null) msg = getRandomMessage(plugin.getConfig(), "join_messages.format");
                    if (msg != null) Bukkit.broadcast(FormatUtil.parse(player, msg));
                }
            }

            // Execute Commands
            List<String> cmds = plugin.getConfig().getStringList("join_messages.commands");
            for (String cmd : cmds) {
                if (cmd != null && !cmd.isEmpty()) {
                    String formattedCmd = cmd.replace("<player>", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
                }
            }

        }, 5L); // Reduced to 5 ticks!
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.getActiveMessageManager().dismissAll(player);
        plugin.getChannelManager().removePlayer(player);

        String mode = plugin.getConfig().getString("quit_messages.mode", "CUSTOM").toUpperCase();
        if (mode.equals("DISABLED")) {
            event.quitMessage(null);
        } else if (mode.equals("CUSTOM")) {
            event.quitMessage(null);
            String msg = getRandomMessage(plugin.getConfig(), "quit_messages.format");
            if (msg != null) Bukkit.broadcast(FormatUtil.parse(player, msg));
        }
    }
}