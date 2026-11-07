// 클래스로더 → 그 클래스로더로 올라온 Bukkit 플러그인. JavaPlugin.getProvidingPlugin / getPlugin, 직접 상속한 Command 의 소유 플러그인 찾기가 쓴다
// (Bukkit 은 PluginClassLoader 가 플러그인을 들고 있지만 Furnace 의 클래스로더는 호환 모듈을 모른다).
package org.domi.furnace.compat.paper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginLoaders {

    private static final Map<ClassLoader, BukkitPluginAdapter> BY_LOADER = new ConcurrentHashMap<>();

    private PluginLoaders() {}

    /** 플러그인의 클래스로더를 묶는다. 꺼질 때 어댑터가 {@link #unbind} 한다. */
    static void bind(ClassLoader loader, BukkitPluginAdapter adapter) { BY_LOADER.put(loader, adapter); }

    static void unbind(BukkitPluginAdapter adapter) { BY_LOADER.values().removeIf(a -> a == adapter); }

    /** 이 클래스로더로 올라온 플러그인(없으면 null). */
    public static BukkitPluginAdapter of(ClassLoader loader) { return loader == null ? null : BY_LOADER.get(loader); }
}
