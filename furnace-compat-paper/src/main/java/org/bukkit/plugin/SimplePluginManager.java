package org.bukkit.plugin;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Server;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.compat.paper.BukkitPluginAdapter;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatPluginManager;
import org.domi.furnace.compat.paper.EventBridge;
import org.domi.furnace.compat.paper.ListenerFallback;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Bukkit 의 PluginManager 구현(Paper 1.21.1 처럼 {@code @Deprecated(forRemoval = true)} 이지만 그대로 있다). 서버(Furnace 인스턴스)마다 하나이고
 * {@code Bukkit.getPluginManager()} 가 이것을 준다 — 플러그인이 리플렉션으로 읽는 필드({@code commandMap}·{@code permissions} 등)도 Bukkit 이름 그대로 있다.
 *
 * <ul>
 *   <li>플러그인 목록·켜기·끄기는 Furnace 플러그인 등록소의 것이다(따로 목록을 두지 않는다). 그래서 Paper 처럼 {@code plugins}·{@code lookupNames} 필드는 비어 있다.</li>
 *   <li>권한 노드는 Furnace 권한 등록소에 산다. {@code permissions} 필드는 그 등록소를 Bukkit {@link Permission} 으로 보이는 살아 있는 지도이고,
 *       같은 노드는 같은 Bukkit 객체로 보인다(자식을 고친 뒤 recalculatePermissibles 하면 등록소에 반영된다).</li>
 *   <li>구독(subscribe)은 Bukkit 처럼 기억하고, 거기에 "지금 접속한 플레이어·콘솔 가운데 그 권한 값이 정해진 대상"을 더해 돌려준다 —
 *       Bukkit 의 PermissibleBase 가 계산할 때마다 스스로 구독하는 것과 같은 결과다.</li>
 * </ul>
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("removal")
public final class SimplePluginManager implements PluginManager {

    private final Server server;
    /** Paper 처럼 비어 있다 — 실제 목록은 Furnace 플러그인 등록소. */
    private final List<Plugin> plugins = new ArrayList<>();
    /** Paper 처럼 비어 있다. */
    private final Map<String, Plugin> lookupNames = new HashMap<>();
    private File updateDirectory;
    /** helper 등이 리플렉션으로 읽는다(Paper 에도 그대로 있다). */
    private final SimpleCommandMap commandMap;
    // Paper 처럼 public — 권한 플러그인이 리플렉션으로 읽는다
    public final Map<String, Permission> permissions = new PermissionView();
    public final Map<Boolean, Set<Permission>> defaultPerms = new DefaultsView();
    public final Map<String, Map<Permissible, Boolean>> permSubs = new HashMap<>();
    public final Map<Boolean, Map<Permissible, Boolean>> defSubs = new HashMap<>();
    private boolean useTimings = false;

    /** Furnace 노드 → 그것을 보이는 Bukkit 객체. 노드가 바뀌면(다른 객체) 새로 만든다. */
    private final ConcurrentHashMap<String, Wrapped> wrapped = new ConcurrentHashMap<>();

    private record Wrapped(org.domi.furnace.api.permission.Permission node, Permission bukkit) {}

    public SimplePluginManager(Server instance, SimpleCommandMap commandMap) {
        this.server = instance;
        this.commandMap = commandMap;
    }

    private static Furnace furnace() { return Compat.furnace(); }

    // ------------------------------------------------------------------ 플러그인 목록

    /** Paper 처럼 공백은 밑줄로 바꾸고 대소문자 없이 찾는다. 이름이 없으면 plugin.yml 의 provides 로도 찾는다. */
    @Override
    public Plugin getPlugin(String name) {
        String n = name.replace(' ', '_');
        FurnacePlugin p = furnace().plugins().plugin(n);
        if (p instanceof BukkitPluginAdapter a && a.javaPlugin() != null) return a.javaPlugin();
        if (p != null) return null;   // Furnace 플러그인 — Bukkit Plugin 이 아니다
        for (FurnacePlugin fp : furnace().plugins().plugins()) {
            if (fp instanceof BukkitPluginAdapter a && a.javaPlugin() != null) {
                for (String provided : a.bukkitDescription().getProvides()) {
                    if (provided.equalsIgnoreCase(n)) return a.javaPlugin();
                }
            }
        }
        return null;
    }

