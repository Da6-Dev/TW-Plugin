package br.com.tw.minas.utils;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TWBossBar {

    private static final Map<UUID, BossBar> ACTIVE_BARS = new HashMap<>();

    private TWBossBar() {
    }

    public static void sendBossBar(Player player, String message, float progress, BarColor color) {
        if (!player.isOnline()) return;

        BossBar bossBar = ACTIVE_BARS.computeIfAbsent(player.getUniqueId(),
                uuid -> Bukkit.createBossBar(message, color, BarStyle.SEGMENTED_20));
        bossBar.setTitle(message);
        bossBar.setColor(color);
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
        if (!bossBar.isVisible()) bossBar.setVisible(true);
    }

    public static void removeBossBar(Player player) {
        BossBar bossBar = ACTIVE_BARS.remove(player.getUniqueId());
        if (bossBar != null) bossBar.removePlayer(player);
    }

    public static void removeAll() {
        ACTIVE_BARS.values().forEach(BossBar::removeAll);
        ACTIVE_BARS.clear();
    }
}
