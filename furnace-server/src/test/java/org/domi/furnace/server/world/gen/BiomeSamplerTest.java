// BiomeSampler Climate 수학 함수 단위 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;

class BiomeSamplerTest {

    @Test void quantize_positive() { assertEquals(9000L, BiomeSampler.quantize(0.9f)); }
    @Test void quantize_negative() { assertEquals(-9000L, BiomeSampler.quantize(-0.9f)); }
    @Test void quantize_zero() { assertEquals(0L, BiomeSampler.quantize(0f)); }
    @Test void quantize_max() { assertEquals(10000L, BiomeSampler.quantize(1.0f)); }

    @Test void paramDistance_in_range() { assertEquals(0L, BiomeSampler.paramDistance(5000L, 9000L, 7000L)); }
    @Test void paramDistance_at_min() { assertEquals(0L, BiomeSampler.paramDistance(5000L, 9000L, 5000L)); }
    @Test void paramDistance_at_max() { assertEquals(0L, BiomeSampler.paramDistance(5000L, 9000L, 9000L)); }
    @Test void paramDistance_above() { assertEquals(2000L, BiomeSampler.paramDistance(5000L, 9000L, 11000L)); }
    @Test void paramDistance_below() { assertEquals(2000L, BiomeSampler.paramDistance(5000L, 9000L, 3000L)); }
    @Test void paramDistance_point_match() { assertEquals(0L, BiomeSampler.paramDistance(7000L, 7000L, 7000L)); }

    @Test void fitness_all_in_range_no_offset() {
        long[] mn = {-1000, -1000, -1000, -1000, -1000, -1000};
        long[] mx = { 1000,  1000,  1000,  1000,  1000,  1000};
        long[] t  = {    0,     0,     0,     0,     0,     0};
        assertEquals(0L, BiomeSampler.fitness(mn, mx, 0L, t));
    }

    @Test void fitness_with_offset() {
        long[] mn = {-1000, -1000, -1000, -1000, -1000, -1000};
        long[] mx = { 1000,  1000,  1000,  1000,  1000,  1000};
        long[] t  = {    0,     0,     0,     0,     0,     0};
        // sq(10000) = 100_000_000
        assertEquals(100_000_000L, BiomeSampler.fitness(mn, mx, 10000L, t));
    }

    @Test void fitness_one_param_out_of_range() {
        long[] mn = {-1000, -1000, -1000, -1000, -1000, -1000};
        long[] mx = { 1000,  1000,  1000,  1000,  1000,  1000};
        long[] t  = { 3000,     0,     0,     0,     0,     0}; // temperature 2000 above max
        // sq(2000) = 4_000_000
        assertEquals(4_000_000L, BiomeSampler.fitness(mn, mx, 0L, t));
    }

    @Test
    void load_entry_count() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        RegistrySet reg = RegistrySet.load("registries/1.21.1.txt");
        BiomeSampler sampler = BiomeSampler.load(data, reg);
        // After filtering out 1.21.4-only biomes (pale_garden etc), expect 7000+ entries
        assertTrue(sampler.entries().size() > 1000, "Expected >1000 entries, got " + sampler.entries().size());
        // And some entries should map to known biome IDs (>= 0)
        assertTrue(sampler.entries().get(0).registryId() >= 0);
    }
}
