package ever.green.hook;

import ever.green.Chat;
import ever.green.util.FormatUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PapiExpansion extends PlaceholderExpansion {

    private final Chat plugin;

    public PapiExpansion(Chat plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "evergreenchat"; }

    @Override
    public @NotNull String getAuthor() { return plugin.getDescription().getAuthors().toString(); }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public boolean canRegister() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // %evergreenchat_channel%
        if (params.equalsIgnoreCase("channel")) {
            String channel = plugin.getChannelManager().getChannel(player);
            if (channel == null) return "None";
            if (channel.equalsIgnoreCase("party")) return "Party";
            return channel.substring(0, 1).toUpperCase() + channel.substring(1).toLowerCase();
        }

        // %evergreenchat_nickname%
        if (params.equalsIgnoreCase("nickname")) {
            String nick = FormatUtil.setPlaceholders(player, "%essentials_nickname%");

            // If they don't have a nickname, or the placeholder fails to parse, fallback to raw username
            if (nick == null || nick.isEmpty() || nick.equals("%essentials_nickname%")) {
                return player.getName();
            }
            return nick;
        }

        return null;
    }
}