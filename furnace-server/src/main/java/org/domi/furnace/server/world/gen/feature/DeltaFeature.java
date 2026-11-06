// delta_feature — 바닐라 DeltaFeature 포팅(현무암 삼각주: 용암 웅덩이 + 마그마 테두리)
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class DeltaFeature implements ConfiguredFeature {

    private static final int[][] DIRS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};   // Direction.values()

    private final BlockAttrs attrs = BlockAttrs.get();
    private final int contents, rim, contentsBlock;
    private final TreeIntProvider size, rimSize;
    private final boolean rimPost, contentsPost;
    private final Set<Integer> cannotReplace;   // 블록 인덱스

    public DeltaFeature(int contents, int rim, TreeIntProvider size, TreeIntProvider rimSize, BlockRegistry r) {
        this.contents = contents;
        this.rim = rim;
        this.contentsBlock = attrs.blockIndex(contents);
        this.rimPost = BlockStates.hasPostProcess(r, rim);
        this.contentsPost = BlockStates.hasPostProcess(r, contents);
        this.size = size;
        this.rimSize = rimSize;
        java.util.HashSet<Integer> s = new java.util.HashSet<>();
        for (String n : new String[]{"bedrock", "nether_bricks", "nether_brick_fence", "nether_brick_stairs", "nether_wart", "chest", "spawner"}) {
            s.add(attrs.blockIndex(r.defaultStateId("minecraft:" + n)));
        }
        this.cannotReplace = Set.copyOf(s);
    }

    @Override
    public boolean place(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz) {
        boolean[] any = {false};
        boolean flag1 = random.nextDouble() < 0.9;
        int i = flag1 ? rimSize.sample(random) : 0;
        int j = flag1 ? rimSize.sample(random) : 0;
        boolean flag2 = flag1 && i != 0 && j != 0;
        int k = size.sample(random);
        int l = size.sample(random);
        int max = Math.max(k, l);
        BlockPosIter.withinManhattan(ox, oy, oz, k, 0, l, (x, y, z) -> {
            if (BlockPosIter.distManhattan(x, y, z, ox, oy, oz) > max) return false;
            if (isClear(v, x, y, z)) {
                if (flag2) {
                    any[0] = true;
                    v.setBlock(x, y, z, rim);
                    if (rimPost) v.markPosForPostprocessing(x, y, z);   // magma_block 은 hasPostProcess
                }
                int qx = x + i, qz = z + j;
                if (isClear(v, qx, y, qz)) {
                    any[0] = true;
                    v.setBlock(qx, y, qz, contents);
                    if (contentsPost) v.markPosForPostprocessing(qx, y, qz);
                }
            }
            return true;
        });
        return any[0];
    }

    private boolean isClear(FeatureWorldView v, int x, int y, int z) {
        int s = v.getBlock(x, y, z);
        int b = attrs.blockIndex(s);
        if (b == contentsBlock) return false;
        if (cannotReplace.contains(b)) return false;
        for (int d = 0; d < 6; d++) {
            boolean air = attrs.isAir(v.getBlock(x + DIRS[d][0], y + DIRS[d][1], z + DIRS[d][2]));
            if (air && d != 1 || !air && d == 1) return false;
        }
        return true;
    }
}
