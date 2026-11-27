package org.domi.furnace.server.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.internal.port.BlockPort;
import org.domi.furnace.api.internal.port.ItemInventoryPort;
import org.domi.furnace.api.internal.port.PortBridges;
import org.domi.furnace.api.internal.port.PortRandom;
import org.domi.furnace.api.internal.port.PortRefused;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.types.CropBlock;
import org.domi.furnace.server.menu.AnvilMenu;
import org.junit.jupiter.api.Test;

/** 서버 쪽 이식 다리: 생성 구현이 서비스로 찾아지고, pure 줄이 서버 값을 그대로 내며, kind 바꾸기가 틀린 짝을 거절한다. */
class PortBridgeServerTest {

    @Test
    void generatedImplementationsAreFound() {
        ItemInventoryPort p = PortBridges.get(ItemInventoryPort.class);
        assertEquals("org.domi.furnace.server.port.gen.ItemInventoryPortImpl", p.getClass().getName());
        assertSame(p, PortBridges.get(ItemInventoryPort.class));
        assertEquals("org.domi.furnace.server.port.gen.BlockPortImpl", PortBridges.get(BlockPort.class).getClass().getName());
    }

    @Test
    void pureLineGivesTheServerValue() {
        ItemInventoryPort p = PortBridges.get(ItemInventoryPort.class);
        for (int c : new int[] {0, 1, 2, 5, 39, 1_000_000, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2 + 1, Integer.MAX_VALUE}) {
            assertEquals(AnvilMenu.calculateIncreasedRepairCost(c), p.anvilMenu$calculateIncreasedRepairCost(c), "cost " + c);
            assertEquals((int) Math.min(c * 2L + 1L, Integer.MAX_VALUE), p.anvilMenu$calculateIncreasedRepairCost(c), "vanilla cost " + c);
        }
    }

    @Test
    void behaviorRefusesAnotherBlockKind() {
        Blocks b = Blocks.get();
        int wheat = b.defaultState("minecraft:wheat");
        assertTrue(PortKinds.behavior(wheat, CropBlock.class, "t") instanceof CropBlock);
        PortRefused e = assertThrows(PortRefused.class, () -> PortKinds.behavior(b.defaultState("minecraft:stone"), CropBlock.class, "CropBlock#x"));
        assertTrue(e.getMessage().startsWith("CropBlock#x: minecraft:stone is a "), e.getMessage());
        assertEquals("CropBlock#x", e.what());
        assertThrows(PortRefused.class, () -> PortKinds.behavior(-1, CropBlock.class, "t"));
        assertThrows(PortRefused.class, () -> PortKinds.behavior(b.stateCount(), CropBlock.class, "t"));
    }

    /** random kind 는 복사하지 않고 부를 때마다 그 콜백으로 간다 — 호출 순서·인자가 그대로 보인다. */
    @Test
    void randomGoesToTheCallbackEveryTime() {
        List<String> calls = new ArrayList<>();
        PortRandom cb = new PortRandom() {
            public int nextInt() { calls.add("i"); return 1; }
            public int nextInt(int bound) { calls.add("i" + bound); return bound - 1; }
            public long nextLong() { calls.add("l"); return 2; }
            public float nextFloat() { calls.add("f"); return 0.5f; }
            public double nextDouble() { calls.add("d"); return 0.25; }
            public boolean nextBoolean() { calls.add("b"); return true; }
            public double nextGaussian() { calls.add("g"); return 0.75; }
        };
        RandomSource r = PortKinds.random(cb);
        assertEquals(1, r.nextInt());
        assertEquals(6, r.nextInt(7));
        assertEquals(2, r.nextLong());
        assertEquals(0.5f, r.nextFloat());
        assertEquals(0.25, r.nextDouble());
        assertTrue(r.nextBoolean());
        assertEquals(0.75, r.nextGaussian());
        assertEquals(12, r.nextIntBetweenInclusive(4, 12));   // 기본 몸통: nextInt(9) + 4
        assertEquals(List.of("i", "i7", "l", "f", "d", "b", "g", "i9"), calls);
    }

    @Test
    void notAServerWorldIsRefused() {
        assertThrows(PortRefused.class, () -> PortGuards.handle(null, "t"));
    }
}
