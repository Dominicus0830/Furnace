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
    }

    public void registerAll(java.util.Collection<RegisteredListener> listeners) {
        for (RegisteredListener l : listeners) register(l);
    }

    public synchronized void unregister(RegisteredListener listener) {
        if (slots.get(listener.getPriority()).remove(listener)) baked = null;
    }

    public synchronized void unregister(Plugin plugin) {
        boolean changed = false;
        for (List<RegisteredListener> l : slots.values()) changed |= l.removeIf(r -> r.getPlugin().equals(plugin));
        if (changed) baked = null;
    }

    public synchronized void unregister(Listener listener) {
        boolean changed = false;
        for (List<RegisteredListener> l : slots.values()) changed |= l.removeIf(r -> r.getListener().equals(listener));
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
