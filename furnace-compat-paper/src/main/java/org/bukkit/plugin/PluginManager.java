package org.bukkit.plugin;

import java.io.File;
import java.util.List;
import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;

/** 플러그인 관리·이벤트·권한(Bukkit PluginManager + Paper 가 더한 addPermissions·clearPermissions). 구현은 {@link SimplePluginManager}. */
public interface PluginManager {

    Plugin getPlugin(String name);

    Plugin[] getPlugins();

    /** Bukkit 플러그인뿐 아니라 Furnace 플러그인도 이름으로 켜짐 여부를 본다. */
    boolean isPluginEnabled(String name);

    boolean isPluginEnabled(Plugin plugin);

    Plugin loadPlugin(File file) throws InvalidPluginException, InvalidDescriptionException, UnknownDependencyException;

    Plugin[] loadPlugins(File directory);

    Plugin[] loadPlugins(File[] files);

    void disablePlugins();

    void clearPlugins();

    void callEvent(Event event) throws IllegalStateException;

    void registerEvents(Listener listener, Plugin plugin);

    void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin);

    void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin,
                       boolean ignoreCancelled);

    void enablePlugin(Plugin plugin);

    void disablePlugin(Plugin plugin);

    // ------------------------------------------------------------------ 권한

    Permission getPermission(String name);

    void addPermission(Permission perm);

    void removePermission(Permission perm);

    void removePermission(String name);

    Set<Permission> getDefaultPermissions(boolean op);

    void recalculatePermissionDefaults(Permission perm);

    void subscribeToPermission(String permission, Permissible permissible);

    void unsubscribeFromPermission(String permission, Permissible permissible);

    Set<Permissible> getPermissionSubscriptions(String permission);

    void subscribeToDefaultPerms(boolean op, Permissible permissible);

    void unsubscribeFromDefaultPerms(boolean op, Permissible permissible);

    Set<Permissible> getDefaultPermSubscriptions(boolean op);

    Set<Permission> getPermissions();

    /** Paper: 여러 권한을 한 번에 올린다. */
    void addPermissions(List<Permission> perm);

    /** Paper: 모든 권한을 지운다. */
    void clearPermissions();

    boolean useTimings();
}
