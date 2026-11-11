// Furnace 이벤트 → Bukkit 이벤트 다리.
//
// Bukkit 처리기가 등록되면(SimplePluginManager.registerEvent) 그 Bukkit 이벤트에 대응하는 Furnace 이벤트를 (플러그인, 우선순위) 마다 한 번씩
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
        /** 전달 직전에 Furnace 이벤트의 현재 값을 Bukkit 이벤트로 옮긴다(앞선 처리기들이 바꾼 값을 보이고, 되돌려 쓰기가 낡은 값을 쓰지 않게). */
        private final BiConsumer<F, B> pull;
        /** Bukkit 이벤트가 Furnace 의 "취소"로 보이는 값과 그 반대 방향. 보통은 Cancellable 그대로, PlayerInteractEvent 처럼 결과가 둘인 이벤트만 따로 정한다. */
        private final java.util.function.Predicate<B> cancelOf;
        private final BiConsumer<B, Boolean> showCancel;
        private final Map<F, Object> live = Collections.synchronizedMap(new WeakHashMap<>());

        Mapping(Class<B> bukkit, Class<F> furnace, Function<F, B> create, BiConsumer<F, B> push, BiConsumer<F, B> pull) {
            this(bukkit, furnace, create, push, pull, b -> ((org.bukkit.event.Cancellable) b).isCancelled(), (b, c) -> ((org.bukkit.event.Cancellable) b).setCancelled(c));
        }

        Mapping(Class<B> bukkit, Class<F> furnace, Function<F, B> create, BiConsumer<F, B> push, BiConsumer<F, B> pull,
                java.util.function.Predicate<B> cancelOf, BiConsumer<B, Boolean> showCancel) {
            this.bukkit = bukkit;
            this.furnace = furnace;
            this.create = create;
            this.push = push;
            this.pull = pull;
            this.cancelOf = cancelOf;
            this.showCancel = showCancel;
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

    /** Bukkit 처리기 목록 클래스 → 짝들(Furnace 이벤트 여럿이 한 Bukkit 이벤트로 올 수 있다 — 예: 몹 사망·플레이어 사망 → EntityDeathEvent 목록). */
    private static final Map<Class<?>, java.util.List<Mapping<?, ?>>> BY_BUKKIT = new HashMap<>();

    static {
        Mappings.registerAll();
    }

    private EventBridge() {}

    /** 값을 되돌려 쓰지 않는 이벤트(취소 상태만 오간다). */
    static <F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> void reg(Class<F> furnace, Class<B> bukkit,
                                                                                                    Function<F, B> create, BiConsumer<F, B> push) {
        add(new Mapping<>(bukkit, furnace, create, push, (f, b) -> {}));
    }

    /**
     * 값을 되돌려 쓰는 이벤트. pull 은 전달 직전에 Furnace 의 현재 값을 Bukkit 이벤트에 맞추고, push 는 전달 뒤 Bukkit 이벤트가 그 값과 달라졌을 때만
     * Furnace 에 쓴다 — 사이에 낀 Furnace 고유 처리기의 변경을 낡은 값으로 덮어쓰지 않는다.
     */
    static <F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> void reg(Class<F> furnace, Class<B> bukkit,
                                                                                                    Function<F, B> create, BiConsumer<F, B> push, BiConsumer<F, B> pull) {
        add(new Mapping<>(bukkit, furnace, create, push, pull));
    }

    /**
     * 취소 규칙이 Bukkit Cancellable 의 isCancelled/setCancelled 와 다른 이벤트. cancelOf 는 Bukkit 이벤트 상태를 Furnace 의 취소 여부로 읽고,
     * showCancel 은 Furnace 의 취소 여부를 Bukkit 이벤트에 보인다(전달 직전, 값이 다를 때만 부른다).
     */
    static <F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> void reg(Class<F> furnace, Class<B> bukkit,
                                                                                                    Function<F, B> create, BiConsumer<F, B> push, BiConsumer<F, B> pull,
                                                                                                    java.util.function.Predicate<B> cancelOf, BiConsumer<B, Boolean> showCancel) {
        add(new Mapping<>(bukkit, furnace, create, push, pull, cancelOf, showCancel));
    }

    /**
     * 호환 계층에 클래스는 있지만 Furnace 서버가 아직 알리지 않는 Bukkit 서버 이벤트. 플러그인이 처리기를 달면 등록은 되고(그 리스너의 다른 처리기가
     * 살도록) 처리기가 불리지 않는다는 경고를 플러그인·이벤트마다 한 번 남긴다 — 조용히 아무 일도 안 일어나는 것처럼 보이지 않게.
     */
    private static final java.util.Set<String> NOT_FIRED = java.util.Set.of(
            "org.bukkit.event.block.BlockCanBuildEvent", "org.bukkit.event.block.BlockDamageEvent", "org.bukkit.event.block.BlockDispenseEvent",
            "org.bukkit.event.block.BlockExpEvent", "org.bukkit.event.block.NotePlayEvent", "org.bukkit.event.block.SignChangeEvent",
            "org.bukkit.event.block.BlockPlaceEvent", "org.bukkit.event.block.BlockFadeEvent", "org.bukkit.event.block.BlockGrowEvent",
            "org.bukkit.event.block.BlockExplodeEvent", "org.bukkit.event.entity.EntityChangeBlockEvent", "org.bukkit.event.entity.EntitySpawnEvent",
            "org.bukkit.event.player.PlayerCommandSendEvent", "org.bukkit.event.player.PlayerDropItemEvent", "org.bukkit.event.world.WorldInitEvent");

    /** 서버가 아직 알리지 않는 Bukkit 이벤트인가(문서·시험용). */
    public static boolean isKnownButNotFired(Class<? extends org.bukkit.event.Event> owning) { return NOT_FIRED.contains(owning.getName()); }

    private static void add(Mapping<?, ?> m) {
        BY_BUKKIT.computeIfAbsent(m.bukkit, k -> new java.util.ArrayList<>(1)).add(m);
    }

    /** 이 Bukkit 이벤트를 받을 수 있는가(Furnace 이벤트와 이어졌는가). 플러그인이 직접 만든 이벤트는 false. */
    public static boolean isBridged(Class<? extends org.bukkit.event.Event> owning) { return BY_BUKKIT.containsKey(owning); }

    /** 지원하는 Bukkit 이벤트 클래스(문서·시험용). */
    public static java.util.Set<Class<?>> bridgedEvents() { return java.util.Set.copyOf(BY_BUKKIT.keySet()); }

    /** owner 가 이 Bukkit 이벤트를 이 우선순위로 받도록 Furnace 버스에 구독한다(한 번만). */
    public static void ensure(BukkitPluginAdapter owner, Class<? extends org.bukkit.event.Event> owning, org.bukkit.event.EventPriority priority) {
        var ms = BY_BUKKIT.get(owning);
        if (ms == null) {
            if (NOT_FIRED.contains(owning.getName()) && owner.markBridged("unfired:" + owning.getName())) {
                owner.logger().warning(owning.getSimpleName() + " 처리기를 등록했지만 Furnace 서버는 아직 이 이벤트를 알리지 않는다 — 그 처리기는 불리지 않는다"
                        + " (지원 목록: docs/api/compat-paper.md)");
            }
            return;   // 플러그인 자기 이벤트 — callEvent 로만 불린다
        }
        for (Mapping<?, ?> m : ms) {
            if (!owner.markBridged(owning.getName() + "<" + m.furnace.getName() + "@" + priority.name())) continue;
            subscribe(owner, m, priority);
        }
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
        m.pull.accept(f, b);
        org.domi.furnace.api.event.Cancellable fc = f instanceof org.domi.furnace.api.event.Cancellable c ? c : null;
        org.bukkit.event.Cancellable bc = b instanceof org.bukkit.event.Cancellable c ? c : null;
        if (fc != null && bc != null && m.cancelOf.test(b) != fc.isCancelled()) m.showCancel.accept(b, fc.isCancelled());   // 앞선 Furnace 처리기의 결과를 먼저 보인다
        var jp = owner.javaPlugin();
        boolean ran = false;
        // 전역 스레드에서 오는 이벤트(명령 등)는 Bukkit 에서 주 스레드 이벤트다 — 리스너가 블록·엔티티를 만지면 region 을 빌린다(다른 스레드는 빈 손잡이)
        try (var access = Furnace.server().scheduler().global().openRegionAccess()) {
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
        }
        if (!ran) return;
        m.push.accept(f, b);
        if (fc != null && bc != null && m.cancelOf.test(b) != fc.isCancelled()) fc.setCancelled(m.cancelOf.test(b));
    }
}
