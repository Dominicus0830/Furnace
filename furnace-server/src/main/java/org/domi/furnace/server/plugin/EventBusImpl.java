// 이벤트 버스 구현. 등록 목록은 타입별로 두고, 발행할 구체 클래스마다 (상위 타입 포함) 처리기 배열을 미리 구워 둔다 —
// 등록이 바뀌면 구운 배열만 버린다. 발행은 배열 한 번 훑기라 잠금·할당이 없다.
package org.domi.furnace.server.plugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventBus;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.event.Listener;
import org.domi.furnace.api.event.Subscribe;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EventBusImpl implements EventBus {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    private static final Handler[] NONE = new Handler[0];

    /** 등록된 처리기 하나. owner 는 리스너 객체(람다 등록이면 null). */
    private record Handler(FurnacePlugin plugin, Class<?> type, EventPriority priority, boolean ignoreCancelled, Consumer<Event> fn,
                           Object owner, long order, String where) {}

    private final ConcurrentHashMap<Class<?>, List<Handler>> byType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Handler[]> baked = new ConcurrentHashMap<>();
    private final AtomicLong order = new AtomicLong();
    /** 호출 통계. null 이면 꺼진 상태(발행 경로에는 null 검사 하나만 남는다). */
    private final Stats stats;

    public EventBusImpl() { this(Stats.enabledByConfig()); }

    EventBusImpl(boolean statsOn) { this.stats = statsOn ? new Stats() : null; }

    @Override
    public void register(FurnacePlugin plugin, Listener listener) {
        Set<Method> methods = new HashSet<>();
        for (Class<?> c = listener.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) if (m.isAnnotationPresent(Subscribe.class)) methods.add(m);
        }
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Method m : methods) {
            Subscribe s = m.getAnnotation(Subscribe.class);
            if (m.getParameterCount() != 1 || !Event.class.isAssignableFrom(m.getParameterTypes()[0]) || Modifier.isStatic(m.getModifiers())) {
                log.error("[{}] @Subscribe 메서드는 Event 하나를 받는 인스턴스 메서드여야 한다: {}", plugin.name(), m);
                continue;
            }
            MethodHandle mh;
            try {
                m.setAccessible(true);
                mh = lookup.unreflect(m).bindTo(listener);
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.error("[{}] 리스너 메서드에 접근할 수 없다: {}", plugin.name(), m, e);
                continue;
            }
            MethodHandle target = mh;
            Consumer<Event> fn = e -> {
                try {
                    target.invoke(e);
                } catch (RuntimeException | Error ex) {
                    throw ex;
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            };
            add(new Handler(plugin, m.getParameterTypes()[0], s.priority(), s.ignoreCancelled(), fn, listener, order.incrementAndGet(),
                    listener.getClass().getName() + "." + m.getName()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event> EventSubscription subscribe(FurnacePlugin plugin, Class<E> type, EventPriority priority, boolean ignoreCancelled,
                                                         Consumer<? super E> handler) {
        Handler h = new Handler(plugin, type, priority, ignoreCancelled, e -> handler.accept((E) e), null, order.incrementAndGet(),
                handler.getClass().getName());
        add(h);
        return () -> remove(x -> x == h);
    }

    private void add(Handler h) {
        byType.computeIfAbsent(h.type(), k -> new CopyOnWriteArrayList<>()).add(h);
        baked.clear();
    }

    private void remove(java.util.function.Predicate<Handler> p) {
        boolean any = false;
        for (List<Handler> l : byType.values()) any |= l.removeIf(p);
        if (any) baked.clear();
    }

    @Override
    public void unregister(Listener listener) { remove(h -> h.owner() == listener); }

    @Override
    public void unregisterAll(FurnacePlugin plugin) { remove(h -> h.plugin() == plugin); }

    @Override
    public boolean hasSubscribers(Class<? extends Event> type) { return handlers(type).length > 0; }

    private Handler[] handlers(Class<?> type) {
        Handler[] hs = baked.get(type);
        // computeIfAbsent: 등록이 바뀌어 baked.clear() 되는 사이에 낡은 목록을 구워 넣는 경합을 막는다(clear 는 같은 칸 잠금을 기다린다)
        return hs != null ? hs : baked.computeIfAbsent(type, this::bake);
    }

    /** 이 클래스와 모든 상위 클래스·인터페이스에 등록된 처리기를 우선순위·등록 순서로. */
    private Handler[] bake(Class<?> type) {
        List<Handler> out = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        Deque<Class<?>> todo = new ArrayDeque<>();
        todo.add(type);
        while (!todo.isEmpty()) {
            Class<?> c = todo.poll();
            if (c == null || !seen.add(c)) continue;
            List<Handler> l = byType.get(c);
            if (l != null) out.addAll(l);
            if (c.getSuperclass() != null) todo.add(c.getSuperclass());
            for (Class<?> i : c.getInterfaces()) todo.add(i);
        }
        if (out.isEmpty()) return NONE;
        out.sort(Comparator.comparing(Handler::priority).thenComparingLong(Handler::order));
        return out.toArray(NONE);
    }

    @Override
    public <E extends Event> E call(E event) {
        Handler[] hs = handlers(event.getClass());
        if (stats != null) return callTimed(event, hs);
        for (Handler h : hs) {
            if (h.ignoreCancelled() && event instanceof Cancellable c && c.isCancelled()) continue;
            if (!h.plugin().isEnabled()) continue;
            invoke(h, event);
        }
        return event;
    }

    private void invoke(Handler h, Event event) {
        try {
            h.fn().accept(event);
        } catch (Throwable t) {
            // Bukkit: "Could not pass event X to Plugin vY"
            String who;
            try {
                who = h.plugin().description().fullName();
            } catch (RuntimeException ex) {   // 설명이 없는 플러그인 객체여도 로그 때문에 발행이 끊기면 안 된다
                who = h.plugin().getClass().getName();
            }
            log.error("Could not pass event {} to {}", event.eventName(), who, t);
        }
    }

    /** 통계가 켜졌을 때의 발행 경로. 처리 순서·취소·예외 처리는 call 과 같고, 시간만 잰다. */
    private <E extends Event> E callTimed(E event, Handler[] hs) {
        long start = System.nanoTime();
        Handler slow = null;
        long slowNs = -1;
        for (Handler h : hs) {
            if (h.ignoreCancelled() && event instanceof Cancellable c && c.isCancelled()) continue;
            if (!h.plugin().isEnabled()) continue;
            long t0 = System.nanoTime();
            invoke(h, event);
            long dt = System.nanoTime() - t0;
            stats.listener(h, dt);
            if (dt > slowNs) { slowNs = dt; slow = h; }
        }
        long total = System.nanoTime() - start;
        stats.record(event.getClass(), total);
        if (total > Stats.SLOW_NS) {
            log.warn("[tickstats] 이벤트 {} 처리에 {} ms 걸렸다 (가장 느린 리스너: {})", event.eventName(), Stats.ms(total),
                    slow == null ? "없음" : slow.plugin().name() + " " + slow.where());
        }
        return event;
    }

    // 테스트 전용 조회
    long statCount(Class<?> type) { return stats == null ? 0 : stats.count(type); }
    long statTotalNs(Class<?> type) { return stats == null ? 0 : stats.totalNs(type); }
    long statMaxNs(Class<?> type) { return stats == null ? 0 : stats.maxNs(type); }

    /** 이벤트 종류별 호출 통계와 리스너별 누적 시간. 발행 스레드들이 동시에 쓰므로 LongAdder·ConcurrentHashMap 을 쓴다. */
    private static final class Stats {
        static final long SLOW_NS = 20_000_000L;
        private static final long REPORT_PERIOD_S = 30;

        private static final class TypeStat {
            final LongAdder count = new LongAdder();
            final LongAdder totalNs = new LongAdder();
            final AtomicLong maxNs = new AtomicLong();
        }

        private final ConcurrentHashMap<Class<?>, TypeStat> types = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Handler, LongAdder> listeners = new ConcurrentHashMap<>();

        Stats() {
            ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "furnace-event-stats");
                t.setDaemon(true);
                return t;
            });
            ses.scheduleAtFixedRate(this::reportSafely, REPORT_PERIOD_S, REPORT_PERIOD_S, TimeUnit.SECONDS);
        }

        /** 켜짐 설정: -Dfurnace.tickstats=true 또는 환경변수 FURNACE_TICKSTATS 가 있을 때. */
        static boolean enabledByConfig() {
            return Boolean.getBoolean("furnace.tickstats") || System.getenv("FURNACE_TICKSTATS") != null;
        }

        static String ms(long ns) { return String.format(Locale.ROOT, "%.2f", ns / 1e6); }

        void record(Class<?> type, long ns) {
            TypeStat t = types.computeIfAbsent(type, k -> new TypeStat());
            t.count.increment();
            t.totalNs.add(ns);
            t.maxNs.accumulateAndGet(ns, Math::max);
        }

        void listener(Handler h, long ns) {
            listeners.computeIfAbsent(h, k -> new LongAdder()).add(ns);
        }

        long count(Class<?> type) { TypeStat t = types.get(type); return t == null ? 0 : t.count.sum(); }
        long totalNs(Class<?> type) { TypeStat t = types.get(type); return t == null ? 0 : t.totalNs.sum(); }
        long maxNs(Class<?> type) { TypeStat t = types.get(type); return t == null ? 0 : t.maxNs.get(); }

        private void reportSafely() {
            try {
                report();
            } catch (Throwable t) {
                log.warn("[tickstats] 통계 출력 실패", t);
            }
        }

        /** 총 시간 상위 5개 이벤트 종류를 한 줄씩 출력한다. */
        private void report() {
            types.entrySet().stream()
                    .sorted(Comparator.comparingLong((Map.Entry<Class<?>, TypeStat> e) -> e.getValue().totalNs.sum()).reversed())
                    .limit(5)
                    .forEach(e -> log.info("[tickstats] {} 호출 {}회, 누적 {} ms, 최대 {} ms", e.getKey().getSimpleName(),
                            e.getValue().count.sum(), ms(e.getValue().totalNs.sum()), ms(e.getValue().maxNs.get())));
        }
    }
}
