package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** 플러그인이 직접 만든 이벤트를 callEvent 로 돌린다(시험용). */
public final class CustomEventPlugin extends JavaPlugin implements Listener {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();

    public static final class Ping extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;
        public final String text;

        public Ping(String text) { this.text = text; }

        @Override
        public HandlerList getHandlers() { return HANDLERS; }

        public static HandlerList getHandlerList() { return HANDLERS; }

        @Override
        public boolean isCancelled() { return cancelled; }

        @Override
        public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    }

    @Override
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @EventHandler(priority = EventPriority.HIGH) public void high(Ping e) {
        LOG.add("high:" + e.text);
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW) public void low(Ping e) { LOG.add("low:" + e.text); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void monitorIgnoring(Ping e) { LOG.add("monitor-ignoring:" + e.text); }

    @EventHandler(priority = EventPriority.MONITOR) public void monitor(Ping e) { LOG.add("monitor:" + e.text + ":" + e.isCancelled()); }
}
