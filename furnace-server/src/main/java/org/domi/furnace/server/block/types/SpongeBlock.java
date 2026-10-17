// 바닐라 SpongeBlock: 놓이거나 이웃이 바뀌면 거리 6·최대 64칸의 물을 빨아들이고 젖은 스펀지가 된다.
package org.domi.furnace.server.block.types;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;

public class SpongeBlock extends BlockBehavior {

    private static final int WET_SPONGE = B.id("wet_sponge"), KELP = B.id("kelp"), KELP_PLANT = B.id("kelp_plant"),
            SEAGRASS = B.id("seagrass"), TALL_SEAGRASS = B.id("tall_seagrass");

    public SpongeBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, block)) tryAbsorbWater(level, pos);
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        tryAbsorbWater(level, pos);
        super.neighborChanged(state, level, pos, sourceBlock, sourcePos, moved);
    }

    protected void tryAbsorbWater(BlockLevel level, Pos pos) {
        if (removeWaterBreadthFirstSearch(level, pos)) {
            level.setBlock(pos, B.defaultState(WET_SPONGE), 2);
            level.playSound(null, pos, "minecraft:block.sponge.absorb", "blocks", 1.0F, 1.0F);
        }
    }

    /** BlockPos.breadthFirstTraversal(pos, 6, 65, 여섯 방향, 물 지우기) > 1. */
    private static boolean removeWaterBreadthFirstSearch(BlockLevel level, Pos start) {
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        ArrayDeque<Integer> depth = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(start);
        depth.add(0);
        int count = 0;
        while (!queue.isEmpty()) {
            Pos p = queue.poll();
            int d = depth.poll();
            if (seen.add(p.asLong()) && visit(level, start, p)) {
                if (++count >= 65) return count > 1;
                if (d < 6) {
                    for (int dir : Dir.ALL) {
                        queue.add(p.relative(dir));
                        depth.add(d + 1);
                    }
                }
            }
        }
        return count > 1;
    }

    private static boolean visit(BlockLevel level, Pos start, Pos p) {
        if (p.equals(start)) return true;
        int state = level.getBlockState(p);
        if (!Fluids.isWater(level.getFluidState(p))) return false;
        if (BucketPickup.is(state) && BucketPickup.pickupBlock(null, level, p, state) != null) return true;
        if (B.isLiquid(state)) {
            level.setBlock(p, B.defaultState("air"), 3);
        } else {
            int b = B.block(state);
            if (b != KELP && b != KELP_PLANT && b != SEAGRASS && b != TALL_SEAGRASS) return false;
            // TODO(드롭): Block.dropResources(state, level, pos, blockEntity)
            level.setBlock(p, B.defaultState("air"), 3);
        }
        return true;
    }
}
