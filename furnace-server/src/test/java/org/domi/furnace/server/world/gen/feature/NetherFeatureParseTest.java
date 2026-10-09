// 네더 5개 바이옴의 모든 placed feature 가 NoopFeature 폴백 없이 구현체로 파싱되는지 확인
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.junit.jupiter.api.Test;

class NetherFeatureParseTest {

    @Test
    void netherBiomeFeaturesAreAllImplemented() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        BlockRegistry registry = BlockRegistry.load("blocks/1.21.1.txt");
        List<String> biomes = List.of("minecraft:nether_wastes", "minecraft:soul_sand_valley", "minecraft:crimson_forest",
            "minecraft:warped_forest", "minecraft:basalt_deltas");

        BiomeFeatureData features = BiomeFeatureData.load(data, biomes, null, registry, 0, 128, 32);

        List<String> noops = new ArrayList<>();
        java.util.Set<Class<?>> seen = new java.util.HashSet<>();
        for (String biome : biomes) {
            for (int step = 0; step < BiomeFeatureData.STEP_COUNT; step++) {
                for (PlacedFeature f : features.featuresForStep(biome, step)) {
                    if (f.feature() instanceof NoopFeature) noops.add(biome + "/" + step + "/" + features.idOf(f));
                    seen.add(f.feature().getClass());
                }
            }
        }
        // 엔드 전용이 아닌 네더 피처는 모두 구현돼 있어야 한다
        assertTrue(noops.isEmpty(), "NoopFeature 로 폴백한 네더 placed feature: " + noops);
        assertTrue(seen.contains(HugeFungusFeature.class));
        assertTrue(seen.contains(NetherForestVegetationFeature.class));
        assertTrue(seen.contains(NetherVinesFeatures.Twisting.class));
        assertTrue(seen.contains(NetherVinesFeatures.Weeping.class));
        assertTrue(seen.contains(NetherrackReplaceBlobsFeature.class));
        assertTrue(seen.contains(BasaltFeatures.Columns.class));
        assertTrue(seen.contains(BasaltFeatures.Pillar.class));
        assertTrue(seen.contains(DeltaFeature.class));
        assertTrue(seen.contains(GlowstoneBlobFeature.class));
    }
}
