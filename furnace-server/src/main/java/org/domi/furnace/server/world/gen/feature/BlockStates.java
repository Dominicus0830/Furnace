// state id의 property 하나를 바꾼 state를 찾는 헬퍼 (BlockState.setValue / trySetValue 대응)
package org.domi.furnace.server.world.gen.feature;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.world.BlockRegistry;

public final class BlockStates {

    private BlockStates() {}

    /** 해당 property가 있으면 값을 바꾼 state, 없으면 -1. */
    public static int trySetValue(BlockRegistry registry, int stateId, String property, String value) {
        String key = registry.canonicalKeyOf(stateId);
        int sep = key.indexOf(';');
        if (sep < 0) return -1;
        Map<String, String> props = parseProps(key.substring(sep + 1));
        if (!props.containsKey(property)) return -1;
        props.put(property, value);
        return registry.stateIdOrAir(key.substring(0, sep), props);
    }

    /** 여러 property를 한 번에 바꾼 state. 하나라도 없는 property가 있으면 -1(trySetValue 연쇄와 달리 부분 적용 없음). */
    public static int trySetValues(BlockRegistry registry, int stateId, Map<String, String> values) {
        String key = registry.canonicalKeyOf(stateId);
        int sep = key.indexOf(';');
        if (sep < 0) return -1;
        Map<String, String> props = parseProps(key.substring(sep + 1));
        for (String name : values.keySet()) if (!props.containsKey(name)) return -1;
        props.putAll(values);
        return registry.stateIdOrAir(key.substring(0, sep), props);
    }

    /** BlockRegistry 정규 키 "이름;a=1,b=2" → (이름, props) 로 state를 다시 찾는다. */
    public static int fromCanonicalKey(BlockRegistry registry, String key) {
        int sep = key.indexOf(';');
        if (sep < 0) return registry.stateIdOrAir(key);
        return registry.stateIdOrAir(key.substring(0, sep), parseProps(key.substring(sep + 1)));
    }

    private static Map<String, String> parseProps(String s) {
        Map<String, String> props = new HashMap<>();
        for (String kv : s.split(",")) {
            int eq = kv.indexOf('=');
            props.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return props;
    }

    public static boolean hasValue(BlockRegistry registry, int stateId, String property, String value) {
        return registry.canonicalKeyOf(stateId).contains(property + "=" + value);
    }

    /**
     * BlockState.hasPostProcess — 바닐라 1.21.1 에서 true 인 블록은 magma_block, brown_mushroom, red_mushroom 뿐이다
     * (순정 jar 의 모든 상태를 열거해 확인). WorldGenRegion.setBlock 이 이런 상태를 쓰면 청크에 후처리 표시를 남긴다.
     */
    public static boolean hasPostProcess(BlockRegistry registry, int stateId) {
        String name = registry.nameOf(stateId);
        return name.equals("minecraft:magma_block") || name.equals("minecraft:brown_mushroom") || name.equals("minecraft:red_mushroom");
    }
}
