// 클래스로더 → 그 클래스로더로 올라온 Bukkit 플러그인. JavaPlugin.getProvidingPlugin / getPlugin, 직접 상속한 Command 의 소유 플러그인 찾기가 쓴다
// (Bukkit 은 PluginClassLoader 가 플러그인을 들고 있지만 Furnace 의 클래스로더는 호환 모듈을 모른다).
// 정적 지도라서 클래스로더(키)와 어댑터(값) 모두 약하게 든다 — 값이 키를 강하게 잡으면 꺼지거나 켜진 적 없는 플러그인의 클래스로더가 영영 남는다.
// 어댑터는 서버가 들고 있는 동안 살아 있고(꺼진 뒤에도 서버 목록에 있으면 getProvidingPlugin 이 Bukkit 처럼 꺼진 플러그인을 돌려준다), 버려지면 저절로 풀린다.
package org.domi.furnace.compat.paper;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class PluginLoaders {

    private static final Map<ClassLoader, WeakReference<BukkitPluginAdapter>> BY_LOADER = Collections.synchronizedMap(new WeakHashMap<>());

    private PluginLoaders() {}

    /** 플러그인의 클래스로더를 묶는다. */
    static void bind(ClassLoader loader, BukkitPluginAdapter adapter) { BY_LOADER.put(loader, new WeakReference<>(adapter)); }

    /** 만들다 실패한 플러그인처럼 서버에 올라가지 못한 어댑터의 묶임을 바로 푼다. */
    static void unbind(BukkitPluginAdapter adapter) {
        synchronized (BY_LOADER) {
            BY_LOADER.values().removeIf(r -> r.get() == null || r.get() == adapter);
        }
    }

    /** 이 클래스로더로 올라온 플러그인(없으면 null). */
    public static BukkitPluginAdapter of(ClassLoader loader) {
        if (loader == null) return null;
        WeakReference<BukkitPluginAdapter> r = BY_LOADER.get(loader);
        return r == null ? null : r.get();
    }
}
