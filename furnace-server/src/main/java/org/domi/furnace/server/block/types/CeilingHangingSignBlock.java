// 바닐라 CeilingHangingSignBlock: 천장에 매단 표지판. 위 칸 아랫면 가운데가 받쳐야 산다.
// 위가 꽉 찬 면이 아니거나 웅크리면 사슬(attached) — 16방향, 아니면 4방향. 매단 표지판 아래에 이어 달면 그 방향을 따른다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class CeilingHangingSignBlock extends SignBlock {

    static final Prop ROTATION = B.prop("rotation"), ATTACHED = B.prop("attached");

    public CeilingHangingSignBlock(int block) { super(block); }

    @Override
    public float yRotationDegrees(int state) { return ROTATION.intValue(state) * 22.5F; }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        // shouldTryToChainAnotherHangingSign: 클릭 명령이 없고 매단 표지판을 들고 아랫면을 클릭 — 아래에 이어 달게 넘긴다.
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.SignBlockEntity sign
                && !sign.canExecuteClickCommands(isFacingFrontText(sign, player), player.isTextFilteringEnabled())
                && isHangingSignItem(stack) && hit.face() == Dir.DOWN) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos above = pos.above();
        return B.isFaceSturdy(level.getBlockState(above), level, above, Dir.DOWN, BlockInfo.CENTER);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos above = ctx.clickedPos().above();
        int as = level.getBlockState(above);
        boolean allHanging = B.isTag(as, "minecraft:all_hanging_signs");
        int dir = Dir.fromYRot(ctx.rotation());
        boolean attached = !B.behavior(as).getCollisionShape(as, level, above, org.domi.furnace.server.block.CollisionContext.EMPTY).isFaceFull(Dir.DOWN) || ctx.isSecondaryUseActive();
        if (allHanging && !ctx.isSecondaryUseActive()) {
            if (WallHangingSignBlock.FACING.has(as)) {
                int f = Dir.byName(WallHangingSignBlock.FACING.valueName(as));
                if (Dir.axis(f) == Dir.axis(dir)) attached = false;
            } else if (ROTATION.has(as)) {
                int d = segmentDirection(ROTATION.intValue(as));
                if (d >= 0 && Dir.axis(d) == Dir.axis(dir)) attached = false;
            }
        }
        int rot = !attached ? segment(Dir.opposite(dir)) : segment(ctx.rotation() + 180.0F);
        int s = ATTACHED.with(defaultState(), attached);
        s = ROTATION.withInt(s, rot);
        return WATERLOGGED.with(s, B.waterAt(ctx));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.UP && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
