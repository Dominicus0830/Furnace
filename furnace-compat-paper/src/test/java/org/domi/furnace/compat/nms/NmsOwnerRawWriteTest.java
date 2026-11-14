package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * N2b-2 주인 스레드 원시 쓰기: 섹션 setBlockState·컨테이너 set·Heightmap.setRawData/update 가 서버 청크에 바로 들어가되 바닐라처럼 조용한가
 * (패킷·저장 표시·빛·하늘빛 원천 그대로, 하이트맵은 낡은 값). 개수·하이트맵 수치 자체는 VanillaRawWriteOracleTest(바닐라 jar 정답기)가 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsOwnerRawWriteTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    org.domi.furnace.api.world.BlockPos at;
    int cx, cz;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("RawWriter");
        server.tick(3);
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        cx = Math.floorDiv(at.x(), 16);
        cz = Math.floorDiv(at.z(), 16);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(Supplier<T> task) { return server.onRegion(at, task); }

    ChunkColumn column() { return server.internals().world().regionizer().ownerOf(cx, cz).loadedOwnedChunk(cx, cz); }

    int truth(int x, int y, int z) { return server.internals().world().regionizer().ownerOf(x >> 4, z >> 4).getBlock(x, y, z); }

    /** 섹션 setBlockState: 칸·개수만 바뀌고 하이트맵·빛·저장 표시·클라이언트는 그대로(바닐라 LevelChunkSection.setBlockState). */
    @Test
    void sectionSetBlockStateIsQuietAndKeepsStaleHeightmap() {
        int lx = 4, lz = 9, x = cx * 16 + lx, z = cz * 16 + lz;
        int h = on(() -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        BlockPos top = new BlockPos(x, h - 1, z);
        org.domi.furnace.api.world.BlockPos apiTop = new org.domi.furnace.api.world.BlockPos(x, h - 1, z);
        int si = level.getSectionIndex(h - 1);
        BlockState[] was = new BlockState[1];
        try {
            tp.clearReceived();
            on(() -> {
                LevelChunk c = level.getChunk(cx, cz);
                ChunkColumn col = column();
                col.clearDirty();
                int sky = col.light().skyAt(x, h - 1, z), source = col.light().lowestSource[lz * 16 + lx];
                int[] before = c.furnaceSectionCounts(si);
                LevelChunkSection sec = c.getSection(si);
                was[0] = sec.getBlockState(lx, (h - 1) & 15, lz);
                assertFalse(was[0].isAir(), "WORLD_SURFACE 꼭대기는 공기 아님");
                BlockState old = sec.setBlockState(lx, (h - 1) & 15, lz, Blocks.AIR.defaultBlockState());
                assertSame(was[0], old, "옛 상태");
                assertSame(Blocks.AIR.defaultBlockState(), level.getBlockState(top), "Level 읽기도 서버 칸을 본다");
                assertEquals(0, truth(x, h - 1, z));
                int[] after = c.furnaceSectionCounts(si);
                assertEquals(before[0] - 1, after[0], "바닐라 setBlockState: nonEmpty -1");
                assertEquals(h, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), "하이트맵은 낡은 값(바닐라)");
                assertEquals(h, c.heightmaps.get(Heightmap.Types.WORLD_SURFACE).getFirstAvailable(lx, lz));
                assertTrue(col.heightmapsFixed());
                assertFalse(col.dirty(), "바닐라 섹션 쓰기는 저장 표시를 세우지 않는다");
                assertEquals(sky, col.light().skyAt(x, h - 1, z), "빛 계산 안 함(D2)");
                assertEquals(source, col.light().lowestSource[lz * 16 + lx], "하늘빛 원천 그대로");
                // 블록 컨테이너 set: 칸만 바뀌고 개수는 그대로(바닐라 PalettedContainer.set)
                sec.getStates().set(lx, (h - 1) & 15, lz, was[0]);
                assertSame(was[0], level.getBlockState(top));
                assertArrayEquals(after, c.furnaceSectionCounts(si), "컨테이너 set 은 개수를 안 고친다");
                assertFalse(sec.hasOnlyAir());
                sec.recalcBlockCounts();   // 개수를 블록에 맞춰 둔다(바닐라 재계수)
                return null;
            });
            server.tick(2);
            assertTrue(tp.received(PacketTypes.Clientbound.BLOCK_UPDATE).stream().noneMatch(u -> u.pos().equals(apiTop)), "칸 갱신 패킷 없음");
        } finally {
            on(() -> level.setBlock(top, was[0] != null ? was[0] : level.getBlockState(top), 2 | 16));
        }
    }

    /** setRawData(맞는 길이)는 값을 그대로, 틀린 길이는 그 종류를 블록에서 다시 센다. update 는 바닐라 규칙. */
    @Test
    void heightmapSetRawDataAndUpdate() {
        int lx = 11, lz = 2, x = cx * 16 + lx, z = cz * 16 + lz;
        on(() -> {
            LevelChunk c = level.getChunk(cx, cz);
            Heightmap hm = c.heightmaps.get(Heightmap.Types.MOTION_BLOCKING);
            assertNotNull(hm);
            long[] orig = hm.getRawData().clone();
            try {
                long[] raw = orig.clone();
                int i = lz * 16 + lx;
                raw[i / 7] = (raw[i / 7] & ~(511L << ((i % 7) * 9))) | (300L << ((i % 7) * 9));
                hm.setRawData(c, Heightmap.Types.MOTION_BLOCKING, raw);
                assertEquals(300 - 64, hm.getFirstAvailable(lx, lz));
                assertEquals(300 - 64, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), "Level.getHeight 도 그 값");
                raw[0] = 0;   // 바닐라는 배열을 복사한다 — 넘긴 배열을 나중에 고쳐도 하이트맵은 그대로
                assertEquals(300 - 64, hm.getFirstAvailable(lx, lz));
                // update: 하이트맵보다 위에 막는 블록이면 그 위로
                assertTrue(hm.update(lx, 250, lz, Blocks.STONE.defaultBlockState()));
                assertEquals(251, hm.getFirstAvailable(lx, lz));
                assertFalse(hm.update(lx, 10, lz, Blocks.STONE.defaultBlockState()), "아래쪽은 그대로");
            } finally {
                hm.setRawData(c, Heightmap.Types.MOTION_BLOCKING, orig);
            }
            assertArrayEquals(orig, hm.getRawData());
            // 틀린 길이 → 경고 + 그 종류를 블록에서(바닐라 prime): WORLD_SURFACE 를 망가뜨려 두고 다시 센 값이 지금 블록의 꼭대기인가
            Heightmap ws = c.heightmaps.get(Heightmap.Types.WORLD_SURFACE);
            long[] wsOrig = ws.getRawData().clone();
            ws.setRawData(c, Heightmap.Types.WORLD_SURFACE, new long[wsOrig.length]);
            assertEquals(-64, ws.getFirstAvailable(lx, lz));
            ws.setRawData(c, Heightmap.Types.WORLD_SURFACE, new long[5]);
            int y = 319;
            while (y >= -64 && level.getBlockState(new BlockPos(x, y, z)).isAir()) y--;
            assertEquals(y + 1, ws.getFirstAvailable(lx, lz));
            ws.setRawData(c, Heightmap.Types.WORLD_SURFACE, wsOrig);
            return null;
        });
    }

    /** 바이옴 컨테이너 getAndSet 은 서버 바이옴 칸에 바로 쓴다. */
    @Test
    @SuppressWarnings("unchecked")
    void biomeContainerWritesThrough() {
        on(() -> {
            LevelChunk c = level.getChunk(cx, cz);
            // 바닐라 getBiomes 는 PalettedContainerRO — 플러그인(FAWE 등)은 PalettedContainer 로 바꿔 쓴다
            PalettedContainer<Holder<Biome>> b = (PalettedContainer<Holder<Biome>>) c.getSection(10).getBiomes();
            Holder<Biome> old = b.get(1, 2, 3), other = old;
            for (int si = 0; si < 24 && other == old; si++) {
                var o = c.getSection(si).getBiomes();
                for (int i = 0; i < 64 && other == old; i++) other = o.get(i & 3, i >> 4, (i >> 2) & 3);
            }
            try {
                assertSame(old, b.getAndSet(1, 2, 3, other));
                assertSame(other, b.get(1, 2, 3));
                assertSame(other, c.getNoiseBiome(cx * 4 + 1, (c.getSectionYFromSectionIndex(10) << 2) + 2, cz * 4 + 3));
            } finally {
                b.set(1, 2, 3, old);
            }
            assertSame(old, b.get(1, 2, 3));
            return null;
        });
    }

    /** 주인 아닌 스레드의 쓰기는 지금은 거절(다른 스레드 쓰기 일지는 N2b-4, D1). */
    @Test
    void offThreadRawWritesThrow() {
        LevelChunk c = level.getChunkSource().getChunkAtIfLoadedImmediately(cx, cz);
        assertNotNull(c);
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertThrows(ThreadOwnershipException.class, () -> c.getSection(0).setBlockState(0, 0, 0, stone));
        assertThrows(ThreadOwnershipException.class, () -> c.getSection(0).getStates().set(0, 0, 0, stone));
        Heightmap hm = c.heightmaps.get(Heightmap.Types.WORLD_SURFACE);
        assertThrows(ThreadOwnershipException.class, () -> hm.setRawData(c, Heightmap.Types.WORLD_SURFACE, new long[37]));
        assertThrows(ThreadOwnershipException.class, () -> hm.update(0, 0, 0, stone));
    }
}
