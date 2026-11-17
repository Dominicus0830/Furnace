package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.api.worldgen.BiomeProvider;
import org.domi.furnace.api.worldgen.Climate;
import org.junit.jupiter.api.Test;

/** 추가 평지 월드의 층: 옛 생성기(SuperflatWorldSource)와 새 길(NoiseWorldSource.flat)의 블록 같음, 공급자 평지의 층 지우기 조건. */
class ExtraFlatLayersTest {
    static final org.domi.furnace.server.world.BlockRegistry REG = ProvidedBiomesTest.REG;
    static final org.domi.furnace.server.net.registry.RegistrySet REGS = ProvidedBiomesTest.REGS;

    static List<SuperflatWorldSource.Layer> layers(Object... a) {
        var out = new java.util.ArrayList<SuperflatWorldSource.Layer>();
        for (int i = 0; i < a.length; i += 2) out.add(new SuperflatWorldSource.Layer((String) a[i], (Integer) a[i + 1]));
        return out;
    }

    /** 이미 있는 Furnace 추가 평지(level.dat: 지형지물·호수 끔, 구조물 없음)는 새 생성기로 열어도 블록이 같다. */
    private static int differences(List<SuperflatWorldSource.Layer> layers, String biome) {
        int bid = REGS.indexOf("minecraft:worldgen/biome", biome);
        var old = new SuperflatWorldSource(REG, layers, bid, -64, 24);
        var neu = NoiseWorldSource.flat(11L, REG, REGS, new NoiseWorldSource.FlatSpec(layers, biome, false, false, List.of()));
        int d = 0;
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) {
            var a = old.generate(cx, cz);
            var b = neu.generateIsolated(cx, cz);
            for (int y = -64; y < 320; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                if (a.getBlock(x, y, z) != b.getBlock(x, y, z)) d++;
            }
        }
        return d;
    }

    @Test
    void oldAndNewExtraFlatGiveTheSameBlocks() {
        assertEquals(0, differences(SuperflatWorldSource.DEFAULT_LAYERS, "minecraft:plains"));
        assertEquals(0, differences(layers("minecraft:bedrock", 1, "minecraft:stone", 3, "minecraft:air", 2, "minecraft:glass", 1, "minecraft:snow", 1), "minecraft:snowy_plains"));
        assertEquals(0, differences(layers("minecraft:bedrock", 1, "minecraft:dirt", 2, "minecraft:grass_block", 1, "minecraft:short_grass", 1), "minecraft:plains"));
        assertEquals(0, differences(layers("minecraft:bedrock", 1, "minecraft:water", 3, "minecraft:torch", 1), "minecraft:desert"));
    }

    /**
     * 공급자 목록에 평지 바이옴이 없으면 바닐라는 평지 바이옴으로 adjustGenerationSettings 를 부르지 않아 움직임을 막지 않는 층(눈)이
     * 채우기에 그대로 남는다. 목록에 있으면 그 층은 fill_layer 지형지물로 옮겨진다(결과 블록은 같다).
     */
    @Test
    void nonBlockingLayerStaysWhenFlatBiomeIsNotAProviderBiome() {
        var layers = layers("minecraft:bedrock", 1, "minecraft:dirt", 2, "minecraft:grass_block", 1, "minecraft:snow", 1);
        var spec = new NoiseWorldSource.FlatSpec(layers, "minecraft:snowy_plains", false, false, List.of());
        int snow = REG.defaultStateId("minecraft:snow");
        for (List<String> biomes : List.of(List.of("minecraft:desert"), List.of("minecraft:desert", "minecraft:snowy_plains"))) {
            BiomeProvider desert = new BiomeProvider() {
                @Override public String biome(int x, int y, int z, Climate c) { return "minecraft:desert"; }
                @Override public List<String> biomes() { return biomes; }
            };
            var n = NoiseWorldSource.flat(11L, REG, REGS, spec, desert);
            var c = n.generateIsolated(0, 0);
            int count = 0;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) if (c.getBlock(x, -60, z) == snow) count++;
            if (biomes.size() == 1) assertEquals(256, count, "공급자 목록에 평지 바이옴이 없으면 눈 층이 남는다");
            else assertEquals(0, count, "평지 바이옴이 목록에 있으면 눈 층은 그 바이옴의 fill_layer 로만 놓인다(사막 칸에는 없음)");
        }
    }
}
