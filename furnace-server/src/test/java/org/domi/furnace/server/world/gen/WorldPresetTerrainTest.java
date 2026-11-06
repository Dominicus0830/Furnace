// 월드 타입 large_biomes·amplified: 바닐라 noise_settings 라우터로 생성되는지(지형 높이·바이옴 크기) 확인한다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.structure.TerrainAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorldPresetTerrainTest {

    static final long SEED = 12345L;
    static BlockRegistry blocks;
    static RegistrySet registries;

    @BeforeAll
    static void load() {
        blocks = BlockRegistry.load("blocks/1.21.1.txt");
        registries = RegistrySet.load("registries/1.21.1.txt");
    }

    @Test
    void noiseSettingsDifferFromOverworldOnlyInRouter() throws Exception {
        var ow = json("overworld");
        for (String name : new String[] {"amplified", "large_biomes"}) {
            var other = json(name);
            for (String key : ow.keySet()) {
                if (key.equals("noise_router")) continue;
                assertEquals(ow.get(key), other.get(key), name + "." + key);
            }
            assertNotEquals(ow.get("noise_router"), other.get("noise_router"), name);
        }
    }

    @Test
    void amplifiedTerrainIsMuchHigher() {
        NoiseWorldSource normal = NoiseWorldSource.overworld(SEED, blocks, registries);
        NoiseWorldSource amplified = NoiseWorldSource.overworld(SEED, blocks, registries, "minecraft:amplified");
        int maxNormal = Integer.MIN_VALUE, maxAmplified = Integer.MIN_VALUE, differ = 0, n = 0;
        for (int x = -2048; x <= 2048; x += 128) {
            for (int z = -2048; z <= 2048; z += 128) {
                int a = normal.firstOccupiedHeight(TerrainAccess.HM_WORLD_SURFACE_WG, x, z);
                int b = amplified.firstOccupiedHeight(TerrainAccess.HM_WORLD_SURFACE_WG, x, z);
                maxNormal = Math.max(maxNormal, a);
                maxAmplified = Math.max(maxAmplified, b);
                if (a != b) differ++;
                n++;
            }
        }
        assertTrue(differ > n / 2, "증폭 지형이 일반과 거의 같다: " + differ + "/" + n);
        assertTrue(maxAmplified > maxNormal + 60, "증폭 최고 " + maxAmplified + ", 일반 최고 " + maxNormal);
    }

    @Test
    void largeBiomesClimateIsLarger() {
        NoiseWorldSource normal = NoiseWorldSource.overworld(SEED, blocks, registries);
        NoiseWorldSource large = NoiseWorldSource.overworld(SEED, blocks, registries, "minecraft:large_biomes");
        // 기후 노이즈(temperature/vegetation/continentalness/erosion)의 첫 옥타브가 2 낮다 = 4배 넓게 바뀐다.
        // weirdness(ridges)는 일반과 같다(바닐라 large_biomes 라우터도 ridges 를 그대로 쓴다).
        for (int param : new int[] {0, 1, 2, 3}) {
            int n = crossings(normal, param), l = crossings(large, param);
            assertTrue(l * 2 < n, "기후 " + param + ": 큰 바이옴 " + l + ", 일반 " + n);
        }
        assertEquals(crossings(normal, 5), crossings(large, 5), "weirdness 는 같은 노이즈");
    }

    /** 가로 줄을 따라 기후 값이 0 을 지나는 횟수. */
    private static int crossings(NoiseWorldSource s, int param) {
        int count = 0;
        for (int row = 0; row < 8; row++) {
            int qz = row * 1024;
            long prev = 0;
            for (int qx = 0; qx < 8192; qx += 4) {
                long v = s.climateTarget(qx, 16, qz)[param];
                if (prev != 0 && v != 0 && (prev < 0) != (v < 0)) count++;
                if (v != 0) prev = v;
            }
        }
        return count;
    }

    @Test
    void presetSourcesGenerateSafeSpawn() {
        for (String id : new String[] {"minecraft:amplified", "minecraft:large_biomes"}) {
            NoiseWorldSource s = NoiseWorldSource.overworld(SEED, blocks, registries, id);
            var spawn = s.defaultSpawn();
            assertEquals(spawn, NoiseWorldSource.overworld(SEED, blocks, registries, id).defaultSpawn(), id);
            var col = s.generate(Math.floorDiv(spawn.x(), 16), Math.floorDiv(spawn.z(), 16));
            assertEquals(org.domi.furnace.server.world.ChunkSection.AIR, col.getBlock(spawn.x() & 15, spawn.y(), spawn.z() & 15), id);
        }
    }

    @Test
    void unknownSettingsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NoiseWorldSource.overworld(SEED, blocks, registries, "minecraft:nether"));
    }

    private static com.google.gson.JsonObject json(String name) throws Exception {
        try (var in = WorldPresetTerrainTest.class.getClassLoader()
                .getResourceAsStream("worldgen/1.21.1/noise_settings/" + name + ".json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
