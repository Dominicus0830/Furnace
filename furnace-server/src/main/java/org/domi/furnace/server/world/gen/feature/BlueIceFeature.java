// blue_ice — 바닐라 BlueIceFeature 포팅 (수면 아래 packed_ice에 붙은 물 속에서 파란 얼음 덩어리 성장)
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class BlueIceFeature implements ConfiguredFeature {

    private static final int SEA_LEVEL = 63;
    private static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private final BlockAttrs attrs = BlockAttrs.get();
    private final Set<Integer> water;
    private final int packedIce;
    private final int ice;
    private final int blueIce;

    public BlueIceFeature(BlockRegistry registry) {
        this.water = registry.stateIdsForBlock("minecraft:water");
        this.packedIce = registry.defaultStateId("minecraft:packed_ice");
        this.ice = registry.defaultStateId("minecraft:ice");
        this.blueIce = registry.defaultStateId("minecraft:blue_ice");
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom r, int x, int y, int z) {
        if (y > SEA_LEVEL - 1) return false;
        if (!water.contains(view.getBlock(x, y, z)) && !water.contains(view.getBlock(x, y - 1, z))) return false;
        boolean nearPacked = false;
        for (int i = 1; i < DIRS.length; i++) {          // DOWN 제외
            int[] d = DIRS[i];
            if (view.getBlock(x + d[0], y + d[1], z + d[2]) == packedIce) {
                nearPacked = true;
                break;
            }
        }
        if (!nearPacked) return false;
        view.setBlock(x, y, z, blueIce);

        for (int n = 0; n < 200; n++) {
            int dy = r.nextInt(5) - r.nextInt(6);
            int spread = 3;
            if (dy < 2) spread += dy / 2;
            if (spread >= 1) {
                int px = x + r.nextInt(spread) - r.nextInt(spread);
                int py = y + dy;
                int pz = z + r.nextInt(spread) - r.nextInt(spread);
                int cur = view.getBlock(px, py, pz);
                if (attrs.isAir(cur) || water.contains(cur) || cur == packedIce || cur == ice) {
                    for (int[] d : DIRS) {
                        if (view.getBlock(px + d[0], py + d[1], pz + d[2]) == blueIce) {
                            view.setBlock(px, py, pz, blueIce);
                            break;
                        }
                    }
                }
            }
        }
        return true;
    }
}
