package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CapturedBlockState;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.util.BlockStateListPopulator;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.thread.PrimaryThread;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.JavaRandomWorldgen;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-211 1단계: NMS {@code ConfiguredFeature#place}·{@code PlacedFeature#place} 와 Paper 쓰기 기록. 기대 동작은 Paper 1.21.1(Level 의 captureTreeGeneration,
 * BlockStateListPopulator)과 FAWE 2.16 PaperweightFaweAdapter#generateTree/generateFeature(populator 하위 클래스가 쓰기·읽기를 서버 레벨로 돌리고,
 * 서버 레벨 기록을 모은다). 비교 기준은 서버의 Paper generateTree 기록(T-141 E5, 같은 시드의 java.util.Random = 바닐라 LegacyRandomSource 수열).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsFeaturePlaceTest {

    FurnaceTestServer server;
    ServerLevel level;
    ChunkGenerator generator;
    org.domi.furnace.api.world.BlockPos origin;
    int next;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        origin = new org.domi.furnace.api.world.BlockPos(4 * 16, -20, 4 * 16);   // 슈퍼플랫 땅(-61..-60) 위 공중, region 가장자리를 피한 청크
        for (int dx = -2; dx <= 3; dx++)
            for (int dz = -2; dz <= 3; dz++) server.loadChunk(origin.chunkX() + dx, origin.chunkZ() + dz);
        level = ((CraftWorld) Compat.world(server.overworld())).getHandle();
        generator = level.getChunkSource().getGenerator();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    /** FAWE TaskManager.sync 자리: 전역 스레드의 Bukkit 동기 작업(region 빌림 구간). */
    <T> T sync(Supplier<T> task) {
        return server.onGlobal(() -> {
            try (var s = PrimaryThread.enter()) {
                return task.get();
            }
        });
    }

    /** 시험마다 다른 자리(10칸 간격, 같은 두 청크 안). 바로 아래는 흙. */
    BlockPos fresh() {
        int i = next++;
        BlockPos at = new BlockPos(origin.x() + 3 + (i % 3) * 10, origin.y(), origin.z() + 3 + (i / 3) * 10);
        owner(() -> level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 2));
        return at;
    }

    static ConfiguredFeature<?, ?> configured(String id) { return NmsWorldgen.configuredFeatures().get(ResourceLocation.parse(id)); }

    static PlacedFeature placed(String id) { return NmsWorldgen.placedFeatures().get(ResourceLocation.parse(id)); }

    /** 서버의 Paper generateTree 기록(월드 안 바꿈): "x,y,z 상태번호". */
    List<String> serverCapture(String id, BlockPos at, long seed) {
        return owner(() -> {
            var r = server.internals().world().regionizer().ownerOf(at.getX() >> 4, at.getZ() >> 4).level()
                    .placeFeatureWith(id, new Pos(at.getX(), at.getY(), at.getZ()), new JavaRandomWorldgen(new Random(seed)), false);
            assertTrue(r.placed());
            List<String> out = new ArrayList<>();
            for (long[] w : r.writes()) out.add(w[0] + "," + w[1] + "," + w[2] + " " + w[3]);
            return out;
        });
    }

    static List<String> states(List<? extends CraftBlockState> list) {
        List<String> out = new ArrayList<>();
        for (CraftBlockState s : list) out.add(s.getPosition().getX() + "," + s.getPosition().getY() + "," + s.getPosition().getZ() + " " + s.getHandle().furnaceStateId());
        return out;
    }

    /** FAWE FaweBlockStateListPopulator 꼴: 쓰기·읽기를 서버 레벨로 돌린다. */
    static final class FawePopulator extends BlockStateListPopulator {
        private final ServerLevel world;

        FawePopulator(ServerLevel world) {
            super(world);
            this.world = world;
        }

        @Override
        public long getSeed() { return world.getSeed(); }

        @Override
        public ServerLevel getLevel() { return world.getLevel(); }

        @Override
        public RandomSource getRandom() { return world.getRandom(); }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) { return world.setBlock(pos, state, flags, maxUpdateDepth); }

        @Override
        public boolean setBlock(BlockPos pos, BlockState state, int flags) { return world.setBlock(pos, state, flags); }

        @Override
        public BlockState getBlockState(BlockPos pos) { return world.getBlockState(pos); }
    }

    /** FAWE generateTree/generateFeature 의 동기 부분. */
    List<CraftBlockState> faweGenerate(boolean isPlaced, String id, BlockPos at, RandomSource random) {
        return sync(() -> {
            FawePopulator populator = new FawePopulator(level);
            level.captureTreeGeneration = true;
            level.captureBlockStates = true;
            try {
                boolean ok = isPlaced ? placed(id).place(populator, generator, random, at) : configured(id).place(populator, generator, random, at);
                if (!ok) return null;
                List<CraftBlockState> placedBlocks = new ArrayList<>(populator.getList());
                placedBlocks.addAll(level.capturedBlockStates.values());
                return placedBlocks;
            } finally {
                level.captureBlockStates = false;
                level.captureTreeGeneration = false;
                level.capturedBlockStates.clear();
            }
        });
    }

    @Test
    void faweForestPathCapturesLikePaperAndLeavesTheWorld() {
        BlockPos at = fresh();
        List<String> expected = serverCapture("minecraft:birch", at, 42L);
        RandomSource random = RandomSource.create(42L);
        List<CraftBlockState> got = faweGenerate(true, "minecraft:birch_checked", at, random);
        assertNotNull(got);
        assertEquals(expected, states(got));
        assertTrue(got.stream().allMatch(s -> s instanceof CapturedBlockState), "서버 레벨 기록의 상태");
        assertTrue(got.stream().anyMatch(s -> s.getFlag() == 19), "나무 블록은 flag 19");
        assertTrue(owner(() -> level.getBlockState(at).isAir()), "기록만 했으니 월드는 그대로");
    }

    @Test
    void faweFeaturePathUsesTheSameRandomSequence() {
        BlockPos at = fresh();
        RandomSource a = RandomSource.create(7L);
        List<CraftBlockState> got = faweGenerate(false, "minecraft:oak", at, a);
        assertEquals(serverCapture("minecraft:oak", at, 7L), states(got));
        Random b = new Random(7L);
        owner(() -> server.internals().world().regionizer().ownerOf(at.getX() >> 4, at.getZ() >> 4).level()
                .placeFeatureWith("minecraft:oak", new Pos(at.getX(), at.getY(), at.getZ()), new JavaRandomWorldgen(b), false));
        assertEquals(b.nextLong(), a.nextLong(), "받은 RandomSource 가 같은 만큼 나아간다");
    }

    @Test
    void capturedStatesAreSeenByLevelReadsAndIndestructibleCellsAreRefused() {
        BlockPos at = fresh();
        owner(() -> level.setBlock(at.below(), Blocks.BEDROCK.defaultBlockState(), 2));
        List<String> seen = sync(() -> {
            level.captureTreeGeneration = true;
            try {
                List<String> out = new ArrayList<>();
                out.add(String.valueOf(level.setBlock(at, Blocks.OAK_LOG.defaultBlockState(), 19)));
                out.add(String.valueOf(level.getBlockState(at).is(Blocks.OAK_LOG)));
                out.add(String.valueOf(level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 19)));   // 부술 수 없는 블록 칸
                out.add(String.valueOf(level.capturedBlockStates.size()));
                return out;
            } finally {
                level.captureTreeGeneration = false;
                level.capturedBlockStates.clear();
            }
        });
        assertEquals(List.of("true", "true", "false", "1"), seen);
        assertTrue(owner(() -> level.getBlockState(at).isAir() && level.getBlockState(at.below()).is(Blocks.BEDROCK)));
    }

    @Test
    void regionThreadNmsWritesAreNotSwallowedByAnotherThreadsCapture() {
        BlockPos at = fresh();
        level.captureTreeGeneration = true;   // 다른 스레드(전역)가 켠 표시
        try {
            assertTrue(owner(() -> level.setBlock(at, Blocks.STONE.defaultBlockState(), 3)));
            assertTrue(level.capturedBlockStates.isEmpty());
            assertTrue(owner(() -> level.getBlockState(at).is(Blocks.STONE)), "region 스레드의 NMS 쓰기는 월드에 그대로");
        } finally {
            level.captureTreeGeneration = false;
            owner(() -> level.setBlock(at, Blocks.AIR.defaultBlockState(), 3));
        }
    }

    @Test
    void regionThreadPlacementIsNotSwallowedByTheGlobalThreadsCapture() {
        // 검토 #494: 전역 스레드가 captureTreeGeneration 을 켠 사이 region 스레드가 서버 레벨에 지형지물을 놓으면 기록이 아니라 월드에 바로 놓인다
        BlockPos at = fresh(), ref = fresh();
        level.captureTreeGeneration = true;
        level.captureBlockStates = true;
        try {
            assertTrue(owner(() -> configured("minecraft:oak").place(level, generator, RandomSource.create(5L), at)));
            assertTrue(owner(() -> configured("minecraft:oak").place(new BlockStateListPopulator(level), generator, RandomSource.create(5L), ref)));
            assertTrue(level.capturedBlockStates.isEmpty(), "기록에 섞이지 않는다");
        } finally {
            level.captureBlockStates = false;
            level.captureTreeGeneration = false;
            level.capturedBlockStates.clear();
        }
        assertTrue(owner(() -> level.getBlockState(at).is(Blocks.OAK_LOG)), "월드에 바로");
        assertTrue(owner(() -> level.getBlockState(ref).isAir()), "populator 는 자기 표에만");
    }

    @Test
    void oreVeinsAreNotFakedThroughALevel() {
        // 바닐라 OreFeature 는 BulkSectionAccess 로 섹션에 바로 쓴다 — Paper 도 populator 의 setBlock 을 거치지 않는다
        BlockPos at = fresh();
        var e = assertThrows(UnsupportedNmsCall.class,
                () -> owner(() -> configured("minecraft:ore_iron").place(new BlockStateListPopulator(level), generator, RandomSource.create(1L), at)));
        assertTrue(e.getMessage().contains("BulkSectionAccess"), e.getMessage());
    }

    @Test
    void plainPopulatorKeepsItsOwnListAndNeverTouchesTheWorld() {
        BlockPos at = fresh();
        List<String> expected = serverCapture("minecraft:oak", at, 9L);
        List<String> got = owner(() -> {
            BlockStateListPopulator populator = new BlockStateListPopulator(level);
            assertTrue(configured("minecraft:oak").place(populator, generator, RandomSource.create(9L), at));
            assertTrue(populator.getBlockState(at).is(Blocks.OAK_LOG), "자기 표를 먼저 읽는다");
            assertTrue(level.getBlockState(at).isAir());
            for (CraftBlockState s : populator.getList()) assertSame(level, s.getWorldHandle());
            return states(populator.getList());
        });
        // Paper BlockStateListPopulator#setBlock 은 list.remove 뒤 put — 다시 쓴 칸은 뒤로 간다(나무 기록은 처음 쓴 차례). 칸과 마지막 상태는 같다
        assertNotEquals(expected, got, "잎 거리 갱신이 잎을 다시 쓰므로 차례가 다르다");
        assertEquals(expected.stream().sorted().toList(), got.stream().sorted().toList());
    }

    @Test
    void serverLevelWithoutCaptureIsTheRuntimePlacement() {
        // 같은 자리에서 비교한다 — 잎 거리 갱신 차례는 바닐라에서도 자리마다(HashSet 순서) 다르다
        BlockPos a = fresh();
        owner(() -> configured("minecraft:birch").place(level, generator, RandomSource.create(3L), a));
        List<String> viaShim = column(a);
        owner(() -> {
            for (int y = 0; y <= 12; y++)
                for (int z = -4; z <= 4; z++)
                    for (int x = -4; x <= 4; x++) level.setBlock(a.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2 | 16);
            return null;
        });
        owner(() -> server.internals().world().regionizer().ownerOf(a.getX() >> 4, a.getZ() >> 4).level()
                .placeFeatureWith("minecraft:birch", new Pos(a.getX(), a.getY(), a.getZ()), new JavaRandomWorldgen(new Random(3L)), true));
        assertFalse(viaShim.isEmpty());
        assertEquals(column(a), viaShim, "서버 레벨 자신에 기록 없이 놓으면 바닐라 런타임 배치(월드에 바로)");
    }

    /** 원점 기준 상자 안 공기 아닌 칸(상대 좌표). */
    List<String> column(BlockPos o) {
        return owner(() -> {
            List<String> out = new ArrayList<>();
            for (int y = 0; y <= 12; y++)
                for (int z = -4; z <= 4; z++)
                    for (int x = -4; x <= 4; x++) {
                        BlockState s = level.getBlockState(o.offset(x, y, z));
                        if (!s.isAir()) out.add(x + "," + y + "," + z + " " + s.furnaceStateId());
                    }
            return out;
        });
    }

    @Test
    void unsupportedCasesFailHonestly() {
        BlockPos at = fresh();
        // 레지스트리 밖의 객체
        var loose = new ConfiguredFeature<>(net.minecraft.world.level.levelgen.feature.Feature.byType("minecraft:tree"), null);
        assertThrows(UnsupportedNmsCall.class, () -> owner(() -> loose.place(level, generator, RandomSource.create(1L), at)));
        // 바이옴 거르기가 든 배치된 지형지물: 위치가 생기면 바닐라처럼 IllegalStateException
        int thrown = 0;
        for (long seed = 1; seed <= 40; seed++) {
            long s = seed;
            try {
                owner(() -> placed("minecraft:trees_plains").place(new BlockStateListPopulator(level), generator, RandomSource.create(s), at));
            } catch (IllegalStateException e) {
                assertEquals("Tried to biome check an unregistered feature, or a feature that should not restrict the biome", e.getMessage());
                thrown++;
            }
        }
        assertTrue(thrown > 0);
        // 다른 스레드: 원점 칸 주인이 아니다
        assertThrows(org.domi.furnace.api.concurrent.ThreadOwnershipException.class,
                () -> configured("minecraft:oak").place(new BlockStateListPopulator(level), generator, RandomSource.create(1L), at));
    }
}
