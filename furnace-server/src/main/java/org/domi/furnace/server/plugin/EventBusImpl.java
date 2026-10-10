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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
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
        if (hs == null) {
            hs = bake(type);
            baked.put(type, hs);
        }
        return hs;
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
        for (Handler h : hs) {
            if (h.ignoreCancelled() && event instanceof Cancellable c && c.isCancelled()) continue;
            if (!h.plugin().isEnabled()) continue;
            try {
                h.fn().accept(event);
            } catch (Throwable t) {
                // Bukkit: "Could not pass event X to Plugin vY"
                log.error("Could not pass event {} to {}", event.eventName(), h.plugin().description().fullName(), t);
            }
        }
        return event;
    }
}
