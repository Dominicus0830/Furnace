// /locate biome 탐색 순서: 바닐라 spiralAround·outFromOrigin 을 손으로 따라간 값과 대조.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BiomeLocatorTest {

    @Test
    void spiralOrder() {
        List<String> got = new ArrayList<>();
        BiomeLocator.spiralAround(1, (x, z) -> got.add(x + "," + z));
        assertEquals(List.of("0,0", "1,0", "1,1", "0,1", "-1,1", "-1,0", "-1,-1", "0,-1", "1,-1"), got);
    }

    @Test
    void spiralCoversSquareOnce() {
        for (int r : new int[] {0, 2, 7}) {
            Set<String> seen = new HashSet<>();
            int[] n = {0};
            BiomeLocator.spiralAround(r, (x, z) -> {
                assertTrue(Math.abs(x) <= r && Math.abs(z) <= r);
                assertTrue(seen.add(x + "," + z));
                n[0]++;
                return true;
            });
            assertEquals((2 * r + 1) * (2 * r + 1), n[0]);
        }
    }

    @Test
    void outFromOrigin() {
        assertArrayEquals(new int[] {0, 64, 128, 192, 256, 320}, BiomeLocator.outFromOrigin(0, -63, 320, 64).toArray());
        assertArrayEquals(new int[] {70, 134, 6, 198, -58, 262}, BiomeLocator.outFromOrigin(70, -63, 320, 64).toArray());
        assertEquals(0, BiomeLocator.outFromOrigin(-64, -63, 320, 64).count());
    }

    @Test
    void findClosest() {
        // 블록 x ≥ 400 이고 y ≥ 64 인 곳만 바이옴 7
        BiomeLocator.NoiseBiome s = (qx, qy, qz) -> qx >= 100 && qy >= 16 ? 7 : 1;
        var f = BiomeLocator.findClosestBiome3d(0, 0, 0, 6400, 32, 64, Set.of(1, 7), b -> b == 7, s, -64, 320);
        // 나선은 반지름 13 고리의 첫 칸 (13, -12) 에서 x=416 에 처음 닿고, 세로는 0 다음 64
        assertEquals(new BiomeLocator.Found(416, 64, -384, 7), f);
        assertNull(BiomeLocator.findClosestBiome3d(0, 0, 0, 6400, 32, 64, Set.of(1), b -> b == 7, s, -64, 320));
        assertEquals(new BiomeLocator.Found(5, 6, 7, 3), BiomeLocator.fixed(5, 6, 7, 3, b -> b == 3));
        // 취소는 못 찾음(null)과 구분된다
        assertThrows(java.util.concurrent.CancellationException.class, () ->
            BiomeLocator.findClosestBiome3d(0, 0, 0, 6400, 32, 64, Set.of(1, 7), b -> b == 7, s, -64, 320, () -> true));
    }
}
