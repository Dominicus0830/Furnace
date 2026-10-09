// glowstone_blob — 바닐라 GlowstoneFeature 포팅(천장에 매달린 글로스톤 덩어리)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class GlowstoneBlobFeature implements ConfiguredFeature {

    private static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};   // Direction.values()

    private final BlockAttrs attrs = BlockAttrs.get();
    private final int glowstone, glowstoneBlock, netherrackBlock, basaltBlock, blackstoneBlock;

    public GlowstoneBlobFeature(BlockRegistry r) {
        this.glowstone = r.defaultStateId("minecraft:glowstone");
        this.glowstoneBlock = attrs.blockIndex(glowstone);
        this.netherrackBlock = attrs.blockIndex(r.defaultStateId("minecraft:netherrack"));
        this.basaltBlock = attrs.blockIndex(r.defaultStateId("minecraft:basalt"));
        this.blackstoneBlock = attrs.blockIndex(r.defaultStateId("minecraft:blackstone"));
    }

    @Override
    public boolean place(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz) {
        if (!attrs.isAir(v.getBlock(ox, oy, oz))) return false;
        int above = attrs.blockIndex(v.getBlock(ox, oy + 1, oz));
        if (above != netherrackBlock && above != basaltBlock && above != blackstoneBlock) return false;
        v.setBlock(ox, oy, oz, glowstone);
        for (int i = 0; i < 1500; i++) {
            // origin.offset(a(8)-a(8), -a(12), a(8)-a(8))
            int px = ox + random.nextInt(8) - random.nextInt(8);
            int py = oy - random.nextInt(12);
            int pz = oz + random.nextInt(8) - random.nextInt(8);
            if (attrs.isAir(v.getBlock(px, py, pz))) {
                int n = 0;
                for (int[] d : DIRS) {
                    if (attrs.blockIndex(v.getBlock(px + d[0], py + d[1], pz + d[2])) == glowstoneBlock) n++;
                    if (n > 1) break;
                }
                if (n == 1) v.setBlock(px, py, pz, glowstone);
            }
        }
        return true;
    }
}
