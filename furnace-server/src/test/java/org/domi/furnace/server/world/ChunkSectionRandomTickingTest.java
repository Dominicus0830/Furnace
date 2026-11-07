// X1: 섹션의 "무작위 틱 받는 칸이 있는가" 판정(팔레트 먼저 보는 빠른 길)이 4096칸 전부 세는 것과 같은 답인지.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.junit.jupiter.api.Test;

class ChunkSectionRandomTickingTest {

    private static List<Integer> tickingStates, plainStates;

    private static void init() {
        if (tickingStates != null) return;
        BlockBehaviors.ofBlock(0);   // 무작위 틱 표를 섹션에 건다
        Blocks bs = Blocks.get();
        tickingStates = new ArrayList<>();
        plainStates = new ArrayList<>();
        for (int s = 0; s < bs.stateCount(); s += 3) {
            if (BlockBehaviors.of(s).isRandomlyTicking(s)) tickingStates.add(s);
            else plainStates.add(s);
        }
        assertFalse(tickingStates.isEmpty());
        assertFalse(plainStates.isEmpty());
    }

    private static boolean brute(ChunkSection sec) {
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int st = sec.getBlock(x, y, z);
            if (BlockBehaviors.of(st).isRandomlyTicking(st)) return true;
        }
        return false;
    }

    @Test
    void matchesBruteForceAcrossPaletteShapes() {
        init();
        Random rnd = new Random(7);
        int[] distinctCounts = {1, 2, 3, 5, 9, 17, 40, 300, 3000};   // 단일값·간접 팔레트·직접 저장 모두
        for (int distinct : distinctCounts) {
            for (int tickingShare = 0; tickingShare <= 2; tickingShare++) {   // 0 = 무작위 틱 없음, 1 = 하나, 2 = 섞음
                int[] flat = new int[4096];
                for (int i = 0; i < 4096; i++) {
                    int k = rnd.nextInt(distinct);
                    flat[i] = plainStates.get(k % plainStates.size());
                }
                if (tickingShare == 1) flat[rnd.nextInt(4096)] = tickingStates.get(rnd.nextInt(tickingStates.size()));
                if (tickingShare == 2) for (int i = 0; i < 50; i++) flat[rnd.nextInt(4096)] = tickingStates.get(rnd.nextInt(tickingStates.size()));
                ChunkSection sec = new ChunkSection();
                sec.replaceBlocks(flat, 0);
                assertEquals(brute(sec), sec.isRandomlyTicking(), "distinct=" + distinct + " share=" + tickingShare);
            }
        }
    }

    @Test
    void staysConsistentWhileBlocksChange() {
        init();
        Random rnd = new Random(11);
        ChunkSection sec = new ChunkSection();
        // 처음에 한 값뿐인 섹션에서 시작해 무작위 틱 상태를 넣었다 빼기를 반복한다(개수 갱신 경로)
        assertEquals(brute(sec), sec.isRandomlyTicking());
        for (int round = 0; round < 400; round++) {
            int x = rnd.nextInt(16), y = rnd.nextInt(16), z = rnd.nextInt(16);
            boolean put = rnd.nextInt(3) == 0;
            int st = put ? tickingStates.get(rnd.nextInt(tickingStates.size())) : plainStates.get(rnd.nextInt(plainStates.size()));
            sec.setBlock(x, y, z, st);
            if (round % 7 == 0) assertEquals(brute(sec), sec.isRandomlyTicking(), "round " + round);
        }
        assertEquals(brute(sec), sec.isRandomlyTicking());
    }
}
