package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.util.BlockStateListPopulator;
import org.bukkit.craftbukkit.util.DummyGeneratorAccess;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.thread.PrimaryThread;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-211 2단계: NMS {@code Structure#generate}·{@code StructureStart#placeInChunk}. 기대 동작은 Paper 1.21.1(StructureStart 의 감싸개 쓰기,
 * Level 의 captureTreeGeneration)과 FAWE 2.16 PaperweightFaweAdapter#generateStructure(populator 가 쓰기·읽기를 서버 레벨로 돌리고 서버 레벨 기록을 모은다).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsStructureTest {

    static final long SEED = 0L;

    FurnaceTestServer server;
    ServerLevel level;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).seed(SEED).spawnChunkRadius(0)
                .generator((blocks, registries) -> NoiseWorldSource.overworld(SEED, blocks, registries), 100).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        for (int cx = 0; cx <= 9; cx++)
            for (int cz = 0; cz <= 9; cz++) server.loadChunk(cx, cz);
        level = ((CraftWorld) Compat.world(server.overworld())).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(64, 64, 64), task); }

    /** FAWE TaskManager.sync 자리. */
    <T> T sync(Supplier<T> task) {
        return server.onGlobal(() -> {
            try (var s = PrimaryThread.enter()) {
                return task.get();
            }
        });
    }

    static Structure structure(String id) { return NmsWorldgen.structures().get(ResourceLocation.parse(id)); }

    /** FAWE FaweBlockStateListPopulator 꼴: 쓰기·읽기·시드·난수를 서버 레벨로 돌린다. */
    static final class FawePopulator extends BlockStateListPopulator {
        private final ServerLevel world;

        FawePopulator(ServerLevel world) {
            super(world);
            this.world = world;
        }

        @Override public long getSeed() { return world.getSeed(); }
        @Override public ServerLevel getLevel() { return world.getLevel(); }
        @Override public RandomSource getRandom() { return world.getRandom(); }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) { return world.setBlock(pos, state, flags, maxUpdateDepth); }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) { return world.setBlock(pos, state, flags); }
        @Override public BlockState getBlockState(BlockPos pos) { return world.getBlockState(pos); }
        @Override public net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(BlockPos pos) { return world.getCurrentDifficultyAt(pos); }
        @Override public net.minecraft.world.level.biome.BiomeManager getBiomeManager() { return world.getBiomeManager(); }
    }

    StructureStart generate(String id, ChunkPos at, net.minecraft.world.level.LevelHeightAccessor height) {
        ServerChunkCache chunks = level.getChunkSource();
        return structure(id).generate(level.registryAccess(), chunks.getGenerator(), chunks.getGenerator().getBiomeSource(), chunks.randomState(),
                level.getStructureManager(), level.getSeed(), at, 0, height, biome -> true);
    }

    /** FAWE generateStructure 의 동기 부분(그대로). */
    List<CraftBlockState> faweGenerate(String id, ChunkPos chunkPos) {
        return sync(() -> {
            FawePopulator populator = new FawePopulator(level);
            level.captureTreeGeneration = true;
            level.captureBlockStates = true;
            try {
                StructureStart start = generate(id, chunkPos, populator);
                if (!start.isValid()) return null;
                BoundingBox b = start.getBoundingBox();
                ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(b.minX()), SectionPos.blockToSectionCoord(b.minZ()));
                ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(b.maxX()), SectionPos.blockToSectionCoord(b.maxZ()));
                ChunkPos.rangeClosed(min, max).forEach(cp -> start.placeInChunk(populator, level.structureManager(), level.getChunkSource().getGenerator(),
                        level.getRandom(), new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(),
                                level.getMaxBuildHeight(), cp.getMaxBlockZ()), cp));
                List<CraftBlockState> placed = new ArrayList<>(populator.getList());
                placed.addAll(level.capturedBlockStates.values());
                return placed;
            } finally {
                level.captureBlockStates = false;
                level.captureTreeGeneration = false;
                level.capturedBlockStates.clear();
            }
        });
    }

    static long count(List<CraftBlockState> states, net.minecraft.world.level.block.Block block) {
        return states.stream().filter(s -> s.getHandle().is(block)).count();
    }

    @Test
    void faweStructurePathCapturesLikePaperAndLeavesTheWorld() {
        List<CraftBlockState> got = faweGenerate("minecraft:igloo", new ChunkPos(3, 3));
        assertNotNull(got);
        assertTrue(count(got, Blocks.SNOW_BLOCK) > 20, "이글루 몸체: " + got.size());
        assertTrue(count(got, Blocks.BARRIER) > 0, "Paper 기록: 블록 엔티티 칸은 방벽(먼저 쓴 방벽 위 쓰기는 거절)");
        assertTrue(got.stream().anyMatch(s -> s.getFlag() == 2), "템플릿 본체는 flag 2");
        CraftBlockState snow = got.stream().filter(s -> s.getHandle().is(Blocks.SNOW_BLOCK)).findFirst().orElseThrow();
        assertFalse(owner(() -> level.getBlockState(snow.getPosition()).is(Blocks.SNOW_BLOCK)), "기록만 했으니 월드는 그대로");
    }

    @Test
    void startValuesFollowVanilla() {
        StructureStart start = sync(() -> generate("minecraft:igloo", new ChunkPos(3, 3), level));
        assertTrue(start.isValid());
        assertEquals(new ChunkPos(3, 3), start.getChunkPos());
        assertEquals(0, start.getReferences());
        assertSame(structure("minecraft:igloo"), start.getStructure());
        assertSame(start.getBoundingBox(), start.getBoundingBox(), "처음 계산한 상자를 다시 준다");
        assertTrue(start.getBoundingBox().intersects(48, 48, 63, 63));

        StructureStart refused = sync(() -> {
            ServerChunkCache chunks = level.getChunkSource();
            return structure("minecraft:igloo").generate(level.registryAccess(), chunks.getGenerator(), chunks.getGenerator().getBiomeSource(),
                    chunks.randomState(), level.getStructureManager(), level.getSeed(), new ChunkPos(3, 3), 0, level, biome -> false);
        });
        assertSame(StructureStart.INVALID_START, refused, "바이옴이 거절하면 유효하지 않은 시작");
        assertThrows(IllegalStateException.class, StructureStart.INVALID_START::getBoundingBox);
    }

    @Test
    void directPlacementOnTheServerLevelWritesTheWorld() {
        StructureStart start = sync(() -> generate("minecraft:igloo", new ChunkPos(6, 6), level));
        BoundingBox b = start.getBoundingBox();
        long snow = server.onRegion(new org.domi.furnace.api.world.BlockPos(b.minX(), 64, b.minZ()), () -> {
            ChunkPos.rangeClosed(new ChunkPos(b.minX() >> 4, b.minZ() >> 4), new ChunkPos(b.maxX() >> 4, b.maxZ() >> 4)).forEach(cp ->
                    start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                            new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), level.getMaxBuildHeight(),
                                    cp.getMaxBlockZ()), cp));
            long n = 0;   // 이글루는 놓을 때 지표 높이로 내려간다(바닐라 IglooPiece) — 높이 전체를 센다
            for (int x = b.minX(); x <= b.maxX(); x++)
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
                    for (int z = b.minZ(); z <= b.maxZ(); z++)
                        if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.SNOW_BLOCK)) n++;
            return n;
        });
        assertTrue(snow > 20, "서버 월드에 바로 놓였다: " + snow);
    }

    @Test
    void faweSwampHutFinalizesMobsAndDropsThem() {
        // T-221: 마녀·고양이를 바닐라처럼 마무리(난수는 그 레벨의 getRandom(), 지역 난이도·바이옴은 FAWE 가 서버 레벨에 넘긴다)한 뒤
        // populator 의 addFreshEntity(바닐라 기본 — 거짓)로 넘겨 버린다. 오두막 블록은 기록되고 월드에는 몹이 없다.
        List<CraftBlockState> got = faweGenerate("minecraft:swamp_hut", new ChunkPos(5, 2));
        assertNotNull(got);
        assertTrue(count(got, Blocks.SPRUCE_PLANKS) > 10, "오두막 몸체: " + got.size());
        long witches = sync(() -> Compat.world(server.overworld()).getEntities().stream().filter(e -> e.getType() == org.bukkit.entity.EntityType.WITCH).count());
        assertEquals(0, witches, "populator 에 넘긴 몹은 월드에 들어가지 않는다");
    }

    @Test
    void plainPopulatorSwampHutThrowsLikePaper() {
        // Paper 의 BlockStateListPopulator 는 getLevel·getCurrentDifficultyAt 를 고쳐 쓰지 않아 DummyGeneratorAccess 가 던진다 — 같은 예외
        var e = assertThrows(UnsupportedOperationException.class, () -> sync(() -> {
            StructureStart start = generate("minecraft:swamp_hut", new ChunkPos(5, 2), level);
            BlockStateListPopulator populator = new BlockStateListPopulator(level);
            BoundingBox b = start.getBoundingBox();
            ChunkPos.rangeClosed(new ChunkPos(b.minX() >> 4, b.minZ() >> 4), new ChunkPos(b.maxX() >> 4, b.maxZ() >> 4)).forEach(cp ->
                    start.placeInChunk(populator, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                            new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), level.getMaxBuildHeight(),
                                    cp.getMaxBlockZ()), cp));
            return null;
        }));
        assertEquals("Not supported yet.", e.getMessage());
        assertFalse(e instanceof UnsupportedNmsCall, "Paper 와 같은 예외(서버가 못 하는 자리가 아니다)");
    }

    @Test
    void foreignInputsFailHonestly() {
        ServerChunkCache chunks = level.getChunkSource();
        Structure igloo = structure("minecraft:igloo");
        var seed = assertThrows(UnsupportedNmsCall.class, () -> igloo.generate(level.registryAccess(), chunks.getGenerator(),
                chunks.getGenerator().getBiomeSource(), chunks.randomState(), level.getStructureManager(), SEED + 1, new ChunkPos(3, 3), 0, level, b -> true));
        assertTrue(seed.getMessage().contains("seed"), seed.getMessage());
        assertThrows(UnsupportedNmsCall.class, () -> igloo.generate(RegistryAccess.SERVER, null, chunks.getGenerator().getBiomeSource(), chunks.randomState(),
                level.getStructureManager(), SEED, new ChunkPos(3, 3), 0, level, b -> true));

        // 서버 레벨을 모르는 레벨(DummyGeneratorAccess — Paper 도 getLevel 에서 던진다)
        StructureStart start = sync(() -> generate("minecraft:igloo", new ChunkPos(3, 3), level));
        assertThrows(UnsupportedNmsCall.class, () -> sync(() -> {
            start.placeInChunk(DummyGeneratorAccess.INSTANCE, level.structureManager(), chunks.getGenerator(), level.getRandom(),
                    new BoundingBox(48, -64, 48, 63, 320, 63), new ChunkPos(3, 3));
            return null;
        }));
    }
}
