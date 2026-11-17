// T-217 객체 훅(Bukkit ChunkGenerator 다리 자리): DECORATION·MOBS 단계, 생성 중 바이옴 쓰기, 월드 장식기(BlockPopulator 자리)의 순서.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.worldgen.ChunkGenerationHooks;
import org.domi.furnace.api.worldgen.DecorationRegion;
import org.domi.furnace.api.worldgen.GenerationChunk;
import org.domi.furnace.api.worldgen.GenerationHeightmap;
import org.domi.furnace.api.worldgen.GenerationStage;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.structure.StructureStart;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GenHooksObjectTest {

    static final long SEED = 12345L;
    static BlockRegistry blocks;
    static RegistrySet registries;

    @BeforeAll
    static void load() {
        blocks = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        registries = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt");
    }

    static NoiseWorldSource source(ChunkGenerationHooks hooks) {
        NoiseWorldSource n = NoiseWorldSource.overworld(SEED, blocks, registries);
        if (hooks != null) assertNotNull(GenHooks.attachObject(n, hooks, "minecraft:overworld", SEED, blocks, registries));
        ChunkDecorators.bind(n, "minecraft:overworld", SEED, blocks, registries);
        return n;
    }

    @Test
    void decorationOffDropsStructurePiecesAndFeatures() {
        StructureStart found = GenHooksTest.findStart(source(null));
        assertNotNull(found);
        NoiseWorldSource off = source(new ChunkGenerationHooks() {
            @Override public boolean runVanilla(GenerationStage stage, int x, int z) { return stage != GenerationStage.DECORATION; }
            @Override public boolean feature(String id, int x, int z) { throw new AssertionError("DECORATION 을 끄면 지형지물을 묻지 않는다: " + id); }
        });
        int cx = found.chunkX(), cz = found.chunkZ();
        NoiseWorldSource.Generated pristine = off.generateWorldChunk(cx, cz);
        off.applyCarvers(pristine, cx, cz);
        GenHooksTest.assertSameBlocks(pristine.col(), off.generateIsolated(cx, cz),
                "DECORATION 을 끄면 " + found.structureId() + " 조각도 지형지물도 없다(Bukkit shouldGenerateDecorations=false)");
    }

    @Test
    void mobsStageDecidesOriginalMobs() {
        assertTrue(source(null).spawnsOriginalMobs(0, 0), "바닐라 노이즈 월드는 처음 동물을 놓는다");
        Set<String> asked = ConcurrentHashMap.newKeySet();
        NoiseWorldSource n = source(new ChunkGenerationHooks() {
            @Override public boolean runVanilla(GenerationStage stage, int x, int z) {
                if (stage == GenerationStage.MOBS) asked.add(x + "," + z);
                return stage != GenerationStage.MOBS || x != 0;
            }
        });
        assertFalse(n.spawnsOriginalMobs(0, 3));
        assertTrue(n.spawnsOriginalMobs(1, 3));
        assertEquals(Set.of("0,3", "1,3"), asked);
    }

    @Test
    void biomesSetDuringGenerationAreSeenByDecoration() {
        List<String> seen = new CopyOnWriteArrayList<>();
        Set<String> features = ConcurrentHashMap.newKeySet();
        NoiseWorldSource n = source(new ChunkGenerationHooks() {
            @Override public void surface(GenerationChunk c) {
                for (int x = 0; x < 16; x += 4)
                    for (int z = 0; z < 16; z += 4)
                        for (int y = c.minY(); y <= c.maxY(); y += 4) c.setBiome(x, y, z, "minecraft:desert");
                assertEquals("minecraft:desert", c.biome(5, 70, 9));
                assertThrows(IllegalArgumentException.class, () -> c.setBiome(0, 0, 0, "minecraft:no_such_biome"));
            }
            @Override public boolean feature(String id, int x, int z) {
                if (x == 0 && z == 0) features.add(id);
                return true;
            }
            @Override public void decorate(DecorationRegion r) {
                if (r.originChunkX() == 0 && r.originChunkZ() == 0) seen.add(r.biome(5, 70, 9));
            }
        });
        n.generateIsolated(0, 0);
        assertEquals(List.of("minecraft:desert"), seen, "장식 단계는 청크에 저장된(바뀐) 바이옴을 읽는다");
        assertTrue(features.contains("minecraft:patch_cactus_desert"), "바뀐 바이옴의 지형지물도 장식 목록에 든다: " + features);
    }

    @Test
    void biomesSetInCarverStageAreSeenByDecoration() {
        Set<String> viaCarvers = desertEverywhere(GenerationStage.CARVERS);
        assertTrue(viaCarvers.contains("minecraft:patch_cactus_desert"), "카버 단계에서 바꾼 바이옴도 장식 목록에 든다: " + viaCarvers);
        assertEquals(desertEverywhere(GenerationStage.SURFACE), viaCarvers,
                "후보는 저장된 바이옴 값만으로 정해진다 — 어느 단계에서 덮어썼든 같다");
    }

    /** 그 단계 훅에서 모든 청크를 사막으로 바꾸고 청크 (0,0) 의 지형지물 후보를 모은다(장식 단계가 사막을 읽는지도 확인). */
    static Set<String> desertEverywhere(GenerationStage stage) {
        List<String> seen = new CopyOnWriteArrayList<>();
        Set<String> features = ConcurrentHashMap.newKeySet();
        NoiseWorldSource n = source(new ChunkGenerationHooks() {
            void paint(GenerationChunk c) {
                for (int x = 0; x < 16; x += 4)
                    for (int z = 0; z < 16; z += 4)
                        for (int y = c.minY(); y <= c.maxY(); y += 4) c.setBiome(x, y, z, "minecraft:desert");
            }
            @Override public void surface(GenerationChunk c) { if (stage == GenerationStage.SURFACE) paint(c); }
            @Override public void carvers(GenerationChunk c) { if (stage == GenerationStage.CARVERS) paint(c); }
            @Override public boolean feature(String id, int x, int z) {
                if (x == 0 && z == 0) features.add(id);
                return true;
            }
            @Override public void decorate(DecorationRegion r) {
                if (r.originChunkX() == 0 && r.originChunkZ() == 0) seen.add(r.biome(5, 70, 9));
            }
        });
        n.generateIsolated(0, 0);
        assertEquals(List.of("minecraft:desert"), seen, stage + " 단계에서 바꾼 바이옴을 장식 단계가 본다");
        return features;
    }

    @Test
    void worldDecoratorsRunAfterHooksInOrderWithHeights() {
        List<String> order = new CopyOnWriteArrayList<>();
        NoiseWorldSource n = source(new ChunkGenerationHooks() {
            @Override public void decorate(DecorationRegion r) { if (r.originChunkX() == 0 && r.originChunkZ() == 0) order.add("hooks"); }
        });
        n.chunkDecorators().add(r -> {
            if (r.originChunkX() != 0 || r.originChunkZ() != 0) return;
            order.add("first");
            int top = r.highestBlockY(8, 8);
            assertEquals(top + 1, r.height(GenerationHeightmap.WORLD_SURFACE, 8, 8), "높이맵은 첫 빈 칸의 y");
            assertTrue(r.setBlock(8, top + 1, 8, BlockData.of("minecraft:gold_block")));
        });
        n.chunkDecorators().add(r -> {
            if (r.originChunkX() != 0 || r.originChunkZ() != 0) return;
            order.add("second:" + r.blockType(8, r.highestBlockY(8, 8), 8));
        });
        ChunkColumn col = n.generateIsolated(0, 0);
        assertEquals(List.of("hooks", "first", "second:minecraft:gold_block"), order);
        int y = col.maxY();
        while (y > col.minY() && blocks.nameOf(col.getBlock(8, y, 8)).equals("minecraft:air")) y--;
        assertEquals("minecraft:gold_block", blocks.nameOf(col.getBlock(8, y, 8)));
    }

    @Test
    void throwingDecoratorDoesNotStopGeneration() {
        NoiseWorldSource n = source(null);
        n.chunkDecorators().add(r -> { throw new IllegalStateException("boom"); });
        GenHooksTest.assertSameBlocks(source(null).generateIsolated(0, 0), n.generateIsolated(0, 0), "던진 장식기는 건너뛴다");
    }
}
