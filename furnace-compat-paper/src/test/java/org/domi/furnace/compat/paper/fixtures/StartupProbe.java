package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** load: STARTUP·POSTWORLD 켜기 순서와 기본 월드 WorldInit·WorldLoad 를 적는 플러그인(시험용). */
public final class StartupProbe extends JavaPlugin implements Listener {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();

    /** Paper 는 모든 플러그인의 onLoad 를 월드를 만들기 전에 부른다 — 0·false 여야 한다. */
    @Override
    public void onLoad() { LOG.add(getName() + ":onload worlds=" + Bukkit.getWorlds().size() + " byName=" + (Bukkit.getWorld("world") != null)); }

    @Override
    public void onEnable() {
        LOG.add(getName() + ":enable worlds=" + Bukkit.getWorlds().size() + " byName=" + (Bukkit.getWorld("world") != null));
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void init(WorldInitEvent e) { LOG.add(getName() + ":init " + e.getWorld().getName() + " visible=" + Bukkit.getWorlds().size()); }

    @EventHandler
    public void load(WorldLoadEvent e) { LOG.add(getName() + ":load " + e.getWorld().getName() + " visible=" + Bukkit.getWorlds().size()); }
}
