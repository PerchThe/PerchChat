package ever.green.listener;

import ever.green.Chat;
import ever.green.util.FormatUtil;
import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.command.UnknownCommandEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

public class SystemListener implements Listener {

    private final Chat plugin;

    public SystemListener(Chat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        String mode = plugin.getConfig().getString("death_messages.mode", "CUSTOM").toUpperCase();
        if (mode.equals("DISABLED")) {
            event.deathMessage(null);
            return;
        }
        if (mode.equals("VANILLA")) return;

        Component original = event.deathMessage();
        if (original == null) return;

        String prefixStr = plugin.getConfig().getString("death_messages.prefix", "");
        Component prefix = FormatUtil.parse(event.getEntity(), prefixStr);
        event.deathMessage(prefix.append(original));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        String mode = plugin.getConfig().getString("advancements.mode", "CUSTOM").toUpperCase();
        if (mode.equals("DISABLED")) {
            event.message(null);
            return;
        }

        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (display == null || !display.doesAnnounceToChat()) return;

        Component message;
        if (mode.equals("VANILLA")) {
            message = event.message();
            if (message == null) return;
        } else {
            String rawFormat = plugin.getConfig().getString("advancements.format", " has made the advancement [%advancement%]");
            String plainTitle = PlainTextComponentSerializer.plainText().serialize(display.title());

            Component titleComp = FormatUtil.format(FormatUtil.escapeMiniMessage(plainTitle));
            message = FormatUtil.parse(event.getPlayer(), rawFormat).replaceText(TextReplacementConfig.builder()
                    .matchLiteral("[%advancement%]")
                    .replacement(titleComp)
                    .build());
        }

        event.message(null);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.hasPermission("customadvancements.suppress")) {
                continue;
            }
            viewer.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUnknownCommand(UnknownCommandEvent event) {
        if (!plugin.getConfig().getBoolean("unknown_command.enabled", true)) return;
        Player player = event.getSender() instanceof Player ? (Player) event.getSender() : null;

        String format = plugin.getConfig().getString("unknown_command.format", "&cUnknown command.");
        event.message(FormatUtil.parse(player, format));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String lower = event.getMessage().toLowerCase();
        if (lower.startsWith("/say ") && plugin.getConfig().getBoolean("say_command.enabled", true)) {
            if (!event.getPlayer().hasPermission("minecraft.command.say") && !event.getPlayer().isOp()) return;
            event.setCancelled(true);
            String text = event.getMessage().substring(5);
            String format = plugin.getConfig().getString("say_command.format", "&d[Server] &f<message>").replace("<message>", text);
            Bukkit.broadcast(FormatUtil.parse(event.getPlayer(), format));
        } else if (lower.startsWith("/me ") && plugin.getConfig().getBoolean("me_command.enabled", true)) {
            if (!event.getPlayer().hasPermission("minecraft.command.me") && !event.getPlayer().isOp()) return;
            event.setCancelled(true);
            String text = event.getMessage().substring(4);
            String format = plugin.getConfig().getString("me_command.format", "* <player> <message>").replace("<message>", text);
            Bukkit.broadcast(FormatUtil.parse(event.getPlayer(), format));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        String lower = event.getCommand().toLowerCase();
        if (lower.startsWith("say ") && plugin.getConfig().getBoolean("say_command.enabled", true)) {
            event.setCancelled(true);
            String text = event.getCommand().substring(4);
            String format = plugin.getConfig().getString("say_command.format", "&d[Server] &f<message>").replace("<message>", text);
            Bukkit.broadcast(FormatUtil.format(format));
        }
    }
}