    @Override
    public Plugin[] getPlugins() {
        List<Plugin> out = new ArrayList<>();
        for (FurnacePlugin p : furnace().plugins().plugins()) if (p instanceof BukkitPluginAdapter a && a.javaPlugin() != null) out.add(a.javaPlugin());
        return out.toArray(new Plugin[0]);
    }

    /** Bukkit 플러그인이면 그 플러그인의 켜짐, 아니면 같은 이름의 Furnace 플러그인의 켜짐. */
    @Override
    public boolean isPluginEnabled(String name) {
        if (name == null) return false;
        Plugin p = getPlugin(name);
        if (p != null) return isPluginEnabled(p);
        return furnace().plugins().isEnabled(name.replace(' ', '_'));
    }

    /** 이 서버가 불러온 플러그인일 때만 그 켜짐(Bukkit 처럼 모르는 Plugin 객체는 false). */
    @Override
    public boolean isPluginEnabled(Plugin plugin) {
        return plugin != null && CompatPluginManager.adapterOrNull(plugin) != null && plugin.isEnabled();
    }

    /** Furnace 는 서버가 도는 중에 새 jar 를 불러오지 않는다(개발 모드의 /plugins reload 만) — Bukkit 이 불러오기 실패에 쓰는 예외로 알린다. */
    @Override
    public Plugin loadPlugin(File file) throws InvalidPluginException {
        if (file == null) throw new IllegalArgumentException("File cannot be null");
        throw new InvalidPluginException("Furnace 는 서버가 도는 중에 플러그인 jar 를 새로 불러오지 않는다(" + file
                + ") — plugins/ 에 두고 서버를 다시 켜거나, 개발 모드에서 /plugins reload <이름> 을 쓴다");
    }

    /** Paper 처럼 오류는 던지지 않고 로그에 남긴다 — 불러온 것이 없으니 빈 배열. */
    @Override
    public Plugin[] loadPlugins(File directory) {
        if (directory == null || !directory.isDirectory()) throw new IllegalArgumentException("Directory must be a directory");
        server.getLogger().warning("Furnace 는 서버가 도는 중에 플러그인을 새로 불러오지 않는다 — loadPlugins(" + directory + ") 는 아무것도 불러오지 않았다");
        return new Plugin[0];
    }

    @Override
    public Plugin[] loadPlugins(File[] files) {
        if (files == null) throw new IllegalArgumentException("File list cannot be null");
        if (files.length > 0) server.getLogger().warning("Furnace 는 서버가 도는 중에 플러그인을 새로 불러오지 않는다 — loadPlugins 는 아무것도 불러오지 않았다");
        return new Plugin[0];
    }

    /** Bukkit 플러그인을 켠 순서의 반대로 끈다. */
    @Override
    public void disablePlugins() {
        Plugin[] all = getPlugins();
        for (int i = all.length - 1; i >= 0; i--) disablePlugin(all[i]);
    }

    @Override
    public void clearPlugins() {
        throw new UnsupportedFeature("PluginManager#clearPlugins", "Furnace 에는 /reload 처럼 플러그인 목록을 비우는 일이 없다 — disablePlugins 로 끌 수는 있다");
    }

    /** 꺼진 플러그인을 켠다(Bukkit 처럼 plugin.yml 명령을 다시 올리고 onEnable·PluginEnableEvent). 켜져 있으면 아무 일도 없다. */
    @Override
    public void enablePlugin(Plugin plugin) {
        if (plugin.isEnabled()) return;
        furnace().plugins().enable(CompatPluginManager.adapter(plugin));
    }

