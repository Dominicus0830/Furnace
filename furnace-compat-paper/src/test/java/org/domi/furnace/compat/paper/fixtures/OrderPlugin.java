package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 우선순위·ignoreCancelled·취소 전달을 보는 플러그인(시험용). */
public final class OrderPlugin extends JavaPlugin implements Listener {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();

    @Override
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @EventHandler(priority = EventPriority.MONITOR) public void monitor(PlayerToggleSneakEvent e) { LOG.add("MONITOR cancelled=" + e.isCancelled()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void highestSkipCancelled(PlayerToggleSneakEvent e) { LOG.add("HIGHEST(ignoreCancelled)"); }

    @EventHandler(priority = EventPriority.HIGH) public void high(PlayerToggleSneakEvent e) {
        LOG.add("HIGH cancelled=" + e.isCancelled());
        e.setCancelled(true);
    }

    @EventHandler public void normal(PlayerToggleSneakEvent e) { LOG.add("NORMAL cancelled=" + e.isCancelled()); }

    @EventHandler(priority = EventPriority.LOW) public void low(PlayerToggleSneakEvent e) { LOG.add("LOW cancelled=" + e.isCancelled()); }

    @EventHandler(priority = EventPriority.LOWEST) public void lowest(PlayerToggleSneakEvent e) { LOG.add("LOWEST cancelled=" + e.isCancelled()); }
}
