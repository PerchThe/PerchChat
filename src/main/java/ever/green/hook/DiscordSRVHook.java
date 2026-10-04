package ever.green.hook;

import ever.green.Chat;
import ever.green.util.FormatUtil;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePostProcessEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class DiscordSRVHook {

    private final Chat plugin;
    private final boolean isEnabled;

    public DiscordSRVHook(Chat plugin) {
        this.plugin = plugin;
        this.isEnabled = Bukkit.getPluginManager().isPluginEnabled("DiscordSRV");

        if (this.isEnabled) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> github.scarsz.discordsrv.DiscordSRV.api.subscribe(this), 30L);
        }
    }

    public void unregister() {
        if (isEnabled) {
            try { github.scarsz.discordsrv.DiscordSRV.api.unsubscribe(this); } catch (Exception ignored) {}
        }
    }

    public void sendToDiscord(Player player, String channelName, boolean isGlobal, String message) {
        if (!isEnabled) return;
        String targetChannel = isGlobal ? "global" : channelName.toLowerCase();
        try {
            github.scarsz.discordsrv.DiscordSRV.getPlugin().processChatMessage(player, message, targetChannel, false);
        } catch (Exception ignored) {}
    }

    public void sendPartyToDiscord(Player player, String message) {
        if (!isEnabled) return;
        String partyID = plugin.getConfig().getString("channels.party.discordID");
        if (partyID != null && !partyID.isBlank()) {
            try {
                github.scarsz.discordsrv.DiscordSRV.getPlugin().processChatMessage(player, message, "party", false);
            } catch (Exception ignored) {}
        }
    }

    @Subscribe
    public void onDiscordToMinecraftPostProcess(DiscordGuildMessagePostProcessEvent event) {
        if (event.getAuthor() != null && event.getAuthor().isBot()) return;

        String channelId = event.getChannel() != null ? event.getChannel().getId() : null;
        if (channelId == null) return;

        // 1. Identify your Global Channel
        String defaultGlobal = plugin.getConfig().getString("channels.defaultGlobal", "Global");
        String globalId = plugin.getConfig().getString("channels.list." + defaultGlobal + ".discordID");

        // 2. THE FIX: Completely ignore the Global Channel exactly like your old code did!
        // This lets DiscordSRV broadcast it normally so InteractiveChat can inject its Map Images.
        if (globalId != null && globalId.equals(channelId)) return;

        // 3. Manually reconstruct messages for secondary channels (Staff, Local, etc)
        for (String ch : plugin.getChannelManager().getAvailableChannels()) {
            if (ch.equalsIgnoreCase(defaultGlobal)) continue;

            String cfgId = plugin.getConfig().getString("channels.list." + ch + ".discordID");
            if (cfgId == null || cfgId.isBlank() || !cfgId.equals(channelId)) continue;

            event.setCancelled(true);

            String fmt = plugin.getConfig().getString("channels.list." + ch + ".fromDiscordFormat", "&f{user}: &f{message}");
            String user = event.getMember() != null ? event.getMember().getEffectiveName() : "Discord";
            String msg = event.getMessage() != null ? event.getMessage().getContentDisplay() : "";

            // Escape MiniMessage tags so Discord URLs (<http...>) don't crash the formatter
            msg = FormatUtil.escapeMiniMessage(msg);

            String rawFormat = fmt.replace("{user}", user).replace("{message}", msg);
            net.kyori.adventure.text.Component built = FormatUtil.format(rawFormat);

            String perm = plugin.getConfig().getString("channels.list." + ch + ".permission");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (perm == null || perm.isBlank() || p.hasPermission(perm)) {
                    p.sendMessage(built);
                }
            }
            return;
        }
    }
}