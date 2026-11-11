// fill_layer — 바닐라 FillLayerFeature(LayerConfiguration(height, state)): 청크 16×16 의 (최저 높이 + height) 칸이 공기면 그 상태로 채운다(플래그 2).
// 평지(FlatLevelGeneratorSettings.adjustGenerationSettings)가 MOTION_BLOCKING 이 아닌 층(눈 층·카펫·공기 등)을 노이즈 대신 이것으로 놓는다.
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class FillLayerFeature implements ConfiguredFeature {
    private final int height;
    private final int state;

    public FillLayerFeature(int height, int state) {
        this.height = height;
        this.state = state;
    }

    public int height() { return height; }

    public int state() { return state; }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int ly = view.minY() + height;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int bx = x + dx, bz = z + dz;
                if (ChunkSection.isAir(view.getBlock(bx, ly, bz))) view.setBlock(bx, ly, bz, state);
            }
        }
        return true;
    }
}
