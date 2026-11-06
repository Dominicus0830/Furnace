// 낚시 전리품(gameplay/fishing): FISHING 문맥으로 평가되고, 보물 표는 낚시찌가 열린 물(in_open_water)일 때만 나온다.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.junit.jupiter.api.Test;

class FishingLootTest {

    /** 보물 표에만 있는 아이템. */
    private static final Set<String> TREASURE_ONLY = Set.of("minecraft:name_tag", "minecraft:saddle", "minecraft:nautilus_shell", "minecraft:enchanted_book");

    private static Set<String> roll(LootData data, boolean openWater, int n) {
        RandomSequences seqs = new RandomSequences(1L);
        LootWorld world = new LootWorld() {
            public NoiseRandom random() { throw new AssertionError("낚시 표는 random_sequence 를 쓴다"); }
            public NoiseRandom randomSequence(ResourceId id) { return seqs.get(id); }
        };
        LootEntity hook = new LootEntity() {
            public String type() { return "minecraft:fishing_bobber"; }
            public Object typeSpecific(String key) { return key.equals("in_open_water") ? openWater : null; }
        };
        LootTable table = data.table("minecraft:gameplay/fishing");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            LootParams params = LootParams.fishing(new double[] {0.5, 62, 0.5}, LootStack.of("minecraft:fishing_rod", 1, Map.of()), hook, 0.0F);
            var items = table.getRandomItems(table.context(data, world, params, 0L));
            assertEquals(1, items.size(), "풀 하나, 굴림 1");
            seen.add(items.get(0).item());
        }
        return seen;
    }

    @Test
    void treasureOnlyInOpenWater() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        Set<String> closed = roll(data, false, 400);
        for (String t : TREASURE_ONLY) assertFalse(closed.contains(t), t + " 는 열린 물에서만");
        assertTrue(closed.contains("minecraft:cod"), "물고기 표");
        Set<String> open = roll(data, true, 400);
        assertTrue(open.stream().anyMatch(TREASURE_ONLY::contains), "열린 물이면 보물이 나온다: " + open);
    }
}
