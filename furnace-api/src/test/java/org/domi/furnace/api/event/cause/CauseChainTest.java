package org.domi.furnace.api.event.cause;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.domi.furnace.api.world.BlockPos;
import org.junit.jupiter.api.Test;

class CauseChainTest {

    private static final UUID STEVE = UUID.randomUUID();

    private static CauseEntry tnt(int i) { return CauseEntry.entity("minecraft:tnt", new UUID(0, i), "minecraft:overworld", new BlockPos(i, 0, 0), CauseEntry.EXPLODE); }

    @Test
    void orderIsDirectFirstRootLast() {
        CauseChain lit = CauseChain.of(CauseEntry.player(STEVE, "Steve", "minecraft:overworld", null, CauseEntry.IGNITE));
        CauseChain c = CauseChain.of(tnt(2), CauseChain.of(tnt(1), lit));
        assertEquals(3, c.size());
        assertEquals(tnt(2), c.direct().orElseThrow());
        assertEquals("Steve", c.root().orElseThrow().name());
        assertEquals(STEVE, c.player().orElseThrow().uuid());
        assertEquals(tnt(1), c.parent().direct().orElseThrow());
        assertEquals(c, CauseChain.of(tnt(2), CauseChain.of(tnt(1), lit)), "값이 같으면 같다");
        assertTrue(c.toString().startsWith("minecraft:tnt explode @2,0,0 ← minecraft:tnt explode"), c.toString());
        assertFalse(c.truncated());
    }

    @Test
    void noneIsEmpty() {
        assertTrue(CauseChain.NONE.isEmpty());
        assertTrue(CauseChain.NONE.direct().isEmpty());
        assertTrue(CauseChain.NONE.root().isEmpty());
        assertTrue(CauseChain.NONE.player().isEmpty());
        assertEquals(0, CauseChain.NONE.entries().size());
        assertEquals(CauseChain.of(tnt(1)), CauseChain.of(tnt(1), null));
    }

    @Test
    void longChainsKeepNearHalfAndRoot() {
        CauseChain c = CauseChain.of(CauseEntry.player(STEVE, "Steve", null, null, CauseEntry.IGNITE));
        for (int i = 1; i <= 100; i++) c = CauseChain.of(tnt(i), c);
        assertTrue(c.size() <= CauseChain.MAX_LENGTH, "길이 " + c.size());
        assertTrue(c.truncated());
        assertEquals(tnt(100), c.direct().orElseThrow());
        assertEquals("Steve", c.root().orElseThrow().name(), "root 는 늘 남는다");
        assertEquals(tnt(99), c.entries().get(1));
    }

    @Test
    void entriesNeedKindTypeAndAction() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> new CauseEntry(CauseEntry.Kind.BLOCK, null, null, null, null, null, "x"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> CauseEntry.block("minecraft:tnt", "w", null, CauseEntry.REDSTONE));
        assertEquals(CauseEntry.Kind.PLUGIN, CauseEntry.plugin("Cannons", "cannons:fire").kind());
    }
}
