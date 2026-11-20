package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.util.DummyGeneratorAccess;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.thread.PrimaryThread;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.BiomeZoom;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-211 3단계: 서버 레벨 나머지 멤버(설계 9장). 기대 동작은 Paper 1.21.1 jar(ServerLevel·Level·LevelReader·BiomeManager·DifficultyInstance·FeatureFlagSet)와
 * 서버의 바닐라 계산(BiomeZoom·ChunkLevel#getCurrentDifficultyAt·destroyBlock·Clearables).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsLevelSurfaceTest {

    static final long SEED = 0L;

    FurnaceTestServer server;
    ServerLevel level;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).seed(SEED).spawnChunkRadius(0).difficulty(org.domi.furnace.server.world.Difficulty.HARD)
                .generator((blocks, registries) -> NoiseWorldSource.overworld(SEED, blocks, registries), 100).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        for (int cx = 0; cx <= 3; cx++)
            for (int cz = 0; cz <= 3; cz++) server.loadChunk(cx, cz);
        level = ((CraftWorld) Compat.world(server.overworld())).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task); }

    org.domi.furnace.server.world.RegionLevel serverLevel(BlockPos p) {
        return server.internals().world().regionizer().ownerOf(p.getX() >> 4, p.getZ() >> 4).level();
    }

    static Pos pos(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }

    @Test
    void shadeAndChunkIfLoadedImmediately() {
        for (Direction d : Direction.values()) {
            assertEquals(1.0F, level.getShade(d, true));
            assertEquals(1.0F, level.getShade(d, false));
        }
        BlockPos in = new BlockPos(20, 70, 40);
        assertTrue(on(in, () -> level.getChunkSource().getChunkNow(1, 2) == level.getChunkIfLoadedImmediately(1, 2)));
        assertNotNull(on(in, () -> level.getChunkIfLoadedImmediately(in)), "주인 스레드는 로드된 청크를 곧바로 본다");
        assertNull(level.getChunkIfLoadedImmediately(400, 400), "로드되지 않은 청크는 null(불러오지 않는다)");
    }

    @Test
    void enabledFeaturesFollowTheServer() {
        FeatureFlagSet set = level.enabledFeatures();
        assertEquals(FeatureFlags.DEFAULT_FLAGS, set, "데이터 팩 기본 = vanilla 하나");
        assertTrue(set.contains(FeatureFlags.VANILLA));
        assertFalse(set.contains(FeatureFlags.BUNDLE));
        assertFalse(FeatureFlags.isExperimental(set));
        // 바닐라 FeatureFlagSet 연산
        FeatureFlagSet all = FeatureFlags.REGISTRY.allFlags();
        assertTrue(set.isSubsetOf(all));
        assertFalse(all.isSubsetOf(set));
        assertEquals(all, set.join(FeatureFlagSet.of(FeatureFlags.BUNDLE, FeatureFlags.TRADE_REBALANCE)));
        assertEquals(FeatureFlagSet.of(FeatureFlags.BUNDLE, FeatureFlags.TRADE_REBALANCE), all.subtract(set));
        assertTrue(set.subtract(set).isEmpty());
        assertEquals("minecraft:bundle", FeatureFlags.printMissingFlags(set, FeatureFlagSet.of(FeatureFlags.BUNDLE)));
        assertEquals(java.util.Set.of(ResourceLocation.parse("minecraft:vanilla")), FeatureFlags.REGISTRY.toNames(set));
        assertTrue(FeatureFlagSet.of().isSubsetOf(set), "빈 집합은 어디에나 들어간다");
        // fastutil HashCommon.mix(long) 식: h = x * φ; h ^= h >>> 32; h ^ h >>> 16
        long h = FeatureFlags.VANILLA.mask * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        assertEquals((int) (h ^ h >>> 16), set.hashCode());
    }

    @Test
    void biomeManagerZoomsLikeTheServer() {
        for (long seed : new long[] {0L, 1L, -7L, 123456789L, Long.MIN_VALUE}) assertEquals(BiomeZoom.obfuscateSeed(seed), BiomeManager.obfuscateSeed(seed));
        BiomeManager m = level.getBiomeManager();
        assertSame(m, level.getBiomeManager());
        long zoom = BiomeZoom.obfuscateSeed(SEED);
        int checked = 0;
        for (int x = 17; x < 47; x += 3)   // 지터로 고른 quart 가 이웃 청크여도 로드된 0..3 안
            for (int z = 18; z < 47; z += 4)
                for (int y = -60; y < 200; y += 37) {
                    int[] q = BiomeZoom.quart(zoom, x, y, z);
                    BlockPos p = new BlockPos(x, y, z);
                    assertSame(level.getNoiseBiome(q[0], q[1], q[2]), level.getBiome(p), p.toString());
                    assertSame(level.getChunk(q[0] >> 2, q[2] >> 2).getNoiseBiome(q[0], q[1], q[2]), m.getBiome(p), "로드된 청크는 그 청크의 바이옴");
                    checked++;
                }
        assertTrue(checked > 50);
        // 로드되지 않은 곳은 생성기 값(getUncachedNoiseBiome)
        BlockPos far = new BlockPos(5000, 64, 5000);
        int[] q = BiomeZoom.quart(zoom, far.getX(), far.getY(), far.getZ());
        assertSame(level.getUncachedNoiseBiome(q[0], q[1], q[2]), m.getBiome(far));
        assertSame(level.getNoiseBiome(q[0] + 1, q[1], q[2]), m.getNoiseBiomeAtQuart(q[0] + 1, q[1], q[2]));
        assertSame(level.getNoiseBiome(far.getX() >> 2, far.getY() >> 2, far.getZ() >> 2), m.getNoiseBiomeAtPosition(far));
    }

    @Test
    void currentDifficultyIsTheServerValue() {
        server.execute("time set 1600000");   // 낮 시간 몫과 달 밝기가 들어가게
        BlockPos p = new BlockPos(24, 70, 24);
        float[] got = on(p, () -> {
            DifficultyInstance d = level.getCurrentDifficultyAt(p);
            var s = serverLevel(p);
            var expected = s.getCurrentDifficultyAt(pos(p));
            var vanilla = new DifficultyInstance(Difficulty.HARD, s.dayTime(), 0L, s.moonBrightness());   // 바닐라 공개 생성자 식
            assertEquals(Difficulty.HARD, d.getDifficulty());
            return new float[] {d.getEffectiveDifficulty(), expected.effectiveDifficulty(), vanilla.getEffectiveDifficulty(), d.getSpecialMultiplier(),
                    expected.specialMultiplier()};
        });
        assertEquals(got[1], got[0]);
        assertEquals(got[2], got[0], "머문 시간 0 인 청크: 바닐라 식과 같다");
        assertEquals(got[4], got[3]);
        assertTrue(got[0] > 2.25F, "달 밝기·낮 시간 몫이 들어갔다: " + got[0]);
        // 틱 스레드가 아닌 스레드는 주인에게 맡기고 기다린다
        assertEquals(got[0], level.getCurrentDifficultyAt(p).getEffectiveDifficulty());
        // 남의 region 을 전역 스레드에서 — 교착을 피해 ThreadOwnershipException
        assertThrows(ThreadOwnershipException.class, () -> server.onGlobal(() -> level.getCurrentDifficultyAt(p)));
        // 로드되지 않은 곳: 머문 시간·달 0
        DifficultyInstance far = level.getCurrentDifficultyAt(new BlockPos(9000, 64, 9000));
        long dayTime = on(p, () -> serverLevel(p).dayTime());
        assertEquals(new DifficultyInstance(Difficulty.HARD, dayTime, 0L, 0.0F).getEffectiveDifficulty(), far.getEffectiveDifficulty());
        assertThrows(UnsupportedOperationException.class, () -> DummyGeneratorAccess.INSTANCE.getCurrentDifficultyAt(p));
    }

    @Test
    void destroyBlockUsesTheServer() {
        BlockPos p = new BlockPos(40, 150, 40);
        on(p, () -> level.setBlock(p, Blocks.DIRT.defaultBlockState(), 3));
        assertTrue(on(p, () -> level.destroyBlock(p, false)));
        assertTrue(on(p, () -> level.getBlockState(p).isAir()));
        assertFalse(on(p, () -> level.destroyBlock(p, true)), "공기는 부수지 않는다");
        on(p, () -> level.setBlock(p, Blocks.STONE.defaultBlockState(), 3));
        assertTrue(on(p, () -> level.destroyBlock(p, true, null, 512)));
        assertTrue(on(p, () -> level.getBlockState(p).isAir()));
        assertFalse(on(p, () -> level.destroyBlock(new BlockPos(40, 500, 40), true)), "높이 밖은 공기");
        assertThrows(IllegalStateException.class, () -> level.destroyBlock(new BlockPos(9000, 64, 9000), false), "로드되지 않은 칸");
        on(p, () -> level.setBlock(p, Blocks.STONE.defaultBlockState(), 3));
        assertThrows(ThreadOwnershipException.class, () -> server.onGlobal(() -> level.destroyBlock(p, false)), "주인이 아닌 스레드");
        assertFalse(DummyGeneratorAccess.INSTANCE.destroyBlock(p, true), "Paper DummyGeneratorAccess: 거짓");
    }

    @Test
    void containerBlockEntitiesClearLikeVanilla() {
        BlockPos p = new BlockPos(56, 150, 8);
        BlockEntity be = on(p, () -> {
            level.setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
            var chest = (org.domi.furnace.server.block.entity.ContainerBlockEntity) serverLevel(p).getBlockEntity(pos(p));
            chest.items[3] = new org.domi.furnace.server.entity.ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:stone"), 5);
            return level.getBlockEntity(p);
        });
        assertInstanceOf(Clearable.class, be);
        on(p, () -> {   // FAWE clearContainerBlockContents: instanceof Clearable → clearContent
            ((Clearable) be).clearContent();
            return null;
        });
        assertTrue(on(p, () -> {
            var chest = (org.domi.furnace.server.block.entity.ContainerBlockEntity) serverLevel(p).getBlockEntity(pos(p));
            for (var s : chest.items) if (!s.isEmpty()) return false;
            return true;
        }));
        // 바닐라에서 Clearable 이 아닌 블록 엔티티
        BlockPos bell = new BlockPos(57, 150, 8);
        assertFalse(on(bell, () -> {
            level.setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
            return level.getBlockEntity(bell);
        }) instanceof Clearable);
        // 플러그인이 만든 떨어진 블록 엔티티는 레벨 동작을 같게 낼 수 없다
        var detached = new ChestBlockEntity(p, Blocks.CHEST.defaultBlockState());
        assertThrows(UnsupportedNmsCall.class, detached::clearContent);
        // 전역 스레드(주 스레드 자리)는 남의 region 을 기다리지 않는다 — 빌림 구간이면 된다
        assertThrows(ThreadOwnershipException.class, () -> server.onGlobal(() -> {
            ((Clearable) be).clearContent();
            return null;
        }));
        server.onGlobal(() -> {
            try (var s = PrimaryThread.enter()) {
                ((Clearable) be).clearContent();
            }
            return null;
        });
    }

    @Test
    void dummyGeneratorAccessRefusesLikePaper() {
        var d = DummyGeneratorAccess.INSTANCE;
        assertThrows(UnsupportedOperationException.class, d::getBiomeManager);
        assertThrows(UnsupportedOperationException.class, d::enabledFeatures);
        assertThrows(UnsupportedOperationException.class, () -> d.getChunkIfLoadedImmediately(0, 0));
    }
}
