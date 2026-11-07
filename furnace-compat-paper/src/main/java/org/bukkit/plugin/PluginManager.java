package org.bukkit.plugin;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** 플러그인 관리·이벤트 등록. 권한 등록·서비스는 H2/H4. */
public interface PluginManager {

    Plugin getPlugin(String name);

    Plugin[] getPlugins();

    /** Bukkit 플러그인뿐 아니라 Furnace 플러그인도 이름으로 켜짐 여부를 본다. */
    boolean isPluginEnabled(String name);

    boolean isPluginEnabled(Plugin plugin);

    void callEvent(Event event) throws IllegalStateException;

    void registerEvents(Listener listener, Plugin plugin);

    void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin);

    void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin,
                       boolean ignoreCancelled);

    void enablePlugin(Plugin plugin);

    void disablePlugin(Plugin plugin);
}
