// Furnace 이벤트 → Bukkit 이벤트 다리.
//
// Bukkit 처리기가 등록되면(CompatPluginManager.registerEvent) 그 Bukkit 이벤트에 대응하는 Furnace 이벤트를 (플러그인, 우선순위) 마다 한 번씩
// Furnace 이벤트 버스에 구독한다. 구독은 그 플러그인의 것이라 코어가 플러그인을 끌 때 알아서 지우고, 프로파일러·MONITOR 검사도 그 플러그인으로 잡힌다.
// Furnace 이벤트 하나에 Bukkit 이벤트 객체는 하나만 만들어 플러그인·우선순위가 다른 구독들이 나눠 쓴다(약한 참조 — 이벤트가 끝나면 사라진다).
// 우선순위 순서는 Furnace 버스가 지킨다: 같은 이벤트를 Furnace 플러그인이 같은 우선순위로 구독했다면 등록 순이다.
package org.domi.furnace.compat.paper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import org.bukkit.event.EventException;
import org.bukkit.plugin.RegisteredListener;
import org.domi.furnace.api.Furnace;

public final class EventBridge {

    private static final Object SKIP = new Object();

    /** Bukkit 이벤트 ↔ Furnace 이벤트 짝. */
    static final class Mapping<F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> {
        final Class<B> bukkit;
        final Class<F> furnace;
        private final Function<F, B> create;
        private final BiConsumer<F, B> push;
        private final Map<F, Object> live = Collections.synchronizedMap(new WeakHashMap<>());

        Mapping(Class<B> bukkit, Class<F> furnace, Function<F, B> create, BiConsumer<F, B> push) {
            this.bukkit = bukkit;
            this.furnace = furnace;
            this.create = create;
            this.push = push;
        }

        /** 이 Furnace 이벤트의 Bukkit 이벤트(처음 부르면 만든다). 건너뛰는 이벤트면 null. */
        @SuppressWarnings("unchecked")
        B bukkitFor(F f) {
            synchronized (live) {
                Object o = live.get(f);
                if (o == null) {
                    B b = create.apply(f);
                    live.put(f, b == null ? SKIP : b);
                    return b;
                }
                return o == SKIP ? null : (B) o;
            }
        }
    }

    private static final Map<Class<?>, Mapping<?, ?>> BY_BUKKIT = new HashMap<>();

    static {
        Mappings.registerAll();
    }

    private EventBridge() {}

    static <F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> void reg(Class<F> furnace, Class<B> bukkit,
                                                                                                    Function<F, B> create, BiConsumer<F, B> push) {
        BY_BUKKIT.put(bukkit, new Mapping<>(bukkit, furnace, create, push));
    }

    /** 이 Bukkit 이벤트를 받을 수 있는가(Furnace 이벤트와 이어졌는가). 플러그인이 직접 만든 이벤트는 false. */
    public static boolean isBridged(Class<? extends org.bukkit.event.Event> owning) { return BY_BUKKIT.containsKey(owning); }

    /** 지원하는 Bukkit 이벤트 클래스(문서·시험용). */
    public static java.util.Set<Class<?>> bridgedEvents() { return java.util.Set.copyOf(BY_BUKKIT.keySet()); }

    /** owner 가 이 Bukkit 이벤트를 이 우선순위로 받도록 Furnace 버스에 구독한다(한 번만). */
    static void ensure(BukkitPluginAdapter owner, Class<? extends org.bukkit.event.Event> owning, org.bukkit.event.EventPriority priority) {
        Mapping<?, ?> m = BY_BUKKIT.get(owning);
        if (m == null) return;   // 플러그인 자기 이벤트 — callEvent 로만 불린다
        if (!owner.markBridged(owning.getName() + "@" + priority.name())) return;
        subscribe(owner, m, priority);
    }

    private static <F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> void subscribe(BukkitPluginAdapter owner, Mapping<F, B> m,
                                                                                                                 org.bukkit.event.EventPriority priority) {
        var fp = org.domi.furnace.api.event.EventPriority.valueOf(priority.name());
        Furnace.server().events().subscribe(owner, m.furnace, fp, false, f -> deliver(owner, m, priority, f));
    }

    private static <F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> void deliver(BukkitPluginAdapter owner, Mapping<F, B> m,
                                                                                                                org.bukkit.event.EventPriority priority, F f) {
        B b = m.bukkitFor(f);
        if (b == null) return;
        org.domi.furnace.api.event.Cancellable fc = f instanceof org.domi.furnace.api.event.Cancellable c ? c : null;
        org.bukkit.event.Cancellable bc = b instanceof org.bukkit.event.Cancellable c ? c : null;
        if (fc != null && bc != null && bc.isCancelled() != fc.isCancelled()) bc.setCancelled(fc.isCancelled());   // 앞선 Furnace 처리기의 결과를 먼저 보인다
        var jp = owner.javaPlugin();
        boolean ran = false;
        for (RegisteredListener rl : b.getHandlers().getRegisteredListeners()) {
            if (rl.getPlugin() != jp || rl.getPriority() != priority) continue;
            ran = true;
            try {
                rl.callEvent(b);
            } catch (Throwable t) {
                Throwable cause = t instanceof EventException ee && ee.getCause() != null ? ee.getCause() : t;
                owner.logger().log(Level.SEVERE, "Could not pass event " + b.getEventName() + " to " + owner.bukkitDescription().getFullName(), cause);
            }
        }
        if (!ran) return;
        m.push.accept(f, b);
        if (fc != null && bc != null && bc.isCancelled() != fc.isCancelled()) fc.setCancelled(bc.isCancelled());
    }
}
