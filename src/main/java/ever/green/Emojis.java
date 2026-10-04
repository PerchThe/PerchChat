package ever.green;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Emojis implements Listener {

    private final JavaPlugin plugin;
    private final File file;

    private volatile List<Rule> rules = List.of();
    private volatile Map<String, String> tokenToOutput = Map.of();

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
            } else if (raw instanceof List<?> list) {
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
            out = out.replace(r.token, stripColors(r.output));
        }
        return out;
    }

    public String processToMiniMessage(String input) {
        if (input == null || input.isEmpty() || rules.isEmpty()) return input;

        String out = input;
        for (Rule r : rules) {
            if (out.contains(r.token)) {
                String cleanOutput = stripColors(r.output);
                String hover = "<white>" + r.token + " » " + cleanOutput + "</white>";
                String replacement = "<hover:show_text:'" + hover + "'>" + r.output + "</hover>";
                out = out.replace(r.token, replacement);
            }
        }
        return out;
    }

    private String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)[&§][0-9A-FK-ORa-fk-or]", "").replaceAll("(?i)[&§]#[A-F0-9]{6}", "");
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
        try { cfg.save(file); } catch (IOException ignored) {}
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