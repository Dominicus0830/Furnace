// LootGenerateEvent 자리(review 콜백): 쪼개기·섞기 뒤에 불리고, 받은 목록은 그대로 칸에 들어가며, null(취소)이면 컨테이너를 건드리지 않는다.
package org.domi.furnace.server.loot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class LootGenerateReviewTest {

    private static LootParams params() { return new LootParams().with(LootParams.ORIGIN, new double[]{0.5, 100.5, 0.5}); }

    @Test
    void cancelLeavesTheContainerUntouched() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        LootStack[] chest = new LootStack[27];
        data.table("minecraft:chests/simple_dungeon").fill(chest, data, VanillaChestLootTest.NO_WORLD, params(), 42L, items -> null);
        for (LootStack s : chest) assertTrue(s == null || s.isEmpty());
    }

    @Test
    void reviewSeesTheSplitListAndItsAnswerIsNotSplitAgain() throws Exception {
        LootData data = VanillaChestLootTest.vanillaLoot();
        LootStack[] plain = new LootStack[27];
        data.table("minecraft:chests/simple_dungeon").fill(plain, data, VanillaChestLootTest.NO_WORLD, params(), 42L);
        int plainCount = 0;
        for (LootStack s : plain) if (s != null && !s.isEmpty()) plainCount++;
        List<Integer> seen = new ArrayList<>();
        LootStack[] chest = new LootStack[27];
        data.table("minecraft:chests/simple_dungeon").fill(chest, data, VanillaChestLootTest.NO_WORLD, params(), 42L, items -> {
            seen.add(items.size());
            return new ArrayList<>(items);   // 그대로 돌려주면 처리기가 없을 때와 칸이 같다
        });
        assertEquals(List.of(plainCount), seen, "이벤트가 보는 목록은 이미 쪼개진 목록(칸 수와 같음)");
        for (int i = 0; i < 27; i++) {
            boolean a = plain[i] == null || plain[i].isEmpty(), b = chest[i] == null || chest[i].isEmpty();
            assertEquals(a, b, "칸 " + i);
            if (!a) assertEquals(plain[i].count(), chest[i].count(), "칸 " + i);
        }
    }
}
