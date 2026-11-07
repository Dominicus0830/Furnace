// 청크 또는 월드 안의 엔티티 수 — 종류(minecraft:zombie)별·자연 스폰 종류 묶음별. 잰 순간의 값(불변).
// 플레이어는 byType 에 minecraft:player 로 들어가고 byCategory 에는 들어가지 않는다. 이미 지워진(removed) 엔티티는 세지 않는다.
package org.domi.furnace.api.world;

import java.util.Map;
import org.domi.furnace.api.worldgen.SpawnCategory;

public record EntityCounts(int total, Map<String, Integer> byType, Map<SpawnCategory, Integer> byCategory) {

    public static final EntityCounts EMPTY = new EntityCounts(0, Map.of(), Map.of());

    public EntityCounts {
        byType = Map.copyOf(byType);
        byCategory = Map.copyOf(byCategory);
    }

    public int count(String type) { return byType.getOrDefault(type, 0); }

    public int count(SpawnCategory category) { return byCategory.getOrDefault(category, 0); }
}
