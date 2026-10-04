package ever.green.command;

import ever.green.Chat;
import ever.green.util.FormatUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AnnounceCommand implements CommandExecutor, TabCompleter {

    private final Chat plugin;

    public AnnounceCommand(Chat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("dismiss")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMsg("not_a_player"));
                return true;
            }
            if (plugin.getActiveMessageManager().dismissAll(player)) {
                sender.sendMessage(plugin.getMsg("dismiss_success"));
            } else {
                sender.sendMessage(plugin.getMsg("dismiss_fail"));
            }
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMsg("usage"));
            return true;
        }

        String type = cmdName.replace("message", "");
        String freq = args[0].toLowerCase();

        if (freq.equals("timed") && type.equals("chat")) { sender.sendMessage(plugin.getMsg("error_chat_timed")); return true; }
        if (freq.equals("sticky") && type.equals("chat")) { sender.sendMessage(plugin.getMsg("error_chat_sticky")); return true; }

        int targetIdx = freq.equals("timed") ? 2 : (freq.equals("repeating") ? 3 : 1);
        if (args.length <= targetIdx + 1) {
            sender.sendMessage(plugin.getMsg("error_missing_args"));
            return true;
        }

        long duration = 0;
        int repeats = 0;
        try {
            if (freq.equals("timed") || freq.equals("repeating")) duration = parseMillis(args[1]);
            if (freq.equals("repeating")) repeats = Integer.parseInt(args[2]);
        } catch (Exception e) {
            sender.sendMessage(plugin.getMsg("error_parsing_time"));
            return true;
        }

        Collection<? extends Player> targets = resolveTargets(args[targetIdx]);
        String msg = String.join(" ", Arrays.copyOfRange(args, targetIdx + 1, args.length));

        for (Player p : targets) {
            Component comp = FormatUtil.parse(p, msg);
            switch (freq) {
                case "once" -> sendOnce(p, type, comp);
                case "sticky" -> applySticky(p, type, comp);
                case "timed" -> applyTimed(p, type, comp, duration);
                case "repeating" -> applyRepeating(p, type, comp, duration, repeats);
            }
        }
        return true;
    }

    // --- TAB COMPLETION LOGIC ---
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("dismiss")) return Collections.emptyList();

        String type = cmdName.replace("message", "");

        if (args.length == 1) {
            // 1. Suggest Frequencies based on the specific command used
            List<String> validFrequencies = new ArrayList<>();
            validFrequencies.add("once");

            if (type.equals("actionbar")) {
                validFrequencies.addAll(Arrays.asList("repeating", "timed", "sticky"));
            } else if (type.equals("chat")) {
                validFrequencies.add("repeating");
            } else if (type.equals("bossbar")) {
                validFrequencies.addAll(Arrays.asList("timed", "sticky"));
            }

            StringUtil.copyPartialMatches(args[0], validFrequencies, completions);
            return completions;
        }

        String freq = args[0].toLowerCase();
        int targetIdx = freq.equals("timed") ? 2 : (freq.equals("repeating") ? 3 : 1);

        if (args.length - 1 < targetIdx) {
            // 2. Suggest Durations or Repeat Amounts
            List<String> suggestions = new ArrayList<>();
            if (freq.equals("timed") && args.length == 2) {
                suggestions.addAll(Arrays.asList("5s", "10s", "30s", "1m", "5m"));
            } else if (freq.equals("repeating")) {
                if (args.length == 2) {
                    suggestions.addAll(Arrays.asList("1s", "5s", "10s", "1m"));
                } else if (args.length == 3) {
                    suggestions.addAll(Arrays.asList("3", "5", "10", "20"));
                }
            }
            StringUtil.copyPartialMatches(args[args.length - 1], suggestions, completions);
        } else if (args.length - 1 == targetIdx) {
            // 3. Suggest Targets (all, worlds, online players)
            List<String> targetSuggestions = new ArrayList<>();
            targetSuggestions.add("all");

            for (World w : Bukkit.getWorlds()) {
                targetSuggestions.add(w.getName());
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                targetSuggestions.add(p.getName());
            }

            StringUtil.copyPartialMatches(args[args.length - 1], targetSuggestions, completions);
        } else {
            // 4. Do not suggest anything for the actual message argument
            return Collections.emptyList();
        }

        Collections.sort(completions);
        return completions;
    }

    private void sendOnce(Player p, String t, Component c) {
        if (t.equals("chat")) p.sendMessage(c);
        else if (t.equals("actionbar")) p.sendActionBar(c);
        else if (t.equals("bossbar")) {
            BossBar b = BossBar.bossBar(c, 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            p.showBossBar(b);
            Bukkit.getScheduler().runTaskLater(plugin, () -> p.hideBossBar(b), 100L);
        }
    }

    private void applySticky(Player p, String t, Component c) {
        if (t.equals("bossbar")) {
            plugin.getActiveMessageManager().setBossBar(p, BossBar.bossBar(c, 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS));
        } else {
            plugin.getActiveMessageManager().setTask(p, t, new BukkitRunnable() {
                public void run() { p.sendActionBar(c); }
            }.runTaskTimer(plugin, 0, 30));
        }
    }

    private void applyTimed(Player p, String t, Component c, long ms) {
        long end = System.currentTimeMillis() + ms;
        if (t.equals("bossbar")) plugin.getActiveMessageManager().setBossBar(p, BossBar.bossBar(c, 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS));

        plugin.getActiveMessageManager().setTask(p, t, new BukkitRunnable() {
            public void run() {
                if (System.currentTimeMillis() >= end) {
                    if (t.equals("bossbar")) plugin.getActiveMessageManager().clearBossBar(p);
                    else p.sendActionBar(Component.empty());
                    this.cancel();
                } else if (t.equals("actionbar")) p.sendActionBar(c);
            }
        }.runTaskTimer(plugin, 0, 10));
    }

    private void applyRepeating(Player p, String t, Component c, long ms, int r) {
        plugin.getActiveMessageManager().setTask(p, t, new BukkitRunnable() {
            int count = 0;
            long next = System.currentTimeMillis() + ms;
            public void run() {
                if (count >= r) { this.cancel(); return; }
                if (System.currentTimeMillis() >= next) {
                    if (t.equals("chat")) p.sendMessage(c);
                    else p.sendActionBar(c);
                    count++;
                    next = System.currentTimeMillis() + ms;
                }
            }
        }.runTaskTimer(plugin, 0, 10));
    }

    private Collection<? extends Player> resolveTargets(String t) {
        if (t.equalsIgnoreCase("all")) return Bukkit.getOnlinePlayers();
        World w = Bukkit.getWorld(t);
        if (w != null) return w.getPlayers();
        Player p = Bukkit.getPlayer(t);
        return p != null ? Collections.singletonList(p) : Collections.emptyList();
    }

    private long parseMillis(String s) {
        long m = s.endsWith("m") ? 60000 : (s.endsWith("h") ? 3600000 : 1000);
        return Long.parseLong(s.replaceAll("[^0-9]", "")) * m;
    }
}