// blocks.json distill 로더가 블록명→state id와 전체 state 수를 읽는지 검증
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BlockRegistryTest {

    @Test
    void loads_knownBlockStateIds() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        assertEquals(0, reg.defaultStateId("minecraft:air"));
        assertEquals(9, reg.defaultStateId("minecraft:grass_block"));
        assertEquals(10, reg.defaultStateId("minecraft:dirt"));
        assertEquals(79, reg.defaultStateId("minecraft:bedrock"));
    }

    @Test
    void totalStateCount_isPositive_andDirectBitsAre15() {
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        assertTrue(reg.totalStateCount() > 20000, "" + reg.totalStateCount());
        assertEquals(15, reg.directBits());
    }
}
