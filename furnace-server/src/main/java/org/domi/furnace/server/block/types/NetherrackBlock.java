// 바닐라 NetherrackBlock: 네더랙. 옆(3×3×3)에 나일리움이 있고 위가 빛을 통하면 뼛가루로 그 나일리움이 된다(둘 다면 무작위).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class NetherrackBlock extends BlockBehavior implements BonemealableBlock {

    private static final int CRIMSON = B.id("crimson_nylium"), WARPED = B.id("warped_nylium");

    public NetherrackBlock(int block) { super(block); }

    /** propagatesSkylightDown 근사: 가리지 않거나 가림 모양이 꽉 차지 않고, 유체가 없다. TODO(빛): 블록별 값. */
    private static boolean propagatesSkylightDown(int state) {
        boolean occluding = INFO.canOcclude(B.block(state)) && INFO.occlusionShape(state).sameAs(org.domi.furnace.server.block.Shape.BLOCK);
        return !occluding && Fluids.isEmpty(B.fluid(state));
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) {
        if (!propagatesSkylightDown(level.getBlockState(pos.above()))) return false;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (B.isTag(level.getBlockState(pos.offset(x, y, z)), "minecraft:nylium")) return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        boolean warped = false, crimson = false;
        outer:
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    int s = level.getBlockState(pos.offset(x, y, z));
                    if (B.is(s, CRIMSON)) crimson = true;
                    if (B.is(s, WARPED)) warped = true;
                    if (crimson && warped) break outer;
                }
            }
        }
        if (crimson && warped) level.setBlock(pos, B.defaultState(random.nextBoolean() ? CRIMSON : WARPED), 3);
        else if (crimson) level.setBlock(pos, B.defaultState(CRIMSON), 3);
        else if (warped) level.setBlock(pos, B.defaultState(WARPED), 3);
    }
}
