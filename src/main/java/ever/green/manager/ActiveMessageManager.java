package ever.green.manager;

import ever.green.Chat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActiveMessageManager {

    private final Chat plugin;
    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private final Map<UUID, Map<String, BukkitTask>> activeTasks = new HashMap<>();

    public ActiveMessageManager(Chat plugin) {
        this.plugin = plugin;
    }

    public void setBossBar(Player player, BossBar bar) {
        clearBossBar(player);
        activeBars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
    }

    public void clearBossBar(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    public void setTask(Player player, String type, BukkitTask task) {
        clearTask(player, type);
        activeTasks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(type, task);
    }

    public void clearTask(Player player, String type) {
        Map<String, BukkitTask> playerTasks = activeTasks.get(player.getUniqueId());
        if (playerTasks != null) {
            BukkitTask task = playerTasks.remove(type);
            if (task != null) task.cancel();
        }
    }

    public boolean dismissAll(Player player) {
        boolean something = activeBars.containsKey(player.getUniqueId()) || activeTasks.containsKey(player.getUniqueId());
        clearBossBar(player);
        Map<String, BukkitTask> tasks = activeTasks.remove(player.getUniqueId());
        if (tasks != null) tasks.values().forEach(BukkitTask::cancel);
        player.sendActionBar(Component.empty());
        return something;
    }

    public void shutdown() {
        activeTasks.values().forEach(m -> m.values().forEach(BukkitTask::cancel));
        activeBars.keySet().forEach(id -> {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) clearBossBar(p);
        });
    }
}