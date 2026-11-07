package org.domi.furnace.compat.paper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;

/** Bukkit PluginManager — 이벤트 등록·호출은 직접, 플러그인 목록은 Furnace 의 것을 본다. */
public final class CompatPluginManager implements PluginManager {

    public static final CompatPluginManager INSTANCE = new CompatPluginManager();

    private CompatPluginManager() {}

    // ------------------------------------------------------------------ 플러그인 목록

    @Override
    public Plugin getPlugin(String name) {
        FurnacePlugin p = Furnace.server().plugins().plugin(name);
        return p instanceof BukkitPluginAdapter a ? a.javaPlugin() : null;
    }

    @Override
    public Plugin[] getPlugins() {
        List<Plugin> out = new ArrayList<>();
        for (FurnacePlugin p : Furnace.server().plugins().plugins()) if (p instanceof BukkitPluginAdapter a && a.javaPlugin() != null) out.add(a.javaPlugin());
        return out.toArray(new Plugin[0]);
    }

    @Override
    public boolean isPluginEnabled(String name) { return name != null && Furnace.server().plugins().isEnabled(name); }

    @Override
    public boolean isPluginEnabled(Plugin plugin) { return plugin != null && plugin.isEnabled(); }

    @Override
    public void enablePlugin(Plugin plugin) {
        if (plugin.isEnabled()) return;
        throw new UnsupportedFeature("PluginManager#enablePlugin", "Furnace 는 꺼진 플러그인을 다시 켜는 API 가 없다(개발 모드의 /plugins reload <이름>)");
    }

    @Override
    public void disablePlugin(Plugin plugin) {
        if (plugin instanceof JavaPlugin jp) {
            BukkitPluginAdapter a = adapter(jp);
            Furnace.server().plugins().disable(a);
        } else {
            throw new IllegalPluginAccessException("Furnace 가 관리하는 Bukkit 플러그인이 아니다: " + plugin);
        }
    }

    static BukkitPluginAdapter adapter(Plugin plugin) {
        if (plugin instanceof JavaPlugin jp) {
            FurnacePlugin p = Furnace.server().plugins().plugin(jp.getName());
            if (p instanceof BukkitPluginAdapter a && a.javaPlugin() == jp) return a;
        }
        throw new IllegalPluginAccessException("Furnace 호환 어댑터가 모르는 플러그인이다: " + plugin);
    }

    // ------------------------------------------------------------------ 이벤트 등록

    @Override
    public void registerEvents(Listener listener, Plugin plugin) {
        if (!plugin.isEnabled()) throw new IllegalPluginAccessException("Plugin attempted to register " + listener + " while not enabled");
        Set<Method> methods = new LinkedHashSet<>();
        try {
            for (Method m : listener.getClass().getMethods()) methods.add(m);
            for (Method m : listener.getClass().getDeclaredMethods()) methods.add(m);
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().log(Level.SEVERE, "Plugin " + plugin.getDescription().getFullName() + " 의 리스너 " + listener.getClass().getName()
                    + " 를 등록하지 못했다 — Furnace 호환 어댑터에 없는 클래스를 쓴다: " + e.getMessage()
                    + " (지원하는 이벤트 목록: docs/api/compat-paper.md)", e);
            throw e;
        }
        for (Method method : methods) {
            EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            if (method.isBridge() || method.isSynthetic()) continue;
            Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().severe("Plugin " + plugin.getDescription().getFullName() + " attempted to register an invalid EventHandler method signature \""
                        + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }
            Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);
            EventExecutor executor = (l, event) -> {
                try {
                    if (!eventClass.isAssignableFrom(event.getClass())) return;
                    method.invoke(l, event);
                } catch (InvocationTargetException ex) {
                    throw new EventException(ex.getCause());
                } catch (Throwable t) {
                    throw new EventException(t);
                }
            };
            registerEvent(eventClass, listener, eh.priority(), executor, plugin, eh.ignoreCancelled());
        }
    }

    @Override
    public void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin) {
        registerEvent(event, listener, priority, executor, plugin, false);
    }

    @Override
    public void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin,
                              boolean ignoreCancelled) {
        if (listener == null || priority == null || executor == null || plugin == null) throw new IllegalArgumentException("registerEvent 인자가 null");
        if (!plugin.isEnabled()) throw new IllegalPluginAccessException("Plugin attempted to register " + event + " while not enabled");
        BukkitPluginAdapter owner = adapter(plugin);
        Class<? extends Event> owning = registrationClass(event);
        handlerList(owning).register(new RegisteredListener(listener, executor, priority, plugin, ignoreCancelled));
        EventBridge.ensure(owner, owning, priority);
    }

    private static HandlerList handlerList(Class<? extends Event> owning) {
        try {
            Method m = owning.getDeclaredMethod("getHandlerList");
            m.setAccessible(true);
            return (HandlerList) m.invoke(null);
        } catch (Exception e) {
            throw new IllegalPluginAccessException(e.toString());
        }
    }

    /** 정적 getHandlerList() 를 가진 가장 가까운 상위 클래스(Bukkit 규칙). */
    static Class<? extends Event> registrationClass(Class<?> clazz) {
        try {
            clazz.getDeclaredMethod("getHandlerList");
            return clazz.asSubclass(Event.class);
        } catch (NoSuchMethodException e) {
            Class<?> sup = clazz.getSuperclass();
            if (sup != null && !sup.equals(Event.class) && Event.class.isAssignableFrom(sup)) return registrationClass(sup);
            throw new IllegalPluginAccessException("Unable to find handler list for event " + clazz.getName() + ". Static getHandlerList method required!");
        }
    }

    // ------------------------------------------------------------------ 이벤트 호출

    /**
     * Bukkit 이벤트를 Bukkit 처리기에게만 돌린다(플러그인이 직접 만든 이벤트). Furnace 이벤트 버스에는 나가지 않는다 —
     * Furnace 가 발화하는 이벤트는 EventBridge 가 Bukkit 이벤트로 바꿔 같은 처리기에 전달한다.
     */
    @Override
    public void callEvent(Event event) {
        for (RegisteredListener rl : event.getHandlers().getRegisteredListeners()) {
            if (!rl.getPlugin().isEnabled()) continue;
            try {
                rl.callEvent(event);
            } catch (Throwable ex) {
                rl.getPlugin().getLogger().log(Level.SEVERE, "Could not pass event " + event.getEventName() + " to "
                        + rl.getPlugin().getDescription().getFullName(), ex instanceof EventException ee && ee.getCause() != null ? ee.getCause() : ex);
            }
        }
    }
}