    @Override
    public void disablePlugin(Plugin plugin) {
        if (!(plugin instanceof JavaPlugin)) throw new IllegalArgumentException("Only expects java plugins.");
        if (!plugin.isEnabled()) return;
        furnace().plugins().disable(CompatPluginManager.adapter(plugin));
    }

    // ------------------------------------------------------------------ 이벤트

    /**
     * Bukkit 이벤트를 Bukkit 처리기에게 돌린다(플러그인이 직접 만든 이벤트). Furnace 이벤트 버스에는 나가지 않는다 —
     * Furnace 가 발화하는 이벤트는 EventBridge 가 Bukkit 이벤트로 바꿔 같은 처리기에 전달한다.
     * Paper 처럼 비동기 이벤트를 주 스레드에서 부르면 IllegalStateException. 반대(동기 이벤트를 주 스레드 밖에서)는 막지 않는다 —
     * Furnace 의 이벤트는 region 스레드에서 오므로 그 안에서 부르는 사용자 이벤트가 모두 막히기 때문이다.
     */
    @Override
    public void callEvent(Event event) {
        if (event.isAsynchronous() && server.isPrimaryThread()) {
            throw new IllegalStateException(event.getEventName() + " may only be triggered asynchronously.");
        }
        for (RegisteredListener rl : event.getHandlers().getRegisteredListeners()) {
            if (!rl.getPlugin().isEnabled()) continue;
            try {
                rl.callEvent(event);
            } catch (Throwable ex) {
                server.getLogger().log(Level.SEVERE, "Could not pass event " + event.getEventName() + " to " + rl.getPlugin().getDescription().getFullName(), ex);
            }
        }
    }

