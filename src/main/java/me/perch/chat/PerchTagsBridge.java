package me.perch.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class PerchTagsBridge {
    public static boolean isCapturing(Player player) {
        Plugin perchTags = Bukkit.getPluginManager().getPlugin("PerchTags");
        if (perchTags == null || !perchTags.isEnabled()) return false;

        try {
            Method getAdd = perchTags.getClass().getMethod("getAddTagCommand");
            Object addCmd = getAdd.invoke(perchTags);
            if (addCmd == null) return false;

            Method isCap = addCmd.getClass().getMethod("isCapturing", Player.class);
            Object cap = isCap.invoke(addCmd, player);
            return cap instanceof Boolean && (Boolean) cap;
        } catch (Exception ignored) {
            return false;
        }
    }
}
