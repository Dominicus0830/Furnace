// full blockstate(Name+Properties) → exact global id 해석 검증(oracle blockstates fixture 의존)
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockRegistryStateTest {

    @Test
    void resolvesPropertiedState_exactGlobalId() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int lower = reg.stateIdOrAir("minecraft:tall_grass", Map.of("half", "lower"));
        int upper = reg.stateIdOrAir("minecraft:tall_grass", Map.of("half", "upper"));
        assertNotEquals(0, lower);
        assertNotEquals(0, upper);
        // canonical key 양측 일치 검증의 핵심: 불일치면 둘 다 default 폴백→같아짐.
        assertNotEquals(lower, upper, "lower/upper는 서로 다른 global id (key 매칭 성공)");
        // half=lower는 default → base id와 동일해야 함.
        assertEquals(reg.stateIdOrAir("minecraft:tall_grass"), lower);
    }

    @Test
    void nonDefaultPropertiedState_differsFromBase() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int base = reg.stateIdOrAir("minecraft:pink_petals");
        int specific = reg.stateIdOrAir("minecraft:pink_petals",
            Map.of("facing", "east", "flower_amount", "3"));
        assertNotEquals(0, specific);
        assertNotEquals(base, specific, "non-default state는 base와 다른 id");
    }

    @Test
    void propertyOrderIrrelevant() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        Map<String, String> a = new LinkedHashMap<>();
        a.put("facing", "north"); a.put("flower_amount", "2");
        Map<String, String> b = new LinkedHashMap<>();
        b.put("flower_amount", "2"); b.put("facing", "north");
        assertEquals(reg.stateIdOrAir("minecraft:pink_petals", a),
                     reg.stateIdOrAir("minecraft:pink_petals", b));
    }

    @Test
    void noProps_matchesDefaultPath() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        assertEquals(reg.stateIdOrAir("minecraft:dirt"),
            reg.stateIdOrAir("minecraft:dirt", Map.of()));
    }
}
