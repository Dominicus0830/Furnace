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
        // 구조물 없이(structure_overrides 빈 목록) 층·지형지물만 본다
        return NoiseWorldSource.flat(1234L, REG, REGS, new NoiseWorldSource.FlatSpec(layers, biome, features, lakes, List.of()));
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

        // 공기만인 평지(voidGen)에 the_void 가 아닌 바이옴이면 (!voidGen || the_void) 이 거짓이라 바이옴 지형지물이 없다
        NoiseWorldSource voidPlains = flat(air, "minecraft:plains", true, false);
        for (int cx = 0; cx < 2; cx++) for (int cz = 0; cz < 2; cz++) {
            ChunkColumn col = voidPlains.generateWithFeatures(cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int yy = -64; yy < 0; yy++) {
                assertTrue(ChunkSection.isAir(col.getBlock(x, yy, z)), cx + "," + cz + " " + x + "," + yy + "," + z);
            }
        }
    }

    /** voidGen 은 월드 높이로 자르기 전 층 전체로 정한다 — 공기 384층 위에 돌 1층이면 voidGen 이 아니다. */
    @Test
    void voidGenUsesWholeLayerList() {
        var spec = new NoiseWorldSource.FlatSpec(List.of(new SuperflatWorldSource.Layer("minecraft:air", 384),
                new SuperflatWorldSource.Layer("minecraft:stone", 1)), "minecraft:plains", true, false, null);
        var layers = NoiseWorldSource.FlatLayers.of(spec, REG);
        assertFalse(layers.voidGen());
        assertEquals(385, layers.states().length);
        NoiseWorldSource gen = NoiseWorldSource.flat(1234L, REG, REGS, spec);
        assertEquals(320, gen.flatSurfaceY());   // 채우기는 월드 높이에서 멈춘다
        assertTrue(ChunkSection.isAir(gen.generate(0, 0).getBlock(0, 319, 0)));
    }

    /** /place·직소의 높이(FlatLevelSource.getBaseHeight·getBaseColumn): 지운(null) 층은 건너뛴다. 노이즈 지형이 아니다. */
    @Test
    void baseHeightFollowsLayers() {
        NoiseWorldSource def = flat(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", false, false);
        assertEquals(-61, def.firstOccupiedHeight(NoiseWorldSource.HM_WORLD_SURFACE_WG, 100, -300));
        assertEquals(-61, def.firstOccupiedHeight(NoiseWorldSource.HM_OCEAN_FLOOR_WG, 7, 7));
        assertEquals(state("minecraft:grass_block"), def.baseBlock(5, -61, 5));
        assertTrue(ChunkSection.isAir(def.baseBlock(5, -60, 5)));

        // 눈 층은 지워졌으니 높이·기둥에 없다
        List<SuperflatWorldSource.Layer> snowy = List.of(new SuperflatWorldSource.Layer("minecraft:bedrock", 1),
                new SuperflatWorldSource.Layer("minecraft:stone", 3), new SuperflatWorldSource.Layer("minecraft:snow", 1));
        NoiseWorldSource gen = flat(snowy, "minecraft:plains", false, false);
        assertEquals(-61, gen.firstOccupiedHeight(NoiseWorldSource.HM_WORLD_SURFACE_WG, 0, 0));
        assertTrue(ChunkSection.isAir(gen.baseBlock(0, -60, 0)));

        // 공기만이면 차원 최저 높이(-64) 아래
        NoiseWorldSource air = flat(List.of(new SuperflatWorldSource.Layer("minecraft:air", 1)), "minecraft:the_void", false, false);
        assertEquals(-65, air.firstOccupiedHeight(NoiseWorldSource.HM_WORLD_SURFACE_WG, 0, 0));
    }

    /** MOTION_BLOCKING 이 아닌 층이 둘(눈 층·카펫) — 인라인 fill_layer 둘이 각자 놓인다. */
    @Test
    void twoNonMotionBlockingLayers() {
        var layers = List.of(new SuperflatWorldSource.Layer("minecraft:bedrock", 1), new SuperflatWorldSource.Layer("minecraft:stone", 3),
                new SuperflatWorldSource.Layer("minecraft:snow", 1), new SuperflatWorldSource.Layer("minecraft:white_carpet", 1));
        ChunkColumn col = flat(layers, "minecraft:plains", false, false).generateWithFeatures(2, -1);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            assertEquals(state("minecraft:snow"), col.getBlock(x, -60, z));
            assertEquals(state("minecraft:white_carpet"), col.getBlock(x, -59, z));
        }
    }

    /** lakes: 두꺼운 돌 층에 용암 호수(lake_lava_underground·surface)가 생기고, lakes=false 면 없다. */
    @Test
    void lakesPlaceLava() {
        var layers = List.of(new SuperflatWorldSource.Layer("minecraft:bedrock", 1), new SuperflatWorldSource.Layer("minecraft:stone", 100));
        NoiseWorldSource on = flat(layers, "minecraft:plains", false, true), off = flat(layers, "minecraft:plains", false, false);
        int lava = state("minecraft:lava");
        int lavaOn = 0, lavaOff = 0;
        for (int cx = 0; cx < 8; cx++) for (int cz = 0; cz < 8; cz++) {
            ChunkColumn a = on.generateWithFeatures(cx, cz), b = off.generateWithFeatures(cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -63; y <= 40; y++) {
                if (a.getBlock(x, y, z) == lava) lavaOn++;
                if (b.getBlock(x, y, z) == lava) lavaOff++;
            }
        }
        assertEquals(0, lavaOff);
        assertTrue(lavaOn > 0, "lakes=true 인데 용암 0");
    }

    /**
     * /place structure 는 평지 땅 높이에 짓는다(노이즈 지형 높이가 아니다). 마을(지그소 project_start_to_heightmap)으로 본다 —
     * 사막 피라미드·이글루 같은 ScatteredFeaturePiece 는 바닐라도 시작 상자를 고정 y(64·90)로 두고 배치 때 땅 높이로 옮긴다.
     */
    @Test
    void placeStructureUsesFlatHeight() {
        NoiseWorldSource gen = flat(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", false, false);
        var cs = gen.structureEngine().generateForCommand("minecraft:village_plains", 0, 0, gen);
        assertNotNull(cs.start());
        int minY = cs.start().boundingBox().minY();
        assertTrue(minY < -50, "village_plains minY=" + minY);   // 노이즈 지형이면 50 근처
    }

    /** 평지 생성기는 청크를 만들 때 동물을 놓지 않는다(FlatLevelSource.spawnOriginalMobs 가 비어 있다). */
    @Test
    void flatChunksSpawnNoAnimals(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        NoiseWorldSource gen = flat(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains", true, false);
        var src = new AnvilWorldSource(dir, new org.domi.furnace.server.world.anvil.RegionFileStore(dir),
                new org.domi.furnace.server.world.anvil.AnvilChunkCodec(REG, 0), gen);
        java.util.concurrent.Executor direct = Runnable::run;
        ChunkColumn col = src.loadChunkAsync(3, 4, direct, direct, direct).get();
        assertFalse(col.freshlyGenerated());
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
