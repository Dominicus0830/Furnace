// T-210: 바이옴 공급자로 만드는 보통(저장되는) 월드 — 노이즈·평지 생성기, 월드 정보(prepare), level.dat 표시와 다시 열기 규칙,
// 추가 평지 월드의 바닐라 평지 생성기(level.dat 설정·generator-settings JSON).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.api.worldgen.BiomeProvider;
import org.domi.furnace.api.worldgen.Climate;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BiomeProviderWorldTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Dimensions dims;
    WorldManager manager;
    BlockRegistry reg;
    RegistrySet registries;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp.resolve("world"), sched);
        dims = new Dimensions();
        dims.register(core.world());
        core.dimensions(dims);
        reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        registries = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");
        var io = TestServers.ioPool();
        manager = new WorldManager(new WorldManager.Parts(tmp, reg, ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"), registries,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"), 7, new ChunkWorkerPool(1), sched, null,
                Config.DEFAULT, false, io, io), dims, core.levelData(), "world");
        core.worldManager(manager);
    }

    @AfterEach
    void tearDown() {
        manager.closeAllForShutdown();
        sched.close();
    }

    /** x 가 음수면 사막, 아니면 눈 덮인 평원. 받은 월드 정보·기후를 기록한다. */
    static final class Halves implements BiomeProvider {
        final List<Context> prepared = new ArrayList<>();
        volatile double maxAbsClimate;
        volatile double maxAbsRange;

        @Override
        public String biome(int x, int y, int z, Climate c) {
            for (Climate.Parameter p : Climate.Parameter.values()) {
                maxAbsClimate = Math.max(maxAbsClimate, Math.abs(c.value(p)));
                maxAbsRange = Math.max(maxAbsRange, Math.max(Math.abs(c.min(p)), Math.abs(c.max(p))));
            }
            return x < 0 ? "minecraft:desert" : "minecraft:snowy_plains";
        }

        @Override
        public List<String> biomes() { return List.of("minecraft:desert", "minecraft:snowy_plains"); }

        @Override
        public synchronized void prepare(Context world) { prepared.add(world); }

        @Override
        public String name() { return "test:halves"; }
    }

    private World create(WorldCreator c) throws Exception { return manager.create(c).get(120, TimeUnit.SECONDS); }

    private static Throwable cause(Throwable t) { return t instanceof ExecutionException && t.getCause() != null ? t.getCause() : t; }

    private String biomeAt(World w, int x, int y, int z) {
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        w.awaitChunk(cp);
        int id = w.submitAtChunk(cp, r -> r.biomeIdAtQuart(x >> 2, y >> 2, z >> 2)).join();
        return registries.entries("minecraft:worldgen/biome").get(id);
    }

    private static NoiseWorldSource gen(World w) {
        var ws = w.worldSource();
        return (NoiseWorldSource) (ws instanceof AnvilWorldSource a ? a.base() : ws);
    }

    @Test
    void noiseWorldTakesTheProviderAndTheRealWorldInfo() throws Exception {
        Halves p = new Halves();
        World w = create(WorldCreator.named("halves").seed(31L).biomeProvider(p).build());
        assertEquals(List.of(new BiomeProvider.Context("halves", "minecraft:halves", WorldEnvironment.NORMAL, 31L, -64, 320)), p.prepared);
        assertSame(p, gen(w).biomeProvider());
        assertEquals("minecraft:desert", biomeAt(w, -40, 64, 8));
        assertEquals("minecraft:snowy_plains", biomeAt(w, 40, 64, 8));
        assertTrue(p.maxAbsClimate > 0, "노이즈 생성기는 바닐라 기후 값을 준다");
    }

    @Test
    void flatWorldTakesTheProviderWithZeroClimate() throws Exception {
        Halves p = new Halves();
        World w = create(WorldCreator.named("flatp").generator(WorldGenerator.FLAT).biomeProvider(p).build());
        NoiseWorldSource n = gen(w);
        assertTrue(n.isFlat(), "오버월드 높이의 추가 평지는 바닐라 평지 생성기");
        assertFalse(n.flatFixedBiome());
        assertNull(n.fixedBiome());
        assertEquals("minecraft:desert", biomeAt(w, -40, -61, 8));
        assertEquals("minecraft:snowy_plains", biomeAt(w, 40, -61, 8));
        assertEquals(0.0, p.maxAbsClimate, "평지의 기후는 0(바닐라 평지 RandomState 는 dummy 라우터)");
        assertEquals(0.0, p.maxAbsRange);
        // 생성기의 바이옴 질의(/locate·스폰·구조물)도 공급자
        assertEquals(registries.indexOf("minecraft:worldgen/biome", "minecraft:desert"), n.noiseBiome(-10, 0, 2));
        assertEquals(registries.indexOf("minecraft:worldgen/biome", "minecraft:snowy_plains"), n.noiseBiome(10, 0, 2));
    }

    @Test
    void generatorsWithoutABiomeSourceRejectTheProvider() {
        Halves p = new Halves();
        for (var g : List.of(WorldGenerator.VOID, WorldGenerator.DEBUG_ALL_BLOCK_STATES)) {
            var e = cause(assertThrows(ExecutionException.class, () -> create(WorldCreator.named("x" + g.ordinal()).generator(g).biomeProvider(p).build())));
            assertInstanceOf(IllegalArgumentException.class, e, g.name());
        }
    }

    @Test
    void storedMarkerKeepsTheWorldClosedUntilTheProviderIsGivenAgain() throws Exception {
        Halves p = new Halves();
        World w = create(WorldCreator.named("kept").seed(7L).biomeProvider(p).build());
        assertEquals("minecraft:desert", biomeAt(w, -40, 64, 8));
        assertTrue(manager.unload(w, true).get(60, TimeUnit.SECONDS));

        WorldCreator stored = manager.storedCreator("kept");
        assertTrue(stored.needsBiomeProvider());
        assertEquals("test:halves", stored.biomeProviderName());
        // 서버가 스스로 여는 길(worlds.autoload·/worlds load)은 열지 않는다
        var e = cause(assertThrows(ExecutionException.class, () -> manager.load("kept").get(60, TimeUnit.SECONDS)));
        var n = assertInstanceOf(WorldManager.NeedsBiomeProviderException.class, e);
        assertEquals("kept", n.world());
        assertEquals("test:halves", n.provider());
        assertNull(core.world("minecraft:kept"));

        // 공급자를 다시 주면 열린다 — 시드는 level.dat 의 것
        Halves again = new Halves();
        World back = create(stored.toBuilder().biomeProvider(again).build());
        assertEquals(List.of(new BiomeProvider.Context("kept", "minecraft:kept", WorldEnvironment.NORMAL, 7L, -64, 320)), again.prepared);
        assertEquals("minecraft:snowy_plains", biomeAt(back, 40, 64, 40));
        // 공급자 없이 같은 이름 — 이미 올라온 월드
        assertSame(back, create(WorldCreator.named("kept").build()));
        assertTrue(manager.unload(back, true).get(60, TimeUnit.SECONDS));

        // 공급자를 지우고 열면 표시도 지워진다(Paper 처럼 새 청크는 바닐라 바이옴)
        World plain = create(manager.storedCreator("kept").toBuilder().biomeProvider(null).build());
        assertNull(gen(plain).biomeProvider());
        assertTrue(manager.unload(plain, true).get(60, TimeUnit.SECONDS));
        assertFalse(manager.storedCreator("kept").needsBiomeProvider());
        assertNotNull(manager.load("kept").get(60, TimeUnit.SECONDS));
    }

    @Test
    void extraFlatWorldUsesTheLevelDatSettings() throws Exception {
        // generator-settings JSON(바닐라 규칙): layers 가 없으면 기본 프리셋 — 요새·마을
        World def = create(WorldCreator.named("deflat").generator(WorldGenerator.FLAT).flatGeneratorSettings("{}").build());
        assertEquals(java.util.Set.of("minecraft:strongholds", "minecraft:villages"), gen(def).structureEngine().allowedSets());
        assertEquals(reg.defaultStateId("minecraft:grass_block"), blockState(def, 3, -61, 3));
        // structure_overrides 가 없으면 레지스트리 전부, features·lakes 는 그대로
        World all = create(WorldCreator.named("allflat").generator(WorldGenerator.FLAT)
                .flatGeneratorSettings("{\"layers\":[{\"block\":\"minecraft:stone\",\"height\":2}],\"biome\":\"minecraft:desert\",\"features\":true}").build());
        assertNull(gen(all).structureEngine().allowedSets());
        assertEquals("minecraft:desert", biomeAt(all, 3, -63, 3));
        // 설명서만(Furnace API·/worlds create flat): 지금까지처럼 구조물 없음
        World api = create(WorldCreator.named("apiflat").generator(WorldGenerator.FLAT).build());
        assertEquals(java.util.Set.of(), gen(api).structureEngine().allowedSets());
        // 다시 열면 level.dat 의 설정
        assertTrue(manager.unload(def, true).get(60, TimeUnit.SECONDS));
        World again = manager.load("deflat").get(60, TimeUnit.SECONDS);
        assertEquals(java.util.Set.of("minecraft:strongholds", "minecraft:villages"), gen(again).structureEngine().allowedSets());
    }

    private int blockState(World w, int x, int y, int z) {
        ChunkPos cp = ChunkPos.ofBlock(x, z);
        w.awaitChunk(cp);
        return w.submitAtChunk(cp, r -> r.level().getBlockState(new org.domi.furnace.server.block.Pos(x, y, z))).join();
    }
}
