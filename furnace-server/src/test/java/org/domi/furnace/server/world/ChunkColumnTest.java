// 섹션 블록 set/get, 비공기 카운트, heightmap 패킹 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkColumnTest {

    private static int surfaceBlockY(ChunkColumn c, int x, int z) throws Exception {
        return (int) ChunkColumn.class.getMethod("surfaceBlockY", int.class, int.class).invoke(c, x, z);
    }

    private static int oceanFloorBlockY(ChunkColumn c, int x, int z) throws Exception {
        return (int) ChunkColumn.class.getMethod("oceanFloorBlockY", int.class, int.class).invoke(c, x, z);
    }

    private static int wgOceanFloorHeight(ChunkColumn c, int x, int z) throws Exception {
        return (int) ChunkColumn.class.getMethod("wgOceanFloorHeight", int.class, int.class).invoke(c, x, z);
    }

    @Test
    void section_setBlock_countsNonAir() {
        ChunkSection s = new ChunkSection();
        assertEquals(0, s.nonAirCount());
        s.setBlock(0, 0, 0, 79); // bedrock
        s.setBlock(1, 0, 0, 79);
        assertEquals(2, s.nonAirCount());
        s.setBlock(0, 0, 0, 0);  // 다시 air → 카운트 감소
        assertEquals(1, s.nonAirCount());
        assertEquals(79, s.getBlock(1, 0, 0));
    }

    @Test
    void column_has24Sections_andBiomeFill() {
        ChunkColumn c = new ChunkColumn(6); // plains 인덱스 6
        assertEquals(24, c.sections().length);
        // 모든 섹션 바이옴이 단일 plains
        assertEquals(6, c.sections()[0].getBiome(0, 0, 0));
    }

    // rail(레일): blocksMotion=false — blocks/1.21.1.txt의 실제 state id.
    private static final int RAIL_STATE = 4663;

    @Test
    void motionBlockingHeightmap_packs9Bit_fromRealBlock() {
        ChunkColumn c = new ChunkColumn(6);
        c.setBlock(0, ChunkColumn.MIN_Y + 3, 0, 79); // bedrock → height=(3)+1=4
        long[] hm = c.motionBlockingHeightmap();
        // 9비트 256칸 valuesPerLong=7 → ceil(256/7)=37 long
        assertEquals(37, hm.length);
        BitStorage bs = BitStorage.wrap(9, 256, hm);
        assertEquals(4, bs.get(0));
    }

    @Test
    void motionBlockingHeightmap_isCached_untilBlockChanges() {
        ChunkColumn c = new ChunkColumn(6);
        c.setBlock(0, ChunkColumn.MIN_Y + 3, 0, 79);

        long[] first = c.motionBlockingHeightmap();
        long[] second = c.motionBlockingHeightmap();

        assertSame(first, second, "변경 없으면 heightmap 재계산 없이 재사용해야");

        c.setBlock(0, ChunkColumn.MIN_Y + 6, 0, 79);
        long[] third = c.motionBlockingHeightmap();

        assertNotSame(first, third, "블록 변경 후에는 캐시 무효화돼야");
        assertEquals(7, BitStorage.wrap(9, 256, third).get(0)); // height = (MIN_Y+6 - MIN_Y) + 1 = 7
    }

    @Test
    void motionBlockingHeightmap_excludesNonMotionBlockingBlock_butWorldSurfaceIncludesIt() {
        ChunkColumn c = new ChunkColumn(6);
        // surface[](WORLD_SURFACE)는 loadGenerated로 프라임해야 라이브 갱신 가드(first==0)를 통과한다(round1 교훈).
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 4096];
        blockIds[0] = 79; // (0,0) y=MIN_Y 바닥돌 1개만 프라임
        c.loadGenerated(blockIds, new int[ChunkColumn.SECTION_COUNT * 64]);
        c.setBlock(0, ChunkColumn.MIN_Y + 3, 0, RAIL_STATE); // 레일(blocksMotion=false)

        assertEquals(ChunkColumn.MIN_Y, c.motionBlockingBlockY(0, 0),
            "레일은 blocksMotion=false라 MOTION_BLOCKING엔 안 잡히고 밑의 바닥돌만 카운트돼야");
        assertEquals(ChunkColumn.MIN_Y + 3, c.surfaceBlockY(0, 0),
            "WORLD_SURFACE(!isAir)는 레일도 바닥으로 센다 — 두 heightmap이 이제 서로 달라야 함");
    }

    @Test
    void motionBlockingHeightmap_countsFluidAsBlocking() {
        ChunkColumn c = new ChunkColumn(6);
        c.setBlock(0, ChunkColumn.MIN_Y, 0, 80); // water(유체)
        assertEquals(ChunkColumn.MIN_Y, c.motionBlockingBlockY(0, 0),
            "vanilla MOTION_BLOCKING = blocksMotion() || fluid — 유체도 바닥으로 세야 함");
    }

    @Test
    void worldSurfaceHeightmap_packs9Bit() {
        ChunkColumn c = new ChunkColumn(6);
        c.setSurface(0, 0, 4); // (x=0,z=0) 표면 높이값 4
        long[] hm = c.worldSurfaceHeightmap();
        assertEquals(37, hm.length);
        BitStorage bs = BitStorage.wrap(9, 256, hm);
        assertEquals(4, bs.get(0));
    }

    @Test
    void worldSurfaceHeightmap_liveUpdatesOnManualBlockPlacement() {
        ChunkColumn c = new ChunkColumn(6);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 4096]; // (0,0) 컬럼에 y=MIN_Y 바닥돌 1개만 프라임
        blockIds[0 * 256 + 0 * 16 + 0] = 79; // idx = (y-MIN_Y)*256 + lz*16 + lx, y=MIN_Y → idx=0
        int[] biomeIds = new int[ChunkColumn.SECTION_COUNT * 64];
        c.loadGenerated(blockIds, biomeIds);
        assertEquals(ChunkColumn.MIN_Y, c.surfaceBlockY(0, 0), "프라임 직후 (0,0) 표면은 y=MIN_Y");

        c.setBlock(0, ChunkColumn.MIN_Y + 3, 0, 79); // (0,0)에 y=MIN_Y+3 bedrock 수동 배치(표면 갱신 대상)
        assertEquals(ChunkColumn.MIN_Y + 3, c.surfaceBlockY(0, 0));

        long[] hm = c.worldSurfaceHeightmap();
        BitStorage bs = BitStorage.wrap(9, 256, hm);
        assertEquals(4, bs.get(0)); // surfaceValue = (y - MIN_Y) + 1 = 4
    }

    @Test
    void worldSurfaceHeightmap_isCached_untilSurfaceChanges() {
        ChunkColumn c = new ChunkColumn(6);
        c.setSurface(0, 0, 4);

        long[] first = c.worldSurfaceHeightmap();
        long[] second = c.worldSurfaceHeightmap();
        assertSame(first, second, "변경 없으면 heightmap 재계산 없이 재사용해야");

        c.setSurface(0, 0, 7);
        long[] third = c.worldSurfaceHeightmap();
        assertNotSame(first, third, "표면 변경 후에는 캐시 무효화돼야");
        assertEquals(7, BitStorage.wrap(9, 256, third).get(0));
    }

    @Test
    void surfaceBlockY_readsPrecomputedSurface() throws Exception {
        ChunkColumn c = new ChunkColumn(6);
        c.setSurface(0, 0, 4);
        c.setSurface(1, 0, 0);

        assertEquals(ChunkColumn.MIN_Y + 3, surfaceBlockY(c, 0, 0));
        assertEquals(ChunkColumn.MIN_Y - 1, surfaceBlockY(c, 1, 0));
    }

    @Test
    void generatedSetBlock_doesNotMarkChunkDirty() {
        ChunkColumn c = new ChunkColumn(6);

        c.setGeneratedBlock(0, ChunkColumn.MIN_Y, 0, 79);

        assertFalse(c.dirty());
        assertEquals(79, c.getBlock(0, ChunkColumn.MIN_Y, 0));
    }

    @Test
    void oceanFloorBlockY_ignoresFluids() throws Exception {
        ChunkColumn c = new ChunkColumn(6);

        c.setGeneratedBlock(0, ChunkColumn.MIN_Y, 0, 1);
        c.setGeneratedBlock(0, ChunkColumn.MIN_Y + 1, 0, 80);
        c.setGeneratedBlock(0, ChunkColumn.MIN_Y + 2, 0, 80);
        c.setSurface(0, 0, 3);

        assertEquals(ChunkColumn.MIN_Y + 2, surfaceBlockY(c, 0, 0));
        assertEquals(ChunkColumn.MIN_Y, oceanFloorBlockY(c, 0, 0));
    }

    /** vanilla ProtoChunk.setBlockState는 OCEAN_FLOOR_WG를 라이브 갱신한다(Heightmap.update) — 최상단 carve 시 아래로 재스캔. */
    @Test
    void wgOceanFloorHeight_tracksCarvedTopLikeVanilla() throws Exception {
        ChunkColumn c = new ChunkColumn(6);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 16 * 16 * 16];
        int[] biomeIds = new int[ChunkColumn.SECTION_COUNT * 64];

        int topY = ChunkColumn.MIN_Y + 5;
        for (int y = ChunkColumn.MIN_Y; y <= topY; y++) {
            int idx = (y - ChunkColumn.MIN_Y) * 256;
            blockIds[idx] = 1;
        }

        c.loadGenerated(blockIds, biomeIds);
        c.setBlock(0, topY, 0, 0);

        assertEquals(topY - 1, oceanFloorBlockY(c, 0, 0));
        assertEquals(topY, wgOceanFloorHeight(c, 0, 0));
    }

    @Test
    void heightmap_followsEdits() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        int bedrock = reg.defaultStateId("minecraft:bedrock");
        int dirt = reg.defaultStateId("minecraft:dirt");
        int grass = reg.defaultStateId("minecraft:grass_block");
        // 운영과 같은 경로로 초기화 — 새로 만든 빈 컬럼은 WORLD_SURFACE를 라이브 갱신하지 않는 "미초기화" 상태다
        // (오버월드 컬럼은 바닥 기반암 때문에 높이 0이 될 수 없어 운영에선 문제 없음).
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 4096];
        for (int i = 0; i < 256; i++) blockIds[i] = bedrock;   // y=-64 층 전체가 기반암 → 높이 1
        ChunkColumn c = new ChunkColumn(6);
        c.loadGenerated(blockIds, new int[1536]);
        assertEquals(1, BitStorage.wrap(9, 256, c.worldSurfaceHeightmap()).get(0));
        c.setBlock(0, -63, 0, dirt);    // → 2
        c.setBlock(0, -61, 0, grass);   // 중간 빈칸(-62)을 두고 최상단 → 4
        assertEquals(4, BitStorage.wrap(9, 256, c.motionBlockingHeightmap()).get(0));
        assertEquals(4, BitStorage.wrap(9, 256, c.worldSurfaceHeightmap()).get(0));
        c.setBlock(0, -61, 0, ChunkSection.AIR); // 최상단 제거 → 아래 비공기(-63)까지 내려가 2
        assertEquals(2, BitStorage.wrap(9, 256, c.motionBlockingHeightmap()).get(0));
        assertEquals(2, BitStorage.wrap(9, 256, c.worldSurfaceHeightmap()).get(0));
        assertTrue(c.dirty());
        c.clearDirty();
        assertFalse(c.dirty());
    }

    @Test
    void everyMutator_bumpsModCount_andDropsCaches_butClearDirtyDoesNot() {
        ChunkColumn c = new ChunkColumn(6);
        long m = c.modCount();
        java.util.function.Consumer<Runnable> check = op -> {
            c.cachedSerializedBody(new byte[]{1});
            c.cachedSnapshot(ChunkSnapshot.of(0, 0, c));
            long before = c.modCount();
            op.run();
            assertTrue(c.modCount() > before, "modCount 증가");
            assertNull(c.cachedSerializedBody(), "본문 캐시 무효화");
            assertNull(c.cachedSnapshot(), "스냅샷 캐시 무효화");
        };
        check.accept(() -> c.setBlock(0, -61, 0, 79));
        check.accept(() -> c.setGeneratedBlock(1, -61, 0, 79));
        check.accept(c::markDirty);
        check.accept(() -> c.setBiome(0, 0, 0, 3));
        check.accept(() -> c.setSurface(0, 0, 4));
        check.accept(() -> c.putChest(ChestBlockEntity.empty(0, -60, 0)));
        check.accept(() -> c.removeBlockEntityAt(0, -60, 0));
        check.accept(() -> c.loadGenerated(new int[24 * 4096], new int[24 * 64]));
        assertTrue(c.modCount() > m);

        long before = c.modCount();
        c.clearDirty();
        c.motionBlockingHeightmap();
        assertEquals(before, c.modCount(), "clearDirty/heightmap 조회는 내용 변경이 아니다");
    }
}
