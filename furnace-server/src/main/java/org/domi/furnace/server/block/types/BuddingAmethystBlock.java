// 바닐라 BuddingAmethystBlock: 싹트는 자수정. 무작위 틱 1/5 로 무작위 면에 싹을 틔우거나 한 단계 키운다(작은→중간→큰→송이).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class BuddingAmethystBlock extends AmethystBlock {

    private static final int SMALL = B.id("small_amethyst_bud"), MEDIUM = B.id("medium_amethyst_bud"), LARGE = B.id("large_amethyst_bud"),
            CLUSTER = B.id("amethyst_cluster"), WATER = B.id("water");

    public BuddingAmethystBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        int dir = Dir.ALL[random.nextInt(6)];
        Pos p = pos.relative(dir);
        int s = level.getBlockState(p);
        int next = -1;
        if (canClusterGrowAtState(s)) next = SMALL;
        else if (B.is(s, SMALL) && AmethystClusterBlock.facing(s) == dir) next = MEDIUM;
        else if (B.is(s, MEDIUM) && AmethystClusterBlock.facing(s) == dir) next = LARGE;
        else if (B.is(s, LARGE) && AmethystClusterBlock.facing(s) == dir) next = CLUSTER;
        if (next >= 0) {
            int ns = AmethystClusterBlock.FACING.with(B.defaultState(next), Dir.NAME[dir]);
            level.setBlockAndUpdate(p, WATERLOGGED.with(ns, Fluids.type(B.fluid(s)) == Fluids.T_WATER));
        }
    }

    /** canClusterGrowAtState: 공기이거나 가득 찬 물 블록. */
    public static boolean canClusterGrowAtState(int state) {
        return B.isAir(state) || B.is(state, WATER) && Fluids.amount(B.fluid(state)) == 8;
    }
}
