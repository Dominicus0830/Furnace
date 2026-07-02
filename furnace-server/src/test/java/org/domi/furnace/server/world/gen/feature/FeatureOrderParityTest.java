// Furnace FeatureSorter의 step별 feature 순서/index가 vanilla와 일치하는지 검증 (광석 좌표 결정자).
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.junit.jupiter.api.Test;

class FeatureOrderParityTest {

    @Test
    void featureSorter_matchesVanillaStepOrder() throws Exception {
        Fixture fx = loadFixture();

        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        BlockRegistry registry = BlockRegistry.load("blocks/1.21.1.txt");
        org.domi.furnace.server.net.registry.RegistrySet registries =
            org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt");

        // production(NoiseWorldSource)이 도출하는 biome 순서가 vanilla possibleBiomes()와 같은지 검증.
        List<String> derivedOrder = possibleBiomeOrder(data, registries);
        assertEquals(fx.biomes, derivedOrder, "possibleBiomes 순서가 vanilla와 불일치 — production 광석 index 어긋남");

        BiomeFeatureData bfd = BiomeFeatureData.load(data, derivedOrder, registry);
        FeatureSorter sorter = FeatureSorter.build(bfd);

        for (int step = 0; step < fx.steps.size(); step++) {
            List<String> expected = fx.steps.get(step);
            List<String> actual = new ArrayList<>();
            for (PlacedFeature pf : sorter.featuresPerStep().get(step).features()) {
                actual.add(bfd.idOf(pf));
            }
            assertEquals(expected, actual, "step " + step + " feature 순서 불일치");
        }
    }

    /** NoiseWorldSource.possibleBiomeOrder와 동일 로직 — biome_parameters.json 첫등장 distinct(registry 존재만). */
    private static List<String> possibleBiomeOrder(WorldgenData data,
            org.domi.furnace.server.net.registry.RegistrySet registries) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (com.google.gson.JsonElement el : data.biomeParameters()) {
            String name = el.getAsJsonObject().get("biome").getAsString();
            if (registries.indexOf("minecraft:worldgen/biome", name) < 0) continue;
            seen.add(name);
        }
        return new ArrayList<>(seen);
    }

    private record Fixture(List<String> biomes, List<List<String>> steps) {}

    private static Fixture loadFixture() throws Exception {
        try (InputStream in = FeatureOrderParityTest.class.getResourceAsStream(
                "/worldgen/fixtures/feature_order.txt")) {
            assertNotNull(in, "feature_order.txt fixture 없음 — oracle featureorder 실행 필요");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> biomes = new ArrayList<>();
            List<List<String>> steps = new ArrayList<>();
            String line = r.readLine();
            int biomeCount = Integer.parseInt(line.split(" ")[1]);
            for (int i = 0; i < biomeCount; i++) biomes.add(r.readLine().trim());
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] h = line.split(" "); // STEP <s> <count>
                int count = Integer.parseInt(h[2]);
                List<String> feats = new ArrayList<>();
                for (int i = 0; i < count; i++) feats.add(r.readLine().trim());
                steps.add(feats);
            }
            return new Fixture(biomes, steps);
        }
    }
}
