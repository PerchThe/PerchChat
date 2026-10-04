package ever.green.util;

import ever.green.Chat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Pattern BUNGEE_HEX_PATTERN = Pattern.compile("(?i)§x(§[0-9a-f]){6}");
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)[&§]#([a-f0-9]{6})");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

    public static Component format(String message) {
        if (message == null || message.isEmpty()) return Component.empty();

        String parsed = message;

        Matcher bungeeMatcher = BUNGEE_HEX_PATTERN.matcher(parsed);
        StringBuilder bungeeBuffer = new StringBuilder();
        while (bungeeMatcher.find()) {
            String hex = bungeeMatcher.group().replace("§x", "").replace("§", "");
            bungeeMatcher.appendReplacement(bungeeBuffer, "<reset><#" + hex + ">");
        }
        bungeeMatcher.appendTail(bungeeBuffer);
        parsed = bungeeBuffer.toString();

        Matcher hexMatcher = HEX_PATTERN.matcher(parsed);
        StringBuilder hexBuffer = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexBuffer, "<reset><#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(hexBuffer);
        parsed = hexBuffer.toString();

        Matcher legacyMatcher = LEGACY_PATTERN.matcher(parsed);
        StringBuilder legacyBuffer = new StringBuilder();
        while (legacyMatcher.find()) {
            String replacement = legacyReplacement(legacyMatcher.group(1).toLowerCase().charAt(0));
            legacyMatcher.appendReplacement(legacyBuffer, Matcher.quoteReplacement(replacement));
        }
        legacyMatcher.appendTail(legacyBuffer);
        parsed = legacyBuffer.toString();

        try {
            return MINI_MESSAGE.deserialize(parsed);
        } catch (Exception e) {
            String safeText = parsed.replace("<", "&lt;").replace(">", "&gt;");
            try {
                return MINI_MESSAGE.deserialize(safeText);
            } catch (Exception ex) {
                return Component.text(parsed);
            }
        }
    }

    public static Component parse(Player player, String message) {
        if (message == null || message.isEmpty()) return Component.empty();

        String nickToken = "⟦PLAYER_NICKNAME_UNIVERSAL⟧";
        String userToken = "⟦USERNAME_UNIVERSAL⟧";
        String displayToken = "⟦PLAYER_UNIVERSAL⟧";

        String processed = message;

        if (player != null) {
            processed = processed.replace("<playernickname>", nickToken)
                    .replace("{playernickname}", nickToken)
                    .replace("%playernickname%", nickToken);

            processed = processed.replace("<username>", userToken)
                    .replace("{username}", userToken)
                    .replace("%username%", userToken);

            processed = processed.replace("<player>", displayToken)
                    .replace("{player}", displayToken)
                    .replace("%player%", displayToken);

            processed = setPlaceholders(player, processed);
        } else {
            processed = setPlaceholders(null, processed);
        }

        Component comp = format(processed);

        if (player != null) {
            // Build the Hover Tooltip
            Component hoverComp = null;
            try {
                Chat plugin = JavaPlugin.getPlugin(Chat.class);
                if (plugin.getConfig().getBoolean("chat_hover.enabled", false)) {
                    List<String> hoverLines = plugin.getConfig().getStringList("chat_hover.text");
                    if (!hoverLines.isEmpty()) {
                        String hoverString = String.join("<newline>", hoverLines);
                        hoverString = setPlaceholders(player, hoverString);
                        hoverComp = format(hoverString);
                    }
                }
            } catch (Exception ignored) {}

            // Inject for {playernickname}
            if (processed.contains(nickToken)) {
                String visibleName = setPlaceholders(player, "%essentials_nickname%");
                if (visibleName == null || visibleName.isEmpty()) visibleName = player.getName();
                Component playerNameComp = format(visibleName);
                if (hoverComp != null) playerNameComp = playerNameComp.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
                comp = comp.replaceText(TextReplacementConfig.builder().matchLiteral(nickToken).replacement(playerNameComp).build());
            }

            // Inject for {username}
            if (processed.contains(userToken)) {
                Component userNameComp = format(player.getName());
                if (hoverComp != null) userNameComp = userNameComp.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
                comp = comp.replaceText(TextReplacementConfig.builder().matchLiteral(userToken).replacement(userNameComp).build());
            }

            // Inject for {player}
            if (processed.contains(displayToken)) {
                Component displayNameComp = format(player.getDisplayName());
                if (hoverComp != null) displayNameComp = displayNameComp.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hoverComp));
                comp = comp.replaceText(TextReplacementConfig.builder().matchLiteral(displayToken).replacement(displayNameComp).build());
            }
        }

        return comp;
    }

    public static String setPlaceholders(Player player, String text) {
        if (text == null) return null;
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    public static String escapeMiniMessage(String input) {
        if (input == null) return "";
        return MINI_MESSAGE.escapeTags(input);
    }

    public static String stripMagicCodes(String message) {
        if (message == null) return "";
        return message.replaceAll("(?i)[&§]k", "");
    }

    private static String legacyReplacement(char code) {
        return switch (code) {
            case '0' -> "<reset><black>"; case '1' -> "<reset><dark_blue>"; case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>"; case '4' -> "<reset><dark_red>"; case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>"; case '7' -> "<reset><gray>"; case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>"; case 'a' -> "<reset><green>"; case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>"; case 'd' -> "<reset><light_purple>"; case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>"; case 'k' -> "<obfuscated>"; case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>"; case 'n' -> "<underlined>"; case 'o' -> "<italic>";
            case 'r' -> "<reset>"; default -> "";
        };
    }
}