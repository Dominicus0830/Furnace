// N-09: 디스크 로드 벌크 디코드가 예전 블록별 setBlock 경로와 같은 청크 상태(블록·비공기 수·높이맵·dirty·재저장 NBT)를 만드는지 대조.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BitStorage;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AnvilBulkDecodeParityTest {

    static BlockRegistry reg;
    static AnvilChunkCodec codec;
    static int plains;
    static int[] pool;

    @BeforeAll
    static void load() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        var biomes = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
        plains = biomes.indexOf("minecraft:plains");
        codec = new AnvilChunkCodec(reg, ItemRegistry.load("items/1.21.1.txt"), biomes, plains,
                ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        List<Integer> ids = new ArrayList<>(List.of(0, 0, 0, 12958, 12959));
        Random r = new Random(5);
        while (ids.size() < 400) {
            int id = r.nextInt(1 << reg.directBits());
            try { if (reg.nameOf(id) != null) ids.add(id); } catch (RuntimeException ignored) {}
        }
        pool = ids.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 수정 전 fromNbt 의 블록 부분 그대로: 블록별 setBlock(공기 건너뜀) 후 높이맵 재구성. */
    static ChunkColumn legacyDecode(NbtCompound root) {
        ChunkColumn col = new ChunkColumn(plains, reg.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS,
                ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        int minY = ChunkColumn.MIN_Y, minSection = minY >> 4;
        for (Object o : root.getList("sections").items()) {
            NbtCompound s = (NbtCompound) o;
            int sectionIdx = s.getByte("Y") - minSection;
            if (sectionIdx < 0 || sectionIdx >= ChunkColumn.SECTION_COUNT || !s.contains("block_states")) continue;
            NbtCompound bs = s.getCompound("block_states");
            NbtList pl = bs.getList("palette");
            int[] ids = new int[pl.size()];
            for (int i = 0; i < ids.length; i++) {
                NbtCompound e = (NbtCompound) pl.get(i);
                if (e.contains("Properties")) {
                    Map<String, String> props = new HashMap<>();
                    for (var p : e.getCompound("Properties").tags().entrySet()) props.put(p.getKey(), (String) p.getValue());
                    ids[i] = reg.stateIdOrAir(e.getString("Name"), props);
                } else ids[i] = reg.stateIdOrAir(e.getString("Name"));
            }
            int baseY = minY + sectionIdx * 16;
            if (!bs.contains("data")) {
                if (ids.length == 0 || ids[0] == ChunkSection.AIR) continue;
                for (int idx = 0; idx < 4096; idx++) col.setBlock(idx & 15, baseY + (idx >> 8), (idx >> 4) & 15, ids[0]);
                continue;
            }
            BitStorage store = BitStorage.wrap(Math.max(4, 32 - Integer.numberOfLeadingZeros(ids.length - 1)), 4096, bs.getLongArray("data"));
            for (int idx = 0; idx < 4096; idx++) {
                int st = ids[store.get(idx)];
                if (st != ChunkSection.AIR) col.setBlock(idx & 15, baseY + (idx >> 8), (idx >> 4) & 15, st);
            }
        }
        col.rebuildHeightmapsAfterSurface();
        return col;
    }

    static ChunkColumn randomColumn(Random r) {
        ChunkColumn col = new ChunkColumn(plains, reg.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS,
                ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        int topSec = r.nextInt(ChunkColumn.SECTION_COUNT + 1);
        for (int sec = 0; sec < topSec; sec++) {
            int kind = r.nextInt(5);
            int distinct = kind == 0 ? 1 : kind == 1 ? 2 + r.nextInt(6) : kind == 2 ? 20 + r.nextInt(100) : kind == 3 ? 300 : 0;
            if (distinct == 0) continue;   // 빈 섹션
            int[] local = new int[distinct];
            for (int i = 0; i < distinct; i++) local[i] = pool[r.nextInt(pool.length)];
            int baseY = ChunkColumn.MIN_Y + sec * 16;
            for (int idx = 0; idx < 4096; idx++) {
                int st = local[r.nextInt(distinct)];
                if (r.nextInt(4) == 0) st = 0;   // 공기 섞기
                if (distinct == 1) st = local[0];
                col.setBlock(idx & 15, baseY + (idx >> 8), (idx >> 4) & 15, st);
            }
        }
        return col;
    }

    static byte[] nbtBytes(NbtCompound c) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NbtIo.write(new DataOutputStream(bos), c, "");
        return bos.toByteArray();
    }

    static void assertSameState(ChunkColumn a, ChunkColumn b, String where) throws Exception {
        for (int s = 0; s < ChunkColumn.SECTION_COUNT; s++) {
            assertArrayEquals(a.sections()[s].blocks(), b.sections()[s].blocks(), where + " 섹션 " + s);
            assertEquals(a.sections()[s].nonAirCount(), b.sections()[s].nonAirCount(), where + " nonAir " + s);
            // 디스크에서 읽은 구역의 무작위 틱 판정은 바닐라 recalcBlockCounts(용암 유체만 셈)다. 옛 디코드는 칸마다 setBlock 이라 물도 세므로
            // (바닐라 setBlockState 와 같음) 그 구역 객체의 판정이 아니라 블록을 통째로 넣은(재계수) 구역과 비교한다.
            ChunkSection recounted = new ChunkSection();
            recounted.replaceBlocks(a.sections()[s].blocks(), 0);
            assertEquals(recounted.isRandomlyTicking(), b.sections()[s].isRandomlyTicking(), where + " ticking " + s);
        }
        assertArrayEquals(a.worldSurfaceHeightmap(), b.worldSurfaceHeightmap(), where + " WORLD_SURFACE");
        assertArrayEquals(a.motionBlockingHeightmap(), b.motionBlockingHeightmap(), where + " MOTION_BLOCKING");
        assertArrayEquals(a.motionBlockingNoLeavesHeightmap(), b.motionBlockingNoLeavesHeightmap(), where + " MB_NO_LEAVES");
        assertArrayEquals(a.oceanFloorHeightmap(), b.oceanFloorHeightmap(), where + " OCEAN_FLOOR");
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                assertEquals(a.surfaceBlockY(x, z), b.surfaceBlockY(x, z), where);
                assertEquals(a.worldSurfaceWgBlockY(x, z), b.worldSurfaceWgBlockY(x, z), where);
                assertEquals(a.wgOceanFloorHeight(x, z), b.wgOceanFloorHeight(x, z), where);
                assertEquals(a.oceanFloorBlockY(x, z), b.oceanFloorBlockY(x, z), where);
            }
        assertEquals(a.dirty(), b.dirty(), where + " dirty");
        // 재저장 NBT 의 블록 관련 부분(섹션·높이맵). 루트의 나머지 키는 블록 외 경로(extraNbt 보존)라 기준 구현에 없다.
        NbtCompound na = codec.toNbt(a, 3, -7), nb = codec.toNbt(b, 3, -7);
        for (String key : new String[]{"sections", "Heightmaps"}) {
            assertEquals(na.tags().get(key) == null, nb.tags().get(key) == null, where + " " + key);
            if (na.tags().get(key) == null) continue;
            NbtCompound wa = new NbtCompound(), wb = new NbtCompound();
            wa.put(key, na.tags().get(key));
            wb.put(key, nb.tags().get(key));
            assertArrayEquals(nbtBytes(wa), nbtBytes(wb), where + " 재저장 " + key);
        }
    }

    @Test
    void randomChunksMatchLegacyDecode() throws Exception {
        Random r = new Random(0xC109);
        for (int iter = 0; iter < 60; iter++) {
            NbtCompound nbt = codec.toNbt(randomColumn(r), 3, -7);
            assertSameState(legacyDecode(nbt), codec.fromNbt(nbt), "iter=" + iter);
        }
    }

    static volatile long sink;

    // -Dfurnace.bench=true 일 때만. 디스크 로드 fromNbt: 전(블록별) / 후(벌크). 교대 5라운드, 워밍업 200 + 측정 200 회 x 4청크.
    @Test
    void bench() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        Random r = new Random(11);
        NbtCompound[] nbts = new NbtCompound[4];
        for (int i = 0; i < nbts.length; i++) nbts[i] = codec.toNbt(randomColumn(r), 0, 0);
        NbtCompound vanilla;
        try (var in = AnvilBulkDecodeParityTest.class.getResourceAsStream("/storage/vanilla-chunk--25.-4.nbt.gz")) {
            vanilla = NbtIo.readGzip(in);
        }
        nbts[0] = vanilla;   // 실제 바닐라 청크 1개 포함
        for (int round = 0; round < 5; round++) {
            for (int g = 0; g < 2; g++) {
                boolean old = g == 0;
                Runnable run = () -> {
                    for (NbtCompound n : nbts) {
                        ChunkColumn c = old ? legacyDecode(n) : codec.fromNbt(n);
                        sink += c.surfaceBlockY(3, 3);
                    }
                };
                for (int w = 0; w < 200; w++) run.run();
                long t = System.nanoTime();
                for (int w = 0; w < 200; w++) run.run();
                double us = (System.nanoTime() - t) / 1000.0 / (200 * nbts.length);
                System.out.printf("[BENCH] N-09 round=%d %s %.1f us/청크%n", round, old ? "전" : "후", us);
            }
        }
    }
}
