// 플러그인별로 예약 작업을 모았다가 플러그인이 꺼질 때 한꺼번에 취소한다(Bukkit 이 disablePlugin 에서 cancelTasks 를 부르는 것과 같다).
// FurnacePlugin.own() 에 한 번만 걸어 두고, 같은 플러그인의 두 번째 작업부터는 집합에 넣기만 한다.
package org.domi.furnace.compat.sched;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.domi.furnace.api.plugin.FurnacePlugin;

final class PluginTaskBook<T> {

    private final Map<FurnacePlugin, Set<T>> byPlugin = new ConcurrentHashMap<>();
    private final Consumer<T> canceller;

    PluginTaskBook(Consumer<T> canceller) { this.canceller = canceller; }

    void add(FurnacePlugin plugin, T task) {
        Set<T> set = byPlugin.get(plugin);
        if (set == null) {
            Set<T> fresh = ConcurrentHashMap.newKeySet();
            Set<T> prev = byPlugin.putIfAbsent(plugin, fresh);
            set = prev != null ? prev : fresh;
            if (prev == null) plugin.own(() -> drop(plugin));
        }
        set.add(task);
    }

    void remove(FurnacePlugin plugin, T task) {
        Set<T> set = byPlugin.get(plugin);
        if (set != null) set.remove(task);
    }

    /** 이 플러그인의 작업을 모두 취소하고 비운다(꺼진 뒤 다시 켜면 새로 걸린다). */
    int cancelAll(FurnacePlugin plugin) {
        Set<T> set = byPlugin.get(plugin);
        if (set == null) return 0;
        List<T> copy = new ArrayList<>(set);
        set.clear();
        for (T t : copy) canceller.accept(t);
        return copy.size();
    }

    /** 플러그인이 꺼졌을 때: 취소하고 이 플러그인 칸도 치운다. */
    void drop(FurnacePlugin plugin) {
        cancelAll(plugin);
        byPlugin.remove(plugin);
    }

    List<T> all() {
        List<T> out = new ArrayList<>();
        for (Set<T> s : byPlugin.values()) out.addAll(s);
        return out;
    }

    List<T> of(FurnacePlugin plugin) {
        Set<T> s = byPlugin.get(plugin);
        return s == null ? List.of() : new ArrayList<>(s);
    }
}
