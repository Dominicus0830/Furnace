// random_patch/simple_block feature의 바닐라 random 소비·배치 parity 검증
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class RandomPatchFeatureTest {

    private static final int GROUND = 9;       // 표면 블록 (grass_block 대역)
    private static final int SHORT_GRASS = 50; // 심을 식물

    /** y=63 표면(GROUND), 그 위는 전부 AIR인 16x16 컬럼. */
    private FeatureWorldView surface() {
        ChunkColumn col = new ChunkColumn(0);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                col.setBlock(x, 63, z, GROUND);
            }
        }
        return new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
    }

    @Test
    void simpleBlock_placesOnValidGround_skipsOnInvalid() {
        FeatureWorldView view = surface();
        SimpleBlockFeature f = new SimpleBlockFeature(SHORT_GRASS, Set.of(GROUND));

        // y=64: 아래(63)=GROUND → 생존, 식재
        assertTrue(f.place(view, new WorldgenRandom(), 8, 64, 8));
        assertEquals(SHORT_GRASS, view.getBlock(8, 64, 8));

        // y=66: 아래(65)=AIR → 생존 실패
        assertFalse(f.place(view, new WorldgenRandom(), 8, 66, 8));
        assertEquals(ChunkSection.AIR, view.getBlock(8, 66, 8));
    }

    private RandomPatchFeature grassPatch(int tries, int xz, int y) {
        SimpleBlockFeature inner = new SimpleBlockFeature(SHORT_GRASS, Set.of(GROUND));
        PlacedFeature innerPlaced = new PlacedFeature(inner,
            List.of(new PlacementModifier.BlockPredicateFilter(Set.of(ChunkSection.AIR))));
        return new RandomPatchFeature(tries, xz, y, innerPlaced);
    }

    @Test
    void randomPatch_consumesSixRandomsPerTry_streamParity() {
        RandomPatchFeature patch = grassPatch(4, 1, 0); // i1=2, i2=1

        WorldgenRandom actual = new WorldgenRandom();
        actual.setFeatureSeed(123456L, 3, 6);
        patch.place(surface(), actual, 8, 64, 8);

        // 바닐라: try마다 nextInt(i1),nextInt(i1),nextInt(i2),nextInt(i2),nextInt(i1),nextInt(i1)
        WorldgenRandom replay = new WorldgenRandom();
        replay.setFeatureSeed(123456L, 3, 6);
        for (int t = 0; t < 4; t++) {
            replay.nextInt(2); replay.nextInt(2);
            replay.nextInt(1); replay.nextInt(1);
            replay.nextInt(2); replay.nextInt(2);
        }
        // 소비량이 같으면 이후 draw가 일치
        assertEquals(replay.nextInt(1_000_000), actual.nextInt(1_000_000));
    }

    @Test
    void randomPatch_placesGrassAtPredictedOffsets() {
        FeatureWorldView view = surface();
        RandomPatchFeature patch = grassPatch(4, 1, 0);

        WorldgenRandom rng = new WorldgenRandom();
        rng.setFeatureSeed(123456L, 3, 6);
        assertTrue(patch.place(view, rng, 8, 64, 8));

        WorldgenRandom p = new WorldgenRandom();
        p.setFeatureSeed(123456L, 3, 6);
        for (int t = 0; t < 4; t++) {
            int dx = p.nextInt(2) - p.nextInt(2);
            int dy = p.nextInt(1) - p.nextInt(1);
            int dz = p.nextInt(2) - p.nextInt(2);
            int x = 8 + dx, y = 64 + dy, z = 8 + dz;
            assertEquals(SHORT_GRASS, view.getBlock(x, y, z),
                "try " + t + " @ " + x + "," + y + "," + z);
        }
    }
}
