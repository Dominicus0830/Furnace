// sea_pickle — 바닐라 SeaPickleFeature 포팅(count 횟수만큼 바다 바닥 근처에 해삼 절임 1~4개).
package org.domi.furnace.server.world.gen.feature;

import java.util.Map;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SeaPickleFeature implements ConfiguredFeature {

    private static final int UP = 1; // BlockAttrs 방향 인덱스 DOWN=0, UP=1

    private final BlockAttrs attrs = BlockAttrs.get();
    private final TreeIntProvider count;
    private final int waterBlock;
    private final int[] byCount = new int[4];

    public SeaPickleFeature(TreeIntProvider count, BlockRegistry registry) {
        this.count = count;
        this.waterBlock = attrs.blockIndex(registry.defaultStateId("minecraft:water"));
        for (int i = 0; i < 4; i++) {
            byCount[i] = registry.stateIdOrAir("minecraft:sea_pickle",
                Map.of("pickles", Integer.toString(i + 1), "waterlogged", "true"));
        }
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int placed = 0;
        int n = count.sample(random);
        for (int i = 0; i < n; i++) {
            int dx = random.nextInt(8) - random.nextInt(8);
            int dz = random.nextInt(8) - random.nextInt(8);
            int py = view.getOceanFloorY(x + dx, z + dz) + 1;
            int state = byCount[random.nextInt(4)];
            if (attrs.blockIndex(view.getBlock(x + dx, py, z + dz)) == waterBlock && canSurvive(view, x + dx, py, z + dz)) {
                view.setBlock(x + dx, py, z + dz, state);
                placed++;
            }
        }
        return placed > 0;
    }

    /** SeaPickleBlock.canSurvive: 아래 블록 위쪽 면이 충돌 모양으로 비어 있지 않거나 단단해야 한다. */
    private boolean canSurvive(FeatureWorldView view, int x, int y, int z) {
        int below = view.getBlock(x, y - 1, z);
        return attrs.isCollisionFaceFull(below, UP) || attrs.isFaceSturdy(below, UP);
    }
}
