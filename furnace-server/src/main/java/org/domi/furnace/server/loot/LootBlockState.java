// 전리품 조건·함수가 보는 블록 상태 — 블록 id 와 속성 값(문자열).
package org.domi.furnace.server.loot;

import java.util.LinkedHashMap;
import java.util.Map;
import org.domi.furnace.server.world.BlockRegistry;

public record LootBlockState(String block, Map<String, String> properties) {

    /** 블록 상태 id → (블록, 전체 속성). BlockRegistry 의 정규 키("minecraft:bee_nest;facing=north,honey_level=0")를 푼다. */
    public static LootBlockState of(BlockRegistry blocks, int stateId) { return parse(blocks.canonicalKeyOf(stateId)); }

    static LootBlockState parse(String canonicalKey) {
        int semi = canonicalKey.indexOf(';');
        Map<String, String> props = new LinkedHashMap<>();
        if (semi < 0) return new LootBlockState(canonicalKey, props);
        for (String kv : canonicalKey.substring(semi + 1).split(",")) {
            int eq = kv.indexOf('=');
            props.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return new LootBlockState(canonicalKey.substring(0, semi), props);
    }
}
