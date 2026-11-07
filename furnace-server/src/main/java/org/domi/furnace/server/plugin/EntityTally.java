// 엔티티 수 집계(EntityCounts 를 만든다). 소유 region 스레드에서 쓰는 것(add)과 합치는 것(merge, 잠금)이 따로다.
package org.domi.furnace.server.plugin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.api.world.EntityCounts;
import org.domi.furnace.api.worldgen.SpawnCategory;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.spawn.EntityMeta;
import org.domi.furnace.server.entity.spawn.MobCategory;

final class EntityTally {

    private static final Map<MobCategory, SpawnCategory> API = new EnumMap<>(MobCategory.class);

    static {
        for (SpawnCategory c : SpawnCategory.values()) API.put(MobCategory.byKey(c.key()), c);
    }

    private int total;
    private final Map<String, Integer> byType = new HashMap<>();
    private final EnumMap<SpawnCategory, Integer> byCategory = new EnumMap<>(SpawnCategory.class);

    void add(Entity e) {
        String type = e.type().name();
        total++;
        byType.merge(type, 1, Integer::sum);
        SpawnCategory api = API.get(EntityMeta.category(type));
        if (api != null) byCategory.merge(api, 1, Integer::sum);
    }

    void addPlayer() {
        total++;
        byType.merge("minecraft:player", 1, Integer::sum);
    }

    synchronized void merge(EntityTally o) {
        total += o.total;
        o.byType.forEach((k, v) -> byType.merge(k, v, Integer::sum));
        o.byCategory.forEach((k, v) -> byCategory.merge(k, v, Integer::sum));
    }

    EntityCounts build() { return new EntityCounts(total, byType, byCategory); }
}
