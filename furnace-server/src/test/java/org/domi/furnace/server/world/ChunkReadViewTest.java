// N2b-3 공개 스냅샷(설계 3.6): 판이 살아 있는 값과 같은가, 바뀌지 않으면 같은 판·섹션 사본을 재사용하는가, 낸 판은 뒤 쓰기에 흔들리지 않는가.
// 더해서 PR #373 검토 후속 — MBNL 훑기 줄이기(R6)가 예전 답과 같은가, 청크 패킷 섹션 개수가 바닐라 nonEmptyBlockCount(유체 두 번 셈)인가.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.junit.jupiter.api.Test;

class ChunkReadViewTest {

    private static int state(String name) {
        BlockBehaviors.ofBlock(0);
        return Blocks.get().defaultState(name);
    }

    /** 바닥 몇 층이 돌, 몇 칸에 다른 블록. */
    private static ChunkColumn column() {
        int stone = state("minecraft:stone");
        ChunkColumn col = new ChunkColumn(0);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < -60; y++) col.setBlock(x, y, z, stone);
        col.setBlock(3, 10, 4, state("minecraft:oak_log"));
        col.setBlock(5, 100, 6, state("minecraft:glass"));
        return col;
    }

    private static void assertMatches(ChunkColumn col, ChunkReadView v) {
        for (int y = col.minY(); y < col.minY() + 16 * v.sectionCount(); y += 7) {
            for (int x = 0; x < 16; x += 3) for (int z = 0; z < 16; z += 5) assertEquals(col.getBlock(x, y, z), v.stateId(x, y, z), x + "," + y + "," + z);
        }
        assertEquals(col.getBlock(3, 10, 4), v.stateId(3, 10, 4));
        assertEquals(col.getBlock(5, 100, 6), v.stateId(5, 100, 6));
        for (int i = 0; i < v.sectionCount(); i++) {
            assertEquals(col.sections()[i].nonAirCount(), v.sectionNonAir(i), "섹션 " + i);
            assertArrayEquals(col.sections()[i].vanillaCounts(), v.sectionCounts(i), "섹션 " + i);
        }
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            assertEquals(col.surfaceBlockY(x, z) + 1, v.firstAvailable("WORLD_SURFACE", x, z));
            assertEquals(col.motionBlockingBlockY(x, z) + 1, v.firstAvailable("MOTION_BLOCKING", x, z));
            assertEquals(col.oceanFloorBlockY(x, z) + 1, v.firstAvailable("OCEAN_FLOOR", x, z));
            assertEquals(col.motionBlockingNoLeavesBlockY(x, z) + 1, v.firstAvailable("MOTION_BLOCKING_NO_LEAVES", x, z));
        }
    }

    @Test
    void viewMatchesLiveColumn() {
        ChunkColumn col = column();
        ChunkReadView v = col.publishReadView(0, 0);
        assertEquals(1, v.version());
        assertSame(col.loadToken(), v.loadToken());
        assertMatches(col, v);
        assertThrows(IndexOutOfBoundsException.class, () -> v.stateId(0, col.minY() - 1, 0));
        assertThrows(IllegalArgumentException.class, () -> v.firstAvailable("NO_SUCH", 0, 0));
    }

    @Test
    void unchangedColumnKeepsSameView() {
        ChunkColumn col = column();
        ChunkReadView a = col.publishReadView(0, 0);
        assertSame(a, col.publishReadView(0, 0), "바뀐 것이 없으면 같은 판");
        assertEquals(1, col.publishReadView(0, 0).version());
    }

    @Test
    void changeRepublishesAndReusesUntouchedSections() {
        ChunkColumn col = column();
        int glass = state("minecraft:glass");
        ChunkReadView a = col.publishReadView(0, 0);
        int before = a.stateId(8, 40, 8);
        col.setBlock(8, 40, 8, glass);   // 섹션 (40+64)/16 = 6
        ChunkReadView b = col.publishReadView(0, 0);
        assertNotSame(a, b);
        assertEquals(2, b.version(), "판 번호는 오른다");
        assertEquals(glass, b.stateId(8, 40, 8));
        assertEquals(before, a.stateId(8, 40, 8), "낸 판은 뒤 쓰기에 흔들리지 않는다(불변)");
        for (int i = 0; i < b.sectionCount(); i++) {
            if (i == 6) assertNotSame(a.section(i), b.section(i), "바뀐 섹션은 새 사본");
            else assertSame(a.section(i), b.section(i), "바뀌지 않은 섹션 " + i + " 은 앞 판의 사본을 그대로");
        }
        assertMatches(col, b);
    }

    @Test
    void biomeAndCountWritesBumpSectionVersion() {
        ChunkColumn col = column();
        ChunkReadView a = col.publishReadView(0, 0);
        col.setSectionVanillaCounts(2, 0, 0, 0);   // 개수 대입(N2b-2) — 블록은 그대로
        ChunkReadView b = col.publishReadView(0, 0);
        assertNotSame(a, b);
        assertArrayEquals(new int[] {0, 0, 0}, b.sectionCounts(2));
        assertEquals(ChunkSection.AIR, b.vanillaStateId(0, -64 + 2 * 16, 0), "nonEmpty 0 섹션은 바닐라 getBlockState 로 공기");
        assertNotSame(a.section(2), b.section(2));
        assertSame(a.section(3), b.section(3));
    }

    @Test
    void scratchWritesAfterPublishDoNotLeakIntoView() {
        ChunkColumn col = column();
        int glass = state("minecraft:glass");
        int[] raw = col.sectionBlocks(1);   // 열린 scratch — 바깥이 배열을 고칠 수 있다
        ChunkReadView v = col.publishReadView(0, 0);
        int before = v.stateId(0, -64 + 16, 0);
        raw[0] = glass;
        assertEquals(before, v.stateId(0, -64 + 16, 0), "scratch 를 연 섹션은 지금 값을 펼쳐 둔다");
        assertFalse(v.section(1).reusableFor(col.sections()[1]), "scratch 가 열린 섹션 사본은 재사용하지 않는다");
    }

    @Test
    void blockEntityPositionsArePackedWorldPositions() {
        ChunkColumn col = column();
        assertEquals(0, col.publishReadView(2, -3).blockEntityPositions().length);
        assertEquals(ChunkReadView.packPos(-1, 64, 1), net_minecraft_asLong(-1, 64, 1));
    }

    /** 바닐라 BlockPos.asLong(1.21.1: X 26비트 << 38, Z 26비트 << 12, Y 12비트). */
    private static long net_minecraft_asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long) x & (1L << 26) - 1L) << 38;
        l |= ((long) y & (1L << 12) - 1L);
        l |= ((long) z & (1L << 26) - 1L) << 12;
        return l;
    }

    // ------------------------------------------------------------------ PR #382 검토 R5: 판 번호·사본이 빠지면 잡히는가

    @Test
    void directSectionBiomeWriteRepublishes() {
        ChunkColumn col = column();
        ChunkReadView a = col.publishReadView(0, 0);
        col.sections()[3].setBiome(0, 0, 0, 5);   // region /fillbiome 길 — 컬럼을 거치지 않는다
        ChunkReadView b = col.publishReadView(0, 0);
        assertNotSame(a, b);
        assertEquals(5, b.sectionBiomes(3)[0]);
        assertEquals(0, a.sectionBiomes(3)[0], "앞 판은 그대로");
    }

    @Test
    void indexedReplaceRepublishes() {
        ChunkColumn col = column();
        int glass = state("minecraft:glass");
        ChunkReadView a = col.publishReadView(0, 0);
        BitStorage idx = new BitStorage(4, 4096);
        idx.set(0, 1);
        col.sections()[2].replaceBlocksIndexed(new int[] {ChunkSection.AIR, glass}, idx);
        ChunkReadView b = col.publishReadView(0, 0);
        assertEquals(glass, b.stateId(0, -64 + 32, 0));
        assertEquals(ChunkSection.AIR, b.stateId(1, -64 + 32, 0));
        assertNotSame(a.section(2), b.section(2));
    }

    @Test
    void scratchWriteThenCompactRepublishes() {
        ChunkColumn col = column();
        int glass = state("minecraft:glass");
        int[] raw = col.sectionBlocks(1);
        col.publishReadView(0, 0);
        raw[5] = glass;
        col.compactSections();
        ChunkReadView b = col.publishReadView(0, 0);
        assertEquals(glass, b.stateId(5, -64 + 16, 0), "scratch 를 닫은 뒤 공개는 닫을 때의 값");
    }

    @Test
    void fixedHeightmapIsCopiedIntoView() {
        ChunkColumn col = column();
        col.setSectionVanillaCounts(10, 0, 0, 0);   // 고정 모드로
        assertTrue(col.heightmapsFixed());
        ChunkReadView a = col.publishReadView(0, 0);
        int before = a.firstAvailable("WORLD_SURFACE", 0, 0);
        assertTrue(col.heightmapUpdate(HeightmapType.WORLD_SURFACE, 0, 200, 0, state("minecraft:stone")));
        assertEquals(before, a.firstAvailable("WORLD_SURFACE", 0, 0), "낸 판의 하이트맵은 고정 배열과 공유하지 않는다");
        assertEquals(201, col.publishReadView(0, 0).firstAvailable("WORLD_SURFACE", 0, 0));
    }

    @Test
    void generatedBlockEntityPositionsIncluded() {
        ChunkColumn col = column();
        col.putGeneratedBlockEntity(3, 10, 4, new org.domi.furnace.server.net.io.nbt.NbtCompound());
        long[] pos = col.publishReadView(2, -3).blockEntityPositions();
        assertArrayEquals(new long[] {ChunkReadView.packPos(2 * 16 + 3, 10, -3 * 16 + 4)}, pos);
    }

    /** R2: 원시 쓰기로 바닐라 개수가 비공기 수보다 작아진 섹션도 패킷 개수는 비공기 수 이상 — 클라이언트 개수(±1)가 블록이 남은 채 0 이 되지 않는다. */
    @Test
    void packetCountNeverBelowNonAir() {
        int stone = state("minecraft:stone");
        ChunkSection s = new ChunkSection();
        s.setVanillaCounts(0, 0, 0);
        s.setBlockAt(0, stone, true);                                    // 보통 쓰기: nonEmpty 1
        for (int i = 1; i < 4096; i++) s.setBlockAt(i, stone, false);    // 컨테이너 원시 쓰기: 개수 그대로
        assertEquals(1, s.vanillaCounts()[0]);
        int client = s.packetNonEmptyCount();
        assertEquals(4096, client);
        for (int i = 0; i < 4095; i++) {
            s.setBlockAt(i, ChunkSection.AIR, true);
            client--;   // 클라이언트 LevelChunkSection.setBlockState 흉내
            assertTrue(client >= s.nonAirCount(), "칸 " + i);
        }
        assertTrue(client > 0);
    }

    // ------------------------------------------------------------------ PR #373 검토 후속

    /** R6: MB 꼭대기에서 시작하는 MBNL 훑기가 처음부터 훑는 답과 같다(잎·물·빈 기둥). */
    @Test
    void motionBlockingNoLeavesMatchesFullScan() {
        int stone = state("minecraft:stone"), leaves = state("minecraft:oak_leaves"), water = state("minecraft:water");
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(0, 30, 0, stone);                     // 잎 없음 → 30
        col.setBlock(1, 30, 0, stone);                     // 돌 위 잎 → 30
        for (int y = 31; y <= 35; y++) col.setBlock(1, y, 0, leaves);
        col.setBlock(2, 50, 0, leaves);                    // 잎·빈칸·잎·돌 → 20
        col.setBlock(2, 45, 0, leaves);
        col.setBlock(2, 20, 0, stone);
        col.setBlock(3, 70, 0, leaves);                    // 잎뿐 → 없음(minY - 1)
        col.setBlock(4, 60, 0, water);                     // 물이 잎 위 → 60
        col.setBlock(4, 59, 0, leaves);
        assertEquals(30, col.motionBlockingNoLeavesBlockY(0, 0));
        assertEquals(30, col.motionBlockingNoLeavesBlockY(1, 0));
        assertEquals(20, col.motionBlockingNoLeavesBlockY(2, 0));
        assertEquals(col.minY() - 1, col.motionBlockingNoLeavesBlockY(3, 0));
        assertEquals(60, col.motionBlockingNoLeavesBlockY(4, 0));
        assertEquals(col.minY() - 1, col.motionBlockingNoLeavesBlockY(9, 9), "빈 기둥");
        // 나중에 아래를 바꿔도 지연 MB 캐시를 거쳐 같은 답
        col.setBlock(2, 20, 0, ChunkSection.AIR);
        assertEquals(col.minY() - 1, col.motionBlockingNoLeavesBlockY(2, 0));
        col.setBlock(1, 36, 0, stone);
        assertEquals(36, col.motionBlockingNoLeavesBlockY(1, 0));
    }

    /**
     * 청크 패킷 섹션 개수 = 바닐라 nonEmptyBlockCount 필드. 바닐라 재계수(recalcBlockCounts — 읽은 섹션)는 물 블록을 블록·유체로 두 번 세고,
     * 칸 쓰기(setBlockState)는 공기 여부로만 ±1 한다(유체는 tickingFluidCount 쪽) — 둘 다 그대로 보낸다.
     */
    @Test
    void packetSectionCountIsVanillaNonEmpty() {
        int stone = state("minecraft:stone"), water = state("minecraft:water");
        ChunkColumn col = new ChunkColumn(0);
        int[] blocks = new int[4096];
        blocks[0] = stone;
        blocks[1] = water;
        col.loadSectionBlocks(4, blocks);   // 디스크에서 읽은 섹션 — 개수 모름 → 재계수
        ChunkSection s = col.sections()[4];
        assertEquals(2, s.nonAirCount());
        assertEquals(3, s.packetNonEmptyCount(), "재계수: 돌 1 + 물(블록 1 + 유체 1)");
        assertEquals(3, s.vanillaCounts()[0]);
        col.setBlock(2, 0, 0, water);   // 칸 쓰기: 공기 → 물 은 nonEmpty +1 만
        assertEquals(4, s.packetNonEmptyCount());
        assertEquals(4, s.vanillaCounts()[0]);
        assertEquals(0, col.sections()[0].packetNonEmptyCount());
        col.setSectionVanillaCounts(4, 0, 0, 0);   // FAWE clearCounts — 바닐라 0 이지만 블록이 있다(hasOnlyAir 틈, 결정 대기 동안 Furnace 비공기 수)
        assertEquals(3, s.packetNonEmptyCount());
    }
}
