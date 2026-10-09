// netherrack_replace_blobs — 바닐라 ReplaceBlobsFeature 포팅(아래로 target 블록을 찾아 맨해튼 타원체 안의 target 을 state 로 교체)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class NetherrackReplaceBlobsFeature implements ConfiguredFeature {

    private final int targetBlock, state;
    private final TreeIntProvider radius;
    private final boolean statePost;
    private final BlockAttrs attrs = BlockAttrs.get();

    public NetherrackReplaceBlobsFeature(int targetState, int state, TreeIntProvider radius, org.domi.furnace.server.world.BlockRegistry registry) {
        this.statePost = BlockStates.hasPostProcess(registry, state);
        this.targetBlock = attrs.blockIndex(targetState);
        this.state = state;
        this.radius = radius;
    }

    @Override
    public boolean place(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz) {
        // origin.mutable().clamp(Y, minY + 1, maxY - 1) 후 아래로 target 탐색
        int py = Math.max(v.minBuildHeight() + 1, Math.min(v.maxBuildHeight() - 1, oy));
        int ty = Integer.MIN_VALUE;
        while (py > v.minBuildHeight() + 1) {
            if (attrs.blockIndex(v.getBlock(ox, py, oz)) == targetBlock) { ty = py; break; }
            py--;
        }
        if (ty == Integer.MIN_VALUE) return false;
        int rx = radius.sample(random);
        int ry = radius.sample(random);
        int rz = radius.sample(random);
        int max = Math.max(rx, Math.max(ry, rz));
        boolean[] any = {false};
        final int fty = ty;
        BlockPosIter.withinManhattan(ox, fty, oz, rx, ry, rz, (x, y, z) -> {
            if (BlockPosIter.distManhattan(x, y, z, ox, fty, oz) > max) return false;
            if (attrs.blockIndex(v.getBlock(x, y, z)) == targetBlock) {
                v.setBlock(x, y, z, state);
                if (statePost) v.markPosForPostprocessing(x, y, z);
                any[0] = true;
            }
            return true;
        });
        return any[0];
    }
}
