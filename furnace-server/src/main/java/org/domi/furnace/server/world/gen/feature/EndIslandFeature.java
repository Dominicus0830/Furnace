// end_island — 바닐라 EndIslandFeature 이식 (반지름 4~6 의 end_stone 원반을 아래로 쌓아 가는 작은 떠 있는 섬)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class EndIslandFeature implements ConfiguredFeature {

    private final int endStone;

    public EndIslandFeature(BlockRegistry registry) {
        this.endStone = registry.defaultStateId("minecraft:end_stone");
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        place(random, (bx, by, bz) -> view.setBlock(bx, by, bz, endStone), x, y, z);
        return true;
    }

    /** 칸 하나를 end_stone 으로. */
    @FunctionalInterface
    public interface Setter {
        void set(int x, int y, int z);
    }

    /** 섬 모양만(월드젠 뷰·살아 있는 레벨 공용 — 엔드 관문 출구 섬). */
    public static void place(WorldgenRandom random, Setter setter, int x, int y, int z) {
        float radius = (float) random.nextInt(3) + 4.0F;
        for (int dy = 0; radius > 0.5F; dy--) {
            // Mth.floor(-r) / Mth.ceil(r)
            int lo = floor(-radius), hi = ceil(radius);
            for (int dx = lo; dx <= hi; dx++) {
                for (int dz = lo; dz <= hi; dz++) {
                    if ((float) (dx * dx + dz * dz) <= (radius + 1.0F) * (radius + 1.0F)) {
                        setter.set(x + dx, y + dy, z + dz);
                    }
                }
            }
            radius -= (float) random.nextInt(2) + 0.5F;
        }
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < (float) i ? i - 1 : i;
    }

    private static int ceil(float v) {
        int i = (int) v;
        return v > (float) i ? i + 1 : i;
    }
}
