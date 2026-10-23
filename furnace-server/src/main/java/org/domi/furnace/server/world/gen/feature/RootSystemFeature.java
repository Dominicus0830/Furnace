// root_system — 바닐라 RootSystemFeature 포팅(뿌리내린 진달래 나무: 위쪽 공간 탐색 → 나무 → 뿌리 흙 기둥 → 매달린 뿌리).
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class RootSystemFeature implements ConfiguredFeature {

    private final PlacedFeature tree;
    private final int requiredVerticalSpace, rootRadius, rootAttempts, rootColumnMaxHeight;
    private final int hangingRootRadius, hangingSpan, hangingAttempts, allowedVerticalWater;
    private final Set<Integer> rootReplaceable;
    private final StateProvider rootState, hangingState;
    private final BlockPredicate allowedTreePosition;
    private final BlockAttrs a = BlockAttrs.get();
    private final FluidKinds fluids;

    public RootSystemFeature(PlacedFeature tree, int requiredVerticalSpace, int rootRadius, Set<Integer> rootReplaceable, StateProvider rootState,
                             int rootAttempts, int rootColumnMaxHeight, int hangingRootRadius, int hangingSpan, StateProvider hangingState,
                             int hangingAttempts, int allowedVerticalWater, BlockPredicate allowedTreePosition, BlockRegistry registry) {
        this.tree = tree;
        this.requiredVerticalSpace = requiredVerticalSpace;
        this.rootRadius = rootRadius;
        this.rootReplaceable = rootReplaceable;
        this.rootState = rootState;
        this.rootAttempts = rootAttempts;
        this.rootColumnMaxHeight = rootColumnMaxHeight;
        this.hangingRootRadius = hangingRootRadius;
        this.hangingSpan = hangingSpan;
        this.hangingState = hangingState;
        this.hangingAttempts = hangingAttempts;
        this.allowedVerticalWater = allowedVerticalWater;
        this.allowedTreePosition = allowedTreePosition;
        this.fluids = new FluidKinds(registry);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        if (!a.isAir(view.getBlock(x, y, z))) return false;
        if (placeDirtAndTree(view, random, x, y, z)) placeRoots(view, random, x, y, z);
        return true;
    }

    private boolean placeDirtAndTree(FeatureWorldView view, WorldgenRandom random, int x, int y0, int z) {
        int y = y0;
        for (int i = 0; i < rootColumnMaxHeight; i++) {
            y++;
            if (allowedTreePosition.test(view, x, y, z) && spaceForTree(view, x, y, z)) {
                int below = view.getBlock(x, y - 1, z);
                if (isLava(below) || !a.isSolid(below)) return false;
                if (tree.placeAt(view, random, new BlockPos(x, y, z))) {
                    placeDirt(view, random, x, y0, z, y0 + i);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLava(int s) {
        FluidKind k = fluids.of(s);
        return k == FluidKind.LAVA || k == FluidKind.FLOWING_LAVA;
    }

    /** FluidState.is(FluidTags.WATER) — 물 블록 또는 침수 블록 등. */
    private boolean hasWater(int s) {
        FluidKind k = fluids.of(s);
        return k == FluidKind.WATER || k == FluidKind.FLOWING_WATER;
    }

    private boolean spaceForTree(FeatureWorldView view, int x, int y, int z) {
        for (int i = 1; i <= requiredVerticalSpace; i++) {
            int s = view.getBlock(x, y + i, z);
            if (a.isAir(s)) continue;
            if (!(i + 1 <= allowedVerticalWater && hasWater(s))) return false;
        }
        return true;
    }

    private void placeDirt(FeatureWorldView view, WorldgenRandom random, int x, int y0, int z, int maxY) {
        for (int y = y0; y < maxY; y++) placeRootedDirt(view, random, x, y, z);
    }

    private void placeRootedDirt(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int r = rootRadius;
        for (int i = 0; i < rootAttempts; i++) {
            int dx = random.nextInt(r) - random.nextInt(r);
            int dz = random.nextInt(r) - random.nextInt(r);
            int px = x + dx, pz = z + dz;
            if (rootReplaceable.contains(view.getBlock(px, y, pz))) view.setBlock(px, y, pz, rootState.getState(random, px, y, pz));
        }
    }

    private void placeRoots(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int r = hangingRootRadius, span = hangingSpan;
        for (int i = 0; i < hangingAttempts; i++) {
            int dx = random.nextInt(r) - random.nextInt(r);
            int dy = random.nextInt(span) - random.nextInt(span);
            int dz = random.nextInt(r) - random.nextInt(r);
            int px = x + dx, py = y + dy, pz = z + dz;
            if (!a.isAir(view.getBlock(px, py, pz))) continue;
            int state = hangingState.getState(random, px, py, pz);
            // HangingRootsBlock.canSurvive == 위 블록의 아래 면이 튼튼함 (두 번째 조건과 동일)
            if (a.isFaceSturdy(view.getBlock(px, py + 1, pz), BlockAttrs.DOWN)) view.setBlock(px, py, pz, state);
        }
    }
}
