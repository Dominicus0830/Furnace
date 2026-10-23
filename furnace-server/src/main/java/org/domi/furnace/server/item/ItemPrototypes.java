// 아이템별 기본 컴포넌트(바닐라 Item.components() — 프로토타입)를 정규 값으로. 원본은 items/components_1.21.1.json.gz(데이터 생성기 보고서).
// 처음 물을 때 그 아이템 하나만 정규화해 둔다.
package org.domi.furnace.server.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.loot.ItemData;

public final class ItemPrototypes {
    private ItemPrototypes() {}

    private static final Map<String, Map<String, Object>> CACHE = new ConcurrentHashMap<>();

    /** 아이템의 기본 컴포넌트 전부(정규 값, 읽기 전용). 모르는 아이템이면 빈 맵. */
    public static Map<String, Object> defaults(String item) {
        Map<String, Object> m = CACHE.get(item);
        if (m != null) return m;
        Map<String, Object> raw = ItemData.vanilla().defaults(item);
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw != null) {
            for (var e : raw.entrySet()) {
                Object v = DataComponents.require(e.getKey()).normalize(e.getValue());
                if (e.getKey().equals("minecraft:jukebox_playable")) v = DataComponents.jukeboxKeyOnly(v);
                out.put(e.getKey(), v);
            }
        }
        m = Collections.unmodifiableMap(out);
        CACHE.put(item, m);
        return m;
    }

    /** 기본값(없으면 null). */
    public static Object defaultValue(String item, String component) { return defaults(item).get(component); }

    public static int maxStackSize(String item) {
        return defaultValue(item, "minecraft:max_stack_size") instanceof Integer i ? i : 1;
    }
}
