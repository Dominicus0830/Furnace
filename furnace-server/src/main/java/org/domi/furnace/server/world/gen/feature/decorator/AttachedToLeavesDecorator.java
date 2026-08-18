// vanilla AttachedToLeavesDecorator 포팅 — 맹그로브 프로파굴(잎에 매달린 블록) 배치
package org.domi.furnace.server.world.gen.feature.decorator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;

public final class AttachedToLeavesDecorator implements TreeDecorator {
    private final float probability;
    private final int exclusionRadiusXZ;
    private final int exclusionRadiusY;
    private final int requiredEmptyBlocks;
    private final TreeFeature.Direction[] directions;
    // block_provider(randomized_int_state_provider) 전개 — age 프로퍼티만 무작위이므로
    // age별 state id 배열 + age 샘플러로 표현(mangrove_propagule hanging=true stage=0 waterlogged=false).
    private final TreeIntProvider ageValues;
    private final int[] stateIdsByAge;

    public AttachedToLeavesDecorator(
            float probability, int exclusionRadiusXZ, int exclusionRadiusY, int requiredEmptyBlocks,
            TreeFeature.Direction[] directions, TreeIntProvider ageValues, int[] stateIdsByAge) {
        this.probability = probability;
        this.exclusionRadiusXZ = exclusionRadiusXZ;
        this.exclusionRadiusY = exclusionRadiusY;
        this.requiredEmptyBlocks = requiredEmptyBlocks;
        this.directions = directions;
        this.ageValues = ageValues;
        this.stateIdsByAge = stateIdsByAge;
    }

    @Override
    public void place(Context context) {
        Set<BlockPos> excluded = new HashSet<>();
        WorldgenRandom random = context.random();

        // vanilla Util.shuffledCopy(leaves, random) — Fisher-Yates(뒤에서부터 nextInt(i) 스왑).
        List<BlockPos> shuffled = new ArrayList<>(context.leaves());
        for (int i = shuffled.size(); i > 1; i--) {
            int j = random.nextInt(i);
            BlockPos tmp = shuffled.get(i - 1);
            shuffled.set(i - 1, shuffled.get(j));
            shuffled.set(j, tmp);
        }

        for (BlockPos leaf : shuffled) {
            TreeFeature.Direction dir = directions[random.nextInt(directions.length)];
            BlockPos target = new BlockPos(leaf.x() + dir.dx(), leaf.y() + dir.dy(), leaf.z() + dir.dz());
            // vanilla 단락 순서: !set.contains && nextFloat < p && hasRequiredEmptyBlocks — RNG 소비 순서 보존.
            if (excluded.contains(target)) continue;
            if (!(random.nextFloat() < probability)) continue;
            if (!hasRequiredEmptyBlocks(context, leaf, dir)) continue;

            for (int dx = -exclusionRadiusXZ; dx <= exclusionRadiusXZ; dx++) {
                for (int dy = -exclusionRadiusY; dy <= exclusionRadiusY; dy++) {
                    for (int dz = -exclusionRadiusXZ; dz <= exclusionRadiusXZ; dz++) {
                        excluded.add(new BlockPos(target.x() + dx, target.y() + dy, target.z() + dz));
                    }
                }
            }

            int age = ageValues.sample(random);
            context.view().setBlock(target.x(), target.y(), target.z(), stateIdsByAge[age]);
            context.decorations().add(target);
        }
    }

    private boolean hasRequiredEmptyBlocks(Context context, BlockPos leaf, TreeFeature.Direction dir) {
        for (int i = 1; i <= requiredEmptyBlocks; i++) {
            int state = context.view().getBlock(leaf.x() + dir.dx() * i, leaf.y() + dir.dy() * i, leaf.z() + dir.dz() * i);
            if (!TreeFeature.isAir(state)) return false;
        }
        return true;
    }
}
