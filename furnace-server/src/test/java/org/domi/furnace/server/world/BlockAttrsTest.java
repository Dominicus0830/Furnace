// 바닐라에서 export한 블록 속성이 BlockRegistry의 state id와 정렬돼 있고 알려진 사실과 맞는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BlockAttrsTest {

    private final BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
    private final BlockAttrs attrs = BlockAttrs.get();

    @Test
    void stateCountMatchesRegistry() {
        assertEquals(reg.totalStateCount(), attrs.stateCount());
    }

    @Test
    void airAndFriends() {
        assertTrue(attrs.isAir(reg.defaultStateId("minecraft:air")));
        assertTrue(attrs.isAir(reg.defaultStateId("minecraft:cave_air")));
        assertFalse(attrs.isAir(reg.defaultStateId("minecraft:stone")));
        assertTrue(attrs.isReplaceable(reg.defaultStateId("minecraft:short_grass")));
        assertFalse(attrs.isReplaceable(reg.defaultStateId("minecraft:stone")));
        assertTrue(attrs.isLiquid(reg.defaultStateId("minecraft:water")));
    }

    @Test
    void sturdyFaces() {
        int stone = reg.defaultStateId("minecraft:stone");
        for (int d = 0; d < 6; d++) assertTrue(attrs.isFaceSturdy(stone, d));
        assertFalse(attrs.isFaceSturdy(reg.defaultStateId("minecraft:oak_leaves"), BlockAttrs.UP));
        assertFalse(attrs.isFaceSturdy(reg.defaultStateId("minecraft:air"), BlockAttrs.UP));
        assertTrue(attrs.canOcclude(stone));
        assertEquals(15, attrs.lightEmission(reg.defaultStateId("minecraft:lava")));
    }
}
