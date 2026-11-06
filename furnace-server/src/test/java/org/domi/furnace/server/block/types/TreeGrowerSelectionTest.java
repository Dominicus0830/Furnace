// TreeGrower 의 나무 고르기: 바닐라처럼 보조 확률 난수를 언제 몇 번 뽑는지와 고른 feature.
package org.domi.furnace.server.block.types;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.RandomSource;
import org.junit.jupiter.api.Test;

class TreeGrowerSelectionTest {

    /** nextFloat 만 정해진 값을 돌려주고 횟수를 센다(다른 호출은 실패). */
    static final class Floats implements RandomSource {
        final float v;
        int calls;
        Floats(float v) { this.v = v; }
        public float nextFloat() { calls++; return v; }
        public int nextInt() { throw new AssertionError(); }
        public int nextInt(int bound) { throw new AssertionError(); }
        public long nextLong() { throw new AssertionError(); }
        public double nextDouble() { throw new AssertionError(); }
        public boolean nextBoolean() { throw new AssertionError(); }
        public double nextGaussian() { throw new AssertionError(); }
    }

    private static String pick(String tree, float f, boolean flowers, int expectCalls) {
        Floats r = new Floats(f);
        String id = TreeGrower.configuredFeature(TreeGrower.of(tree), r, flowers);
        assertEquals(expectCalls, r.calls, tree);
        return id;
    }

    @Test
    void secondaryChanceAlwaysDrawsOneFloat() {
        assertEquals("minecraft:fancy_oak", pick("oak", 0.05F, false, 1));
        assertEquals("minecraft:fancy_oak_bees_005", pick("oak", 0.05F, true, 1));
        assertEquals("minecraft:oak", pick("oak", 0.5F, false, 1));
        assertEquals("minecraft:oak_bees_005", pick("oak", 0.5F, true, 1));
        assertEquals("minecraft:birch_bees_005", pick("birch", 0.0F, true, 1));   // 보조 확률 0 이어도 뽑는다
        assertEquals("minecraft:tall_mangrove", pick("mangrove", 0.8F, false, 1));
        assertEquals("minecraft:mangrove", pick("mangrove", 0.9F, false, 1));
        assertEquals("minecraft:spruce", pick("spruce", 0.1F, true, 1));            // 보조 나무 없음 → 기본
        assertNull(pick("dark_oak", 0.1F, false, 1));                                 // 1×1 짙은 참나무 없음
    }

    @Test
    void megaDrawsOnlyWithSecondaryMega() {
        Floats r = new Floats(0.4F);
        assertEquals("minecraft:mega_pine", TreeGrower.configuredMegaFeature(TreeGrower.of("spruce"), r));
        assertEquals(1, r.calls);
        r = new Floats(0.6F);
        assertEquals("minecraft:mega_spruce", TreeGrower.configuredMegaFeature(TreeGrower.of("spruce"), r));
        r = new Floats(0.0F);
        assertEquals("minecraft:mega_jungle_tree", TreeGrower.configuredMegaFeature(TreeGrower.of("jungle"), r));
        assertEquals("minecraft:dark_oak", TreeGrower.configuredMegaFeature(TreeGrower.of("dark_oak"), r));
        assertNull(TreeGrower.configuredMegaFeature(TreeGrower.of("oak"), r));
        assertEquals(0, r.calls);
    }
}
