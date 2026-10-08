// block_column feature — vanilla BlockColumnFeature 포팅(레이어별 높이 샘플 → 허용 위치까지 잘라냄 → 방향으로 쌓기)
package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class BlockColumnFeature implements ConfiguredFeature {

    public record Layer(TreeIntProvider height, StateProvider provider) {}

    private final List<Layer> layers;
    private final int[] dir;                  // {dx,dy,dz}
    private final BlockPredicate allowedPlacement;
    private final boolean prioritizeTip;

    public BlockColumnFeature(List<Layer> layers, int[] dir, BlockPredicate allowedPlacement, boolean prioritizeTip) {
        this.layers = List.copyOf(layers);
        this.dir = dir.clone();
        this.allowedPlacement = allowedPlacement;
        this.prioritizeTip = prioritizeTip;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int n = layers.size();
        int[] heights = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            heights[i] = layers.get(i).height().sample(random);
            total += heights[i];
        }
        if (total == 0) return false;

        int px = x + dir[0], py = y + dir[1], pz = z + dir[2];
        for (int i = 0; i < total; i++) {
            if (!allowedPlacement.test(view, px, py, pz)) {
                reduce(heights, total, i, prioritizeTip);
                break;
            }
            px += dir[0]; py += dir[1]; pz += dir[2];
        }

        int cx = x, cy = y, cz = z;
        for (int i = 0; i < n; i++) {
            int h = heights[i];
            if (h == 0) continue;
            Layer layer = layers.get(i);
            for (int j = 0; j < h; j++) {
                view.setBlock(cx, cy, cz, layer.provider().getState(random, cx, cy, cz));
                cx += dir[0]; cy += dir[1]; cz += dir[2];
            }
        }
        return true;
    }

    private static void reduce(int[] heights, int total, int fitted, boolean prioritizeTip) {
        int remaining = total - fitted;
        int step = prioritizeTip ? 1 : -1;
        int start = prioritizeTip ? 0 : heights.length - 1;
        int end = prioritizeTip ? heights.length : -1;
        for (int i = start; i != end && remaining > 0; i += step) {
            int h = heights[i];
            int cut = Math.min(h, remaining);
            remaining -= cut;
            heights[i] -= cut;
        }
    }
}
