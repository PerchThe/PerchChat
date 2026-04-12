package me.perch.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Emojis implements Listener {

    private final JavaPlugin plugin;
    private final File file;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    private volatile List<Rule> rules = List.of();
    private volatile Map<String, String> tokenToOutput = Map.of();

    private static final Pattern HEX_AMP = Pattern.compile("&" + "#" + "([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_SECTION = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern LEGACY_HEX = Pattern.compile("§x(§[0-9A-Fa-f]){6}");

    private final Set<String> messageCommands = Set.of(
            "/msg", "/m", "/t", "/pm", "/tell", "/w", "/whisper", "/emsg", "/epm", "/etell", "/ewhisper",
            "/r", "/reply", "/er", "/ereply",
            "/me", "/eme", "/action", "/broadcast", "/ebroadcast", "/bc", "/ebc"
    );

    public Emojis(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "emojis.yml");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String commandLine = event.getMessage();

        String[] parts = commandLine.split(" ", 2);
        if (parts.length < 2) return;

        String cmd = parts[0].toLowerCase();

        if (messageCommands.contains(cmd)) {
            String newArguments = plain(parts[1]);
            event.setMessage(parts[0] + " " + newArguments);
        }
    }

    public void reload() {
        ensureFile();

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("emojis");
        if (root == null) {
            rules = List.of();
            tokenToOutput = Map.of();
            return;
        }

        Map<String, String> map = new LinkedHashMap<>();
        List<Rule> tmp = new ArrayList<>();

        for (String output : root.getKeys(false)) {
            Object raw = root.get(output);

            if (raw instanceof String s) {
                String token = s.trim();
                if (!token.isEmpty() && !map.containsKey(token)) {
                    map.put(token, output);
                    tmp.add(new Rule(token, output));
                }
                continue;
            }

            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof String s2) {
                        String token = s2.trim();
                        if (!token.isEmpty() && !map.containsKey(token)) {
                            map.put(token, output);
                            tmp.add(new Rule(token, output));
                        }
                    }
                }
            }
        }

        tmp.sort(Comparator.comparingInt((Rule r) -> r.token.length()).reversed());

        rules = List.copyOf(tmp);
        tokenToOutput = Map.copyOf(map);
    }

    public String plain(String input) {
        if (input == null || input.isEmpty()) return input;

        String out = input;
        for (Rule r : rules) {
            out = out.replace(r.token, r.output);
        }
        return out;
    }

    public Component render(String legacyStringWithTokens) {
        if (legacyStringWithTokens == null || legacyStringWithTokens.isEmpty()) return Component.empty();

        List<Rule> rs = rules;
        if (rs.isEmpty()) return legacy.deserialize(legacyStringWithTokens);

        Component out = Component.empty();
        String state = "";

        int i = 0;
        while (i < legacyStringWithTokens.length()) {
            int bestPos = -1;
            Rule best = null;

            for (Rule r : rs) {
                int pos = legacyStringWithTokens.indexOf(r.token, i);
                if (pos == -1) continue;
                if (bestPos == -1 || pos < bestPos) {
                    bestPos = pos;
                    best = r;
                    if (bestPos == i) break;
                }
            }

            if (best == null) {
                String tail = legacyStringWithTokens.substring(i);
                out = out.append(legacy.deserialize(state + tail));
                break;
            }

            if (bestPos > i) {
                String seg = legacyStringWithTokens.substring(i, bestPos);
                out = out.append(legacy.deserialize(state + seg));
                state = updateState(state, seg);
            }

            String outputLegacy = normalizeToSection(best.output);
            String hoverText = best.token + " » " + stripColors(normalizeToSection(best.output));

            Component emoji = legacy.deserialize(state + outputLegacy)
                    .hoverEvent(HoverEvent.showText(Component.text(hoverText)));

            out = out.append(emoji);
            state = updateState(state, outputLegacy);

            i = bestPos + best.token.length();
        }

        return out;
    }

    private String normalizeToSection(String s) {
        if (s == null || s.isEmpty()) return "";

        Matcher m = HEX_AMP.matcher(s);
        StringBuffer buf = new StringBuffer(s.length() + 32);
        while (m.find()) {
            String g = m.group(1);
            String rep = "§x§" + g.charAt(0) + "§" + g.charAt(1) + "§" + g.charAt(2) + "§" + g.charAt(3) + "§" + g.charAt(4) + "§" + g.charAt(5);
            m.appendReplacement(buf, Matcher.quoteReplacement(rep));
        }
        String out = m.appendTail(buf).toString();
        return out.replace('&', '§');
    }

    private String stripColors(String s) {
        if (s == null || s.isEmpty()) return "";
        String out = LEGACY_HEX.matcher(s).replaceAll("");
        out = LEGACY_SECTION.matcher(out).replaceAll("");
        return out;
    }

    private String updateState(String state, String seg) {
        String color = extractColor(state);
        String formats = extractFormats(state);

        int idx = 0;
        while (idx < seg.length() - 1) {
            char c = seg.charAt(idx);
            if (c != '§') {
                idx++;
                continue;
            }
            char code = seg.charAt(idx + 1);

            if (code == 'x' || code == 'X') {
                if (idx + 13 < seg.length()) {
                    String hex = seg.substring(idx, idx + 14);
                    if (LEGACY_HEX.matcher(hex).matches()) {
                        color = hex;
                        formats = "";
                        idx += 14;
                        continue;
                    }
                }
                idx += 2;
                continue;
            }

            char lower = Character.toLowerCase(code);

            if (lower == 'r') {
                color = "";
                formats = "";
                idx += 2;
                continue;
            }

            if ((lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f')) {
                color = "§" + lower;
                formats = "";
                idx += 2;
                continue;
            }

            if (lower == 'k' || lower == 'l' || lower == 'm' || lower == 'n' || lower == 'o') {
                String f = "§" + lower;
                if (!formats.contains(f)) formats = formats + f;
                idx += 2;
                continue;
            }

            idx += 2;
        }

        return color + formats;
    }

    private String extractColor(String state) {
        if (state == null || state.isEmpty()) return "";
        Matcher m = LEGACY_HEX.matcher(state);
        if (m.find()) return m.group();
        Matcher m2 = LEGACY_SECTION.matcher(state);
        String last = "";
        while (m2.find()) {
            String g = m2.group();
            char c = Character.toLowerCase(g.charAt(1));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'r') last = g;
        }
        if ("§r".equalsIgnoreCase(last)) return "";
        return last;
    }

    private String extractFormats(String state) {
        if (state == null || state.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Matcher m = LEGACY_SECTION.matcher(state);
        while (m.find()) {
            String g = m.group();
            char c = Character.toLowerCase(g.charAt(1));
            if (c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o') {
                if (sb.indexOf(g.toLowerCase()) == -1 && sb.indexOf(g.toUpperCase()) == -1) sb.append("§").append(c);
            }
        }
        return sb.toString();
    }

    private void ensureFile() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        if (file.exists()) return;

        if (plugin.getResource("emojis.yml") != null) {
            plugin.saveResource("emojis.yml", false);
            return;
        }

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("emojis.🐦", List.of(":bird:"));
        try {
            cfg.save(file);
        } catch (IOException ignored) {
        }
    }

    private static final class Rule {
        private final String token;
        private final String output;

        private Rule(String token, String output) {
            this.token = token;
            this.output = output;
        }
    }
}