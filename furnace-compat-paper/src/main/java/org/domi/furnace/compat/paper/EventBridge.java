// Furnace 이벤트 → Bukkit 이벤트 다리.
//
// Bukkit 처리기가 등록되면(SimplePluginManager.registerEvent) 그 Bukkit 이벤트에 대응하는 Furnace 이벤트를 처리기마다 한 번씩
// Furnace 이벤트 버스에 구독한다(A15-07). 구독은 그 플러그인의 것이라 코어가 플러그인을 끌 때 알아서 지우고, 프로파일러·MONITOR 검사도 그 플러그인으로 잡힌다.
// 처리기마다 구독하는 까닭: Bukkit HandlerList 는 같은 우선순위 안에서 플러그인을 가리지 않고 등록 순으로 부른다 — 플러그인 A 가 B 보다 먼저 켜진 뒤
// 처리기를 더 달아도 A1 → B1 → A2 순서가 지켜져야 한다. 버스는 (우선순위, 구독 순서)로 부르고 구독은 처리기를 등록하는 그 자리에서 하므로 두 순서가 같다.
// Furnace 이벤트 하나에 Bukkit 이벤트 객체는 짝(Mapping)마다 하나만 만들어 플러그인·우선순위가 다른 구독들이 나눠 쓴다. 그 객체는 Furnace 이벤트의
// 붙임 칸(EventSlot, 짝마다 열쇠 하나)에 붙어 이벤트와 함께 사라진다 — 전역 맵·잠금이 없어 region 스레드들이 같은 종류를 동시에 내도 줄 서지 않는다(T-032).
// 우선순위 순서는 Furnace 버스가 지킨다: 같은 이벤트를 Furnace 플러그인이 같은 우선순위로 구독했다면 등록 순이다.
package org.domi.furnace.compat.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import org.bukkit.event.EventException;
import org.bukkit.plugin.RegisteredListener;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.event.EventSlot;

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
        /** 이 짝이 만든 Bukkit 이벤트(또는 SKIP)를 Furnace 이벤트에 붙이는 칸. 한 Furnace 이벤트가 여러 짝으로 갈 수 있어(예: 사망) 짝마다 따로다. */
        private final EventSlot<Object> live;

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
            this.live = new EventSlot<>(furnace.getSimpleName() + "->" + bukkit.getSimpleName());
        }

        /**
         * 이 Furnace 이벤트의 Bukkit 이벤트(처음 부르면 만든다). 건너뛰는 이벤트면 null. 같은 Furnace 이벤트에는 늘 같은 객체를 돌려준다
         * (두 스레드가 처음 부르기를 겨루면 만들기는 둘 다 돌 수 있지만 먼저 붙은 객체 하나를 둘 다 받는다).
         */
        @SuppressWarnings("unchecked")
        B bukkitFor(F f) {
            Object o = live.computeIfAbsent(f, this::make);
            return o == SKIP ? null : (B) o;
        }

        private Object make(F f) {
            B b = create.apply(f);
            return b == null ? SKIP : b;
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
            "org.bukkit.event.player.PlayerCommandSendEvent", "org.bukkit.event.world.WorldInitEvent");

    /** 서버가 아직 알리지 않는 Bukkit 이벤트인가(문서·시험용). */
    public static boolean isKnownButNotFired(Class<? extends org.bukkit.event.Event> owning) { return NOT_FIRED.contains(owning.getName()); }

    private static void add(Mapping<?, ?> m) {
        BY_BUKKIT.computeIfAbsent(m.bukkit, k -> new java.util.ArrayList<>(1)).add(m);
    }

    /** 이 Bukkit 이벤트를 받을 수 있는가(Furnace 이벤트와 이어졌는가). 플러그인이 직접 만든 이벤트는 false. */
    public static boolean isBridged(Class<? extends org.bukkit.event.Event> owning) { return BY_BUKKIT.containsKey(owning); }

    /** 지원하는 Bukkit 이벤트 클래스(문서·시험용). */
    public static java.util.Set<Class<?>> bridgedEvents() { return java.util.Set.copyOf(BY_BUKKIT.keySet()); }

    /** 같은 HandlerList 를 쓰는 Bukkit 이벤트 클래스들(그 클래스가 getHandlerList 를 직접 가진 것만) — 처음 쓸 때 한 번 만든다. */
    private static volatile Map<org.bukkit.event.HandlerList, java.util.List<Class<? extends org.bukkit.event.Event>>> owners;

    private static Map<org.bukkit.event.HandlerList, java.util.List<Class<? extends org.bukkit.event.Event>>> owners() {
        var m = owners;
        if (m == null) {
            synchronized (EventBridge.class) {
                m = owners;
                if (m == null) {
                    m = new java.util.IdentityHashMap<>();
                    for (Class<?> raw : BY_BUKKIT.keySet()) {
                        Class<? extends org.bukkit.event.Event> c = raw.asSubclass(org.bukkit.event.Event.class);
                        try {
                            m.computeIfAbsent(CompatPluginManager.handlerList(c), k -> new java.util.ArrayList<>(1)).add(c);
                        } catch (RuntimeException notDeclared) {
                            // getHandlerList 를 직접 갖지 않는 클래스 — 부모 목록으로 등록되므로 부모 쪽에서 잡힌다
                        }
                    }
                    owners = m;
                }
            }
        }
        return m;
    }

    /**
     * HandlerList 에 처리기 하나가 등록될 때 Furnace 버스에 이어 준다(처리기마다 구독 하나 — 같은 우선순위 순서가 등록 순이 되도록).
     * 돌려준 구독은 그 처리기가 해제될 때 HandlerList 가 끊는다. 이 호환 계층의 플러그인이 아니면 이을 것이 없다.
     */
    public static java.util.List<org.domi.furnace.api.event.EventSubscription> attach(org.bukkit.event.HandlerList list, org.bukkit.plugin.RegisteredListener registered) {
        BukkitPluginAdapter owner;
        try {
            owner = CompatPluginManager.adapterOrNull(registered.getPlugin());
        } catch (RuntimeException noServer) {
            return java.util.List.of();   // 서버가 없는 환경(HandlerList 단독 사용)
        }
        var classes = owner == null ? null : owners().get(list);
        if (classes == null) return java.util.List.of();
        java.util.List<org.domi.furnace.api.event.EventSubscription> out = new java.util.ArrayList<>();
        for (Class<? extends org.bukkit.event.Event> owning : classes) out.addAll(ensure(owner, owning, registered.getPriority(), registered));
        if (registered.getPriority() == org.bukkit.event.EventPriority.MONITOR && classes.contains(org.bukkit.event.player.PlayerQuitEvent.class)) requeueQuitWatch();
        return out;
    }

    /** 서버가 알리지 않는 이벤트의 처리기를 등록하면 플러그인·이벤트마다 한 번 경고한다. */
    public static void warnIfNotFired(BukkitPluginAdapter owner, Class<? extends org.bukkit.event.Event> owning) {
        // WorldInitEvent 는 load: STARTUP 플러그인을 켜는 동안 단 처리기만 기본 월드 몫을 받는다(Compat.revealWorldsAfterStartup) — 그때는 경고하지 않는다
        if (owning == org.bukkit.event.world.WorldInitEvent.class && Compat.startupWorldsPending()) return;
        if (!BY_BUKKIT.containsKey(owning) && NOT_FIRED.contains(owning.getName()) && owner.markBridged("unfired:" + owning.getName())) {
            owner.logger().warning(owning.getSimpleName() + " 처리기를 등록했지만 Furnace 서버는 아직 이 이벤트를 알리지 않는다 — 그 처리기는 불리지 않는다"
                    + " (지원 목록: docs/api/compat-paper.md)");
        }
    }

    private static java.util.List<org.domi.furnace.api.event.EventSubscription> ensure(BukkitPluginAdapter owner, Class<? extends org.bukkit.event.Event> owning,
                                                                                        org.bukkit.event.EventPriority priority, org.bukkit.plugin.RegisteredListener registered) {
        var ms = BY_BUKKIT.get(owning);
        if (ms == null) return java.util.List.of();   // 플러그인 자기 이벤트 — callEvent 로만 불린다
        java.util.List<org.domi.furnace.api.event.EventSubscription> subs = new java.util.ArrayList<>(ms.size());
        for (Mapping<?, ?> m : ms) subs.add(subscribe(owner, m, priority, registered));
        // 로그인 거부가 확정되면(Furnace MONITOR — 모든 처리기 뒤) 그 접속용으로 만든 CraftPlayer 를 걷는다: 권한 구독·플레이어 표에 남지 않게(PR #210 검토 R4)
        if (owning == org.bukkit.event.player.PlayerLoginEvent.class && owner.markBridged("login-denied-cleanup")) {
            Furnace.server().events().subscribe(owner, org.domi.furnace.api.event.player.PlayerCreateEvent.class,
                    org.domi.furnace.api.event.EventPriority.MONITOR, false, f -> { if (!f.isAllowed()) Compat.forgetDenied(f.player()); });
        }
        watchQuit(owner);
        return subs;
    }

    /** 플러그인별 퇴장 정리 구독(권한 구독·가상 채널을 걷는 것). 같은 우선순위에서는 등록 순으로 불리므로, Bukkit 퇴장 MONITOR 처리기가 이어질 때마다 다시 걸어 맨 뒤에 둔다. */
    private static final java.util.Map<BukkitPluginAdapter, org.domi.furnace.api.event.EventSubscription> QUIT_WATCH = new java.util.IdentityHashMap<>();

    private static org.domi.furnace.api.event.EventSubscription subscribeQuitWatch(BukkitPluginAdapter owner) {
        return Furnace.server().events().subscribe(owner, org.domi.furnace.api.event.player.PlayerQuitEvent.class,
                org.domi.furnace.api.event.EventPriority.MONITOR, false, f -> Compat.disconnected(f.player()));
    }

    /**
     * 접속이 끝나면(Furnace MONITOR) NMS 연결의 가상 채널을 닫고 그 접속의 Bukkit 권한 계산·구독을 걷는다(Paper 퇴장 때 perm.clearPermissions) —
     * 플러그인이 이벤트를 하나도 안 들어도(권한만 묻는 플러그인) 걸리도록 플러그인을 켤 때와 처리기를 이을 때 모두 부른다(플러그인별로 한 번 —
     * 닫기·정리는 멱등이고, 구독은 그 플러그인이 꺼지면 같이 풀린다).
     * Bukkit 퇴장 처리기들이 모두 본 뒤에 돌도록 {@link #requeueQuitWatch} 가 처리기가 이어질 때마다 맨 뒤로 옮긴다.
     */
    static synchronized void watchQuit(BukkitPluginAdapter owner) {
        if (!QUIT_WATCH.containsKey(owner)) QUIT_WATCH.put(owner, subscribeQuitWatch(owner));
    }

    /** 플러그인이 꺼진다 — 그 플러그인의 퇴장 정리 구독을 뗀다. */
    static synchronized void unwatchQuit(BukkitPluginAdapter owner) {
        var sub = QUIT_WATCH.remove(owner);
        if (sub != null) {
            try {
                sub.unsubscribe();
            } catch (RuntimeException ignored) {
                // 서버가 이미 내려갔다
            }
        }
    }

    /** 퇴장 MONITOR 처리기가 이어졌다 — 모든 퇴장 정리 구독을 떼었다 다시 걸어 같은 우선순위 맨 뒤(= 모든 Bukkit 처리기 뒤, Paper 순서)로 보낸다. */
    private static synchronized void requeueQuitWatch() {
        for (var it = QUIT_WATCH.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            var jp = e.getKey().javaPlugin();
            if (jp != null && !jp.isEnabled()) {   // 꺼졌는데 못 뗀 칸(서버를 다시 띄운 시험 등)
                it.remove();
                continue;
            }
            try {
                var old = e.getValue();
                e.setValue(subscribeQuitWatch(e.getKey()));   // 새 구독을 먼저 건 뒤 옛것을 뗀다 — 그 사이 퇴장이 와도 정리가 빠지지 않는다(정리는 멱등)
                old.unsubscribe();
            } catch (RuntimeException gone) {
                it.remove();   // 내려간 서버의 낡은 칸
            }
        }
    }

    private static <F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> org.domi.furnace.api.event.EventSubscription subscribe(
            BukkitPluginAdapter owner, Mapping<F, B> m, org.bukkit.event.EventPriority priority, org.bukkit.plugin.RegisteredListener registered) {
        var fp = org.domi.furnace.api.event.EventPriority.valueOf(priority.name());
        return Furnace.server().events().subscribe(owner, m.furnace, fp, false, f -> deliver(owner, m, registered, f));
    }

    private static <F extends org.domi.furnace.api.event.Event, B extends org.bukkit.event.Event> void deliver(BukkitPluginAdapter owner, Mapping<F, B> m,
                                                                                                                org.bukkit.plugin.RegisteredListener rl, F f) {
        B b = m.bukkitFor(f);
        if (b == null) return;
        if (!b.getHandlers().isRegistered(rl)) return;   // 그 사이 해제된 처리기(HandlerList.unregister·플러그인 끄기 직전)
        m.pull.accept(f, b);
        org.domi.furnace.api.event.Cancellable fc = f instanceof org.domi.furnace.api.event.Cancellable c ? c : null;
        org.bukkit.event.Cancellable bc = b instanceof org.bukkit.event.Cancellable c ? c : null;
        if (fc != null && bc != null && m.cancelOf.test(b) != fc.isCancelled()) m.showCancel.accept(b, fc.isCancelled());   // 앞선 Furnace 처리기의 결과를 먼저 보인다
        // 전역 스레드에서 오는 이벤트(명령 등)는 Bukkit 에서 주 스레드 이벤트다 — 리스너가 블록·엔티티를 만지면 region 을 빌린다(다른 스레드는 빈 손잡이)
        try (var access = Furnace.server().scheduler().global().openRegionAccess()) {
            try {
                rl.callEvent(b);
            } catch (Throwable t) {
                Throwable cause = t instanceof EventException ee && ee.getCause() != null ? ee.getCause() : t;
                owner.logger().log(Level.SEVERE, "Could not pass event " + b.getEventName() + " to " + owner.bukkitDescription().getFullName(), cause);
            }
        }
        m.push.accept(f, b);
        if (fc != null && bc != null && m.cancelOf.test(b) != fc.isCancelled()) fc.setCancelled(m.cancelOf.test(b));
    }
}
