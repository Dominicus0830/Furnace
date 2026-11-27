// 루트 다리가 쓰는 굴리기 규칙(LootTable#getRandomItems(params, seed)): 시드가 0 이 아니면 그 시드로만, 0 이면 월드의 난수 시퀀스.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.Test;

class LootRollSeedTest {

    static List<String> roll(LootData data, LootWorld world, long seed) {
        LootTable t = data.table("minecraft:chests/simple_dungeon");
        var p = new LootParams().with(LootParams.ORIGIN, new double[] {0.5, 64.5, 0.5});
        List<String> out = new ArrayList<>();
        for (var s : t.getRandomItems(t.context(data, world, p, seed))) if (!s.isEmpty()) out.add(s.toString());
        return out;
    }

    @Test
    void seedGivenIsDeterministicAndNeverTouchesWorldRandom() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        var a = roll(data, VanillaChestLootTest.NO_WORLD, 12345L);
        var b = roll(data, VanillaChestLootTest.NO_WORLD, 12345L);
        assertFalse(a.isEmpty());
        assertEquals(a, b);
    }

    @Test
    void seedZeroUsesWorldRandomSequence() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        AtomicInteger asked = new AtomicInteger();
        LootWorld world = new LootWorld() {
            public NoiseRandom random() { throw new AssertionError("표에 random_sequence 가 있으면 월드 난수가 아니라 그 시퀀스"); }
            public NoiseRandom randomSequence(ResourceId id) { asked.incrementAndGet(); return new LegacyRandom(777L); }
        };
        var first = roll(data, world, 0L);
        assertTrue(asked.get() >= 1);
        assertEquals(first, roll(data, world, 0L));   // 같은 월드 난수 시드 → 같은 결과
    }
}
