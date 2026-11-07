// 월드 하나의 자연 스폰 정책 — 불변 스냅샷. 바뀔 때마다 새로 만들어 volatile 로 바꿔 끼우므로 region 스레드는 잠금 없이 읽는다.
// 상한 해석 순서: 플레이어별 값 > 월드별 값(플러그인) > 설정(furnace.properties) > 바닐라 상수. 필터는 비어 있으면 스폰 루프가 호출하지 않는다.
package org.domi.furnace.server.entity.spawn;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpawnPolicyState {

    /** 바닐라 그대로(아무것도 걸지 않음). */
    public static final SpawnPolicyState VANILLA = new SpawnPolicyState(unset(), unset(), Map.of(), List.of());

    /** 모든 종류가 "정하지 않음"(-1)인 칸. */
    static int[] unset() {
        int[] a = new int[MobCategory.values().length];
        java.util.Arrays.fill(a, -1);
        return a;
    }

    /** 한 필터와 그 주인. owner 는 꺼졌는지 보는 데 쓴다(서버 쪽은 불리언 공급자). */
    public record Filter(Object owner, java.util.function.BooleanSupplier ownerEnabled, FilterFn fn) {}

    @FunctionalInterface
    public interface FilterFn {
        boolean allow(MobCategory category, String type, String dimension, int x, int y, int z, String biome, UUID nearestPlayer);
    }

    /** 카테고리 순번 → 값, -1 은 정하지 않음. */
    final int[] caps, playerCaps;
    final Map<UUID, int[]> perPlayer;
    final List<Filter> filters;

    SpawnPolicyState(int[] caps, int[] playerCaps, Map<UUID, int[]> perPlayer, List<Filter> filters) {
        this.caps = caps;
        this.playerCaps = playerCaps;
        this.perPlayer = perPlayer;
        this.filters = filters;
    }

    public boolean hasFilters() { return !filters.isEmpty(); }

    public List<Filter> filters() { return filters; }

    /** 월드별 값이 있으면 그것, 없으면 설정 > 바닐라. */
    public int cap(MobCategory c) {
        int v = caps[c.ordinal()];
        return v >= 0 ? v : SpawnConfig.current().cap(c);
    }

    public int playerCap(MobCategory c) {
        int v = playerCaps[c.ordinal()];
        return v >= 0 ? v : SpawnConfig.current().playerCap(c);
    }

    public int playerCap(UUID player, MobCategory c) {
        int[] own = perPlayer.get(player);
        if (own != null && own[c.ordinal()] >= 0) return own[c.ordinal()];
        return playerCap(c);
    }

    /** 플레이어별 값이 하나라도 있는가(스폰 상태가 사람마다 묻지 않아도 되게). */
    public boolean hasPerPlayer() { return !perPlayer.isEmpty(); }

    /** 이 틱에 쓸 해석된 값 사본(카테고리 순번 인덱스). */
    int[] resolvedCaps() {
        int[] out = new int[MobCategory.values().length];
        for (MobCategory c : MobCategory.values()) out[c.ordinal()] = cap(c);
        return out;
    }

    int[] resolvedPlayerCaps() {
        int[] out = new int[MobCategory.values().length];
        for (MobCategory c : MobCategory.values()) out[c.ordinal()] = playerCap(c);
        return out;
    }
}
