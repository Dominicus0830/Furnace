// 바닐라 TrapDoorBlock: 다락문. 옆면 위·아래 반쪽 클릭이나 바라보는 방향으로 놓는다. 손(블록 세트가 허락하면)·신호로 여닫는다. 물에 잠길 수 있다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockSetType;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class TrapDoorBlock extends HorizontalDirectionalBlock {

    static final Prop OPEN = B.prop("open"), HALF = B.prop("half"), POWERED = B.prop("powered");
    private final BlockSetType type;

    public TrapDoorBlock(int block) {
        super(block);
        this.type = BlockSetType.of(block);
    }

    /**
     * LivingEntity.trapdoorUsableAsLadder: 열린 다락문이고 바로 아래가 같은 방향을 보는 사다리면 오를 수 있다
     * (#climbable 이 아니어도). {@code trap} 이 다락문(TrapDoorBlock)인지는 부르는 쪽이 확인한다.
     */
    public static boolean usableAsLadder(int trap, int below) {
        if (!OPEN.is(trap)) return false;
        return below >= 0 && B.is(below, "minecraft:ladder") && FACING.get(below) == FACING.get(trap);
    }

    /** 이 상태가 다락문(TrapDoorBlock, 구리 다락문 포함)인가 — 바닐라 instanceof TrapDoorBlock. */
    public static boolean isTrapDoor(int state) { return state >= 0 && B.behavior(state) instanceof TrapDoorBlock; }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!type.canOpenByHand()) return InteractionResult.PASS;
        toggle(state, level, pos, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onExplosionHit(int state, BlockLevel level, Pos pos, Explosion explosion) {
        if (explosion.canTriggerBlocks() && type.canOpenByWindCharge() && !POWERED.is(state)) toggle(state, level, pos, null);
    }

    private void toggle(int state, BlockLevel level, Pos pos, BlockActor player) {
        int s = OPEN.cycle(state);
        level.setBlock(pos, s, 2);
        if (WATERLOGGED.is(s)) level.scheduleFluidTick(pos, Fluids.T_WATER, 5);
        playSound(player, level, pos, OPEN.is(s));
    }

    protected void playSound(BlockActor player, BlockLevel level, Pos pos, boolean open) {
        level.playSound(player, pos, open ? type.trapdoorOpen() : type.trapdoorClose(), "blocks", 1.0F, B.openPitch(level));
        level.gameEvent(open ? "minecraft:block_open" : "minecraft:block_close", pos, player, -1);
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != POWERED.is(state)) {
            if (OPEN.is(state) != signal) {
                state = OPEN.with(state, signal);
                playSound(null, level, pos, signal);
            }
            level.setBlock(pos, POWERED.with(state, signal), 2);
            if (WATERLOGGED.is(state)) level.scheduleFluidTick(pos, Fluids.T_WATER, 5);
        }
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = defaultState();
        int face = ctx.clickedFace();
        if (!ctx.replacingClickedOnBlock() && Dir.horizontal(face)) {
            s = FACING.with(s, Dir.NAME[face]);
            s = HALF.with(s, ctx.clickY() - ctx.clickedPos().y() > 0.5 ? "top" : "bottom");
        } else {
            s = FACING.with(s, Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
            s = HALF.with(s, face == Dir.UP ? "bottom" : "top");
        }
        if (ctx.level().hasNeighborSignal(ctx.clickedPos())) s = POWERED.with(OPEN.with(s, true), true);
        return WATERLOGGED.with(s, B.waterAt(ctx));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
