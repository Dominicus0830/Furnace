// end_platform — 바닐라 EndPlatformFeature.createEndPlatform 이식 (5x5 흑요석 발판 + 위 3칸 공기)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class EndPlatformFeature implements ConfiguredFeature {

    private final int obsidian;
    private final int air;

    public EndPlatformFeature(BlockRegistry registry) {
        this.obsidian = registry.defaultStateId("minecraft:obsidian");
        this.air = registry.defaultStateId("minecraft:air");
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        createEndPlatform(view, x, y, z);
        return true;
    }

    /**
     * dropBlocks=false 경로(월드젠). 이미 같은 블록이면 건드리지 않는다.
     * BlockState.is(Block) 은 블록 종류 비교인데, 흑요석·공기는 속성이 없어 state id 비교가 곧 블록 비교다
     * (air/cave_air/void_air 는 서로 다른 블록이라 id 비교 그대로가 바닐라와 같다).
     */
    public void createEndPlatform(FeatureWorldView view, int ox, int oy, int oz) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy < 3; dy++) {
                    int wx = ox + dx, wy = oy + dy, wz = oz + dz;
                    int want = dy == -1 ? obsidian : air;
                    if (view.getBlock(wx, wy, wz) != want) view.setBlock(wx, wy, wz, want);
                }
            }
        }
    }
}
