// 바닐라 SnowLayerBlock: 눈(layers 1..8). 같은 것을 들고 윗면을 누르면 한 겹씩 쌓이고, 블록빛이 11 넘으면 녹는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SnowLayerBlock extends BlockBehavior {

    static final Prop LAYERS = B.prop("layers");
    public static final int MAX_HEIGHT = 8;

    public SnowLayerBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos bp = pos.below();
        int below = level.getBlockState(bp);
        if (B.isTag(below, "minecraft:snow_layer_cannot_survive_on")) return false;
        if (B.isTag(below, "minecraft:snow_layer_can_survive_on")) return true;
        return B.behavior(below).getCollisionShape(below, level, bp, null).isFaceFull(Dir.UP) || B.is(below, block) && LAYERS.intValue(below) == MAX_HEIGHT;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.getBlockLight(pos) > 11) {
            level.dropResources(state, pos);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) {
        int layers = LAYERS.intValue(state);
        if (!ctx.holding(block) || layers >= MAX_HEIGHT) return layers == 1;
        return !ctx.replacingClickedOnBlock() || ctx.clickedFace() == Dir.UP;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = ctx.level().getBlockState(ctx.clickedPos());
        if (B.is(s, block)) return LAYERS.withInt(s, Math.min(MAX_HEIGHT, LAYERS.intValue(s) + 1));
        return super.getStateForPlacement(ctx);
    }
}
