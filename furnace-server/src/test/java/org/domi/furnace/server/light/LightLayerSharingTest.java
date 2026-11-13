// T-012(O07-05): 빛 층 공유 상수(ZERO·FULL)는 고쳐지지 않고, 쓰기는 청크 몫 복사에만 가며, 균일 층은 공유로 돌아간다.
// + 빛 검사 집합(LongLinkedSet)이 LinkedHashSet 과 같은 순서·중복 제거를 하는가(Long.MIN_VALUE 키 포함).
package org.domi.furnace.server.light;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class LightLayerSharingTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    @Test
    void newLight_ownsNoLayers_andWritesCopyOnlyTheTouchedLayer() {
        ChunkLight a = new ChunkLight(), b = new ChunkLight();
        assertEquals(0, a.ownedLayers());
        a.setBlock(3, 10, 4, 12);
        assertEquals(1, a.ownedLayers(), "한 칸 쓰기는 그 층 하나만 복사");
        assertEquals(12, a.blockAt(3, 10, 4));
        assertEquals(0, b.blockAt(3, 10, 4), "다른 청크는 공유 층을 그대로 본다");
        assertTrue(ChunkLight.isKnownZero(b.block(ChunkLight.sectionIndex(10))));
        assertTrue(ChunkLight.sharedIntact());
    }

    @Test
    void fullLayer_isSharedUntilWritten_thenCopiedWithItsValues() {
        ChunkLight a = new ChunkLight(), b = new ChunkLight();
        int sec = ChunkLight.sectionIndex(100);
        a.fillSkyFull(sec);
        b.fillSkyFull(sec);
        assertSame(a.sky(sec), b.sky(sec), "모두 15 인 층은 공유");
        assertEquals(0, a.ownedLayers());
        a.setSky(0, 100, 0, 7);
        assertEquals(7, a.skyAt(0, 100, 0));
        assertEquals(15, a.skyAt(1, 100, 0), "복사본은 15 를 이어받는다");
        assertEquals(15, b.skyAt(0, 100, 0), "공유 FULL 은 그대로");
        assertTrue(ChunkLight.isKnownFull(b.sky(sec)));
        assertTrue(ChunkLight.sharedIntact());
    }

    @Test
    void sharedLayer_cannotBeWrittenDirectly() {
        ChunkLight a = new ChunkLight();
        assertThrows(AssertionError.class, () -> ChunkLight.set(a.sky(0), 0, 0, 0, 5), "공유 층 쓰기는 -ea 에서 막힌다");
        assertTrue(ChunkLight.sharedIntact());
    }

    @Test
    void fromLayers_mapsUniformLayersToShared_adoptsOthers_rejectsWrongLength() {
        byte[][] sky = new byte[ChunkLight.SECTIONS][], block = new byte[ChunkLight.SECTIONS][];
        sky[0] = new byte[2048];
        sky[1] = new byte[2048];
        java.util.Arrays.fill(sky[1], (byte) 0xFF);
        sky[2] = new byte[2048];
        sky[2][100] = 0x35;
        sky[3] = new byte[100];
        block[4] = new byte[2048];
        block[4][0] = 1;
        ChunkLight l = ChunkLight.fromLayers(sky, block);
        assertTrue(ChunkLight.isKnownZero(l.sky(0)));
        assertTrue(ChunkLight.isKnownFull(l.sky(1)));
        assertSame(sky[2], l.sky(2), "고르지 않은 층은 복사 없이 넘겨받는다");
        assertTrue(ChunkLight.isKnownZero(l.sky(3)), "길이가 틀린 층은 0");
        assertSame(block[4], l.block(4));
        assertTrue(ChunkLight.isKnownZero(l.block(5)), "없는 층은 0");
        assertEquals(2, l.ownedLayers());
    }

    @Test
    void precompute_compactsUniformLayers_withoutChangingValues() {
        ChunkColumn col = new SuperflatWorldSource(REG, 0).generate(0, 0);
        col.setBlock(5, 40, 5, REG.defaultStateId("minecraft:glowstone"));
        ChunkLevel.precomputeLight(col, 0, 0, REG.defaultStateId("minecraft:bedrock"), true);
        ChunkLight l = col.light();
        assertTrue(l.ownedLayers() < 2 * ChunkLight.SECTIONS / 4, "슈퍼플랫 빛은 대부분 공유 층(가진 층 " + l.ownedLayers() + ")");
        assertEquals(15, l.skyAt(0, 200, 0), "하늘");
        assertEquals(15, l.skyAt(0, -60, 0), "풀 위 공기");
        assertEquals(0, l.skyAt(0, -62, 0), "흙 속");
        assertEquals(15, l.blockAt(5, 40, 5), "발광석");
        assertEquals(14, l.blockAt(6, 40, 5));
        assertEquals(0, l.blockAt(5, 100, 5));
        // 미리 계산(구역 힌트) == 힌트 없는 계산, 두 결과 모두 같은 공유 상태
        ChunkLight fresh = LevelLight.computeIsolated(0, 0, (x, y, z) -> col.getBlock(x & 15, y, z & 15),
                REG.defaultStateId("minecraft:bedrock"), -64, 320, true);
        for (int s = 0; s < ChunkLight.SECTIONS; s++) {
            assertArrayEquals(fresh.sky(s), l.sky(s), "sky " + s);
            assertArrayEquals(fresh.block(s), l.block(s), "block " + s);
            assertEquals(ChunkLight.isShared(fresh.sky(s)), ChunkLight.isShared(l.sky(s)), "sky 공유 " + s);
        }
        assertTrue(ChunkLight.sharedIntact());
    }

    @Test
    void longLinkedSet_matchesLinkedHashSet_includingMinValueKey() {
        Random r = new Random(42);
        LightEngine.LongLinkedSet set = new LightEngine.LongLinkedSet();
        for (int round = 0; round < 200; round++) {
            LinkedHashSet<Long> ref = new LinkedHashSet<>();
            int n = r.nextInt(round % 7 == 0 ? 20_000 : 300);
            for (int i = 0; i < n; i++) {
                long k = switch (r.nextInt(5)) {
                    case 0 -> Long.MIN_VALUE;
                    case 1 -> r.nextInt(64);                  // 겹치는 작은 키
                    case 2 -> Long.MAX_VALUE - r.nextInt(3);
                    default -> r.nextLong();
                };
                assertEquals(ref.add(k), set.add(k), "add " + k);
                assertEquals(ref.size(), set.size());
            }
            List<Long> got = new ArrayList<>();
            for (long k : set.drain()) got.add(k);
            assertEquals(new ArrayList<>(ref), got, "round " + round);
            assertTrue(set.isEmpty());
        }
    }
}
