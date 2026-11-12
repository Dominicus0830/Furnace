package org.bukkit.event;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

/** 이벤트 종류 하나의 처리기 목록. 우선순위 순(같으면 등록 순)으로 돌려준다. */
public class HandlerList {

    private static final List<HandlerList> ALL = new CopyOnWriteArrayList<>();

    private final EnumMap<EventPriority, ArrayList<RegisteredListener>> slots = new EnumMap<>(EventPriority.class);
    private volatile RegisteredListener[] baked;
    /** 지금 등록돼 있는 처리기(객체 동일성) — 이벤트 다리가 "그 사이 해제됐는가"를 목록 훑기 없이 본다. */
    private final java.util.Set<RegisteredListener> live = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public HandlerList() {
        for (EventPriority p : EventPriority.values()) slots.put(p, new ArrayList<>());
        ALL.add(this);
    }

    public static void bakeAll() {
        for (HandlerList h : ALL) h.bake();
    }

    public static void unregisterAll() {
        for (HandlerList h : ALL) {
            synchronized (h) {
                for (List<RegisteredListener> l : h.slots.values()) l.clear();
                h.live.clear();
                h.baked = null;
            }
        }
    }

    public static void unregisterAll(Plugin plugin) {
        for (HandlerList h : ALL) h.unregister(plugin);
    }

    public static void unregisterAll(Listener listener) {
        for (HandlerList h : ALL) h.unregister(listener);
    }

    public synchronized void register(RegisteredListener listener) {
        ArrayList<RegisteredListener> slot = slots.get(listener.getPriority());
        if (slot.contains(listener)) throw new IllegalStateException("This listener is already registered to priority " + listener.getPriority());
        baked = null;
        slot.add(listener);
        live.add(listener);
    }

    public void registerAll(java.util.Collection<RegisteredListener> listeners) {
        for (RegisteredListener l : listeners) register(l);
    }

    public synchronized void unregister(RegisteredListener listener) {
        if (slots.get(listener.getPriority()).remove(listener)) {
            live.remove(listener);
            baked = null;
        }
    }

    /** 이 처리기가 아직 등록돼 있는가(호환 계층 내부용 — Paper 에는 없다). */
    public boolean isRegistered(RegisteredListener listener) { return live.contains(listener); }

    public synchronized void unregister(Plugin plugin) {
        boolean changed = false;
        for (List<RegisteredListener> l : slots.values()) changed |= l.removeIf(r -> {
            boolean match = r.getPlugin().equals(plugin);
            if (match) live.remove(r);
            return match;
        });
        if (changed) baked = null;
    }

    public synchronized void unregister(Listener listener) {
        boolean changed = false;
        for (List<RegisteredListener> l : slots.values()) changed |= l.removeIf(r -> {
            boolean match = r.getListener().equals(listener);
            if (match) live.remove(r);
            return match;
        });
        if (changed) baked = null;
    }

    public synchronized void bake() {
        if (baked != null) return;
        List<RegisteredListener> out = new ArrayList<>();
        for (EventPriority p : EventPriority.values()) out.addAll(slots.get(p));
        baked = out.toArray(new RegisteredListener[0]);
    }

    /** 우선순위 순으로 정렬된 처리기. */
    public RegisteredListener[] getRegisteredListeners() {
        RegisteredListener[] b = baked;
        if (b == null) {
            bake();
            b = baked;
        }
        return b;
    }

    public static ArrayList<RegisteredListener> getRegisteredListeners(Plugin plugin) {
        ArrayList<RegisteredListener> out = new ArrayList<>();
        for (HandlerList h : ALL) {
            synchronized (h) {
                for (List<RegisteredListener> l : h.slots.values()) for (RegisteredListener r : l) if (r.getPlugin().equals(plugin)) out.add(r);
            }
        }
        return out;
    }

    public static List<HandlerList> getHandlerLists() { return new ArrayList<>(ALL); }
}
