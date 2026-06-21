// 블록 state를 oracle 덤프와 byte-identical한 canonical key 문자열로 직렬화하는 유틸
package org.domi.furnace.server.world;

import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical key 포맷 (oracle blockstates 덤프 canonicalKey와 정확히 일치해야 함):
 *   - properties 없음 → "name"
 *   - properties 있음 → "name;k1=v1,k2=v2" (property 이름 String 오름차순 정렬, key=value를 ','로 join)
 * value는 vanilla Property.getName(value) 문자열(JSON Properties value와 동일 형식).
 */
public final class BlockStateKey {
    private BlockStateKey() {}

    public static String of(String name, Map<String, String> props) {
        if (props == null || props.isEmpty()) return name;
        TreeMap<String, String> sorted =
            (props instanceof TreeMap) ? (TreeMap<String, String>) props : new TreeMap<>(props);
        StringBuilder sb = new StringBuilder(name).append(';');
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }
}
