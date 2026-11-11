// A06-04(앞쪽): 평지 생성기(바닐라 FlatLevelSource)가 adjustGenerationSettings 대로 지형지물·호수·fill_layer 를 장식 경로로 돌린다.
// 빈 공간 프리셋(공기 1층, the_void, features)은 청크 (0,0) 둘레에 시작 발판을 놓는다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.junit.jupiter.api.Test;

class FlatWorldGenerationTest {
    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final RegistrySet REGS = RegistrySet.load("registries/1.21.1.txt");

    private static NoiseWorldSource flat(List<SuperflatWorldSource.Layer> layers, String biome, boolean features, boolean lakes) {
        return NoiseWorldSource.flat(1234L, REG, REGS, new NoiseWorldSource.FlatSpec(layers, biome, features, lakes, null));
    }

    private static int state(String name) { return REG.defaultStateId(name); }

    /** 기본 평지(지형지물·호수 꺼짐)는 층만 — 예전 층 쌓기 결과와 블록이 같다. */
    @Test
    void defaultFlatIsLayersOnly() {
        NoiseWorldSource gen = flat(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", false, false);
        int plains = REGS.indexOf("minecraft:worldgen/biome", "minecraft:plains");
        SuperflatWorldSource layers = new SuperflatWorldSource(REG, plains);
        for (int[] cp : new int[][] {{0, 0}, {5, -3}, {-7, 11}}) {
            ChunkColumn got = gen.generateWithFeatures(cp[0], cp[1]);
            ChunkColumn want = layers.generate(cp[0], cp[1]);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 120; y++) {
                assertEquals(want.getBlock(x, y, z), got.getBlock(x, y, z), "청크 " + cp[0] + "," + cp[1] + " " + x + "," + y + "," + z);
            }
            assertEquals(plains, got.sections()[0].getBiome(0, 0, 0));
        }
        assertEquals(-60, gen.flatSurfaceY());
        assertEquals(-63, gen.seaLevel());
    }

    /** 빈 공간 프리셋: void_start_platform — (8, 최저+3, 8) 둘레 체스판 거리 16 안이 돌, 가운데는 조약돌. 지형지물이 꺼지면 없다. */
    @Test
    void voidPresetPlacesStartPlatform() {
        List<SuperflatWorldSource.Layer> air = List.of(new SuperflatWorldSource.Layer("minecraft:air", 1));
        NoiseWorldSource gen = flat(air, "minecraft:the_void", true, false);
        int y = -64 + 3;
        ChunkColumn c00 = gen.generateWithFeatures(0, 0);
        assertEquals(state("minecraft:cobblestone"), c00.getBlock(8, y, 8));
        assertEquals(state("minecraft:stone"), c00.getBlock(0, y, 0));
        assertTrue(ChunkSection.isAir(c00.getBlock(8, y + 1, 8)));
        ChunkColumn c11 = gen.generateWithFeatures(1, 1);
        assertEquals(state("minecraft:stone"), c11.getBlock(8, y, 8), "(24,24) 는 체스판 거리 16");
        assertTrue(ChunkSection.isAir(c11.getBlock(9, y, 9)), "(25,25) 는 거리 17");
        ChunkColumn c20 = gen.generateWithFeatures(2, 0);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) assertTrue(ChunkSection.isAir(c20.getBlock(x, y, z)));

        NoiseWorldSource bare = flat(air, "minecraft:the_void", false, false);
        assertTrue(ChunkSection.isAir(bare.generateWithFeatures(0, 0).getBlock(8, y, 8)));
        // 공기 층이 아닌 평지에 the_void 바이옴이 아니면(!voidGen || the_void) 은 참이라 발판이 놓이는 것은 the_void 바이옴 쪽뿐이다
    }

    /** 지형지물이 켜진 평원 평지는 풀·꽃·나무가 자란다(지형지물이 꺼지면 땅 위가 비어 있다). 같은 시드는 같은 결과. */
    @Test
    void featuresGrowVegetation() {
        NoiseWorldSource on = flat(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", true, false);
        NoiseWorldSource off = flat(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", false, false);
        NoiseWorldSource again = flat(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", true, false);
        int above = 0;
        for (int cx = 0; cx < 4; cx++) for (int cz = 0; cz < 4; cz++) {
            ChunkColumn a = on.generateIsolated(cx, cz), b = off.generateIsolated(cx, cz), c = again.generateIsolated(cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -60; y < 0; y++) {
                if (!ChunkSection.isAir(a.getBlock(x, y, z))) above++;
                assertTrue(ChunkSection.isAir(b.getBlock(x, y, z)));
                assertEquals(a.getBlock(x, y, z), c.getBlock(x, y, z));
            }
        }
        assertTrue(above > 50, "땅 위 블록 " + above);
    }

    /** MOTION_BLOCKING 이 아닌 층(눈 층)은 노이즈 대신 fill_layer 가 놓는다 — 결과는 층 그대로. */
    @Test
    void nonMotionBlockingLayerIsFilled() {
        List<SuperflatWorldSource.Layer> layers = List.of(new SuperflatWorldSource.Layer("minecraft:bedrock", 1),
                new SuperflatWorldSource.Layer("minecraft:stone", 3), new SuperflatWorldSource.Layer("minecraft:snow", 1));
        NoiseWorldSource gen = flat(layers, "minecraft:plains", false, false);
        // 노이즈 채우기 결과에는 눈 층이 없다(바닐라 getLayers 에서 null)
        assertTrue(ChunkSection.isAir(gen.generate(0, 0).getBlock(3, -60, 3)));
        ChunkColumn col = gen.generateWithFeatures(0, 0);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            assertEquals(state("minecraft:snow"), col.getBlock(x, -60, z));
            assertEquals(state("minecraft:stone"), col.getBlock(x, -61, z));
        }
    }

    /** level.dat 평지 settings → 생성기 설정(바닐라 코덱: features·lakes 없으면 거짓, structure_overrides 없으면 전부). */
    @Test
    void settingsRoundTrip() {
        var json = com.google.gson.JsonParser.parseString(
                "{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:the_void\",\"features\":true}").getAsJsonObject();
        var spec = SuperflatWorldSource.flatSpecOf(SuperflatWorldSource.settingsNbt(json));
        assertEquals("minecraft:the_void", spec.biome());
        assertTrue(spec.features());
        assertFalse(spec.lakes());
        assertEquals(List.of(new SuperflatWorldSource.Layer("minecraft:air", 1)), spec.layers());
        var none = SuperflatWorldSource.flatSpecOf(null);
        assertEquals(SuperflatWorldSource.DEFAULT_LAYERS, none.layers());
        assertFalse(none.features());
        assertNull(none.structureOverrides());
    }
}