    @Override
    public void registerEvents(Listener listener, Plugin plugin) {
        if (!plugin.isEnabled()) throw new IllegalPluginAccessException("Plugin attempted to register " + listener + " while not enabled");
        Set<Method> methods = new LinkedHashSet<>();
        try {
            Collections.addAll(methods, listener.getClass().getMethods());
            Collections.addAll(methods, listener.getClass().getDeclaredMethods());
        } catch (NoClassDefFoundError e) {
            // 대개 호환 어댑터에 없는 이벤트 클래스다. Paper 는 그 리스너 클래스를 통째로 버리지만(Paper 에는 그 클래스가 있어 일어나지 않는 일),
            // 여기서는 처리기 메서드를 하나씩 풀어 풀리는 것만 등록한다(T-040a) — 없는 클래스를 쓰는 처리기 하나가 같은 클래스의 다른 처리기를 죽이지 않게
            registerEventsPerMethod(listener, plugin, e);
            return;
        }
        for (Method method : methods) {
            EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            if (method.isBridge() || method.isSynthetic()) continue;
            Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().severe(plugin.getDescription().getFullName() + " attempted to register an invalid EventHandler method signature \""
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

    /** getMethods 가 NoClassDefFoundError 로 실패한 리스너: @EventHandler 메서드를 하나씩 풀어 풀리는 것만 등록하고, 건너뛴 것은 이름·없는 클래스를 경고에 남긴다. */
    private void registerEventsPerMethod(Listener listener, Plugin plugin, NoClassDefFoundError cause) {
        ListenerFallback.Result r;
        try {
            r = ListenerFallback.collect(listener.getClass());
        } catch (Throwable t) {
            plugin.getLogger().severe("Failed to register events for " + listener.getClass() + " because " + cause.getMessage() + " does not exist."
                    + " (Furnace 호환 어댑터가 지원하는 이벤트 목록: docs/api/compat-paper.md)");
            return;
        }
        for (String bad : r.invalid()) {
            plugin.getLogger().severe(plugin.getDescription().getFullName() + " attempted to register an invalid EventHandler method signature \"" + bad
                    + "\" in " + listener.getClass());
        }
        int registered = 0;
        for (ListenerFallback.Handler h : r.handlers()) {
            MethodHandle handle = h.handle();
            Class<? extends Event> eventClass = h.event();
            EventExecutor executor = (l, event) -> {
                try {
                    if (!eventClass.isAssignableFrom(event.getClass())) return;
                    handle.invokeExact((Object) l, event);
                } catch (Throwable t) {
                    throw new EventException(t);
                }
            };
            registerEvent(eventClass, listener, h.priority(), executor, plugin, h.ignoreCancelled());
            registered++;
        }
        if (!r.skipped().isEmpty()) {
            StringBuilder sb = new StringBuilder("Failed to register ").append(r.skipped().size()).append(" event handler(s) of ").append(listener.getClass())
                    .append(" because the class does not exist:");
            for (ListenerFallback.Skipped s : r.skipped()) sb.append(" ").append(s.method()).append(" -> ").append(s.missingClass()).append(";");
            sb.append(" the other ").append(registered).append(" handler(s) of the listener were registered."
                    + " (Furnace 호환 어댑터가 지원하는 이벤트 목록: docs/api/compat-paper.md)");
            plugin.getLogger().warning(sb.toString());
        } else if (r.handlers().isEmpty() && r.invalid().isEmpty()) {
            plugin.getLogger().severe("Failed to register events for " + listener.getClass() + " because " + cause.getMessage() + " does not exist."
                    + " (Furnace 호환 어댑터가 지원하는 이벤트 목록: docs/api/compat-paper.md)");
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
        BukkitPluginAdapter owner = CompatPluginManager.adapter(plugin);
        Class<? extends Event> owning = CompatPluginManager.registrationClass(event);
        RegisteredListener registered = new RegisteredListener(listener, executor, priority, plugin, ignoreCancelled);
        CompatPluginManager.handlerList(owning).register(registered);   // 버스 구독은 HandlerList 가 만들고 해제 때 끊는다(직접 register 한 처리기도 같게)
        EventBridge.warnIfNotFired(owner, owning);
    }

    // ------------------------------------------------------------------ 권한

    @Override
    public Permission getPermission(String name) { return permissions.get(name.toLowerCase(Locale.ROOT)); }

    @Override
    public void addPermission(Permission perm) {
        String name = perm.getName().toLowerCase(Locale.ROOT);
        synchronized (this) {
            if (permissions.containsKey(name)) throw new IllegalArgumentException("The permission " + name + " is already defined!");
            permissions.put(name, perm);
        }
    }

    /** Paper: 여러 개를 올린다(하나가 이미 있으면 거기서 IllegalArgumentException — 앞의 것은 올라간 채다). */
    @Override
    public void addPermissions(List<Permission> perms) {
        for (Permission p : perms) addPermission(p);
    }

    @Override
    public Set<Permission> getDefaultPermissions(boolean op) { return Collections.unmodifiableSet(new LinkedHashSet<>(defaultPerms.get(op))); }

    @Override
    public void removePermission(Permission perm) { removePermission(perm.getName()); }

    @Override
    public void removePermission(String name) { permissions.remove(name.toLowerCase(Locale.ROOT)); }

    /**
     * Bukkit 에서는 기본값 묶음을 다시 계산한다. 여기서는 그 객체가 이 노드를 보이는 객체일 때 고친 기본값·자식·설명을 Furnace 등록소에 옮긴다
     * (권한 값은 다음 확인 때 저절로 다시 계산된다). 같은 이름의 다른 객체나 올리지 않은 객체는 무시한다.
     */
    @Override
    public void recalculatePermissionDefaults(Permission perm) {
        if (perm == null) return;   // Paper 도 null 을 받는다
        String name = perm.getName().toLowerCase(Locale.ROOT);
        synchronized (this) {
            Wrapped w = wrapped.get(name);
            if (w == null || w.bukkit() != perm) return;
            if (furnace().permissions().permission(name) == null) {   // 그 사이 Furnace 쪽에서 지워졌다
                wrapped.remove(name);
                return;
            }
            org.domi.furnace.api.permission.Permission node = toFurnace(perm);
            if (!node.equals(w.node())) {
                furnace().permissions().replace(node);
                wrapped.put(name, new Wrapped(node, perm));
            }
        }
    }

    @Override
    public void subscribeToPermission(String permission, Permissible permissible) {
        synchronized (permSubs) {
            permSubs.computeIfAbsent(permission.toLowerCase(Locale.ROOT), k -> new WeakHashMap<>()).put(permissible, true);
        }
    }

    @Override
    public void unsubscribeFromPermission(String permission, Permissible permissible) {
        String name = permission.toLowerCase(Locale.ROOT);
        synchronized (permSubs) {
            Map<Permissible, Boolean> map = permSubs.get(name);
            if (map != null) {
                map.remove(permissible);
                if (map.isEmpty()) permSubs.remove(name);
            }
        }
    }

    /** 구독한 대상 + 지금 접속한 플레이어·콘솔 가운데 그 권한 값이 정해진(등록 기본값·자식·첨부로) 대상. */
    @Override
    public Set<Permissible> getPermissionSubscriptions(String permission) {
        String name = permission.toLowerCase(Locale.ROOT);
        Set<Permissible> out = new LinkedHashSet<>();
        synchronized (permSubs) {
            Map<Permissible, Boolean> map = permSubs.get(name);
            if (map != null) out.addAll(map.keySet());
        }
        for (Player p : server.getOnlinePlayers()) if (p.isPermissionSet(name)) out.add(p);
        if (furnace().console().isPermissionSet(name)) out.add(server.getConsoleSender());
        return Collections.unmodifiableSet(out);
    }

    @Override
    public void subscribeToDefaultPerms(boolean op, Permissible permissible) {
        synchronized (defSubs) {
            defSubs.computeIfAbsent(op, k -> new WeakHashMap<>()).put(permissible, true);
        }
    }

    @Override
    public void unsubscribeFromDefaultPerms(boolean op, Permissible permissible) {
        synchronized (defSubs) {
            Map<Permissible, Boolean> map = defSubs.get(op);
            if (map != null) {
                map.remove(permissible);
                if (map.isEmpty()) defSubs.remove(op);
            }
        }
    }

    /** 구독한 대상 + 지금 접속한 플레이어·콘솔 가운데 OP 여부가 같은 대상(Bukkit 은 권한을 계산할 때마다 자기 OP 쪽에 구독한다). */
    @Override
    public Set<Permissible> getDefaultPermSubscriptions(boolean op) {
        Set<Permissible> out = new LinkedHashSet<>();
        synchronized (defSubs) {
            Map<Permissible, Boolean> map = defSubs.get(op);
            if (map != null) out.addAll(map.keySet());
        }
        for (Player p : server.getOnlinePlayers()) if (p.isOp() == op) out.add(p);
        if (op) out.add(server.getConsoleSender());
        return Collections.unmodifiableSet(out);
    }

    @Override
    public Set<Permission> getPermissions() { return new HashSet<>(permissions.values()); }

    /** Paper: 모든 권한 노드를 지운다(Furnace 플러그인이 올린 것도 — Paper 도 모두 지운다). */
    @Override
    public void clearPermissions() { permissions.clear(); }

    @Override
    public boolean useTimings() { return useTimings; }

    public void useTimings(boolean use) { useTimings = use; }

    // ------------------------------------------------------------------ Bukkit Permission ↔ Furnace 노드

    private static org.domi.furnace.api.permission.Permission toFurnace(Permission p) {
        return new org.domi.furnace.api.permission.Permission(p.getName(), p.getDescription(),
                org.domi.furnace.api.permission.PermissionDefault.valueOf(p.getDefault().name()), new java.util.LinkedHashMap<>(p.getChildren()));
    }

    /** 이름(소문자)의 노드를 보이는 Bukkit 객체. 노드가 없으면 null. */
    private Permission lookup(String name) {
        org.domi.furnace.api.permission.Permission node = furnace().permissions().permission(name);
        if (node == null) {
            wrapped.remove(name);
            return null;
        }
        Wrapped w = wrapped.get(name);
        if (w != null && w.node() == node) return w.bukkit();
        synchronized (this) {
            w = wrapped.get(name);
            if (w != null && w.node() == node) return w.bukkit();
            Permission b = new Permission(node.name(), node.description(), PermissionDefault.valueOf(node.defaultValue().name()), node.children());
            wrapped.put(name, new Wrapped(node, b));
            return b;
        }
    }

    /** {@code permissions} 필드: Furnace 권한 등록소를 Bukkit 객체로 보인다. 넣기·지우기는 등록소에 반영된다. */
    private final class PermissionView extends AbstractMap<String, Permission> {
        @Override
        public Permission get(Object key) { return key instanceof String s ? lookup(s.toLowerCase(Locale.ROOT)) : null; }

        @Override
        public boolean containsKey(Object key) { return key instanceof String s && furnace().permissions().permission(s) != null; }

        @Override
        public Permission put(String key, Permission value) {
            if (key == null || value == null) throw new NullPointerException();
            String name = key.toLowerCase(Locale.ROOT);
            synchronized (SimplePluginManager.this) {
                Permission old = lookup(name);
                org.domi.furnace.api.permission.Permission node = toFurnace(value);
                if (!node.name().equals(name)) {   // 키와 이름이 다르다(리플렉션으로 넣을 때만) — 키 이름의 노드로 올린다
                    node = new org.domi.furnace.api.permission.Permission(name, node.description(), node.defaultValue(), node.children());
                }
                furnace().permissions().replace(node);
                wrapped.put(name, new Wrapped(node, value));
                return old;
            }
        }

        @Override
        public Permission remove(Object key) {
            if (!(key instanceof String s)) return null;
            String name = s.toLowerCase(Locale.ROOT);
            synchronized (SimplePluginManager.this) {
                Permission old = lookup(name);
                if (old != null) furnace().permissions().unregister(name);
                wrapped.remove(name);
                return old;
            }
        }

        @Override
        public int size() { return furnace().permissions().permissions().size(); }

        @Override
        public Set<Entry<String, Permission>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() { return PermissionView.this.size(); }

                @Override
                public Iterator<Entry<String, Permission>> iterator() {
                    List<Entry<String, Permission>> snapshot = new ArrayList<>();
                    for (org.domi.furnace.api.permission.Permission n : furnace().permissions().permissions()) {
                        Permission b = lookup(n.name());
                        if (b != null) snapshot.add(new SimpleImmutableEntry<>(n.name(), b));
                    }
                    Iterator<Entry<String, Permission>> it = snapshot.iterator();
                    return new Iterator<>() {
                        private Entry<String, Permission> last;

                        @Override
                        public boolean hasNext() { return it.hasNext(); }

                        @Override
                        public Entry<String, Permission> next() { return last = it.next(); }

                        @Override
                        public void remove() {
                            if (last == null) throw new IllegalStateException();
                            PermissionView.this.remove(last.getKey());
                            last = null;
                        }
                    };
                }
            };
        }
    }

    /** {@code defaultPerms} 필드: OP(true)·OP 아님(false) 각각 기본값으로 갖는 권한(Bukkit 의 계산과 같은 묶음). 읽기 전용. */
    private final class DefaultsView extends AbstractMap<Boolean, Set<Permission>> {
        @Override
        public Set<Permission> get(Object key) {
            if (!(key instanceof Boolean op)) return null;
            Set<Permission> out = new LinkedHashSet<>();
            for (Permission p : permissions.values()) if (p.getDefault().getValue(op)) out.add(p);
            return Collections.unmodifiableSet(out);
        }

        @Override
        public boolean containsKey(Object key) { return key instanceof Boolean; }

        @Override
        public Set<Entry<Boolean, Set<Permission>>> entrySet() {
            return Set.of(new SimpleImmutableEntry<>(true, get(true)), new SimpleImmutableEntry<>(false, get(false)));
        }
    }
}
