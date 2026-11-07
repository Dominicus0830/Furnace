// 월드 하나의 스폰 정책을 담는 바뀔 수 있는 칸. 쓰기는 복사해서 새 스냅샷으로 바꾸고(잠금), 읽기는 volatile 한 번이다.
package org.domi.furnace.server.entity.spawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class WorldSpawnPolicy {

    private volatile SpawnPolicyState state = SpawnPolicyState.VANILLA;
    private final Object lock = new Object();

    /** 지금 스냅샷. */
    public SpawnPolicyState state() { return state; }

    private static int[] unset() { return SpawnPolicyState.unset(); }

    private static boolean isVanillaEmpty(SpawnPolicyState s) { return s == SpawnPolicyState.VANILLA; }

    private void mutate(java.util.function.Consumer<Builder> change) {
        synchronized (lock) {
            Builder b = new Builder(state);
            change.accept(b);
            state = b.build();
        }
    }

    private static final class Builder {
        int[] caps, playerCaps;
        Map<UUID, int[]> perPlayer;
        List<SpawnPolicyState.Filter> filters;

        Builder(SpawnPolicyState s) {
            boolean base = isVanillaEmpty(s);
            caps = base ? unset() : s.caps.clone();
            playerCaps = base ? unset() : s.playerCaps.clone();
            perPlayer = new HashMap<>();
            if (!base) s.perPlayer.forEach((k, v) -> perPlayer.put(k, v.clone()));
            filters = new ArrayList<>(s.filters);
        }

        SpawnPolicyState build() {
            boolean allUnset = true;
            for (int v : caps) if (v >= 0) allUnset = false;
            for (int v : playerCaps) if (v >= 0) allUnset = false;
            perPlayer.values().removeIf(a -> {
                for (int v : a) if (v >= 0) return false;
                return true;
            });
            if (allUnset && perPlayer.isEmpty() && filters.isEmpty()) return SpawnPolicyState.VANILLA;
            return new SpawnPolicyState(caps, playerCaps, Map.copyOf(perPlayer), List.copyOf(filters));
        }
    }

    private static MobCategory spawning(MobCategory c) {
        if (c == null) throw new NullPointerException("category");
        if (!SpawnConfig.spawns(c)) throw new IllegalArgumentException("자연 스폰이 없는 종류: " + c.key());
        return c;
    }

    private static void checkCap(int cap) {
        if (cap < 0 || cap > SpawnConfig.MAX_CAP) throw new IllegalArgumentException("상한은 0~" + SpawnConfig.MAX_CAP + ": " + cap);
    }

    public void setCap(MobCategory c, int cap) {
        spawning(c);
        checkCap(cap);
        mutate(b -> b.caps[c.ordinal()] = cap);
    }

    public void resetCap(MobCategory c) {
        spawning(c);
        mutate(b -> b.caps[c.ordinal()] = -1);
    }

    public void setPlayerCap(MobCategory c, int cap) {
        spawning(c);
        checkCap(cap);
        mutate(b -> b.playerCaps[c.ordinal()] = cap);
    }

    public void resetPlayerCap(MobCategory c) {
        spawning(c);
        mutate(b -> b.playerCaps[c.ordinal()] = -1);
    }

    public void setPlayerCap(UUID player, MobCategory c, int cap) {
        spawning(c);
        checkCap(cap);
        if (player == null) throw new NullPointerException("player");
        mutate(b -> b.perPlayer.computeIfAbsent(player, k -> unset())[c.ordinal()] = cap);
    }

    public void resetPlayerCap(UUID player, MobCategory c) {
        spawning(c);
        if (player == null) throw new NullPointerException("player");
        mutate(b -> {
            int[] a = b.perPlayer.get(player);
            if (a != null) a[c.ordinal()] = -1;
        });
    }

    /** 필터를 건다. 돌려준 Runnable 로 뗀다. */
    public Runnable addFilter(Object owner, BooleanSupplier ownerEnabled, SpawnPolicyState.FilterFn fn) {
        SpawnPolicyState.Filter f = new SpawnPolicyState.Filter(owner, ownerEnabled, fn);
        mutate(b -> b.filters.add(f));
        return () -> removeFilter(f);
    }

    public void removeFilter(SpawnPolicyState.Filter f) { mutate(b -> b.filters.remove(f)); }

    public int filterCount() { return state.filters.size(); }
}
