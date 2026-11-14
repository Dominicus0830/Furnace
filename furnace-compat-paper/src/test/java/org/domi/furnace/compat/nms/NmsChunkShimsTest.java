package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * N1·N2a: 청크 shim(LevelChunk·LevelChunkSection·PalettedContainer·Heightmap·ServerChunkCache)이 서버 청크의 보기인가, Level 블록 읽기·쓰기가
 * 바닐라 플래그 뜻대로인가. 정답은 서버 내부(Region.getBlock·heightAt)와 바닐라 1.21.1 동작(바이트코드로 확인한 것, 주석).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsChunkShimsTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    org.domi.furnace.api.world.World world;
    org.domi.furnace.api.world.BlockPos at;
    int cx, cz;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Chunky");
        server.tick(3);
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        cx = Math.floorDiv(at.x(), 16);
        cz = Math.floorDiv(at.z(), 16);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        world = level.getWorld().furnaceHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(Supplier<T> task) { return server.onRegion(at, task); }

    int truth(int x, int y, int z) { return server.internals().world().regionizer().ownerOf(x >> 4, z >> 4).getBlock(x, y, z); }

    @Test
    void heightAccessorMatchesDimension() {
        assertEquals(-64, level.getMinBuildHeight());
        assertEquals(384, level.getHeight());
        assertEquals(320, level.getMaxBuildHeight());
        assertEquals(-4, level.getMinSection());
        assertEquals(20, level.getMaxSection());
        assertEquals(24, level.getSectionsCount());
        assertEquals(0, level.getSectionIndex(-64));
        assertEquals(23, level.getSectionIndex(319));
        assertTrue(level.isOutsideBuildHeight(320));
        assertSame(Level.OVERWORLD, level.dimension());
        assertSame(level, level.getServer().getLevel(Level.OVERWORLD));
    }

    @Test
    void sectionViewMatchesServerChunkCellByCell() {
        String r = on(() -> {
            LevelChunk chunk = level.getChunk(cx, cz);
            assertEquals(cx, chunk.getPos().x);
            assertEquals(ChunkStatus.FULL, chunk.getPersistedStatus());
            LevelChunkSection[] secs = chunk.getSections();
            assertEquals(24, secs.length);
            int checked = 0;
            for (int si : new int[] {0, 1, 4, 23}) {
                LevelChunkSection s = secs[si];
                PalettedContainer<BlockState> states = s.getStates();
                int baseY = -64 + si * 16;
                List<BlockState> all = new ArrayList<>();
                states.getAll(all::add);
                List<BlockState> firstSeen = new ArrayList<>();
                boolean onlyAir = true;
                for (int i = 0; i < 4096; i++) {
                    int x = i & 15, z = i >> 4 & 15, y = i >> 8;
                    int id = truth(cx * 16 + x, baseY + y, cz * 16 + z);
                    BlockState want = Block.stateById(id);
                    assertSame(want, states.get(x, y, z), "칸 " + x + "," + y + "," + z);
                    assertSame(want, s.getBlockState(x, y, z));
                    if (!firstSeen.contains(want)) firstSeen.add(want);
                    if (!want.isAir()) onlyAir = false;
                    checked++;
                }
                assertEquals(firstSeen, all, "getAll = 칸에 있는 값마다 한 번, 처음 나온 순서(바닐라: 저장소에서 처음 나온 팔레트 번호 순)");
                assertEquals(onlyAir, s.hasOnlyAir(), "섹션 " + si);
            }
            return "ok " + checked;
        });
        assertEquals("ok " + 4 * 4096, r);
    }

    @Test
    void levelReadsAndHeightmapsMatchServer() {
        on(() -> {
            int x = at.x(), z = at.z();
            int surface = server.internals().world().regionizer().ownerOf(cx, cz).heightAt(org.domi.furnace.server.world.HeightmapType.WORLD_SURFACE, x, z);
            assertEquals(surface, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), "Level.getHeight = 첫 빈 y");
            LevelChunk c = level.getChunkAt(new BlockPos(x, 0, z));
            assertEquals(surface - 1, c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15), "ChunkAccess.getHeight = 가장 높은 블록 y");
            Heightmap hm = c.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING);
            assertEquals(level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), hm.getFirstAvailable(x & 15, z & 15));
            assertEquals(-64, c.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).getFirstAvailable(0, 0), "월드젠 종류는 비어 있다");
            // 바닐라 ChunkAccess.getHeight 는 없는 하이트맵을 primeHeightmaps 로 블록에서 계산한다 — WG 종류는 술어가 같은 살아 있는 종류와 같은 값
            assertEquals(c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15), c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15));
            assertEquals(c.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15), c.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15));
            assertEquals(surface, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z));
            // getRawData: 9비트(384 → ceillog2(385)), long 당 7칸 — 풀면 첫 빈 y - 최저 높이
            long[] raw = hm.getRawData();
            assertEquals(37, raw.length);
            for (int i = 0; i < 256; i++) {
                long v = raw[i / 7] >>> ((i % 7) * 9) & 511L;
                assertEquals(hm.getFirstAvailable(i & 15, i >> 4) + 64, v);
            }
            BlockPos below = new BlockPos(x, surface - 1, z);
            assertSame(Block.stateById(truth(x, surface - 1, z)), level.getBlockState(below));
            assertSame(Blocks.VOID_AIR.defaultBlockState(), level.getBlockState(new BlockPos(x, 400, z)), "높이 밖 = void_air");
            assertSame(level.getBlockState(below).getFluidState(), level.getFluidState(below));
            assertSame(Blocks.VOID_AIR.defaultBlockState(), level.getBlockStateIfLoaded(new BlockPos(x, -70, z)));
            // 바이옴: 서버 biomeNow 와 같은 키
            var b = c.getNoiseBiome(x >> 2, (surface) >> 2, z >> 2);
            assertEquals(world.biomeNow(new org.domi.furnace.api.world.BlockPos(x, surface, z)).orElseThrow(), b.unwrapKey().orElseThrow().location().toString());
            var sec = c.getSection(c.getSectionIndex(surface));
            assertSame(b, sec.getBiomes().get((x >> 2) & 3, (surface >> 2) & 3, (z >> 2) & 3), "같은 바이옴은 같은 holder");
            return null;
        });
    }

    @Test
    void copyIsDetachedAndViewRefusesWrites() {
        on(() -> {
            var s = level.getChunk(cx, cz).getSection(0);
            PalettedContainer<BlockState> view = s.getStates();
            PalettedContainer<BlockState> copy = view.copy();
            BlockState before = view.get(3, 3, 3);
            BlockState old = copy.getAndSet(3, 3, 3, Blocks.GOLD_BLOCK.defaultBlockState());
            assertSame(before, old);
            assertSame(Blocks.GOLD_BLOCK.defaultBlockState(), copy.get(3, 3, 3));
            assertSame(before, view.get(3, 3, 3), "사본을 고쳐도 청크는 그대로");
            assertThrows(UnsupportedNmsCall.class, () -> view.set(3, 3, 3, Blocks.STONE.defaultBlockState()));
            int[] seen = new int[1];
            view.count((v, n) -> seen[0] += n);
            assertEquals(4096, seen[0]);
            assertTrue(view.maybeHas(st -> st == before));
            assertFalse(view.maybeHas(st -> st == Blocks.GOLD_BLOCK.defaultBlockState()));
            return null;
        });
    }

    /**
     * N2b-1 정체성: 바닐라처럼 로드 동안 LevelChunk 하나, 섹션 배열 하나, 칸마다 섹션 하나, 컨테이너·하이트맵 하나. 섹션 하나만 쥐고 있어도
     * 청크 shim(그리고 배열)이 살아 있어 다음 getChunk 가 같은 배열을 준다(설계 3.2 R14). 보기 섹션은 쓰기를 거절한다(N2b-2 전).
     */
    @Test
    void chunkShimIdentityIsStableWhileHeld() {
        on(() -> {
            LevelChunk c = level.getChunk(cx, cz);
            assertSame(c, level.getChunk(cx, cz));
            assertSame(c, level.getChunkSource().getChunkNow(cx, cz));
            LevelChunkSection[] secs = c.getSections();
            assertSame(secs, c.getSections());
            assertSame(secs[3], c.getSection(3));
            assertSame(secs[3].getStates(), secs[3].getStates());
            assertSame(secs[3].getBiomes(), secs[3].getBiomes());
            for (Heightmap.Types t : Heightmap.Types.values()) {
                if (t.keepAfterWorldgen()) assertNotNull(c.heightmaps.get(t), "로드된 청크의 하이트맵 필드는 살아 있는 네 종류를 늘 가진다: " + t);
            }
            assertSame(c.heightmaps.get(Heightmap.Types.MOTION_BLOCKING), c.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING));
            assertEquals(c.getHeight(Heightmap.Types.MOTION_BLOCKING, 5, 6) + 1, c.heightmaps.get(Heightmap.Types.MOTION_BLOCKING).getFirstAvailable(5, 6));
            Heightmap wg = c.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            assertSame(wg, c.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG), "바닐라: 만들어 넣은 뒤로 같은 객체");
            assertTrue(c.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE_WG), "바닐라: 만들어 넣으면 있음");
            assertThrows(UnsupportedNmsCall.class, () -> secs[3].setBlockState(1, 1, 1, Blocks.STONE.defaultBlockState()));
            return null;
        });
        LevelChunkSection held = on(() -> level.getChunk(cx, cz).getSection(5));
        for (int i = 0; i < 3; i++) System.gc();
        on(() -> {
            assertSame(held, level.getChunk(cx, cz).getSection(5), "섹션을 쥐고 있으면 같은 청크 shim·배열");
            return null;
        });
    }

    /** 바닐라: 동굴 공기만 있는 섹션은 hasOnlyAir(공기 아닌 칸 0)라 LevelChunk.getBlockState 는 air, 섹션 직접 읽기는 cave_air. */
    @Test
    void caveAirOnlySectionReadsAsAirThroughChunk() {
        BlockData caveAir = BlockData.of("minecraft:cave_air");
        int x0 = cx * 16, z0 = cz * 16;
        // 바닐라 setBlockState 는 공기뿐인 섹션에 공기류를 놓지 않는다(아무것도 안 함) — 돌 한 칸으로 섹션을 비지 않게 한 뒤 채우고 마지막에 그 칸도 바꾼다
        var stone = BlockData.of("minecraft:stone");
        var keep = new org.domi.furnace.api.world.BlockPos(x0 + 15, 303, z0 + 15);
        on(() -> world.editBlocksNow(org.domi.furnace.api.world.BlockEditOptions.FAST, ed -> {
            ed.set(keep, stone);
        }));
        on(() -> world.editBlocksNow(org.domi.furnace.api.world.BlockEditOptions.FAST, ed -> {
            for (int y = 288; y <= 303; y++)
                for (int z = z0; z <= z0 + 15; z++)
                    for (int x = x0; x <= x0 + 15; x++) if (!(x == keep.x() && y == keep.y() && z == keep.z())) ed.set(x, y, z, caveAir);
        }));
        on(() -> world.editBlocksNow(org.domi.furnace.api.world.BlockEditOptions.FAST, ed -> ed.set(keep, caveAir)));
        on(() -> {
            LevelChunk c = level.getChunk(cx, cz);
            LevelChunkSection s = c.getSection(c.getSectionIndex(290));
            assertTrue(s.hasOnlyAir());
            assertSame(Blocks.CAVE_AIR.defaultBlockState(), s.getBlockState(1, 2, 3));
            assertSame(Blocks.AIR.defaultBlockState(), c.getBlockState(new BlockPos(x0 + 1, 290, z0 + 3)));
            assertNull(c.setBlockState(new BlockPos(x0 + 1, 290, z0 + 3), Blocks.AIR.defaultBlockState(), false), "공기뿐인 섹션에 공기류 = null(바닐라)");
            return null;
        });
        on(() -> world.editBlocksNow(org.domi.furnace.api.world.BlockEditOptions.FAST,
                ed -> ed.fill(new org.domi.furnace.api.world.BlockPos(x0, 288, z0), new org.domi.furnace.api.world.BlockPos(x0 + 15, 303, z0 + 15), BlockData.AIR)));
    }

    /**
     * 보기의 무작위 틱 판정은 서버 섹션의 바닐라 개수를 따른다(PR #358): 칸 쓰기로 놓은 물은 tickingFluidCount 를 올려
     * isRandomlyTickingFluids·isRandomlyTicking 이 참, 물 블록 자체는 무작위 틱이 아니라 isRandomlyTickingBlocks 는 거짓. 지우면 다시 거짓.
     */
    @Test
    void viewRandomTickPartsFollowServerVanillaCounts() {
        int x0 = cx * 16, z0 = cz * 16;
        BlockPos p = new BlockPos(x0 + 4, 312, z0 + 4);
        BlockState water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        on(() -> {
            LevelChunk c = level.getChunk(cx, cz);
            LevelChunkSection s = c.getSection(c.getSectionIndex(312));
            assertTrue(s.hasOnlyAir(), "맨 위 섹션은 비어 있어야 한다");
            assertFalse(s.isRandomlyTicking());
            assertTrue(level.setBlock(p, water, 2 | 16));
            assertTrue(s.isRandomlyTickingFluids(), "칸 쓰기로 놓은 물 = tickingFluid 1(바닐라)");
            assertFalse(s.isRandomlyTickingBlocks());
            assertTrue(s.isRandomlyTicking());
            assertTrue(level.setBlock(p, air, 2 | 16));
            assertFalse(s.isRandomlyTickingFluids());
            assertFalse(s.isRandomlyTicking());
            return null;
        });
    }

    /** 플래그 3 은 이웃 갱신(받침 잃은 횃불이 떨어짐), 2|16 은 이웃·모양 갱신 없음(횃불이 남음). */
    @Test
    void setBlockFlagsFollowVanillaMeaning() {
        int x = at.x() + 3, z = at.z() + 3;
        int y = on(() -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        BlockPos base = new BlockPos(x, y, z), torch = base.above();
        BlockState stone = Blocks.STONE.defaultBlockState(), torchState = Blocks.TORCH.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        try {
            for (int flags : new int[] {2 | 16, 3}) {
                on(() -> {
                    assertTrue(level.setBlock(base, stone, 3));
                    assertTrue(level.setBlock(torch, torchState, 3));
                    assertFalse(level.setBlock(torch, torchState, 3), "같은 상태면 false");
                    assertTrue(level.setBlock(base, air, flags));
                    return null;
                });
                server.tick(2);
                BlockState left = on(() -> level.getBlockState(torch));
                if (flags == 3) assertSame(air, left, "이웃 갱신: 횃불이 떨어진다");
                else assertSame(torchState, left, "플래그 2|16: 횃불이 남는다");
                on(() -> level.setBlock(torch, air, 2 | 16));
            }
            // 32 는 바닐라 Level.setBlock 층에서 모양 갱신 플래그에서 지워질 뿐(flags & -34) — 던지지 않고 놓는다. 64(피스톤이 옮김)도 그대로 받는다
            assertTrue(on(() -> level.setBlock(base, stone, 3 | 32)));
            assertTrue(on(() -> level.setBlock(base, air, 3 | 64)));
        } finally {
            on(() -> {
                level.setBlock(torch, air, 2 | 16);
                return level.setBlock(base, air, 2 | 16);
            });
        }
    }

    /** maxUpdateDepth: 깊이 1 이면 모양 갱신이 이웃 한 칸에서 멈춘다 — 카펫 두 장 중 위 카펫은 남는다(바닐라 Level.setBlock 의 recursionLeft - 1). */
    @Test
    void setBlockMaxUpdateDepthLimitsShapeChain() {
        int x = at.x() - 5, z = at.z() - 5;
        int y = on(() -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)) + 3;
        BlockPos base = new BlockPos(x, y, z), c1 = base.above(), c2 = c1.above();
        BlockState carpet = Blocks.WHITE_CARPET.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        try {
            on(() -> {
                level.setBlock(base, Blocks.STONE.defaultBlockState(), 2 | 16);
                level.setBlock(c1, carpet, 2 | 16);
                level.setBlock(c2, carpet, 2 | 16);
                return level.setBlock(base, air, 3, 1);
            });
            server.tick(2);
            assertSame(air, on(() -> level.getBlockState(c1)), "아래 카펫은 떨어진다");
            assertSame(carpet, on(() -> level.getBlockState(c2)), "깊이 0 이라 위 카펫까지 번지지 않는다");
        } finally {
            on(() -> { level.setBlock(c2, air, 2 | 16); level.setBlock(c1, air, 2 | 16); return level.setBlock(base, air, 2 | 16); });
        }
    }

    /** 플래그 2 의 클라이언트 알림은 바닐라처럼 틱 끝에 섹션별로 모은다 — 한 섹션의 많은 칸은 section_blocks_update 로 간다. */
    @Test
    void setBlockClientUpdatesAreBatchedPerSection() {
        int x0 = (cx << 4), z0 = (cz << 4), y0 = 208;
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        try {
            server.tick(1);
            tp.clearReceived();
            on(() -> {
                for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) level.setBlock(new BlockPos(x0 + dx, y0, z0 + dz), stone, 2);
                return null;
            });
            server.tick(2);
            assertEquals(0, tp.received(PacketTypes.Clientbound.BLOCK_UPDATE).stream().filter(u -> u.pos().y() == y0).count(), "칸마다 보내지 않는다");
            assertFalse(tp.received(PacketTypes.Clientbound.SECTION_BLOCKS_UPDATE).isEmpty(), "섹션 묶음으로 보낸다");
        } finally {
            on(() -> {
                for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) level.setBlock(new BlockPos(x0 + dx, y0, z0 + dz), air, 2 | 16);
                return null;
            });
        }
    }

    /** LevelChunk.setBlockState: 옛 상태를 돌려주고(같으면 null) 클라이언트에 알리지 않는다. Level.setBlock(플래그 3)은 알린다. */
    @Test
    void chunkSetBlockStateDoesNotNotifyClients() {
        int x = at.x() - 2, z = at.z() + 1;
        int y = on(() -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)) + 1;
        BlockPos p = new BlockPos(x, y, z);
        org.domi.furnace.api.world.BlockPos ap = new org.domi.furnace.api.world.BlockPos(x, y, z);
        try {
            tp.clearReceived();
            BlockState old = on(() -> level.getChunkAt(p).setBlockState(p, Blocks.GLASS.defaultBlockState(), false));
            assertSame(Blocks.AIR.defaultBlockState(), old);
            assertNull(on(() -> level.getChunkAt(p).setBlockState(p, Blocks.GLASS.defaultBlockState(), false)), "같은 상태 = null");
            server.tick(2);
            assertEquals("minecraft:glass", on(() -> world.blockNow(ap).orElseThrow().type()));
            assertTrue(tp.received(PacketTypes.Clientbound.BLOCK_UPDATE).stream().noneMatch(u -> u.pos().equals(ap)), "칸 갱신 패킷 없음");
            on(() -> level.setBlock(p, Blocks.STONE.defaultBlockState(), 3));
            server.tick(2);
            assertTrue(tp.received(PacketTypes.Clientbound.BLOCK_UPDATE).stream().anyMatch(u -> u.pos().equals(ap)), "플래그 2 면 칸 갱신 패킷");
        } finally {
            on(() -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 3));
        }
    }

    @Test
    void unloadedAndOffThreadBehaviour() {
        int fx = cx + 400, fz = cz;   // 로드되지 않은 먼 청크
        assertFalse(level.hasChunk(fx, fz));
        assertNull(level.getChunkIfLoaded(fx, fz));
        assertNull(level.getChunkSource().getChunkAtIfLoadedImmediately(fx, fz));
        assertNull(level.getChunkSource().getChunk(fx, fz, false));
        assertThrows(IllegalStateException.class, () -> level.getChunk(fx, fz));
        assertEquals(-1, (int) on(() -> NmsChunks.bridge().stateIdNow(world, fx * 16, 0, fz * 16)), "region 이 없는 청크도 '로드 안 됨' 표시값");
        assertNull(on(() -> level.getBlockStateIfLoaded(new BlockPos(fx * 16, 0, fz * 16))));
        assertEquals(-64, (int) on(() -> level.getHeight(Heightmap.Types.WORLD_SURFACE, fx * 16, fz * 16)), "로드 안 된 청크 = 최저 높이(바닐라)");
        // 로드된 청크 보기는 아무 스레드에서 얻지만, 읽기는 소유 region 스레드에서만
        LevelChunk c = level.getChunkSource().getChunkAtIfLoadedImmediately(cx, cz);
        assertNotNull(c);
        assertEquals(c, level.getChunk(cx, cz), "같은 청크 보기는 equals");
        assertThrows(ThreadOwnershipException.class, () -> c.getBlockState(new BlockPos(at.x(), at.y(), at.z())));
        assertThrows(ThreadOwnershipException.class, () -> c.getSection(0).getStates().get(0, 0, 0));
    }

    @Test
    void chunkStatusAndHeightmapTypesMatchVanilla() {
        List<String> names = new ArrayList<>();
        for (ChunkStatus s : ChunkStatus.getStatusList()) names.add(s.getName());
        assertEquals(List.of("minecraft:empty", "minecraft:structure_starts", "minecraft:structure_references", "minecraft:biomes", "minecraft:noise",
                "minecraft:surface", "minecraft:carvers", "minecraft:features", "minecraft:initialize_light", "minecraft:light", "minecraft:spawn",
                "minecraft:full"), names);
        assertSame(ChunkStatus.FULL, ChunkStatus.byName("full"));
        assertSame(ChunkStatus.EMPTY, ChunkStatus.byName("no_such"), "모르는 이름 = 기본값 empty");
        assertSame(ChunkStatus.EMPTY, ChunkStatus.EMPTY.getParent());
        assertTrue(ChunkStatus.FULL.isOrAfter(ChunkStatus.LIGHT));
        assertEquals(11, ChunkStatus.FULL.getIndex());
        assertEquals("minecraft:noise", ChunkStatus.NOISE.toString());
        assertFalse(Heightmap.Types.OCEAN_FLOOR_WG.keepAfterWorldgen());
        assertTrue(Heightmap.Types.MOTION_BLOCKING.sendToClient());
        assertFalse(Heightmap.Types.OCEAN_FLOOR.sendToClient());
        assertEquals("MOTION_BLOCKING_NO_LEAVES", Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.getSerializationKey());
    }

    @Test
    void entityHandleMembers() {
        var e = ((org.bukkit.craftbukkit.entity.CraftPlayer) Compat.player(tp.player())).getHandle();
        var loc = tp.player().location();
        assertEquals(new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z())), e.blockPosition());
        assertEquals(cx, e.chunkPosition().x);
        assertEquals(loc.x(), e.position().x(), 1e-9);
        assertEquals("minecraft:player", net.minecraft.world.entity.EntityType.getKey(e.getType()).toString());
        assertSame(e, on(() -> level.getEntity(e.getUUID())), "getEntity(UUID)");
    }
}
