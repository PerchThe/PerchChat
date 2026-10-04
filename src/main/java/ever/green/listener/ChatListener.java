package ever.green.listener;

import ever.green.Chat;
import ever.green.util.FormatUtil;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private final Chat plugin;
    private static final Pattern PTAG_TOKEN = Pattern.compile("⟦PTAG:([^⟧]+)⟧");
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\b(?:https?://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");

    private static final Map<UUID, Integer> localWarningCounter = new HashMap<>();
    private static final Map<UUID, Integer> partyWarningCounter = new HashMap<>();

    public ChatListener(Chat plugin) {
        this.plugin = plugin;
    }

    private void sendMsg(Player player, String key, String fallback, String... replacements) {
        if (plugin.getMessagesConfig() == null) {
            player.sendMessage(FormatUtil.format(fallback.replace("{prefix}", "")));
            return;
        }

        String prefix = plugin.getMessagesConfig().getString("prefix", "");
        String msg = plugin.getMessagesConfig().getString(key, fallback);
        msg = msg.replace("{prefix}", prefix);

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                msg = msg.replace(replacements[i], replacements[i + 1]);
            }
        }
        player.sendMessage(FormatUtil.format(msg));
    }

    private boolean shouldSendWarning(Player player, Map<UUID, Integer> counterMap) {
        UUID uuid = player.getUniqueId();
        int count = counterMap.getOrDefault(uuid, 0) + 1;
        counterMap.put(uuid, count);
        return count % 50 == 1;
    }

    private boolean isCapturingTags(Player player) {
        try {
            Plugin tags = Bukkit.getPluginManager().getPlugin("PerchTags");
            if (tags == null || !tags.isEnabled()) return false;
            Method getAdd = tags.getClass().getMethod("getAddTagCommand");
            Object addCmd = getAdd.invoke(tags);
            if (addCmd == null) return false;
            Method isCap = addCmd.getClass().getMethod("isCapturing", Player.class);
            return (Boolean) isCap.invoke(addCmd, player);
        } catch (Exception ignored) {
            return false;
        }
    }

    private Component resolvePerchTag(String uniqueId) {
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin("PerchTags");
            if (pl == null || !pl.isEnabled()) return Component.empty();
            Method m = pl.getClass().getMethod("getTagComponentById", String.class);
            Object out = m.invoke(pl, uniqueId);
            if (out instanceof Component c) return c;
        } catch (Throwable ignored) {}
        return Component.empty();
    }

    private Component buildFinalComponent(Player player, String template, String rawMessage) {
        String nickToken = "⟦PLAYER_NICKNAME⟧";
        String userToken = "⟦USERNAME⟧";
        String displayToken = "⟦PLAYER⟧";

        String secureTemplate = template.replace("{playernickname}", nickToken)
                .replace("<playernickname>", nickToken)
                .replace("%playernickname%", nickToken)
                .replace("{username}", userToken)
                .replace("<username>", userToken)
                .replace("%username%", userToken)
                .replace("{player}", displayToken)
                .replace("<player>", displayToken)
                .replace("%player%", displayToken);

        String processedTemplate = FormatUtil.setPlaceholders(player, secureTemplate);

        String safeMessage = FormatUtil.escapeMiniMessage(rawMessage);

        if (plugin.getEmojiManager() != null) {
            safeMessage = plugin.getEmojiManager().processToMiniMessage(safeMessage);
        }

        String fullString = processedTemplate.replace("{message}", safeMessage);
        Component comp = FormatUtil.format(fullString);

        Component hoverComp = null;
        if (plugin.getConfig().getBoolean("chat_hover.enabled", false)) {
            List<String> hoverLines = plugin.getConfig().getStringList("chat_hover.text");
            if (!hoverLines.isEmpty()) {
                String hoverString = String.join("<newline>", hoverLines);
                hoverString = FormatUtil.setPlaceholders(player, hoverString);
                hoverComp = FormatUtil.format(hoverString);
            }
        }

        if (fullString.contains(nickToken)) {
            String visibleName = FormatUtil.setPlaceholders(player, "%essentials_nickname%");
            if (visibleName == null || visibleName.isEmpty()) visibleName = player.getName();
            Component playerNameComp = FormatUtil.format(visibleName);
            if (hoverComp != null) playerNameComp = playerNameComp.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
            comp = comp.replaceText(TextReplacementConfig.builder().matchLiteral(nickToken).replacement(playerNameComp).build());
        }

        if (fullString.contains(userToken)) {
            Component userNameComp = FormatUtil.format(player.getName());
            if (hoverComp != null) userNameComp = userNameComp.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
            comp = comp.replaceText(TextReplacementConfig.builder().matchLiteral(userToken).replacement(userNameComp).build());
        }

        if (fullString.contains(displayToken)) {
            Component displayNameComp = FormatUtil.format(player.getDisplayName());
            if (hoverComp != null) displayNameComp = displayNameComp.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
            comp = comp.replaceText(TextReplacementConfig.builder().matchLiteral(displayToken).replacement(displayNameComp).build());
        }

        String linkColor = plugin.getMessagesConfig().getString("link_color", "&#48ab76");
        comp = comp.replaceText(TextReplacementConfig.builder()
                .match(URL_PATTERN)
                .replacement((matchResult, builder) -> {
                    String rawUrl = matchResult.group();
                    String clickableUrl = rawUrl.toLowerCase().startsWith("http") ? rawUrl : "https://" + rawUrl;

                    return FormatUtil.format(linkColor + rawUrl)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(clickableUrl))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(FormatUtil.format("&7Click to visit: &f" + clickableUrl)));
                }).build());

        Matcher m = PTAG_TOKEN.matcher(fullString);
        if (m.find()) {
            m.reset();
            while (m.find()) {
                String full = m.group(0);
                String id = m.group(1);
                Component tagComp = resolvePerchTag(id);
                comp = comp.replaceText(TextReplacementConfig.builder()
                        .matchLiteral(full).replacement(tagComp).build());
            }
        }
        return comp;
    }

    // Changing this to HIGHEST is the magic key. It allows InteractiveChat to read the command
    // and cache the [i] before we intercept and cancel the event!
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChannelCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String[] args = message.split(" ");
        String cmd = args[0].substring(1).toLowerCase();

        List<String> partyCmds = plugin.getConfig().getStringList("channels.party.commands");
        if (partyCmds != null && partyCmds.contains(cmd)) {
            event.setCancelled(true);
            handleCommandRouting(event.getPlayer(), "party", args, message);
            return;
        }

        if (plugin.getConfig().getConfigurationSection("channels.list") != null) {
            for (String channel : plugin.getConfig().getConfigurationSection("channels.list").getKeys(false)) {
                List<String> channelCmds = plugin.getConfig().getStringList("channels.list." + channel + ".commands");
                if (channelCmds != null && channelCmds.contains(cmd)) {
                    event.setCancelled(true);
                    handleCommandRouting(event.getPlayer(), channel, args, message);
                    return;
                }
            }
        }
    }

    private void handleCommandRouting(Player player, String targetChannel, String[] args, String fullMessage) {
        if (targetChannel.equalsIgnoreCase("party")) {
            String partyName = null;
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
                    partyName = com.gmail.nossr50.api.PartyAPI.getPartyName(player);
                }
            } catch (Throwable ignored) {}

            if (partyName == null || partyName.isEmpty()) {
                sendMsg(player, "notInParty", "{prefix}You are not in a party");
                return;
            }
        } else {
            String perm = plugin.getConfig().getString("channels.list." + targetChannel + ".permission");
            if (perm != null && !perm.isBlank() && !player.hasPermission(perm)) {
                sendMsg(player, "noPerm", "{prefix}No Permission");
                return;
            }
        }

        if (args.length > 1) {
            String chatMsg = fullMessage.substring(args[0].length() + 1);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> processChat(player, chatMsg, targetChannel));
        } else {
            plugin.getChannelManager().setChannel(player, targetChannel);

            String displayChannel = targetChannel.equalsIgnoreCase("party") ? "Party" : targetChannel;
            sendMsg(player, "switchedChannel", "{prefix}Channel set to &f{channel-name}", "{channel-name}", displayChannel);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isCapturingTags(player)) return;

        event.setCancelled(true);
        String rawMessage = FormatUtil.stripMagicCodes(event.getMessage());
        String currentChannel = plugin.getChannelManager().getChannel(player);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> processChat(player, rawMessage, currentChannel));
    }

    public void processChat(Player player, String rawMessage, String channelName) {
        if (channelName.equalsIgnoreCase("party")) {
            handlePartyChat(player, rawMessage);
            return;
        }

        String path = "channels.list." + channelName;

        String format = plugin.getConfig().getString(path + ".messageFormat", "{playernickname}: {message}");
        String prefix = plugin.getConfig().getString(path + ".prefix", "");
        String permission = plugin.getConfig().getString(path + ".permission");
        boolean isGlobal = channelName.equalsIgnoreCase(plugin.getConfig().getString("channels.defaultGlobal", "Global"));

        String template = format.replace("{channel-prefix}", prefix);
        Component finalComponent = buildFinalComponent(player, template, rawMessage);

        if (plugin.getDiscordHook() != null) {
            plugin.getDiscordHook().sendToDiscord(player, channelName, isGlobal, rawMessage);
        }

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        UUID senderUUID = player.getUniqueId();
        boolean gpEnabled = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");

        if (isGlobal) {
            for (Player p : online) {
                if (permission != null && !p.hasPermission(permission)) continue;
                if (gpEnabled && GriefPrevention.instance.dataStore.getPlayerData(p.getUniqueId()).ignoredPlayers.containsKey(senderUUID)) continue;
                p.sendMessage(finalComponent);
            }
            Bukkit.getConsoleSender().sendMessage(finalComponent);
            return;
        }

        boolean enableDistance = plugin.getConfig().getBoolean(path + ".enableDistanceMessage", false);
        double distance = plugin.getConfig().getDouble(path + ".distanceMessage", 250);
        boolean sendRegardless = plugin.getConfig().getBoolean(path + ".sendRegardlessOfCurrentChannel", false);
        boolean someoneHeard = false;

        for (Player p : online) {
            if (plugin.getChannelManager().isSpying(p, channelName)) {
                p.sendMessage(finalComponent);
                continue;
            }
            if (permission != null && !p.hasPermission(permission)) continue;
            boolean inSameChannel = sendRegardless || channelName.equalsIgnoreCase(plugin.getChannelManager().getChannel(p));
            if (!inSameChannel) continue;

            if (enableDistance) {
                if (!p.getWorld().equals(player.getWorld()) || p.getLocation().distance(player.getLocation()) > distance) continue;
            }

            p.sendMessage(finalComponent);
            if (!p.equals(player)) someoneHeard = true;
        }

        Bukkit.getConsoleSender().sendMessage(finalComponent);

        if (enableDistance && !someoneHeard && shouldSendWarning(player, localWarningCounter)) {
            sendMsg(player, "noOneNearby", "{prefix}&cNo one is nearby to hear your message.");
        }
    }

    private void handlePartyChat(Player player, String message) {
        String partyName = null;

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
                partyName = com.gmail.nossr50.api.PartyAPI.getPartyName(player);
            }
        } catch (Throwable ignored) {}

        if (partyName == null || partyName.isEmpty()) {
            sendMsg(player, "notInParty", "{prefix}You are not in a party");
            return;
        }

        partyName = partyName.replaceAll("(?i)([&\u00A7])([0-9a-fk-orx])", "$1\u200B$2");

        String format = plugin.getConfig().getString("channels.party.format", "{party-name} {playernickname}: {message}");
        String template = format.replace("{party-name}", partyName);

        Component finalComponent = buildFinalComponent(player, template, message);
        boolean someoneHeard = false;

        try {
            if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
                for (Player member : com.gmail.nossr50.api.PartyAPI.getOnlineMembers(player)) {
                    member.sendMessage(finalComponent);
                    if (!member.equals(player)) someoneHeard = true;
                }
            }
        } catch (Throwable ignored) {}

        if (plugin.getDiscordHook() != null) {
            plugin.getDiscordHook().sendPartyToDiscord(player, message);
        }

        if (!someoneHeard && shouldSendWarning(player, partyWarningCounter)) {
            sendMsg(player, "noPartyMemberOnline", "{prefix}&cNo party member is online to see your message.");
        }
    }
}