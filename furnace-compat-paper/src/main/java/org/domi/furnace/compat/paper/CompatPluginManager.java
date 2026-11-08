package org.domi.furnace.compat.paper;

import java.lang.reflect.Method;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;

/**
 * Bukkit 플러그인 관리의 Furnace 쪽 손잡이: Bukkit {@link Plugin} → 그것을 감싼 Furnace 껍데기, 이벤트의 처리기 목록 찾기.
 * Bukkit 에 보이는 PluginManager 는 {@link org.bukkit.plugin.SimplePluginManager}(서버마다 하나, {@link Compat#pluginManager()}).
 */
public final class CompatPluginManager {

    private CompatPluginManager() {}

    /** 이 서버가 불러온 Bukkit 플러그인의 껍데기. 모르는 플러그인이면 IllegalPluginAccessException. */
    public static BukkitPluginAdapter adapter(Plugin plugin) {
        BukkitPluginAdapter a = adapterOrNull(plugin);
        if (a == null) throw new IllegalPluginAccessException("Furnace 호환 어댑터가 모르는 플러그인이다: " + plugin);
        return a;
    }

    /** 이 서버가 불러온 Bukkit 플러그인의 껍데기, 아니면 null. */
    public static BukkitPluginAdapter adapterOrNull(Plugin plugin) {
        if (plugin instanceof JavaPlugin jp) {
            FurnacePlugin p = Furnace.server().plugins().plugin(jp.getName());
            if (p instanceof BukkitPluginAdapter a && a.javaPlugin() == jp) return a;
        }
        return null;
    }

    /** 이벤트 클래스의 정적 getHandlerList(). */
    public static HandlerList handlerList(Class<? extends Event> owning) {
        try {
            Method m = owning.getDeclaredMethod("getHandlerList");
            m.setAccessible(true);
            return (HandlerList) m.invoke(null);
        } catch (Exception e) {
            throw new IllegalPluginAccessException(e.toString());
        }
    }

    /** 정적 getHandlerList() 를 가진 가장 가까운 상위 클래스(Bukkit 규칙). */
    public static Class<? extends Event> registrationClass(Class<?> clazz) {
        try {
            clazz.getDeclaredMethod("getHandlerList");
            return clazz.asSubclass(Event.class);
        } catch (NoSuchMethodException e) {
            Class<?> sup = clazz.getSuperclass();
            if (sup != null && !sup.equals(Event.class) && Event.class.isAssignableFrom(sup)) return registrationClass(sup);
            throw new IllegalPluginAccessException("Unable to find handler list for event " + clazz.getName() + ". Static getHandlerList method required!");
        }
    }
}
