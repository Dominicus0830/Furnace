// 바닐라 GrindstoneBlock: 어디에 붙어도 산다. 쓰면 숫돌 화면.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class GrindstoneBlock extends FaceAttachedHorizontalDirectionalBlock {

    public GrindstoneBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return true; }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        player.openGrindstone(pos);
        player.awardStat("minecraft:interact_with_grindstone");
        return InteractionResult.CONSUME;
    }
}
