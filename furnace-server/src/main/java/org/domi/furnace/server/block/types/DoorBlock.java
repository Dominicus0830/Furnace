// 바닐라 DoorBlock: 두 칸 문. 경첩은 양옆 단단한 블록·이웃 문·클릭 위치로 정한다. 손(블록 세트가 허락하면)이나 레드스톤으로 여닫는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockSetType;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class DoorBlock extends BlockBehavior {

    static final Prop FACING = B.prop("facing"), OPEN = B.prop("open"), HINGE = B.prop("hinge"), POWERED = B.prop("powered"), HALF = B.prop("half");
    private final BlockSetType type;

    public DoorBlock(int block) {
        super(block);
        this.type = BlockSetType.of(block);
    }

    public BlockSetType type() { return type; }

    private static boolean lower(int state) { return "lower".equals(HALF.valueName(state)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        boolean lower = lower(state);
        if (Dir.axis(dir) != Dir.AXIS_Y || lower != (dir == Dir.UP)) {
            return lower && dir == Dir.DOWN && !canSurvive(state, level, pos)
                    ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        }
        return B.behavior(neighborState) instanceof DoorBlock && lower(neighborState) != lower
                ? HALF.with(neighborState, HALF.valueName(state)) : B.defaultState("air");
    }

    // TODO(폭발): onExplosionHit — 손으로 못 여는 문이 아니고 신호로 열린 게 아니면 여닫는다(windcharge 등).

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (player.isCreative() || !player.hasCorrectToolForDrops(state)) DoublePlantBlock.preventDropFromBottomPart(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        Pos pos = ctx.clickedPos();
        BlockLevel level = ctx.level();
        if (pos.y() < level.maxBuildHeight() - 1 && B.canBeReplaced(level.getBlockState(pos.above()), ctx)) {
            boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
            int s = FACING.with(defaultState(), Dir.NAME[ctx.horizontalDirection()]);
            s = HINGE.with(s, getHinge(ctx));
            s = POWERED.with(s, powered);
            s = OPEN.with(s, powered);
            return HALF.with(s, "lower");
        }
        return -1;
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        level.setBlock(pos.above(), HALF.with(state, "upper"), 3);
    }

    /** getHinge: 왼쪽(반시계)·오른쪽(시계) 칸의 단단함과 이웃 문, 그다음 클릭 위치. */
    private static String getHinge(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        int facing = ctx.horizontalDirection();
        Pos above = pos.above();
        int ccw = Dir.counterClockWise(facing);
        Pos l = pos.relative(ccw), lu = above.relative(ccw);
        int ls = level.getBlockState(l), lus = level.getBlockState(lu);
        int cw = Dir.clockWise(facing);
        Pos r = pos.relative(cw), ru = above.relative(cw);
        int rs = level.getBlockState(r), rus = level.getBlockState(ru);
        int score = (B.isCollisionShapeFullBlock(ls) ? -1 : 0) + (B.isCollisionShapeFullBlock(lus) ? -1 : 0)
                + (B.isCollisionShapeFullBlock(rs) ? 1 : 0) + (B.isCollisionShapeFullBlock(rus) ? 1 : 0);
        boolean leftDoor = B.behavior(ls) instanceof DoorBlock && lower(ls);
        boolean rightDoor = B.behavior(rs) instanceof DoorBlock && lower(rs);
        if ((!leftDoor || rightDoor) && score <= 0) {
            if ((!rightDoor || leftDoor) && score >= 0) {
                int sx = Dir.DX[facing], sz = Dir.DZ[facing];
                double hx = ctx.clickX() - pos.x(), hz = ctx.clickZ() - pos.z();
                return (sx >= 0 || !(hz < 0.5)) && (sx <= 0 || !(hz > 0.5)) && (sz >= 0 || !(hx > 0.5)) && (sz <= 0 || !(hx < 0.5)) ? "left" : "right";
            }
            return "left";
        }
        return "right";
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!type.canOpenByHand()) return InteractionResult.PASS;
        state = OPEN.cycle(state);
        level.setBlock(pos, state, 10);
        playSound(player, level, pos, OPEN.is(state));
        level.gameEvent(OPEN.is(state) ? "minecraft:block_open" : "minecraft:block_close", pos, player, -1);
        return InteractionResult.CONSUME;
    }

    public boolean isOpen(int state) { return OPEN.is(state); }

    /** setOpen: 이 문이고 상태가 다르면 여닫는다(마을 사람·좀비 등). */
    public void setOpen(BlockActor entity, BlockLevel level, int state, Pos pos, boolean open) {
        if (B.is(state, block) && OPEN.is(state) != open) {
            level.setBlock(pos, OPEN.with(state, open), 10);
            playSound(entity, level, pos, open);
            level.gameEvent(open ? "minecraft:block_open" : "minecraft:block_close", pos, entity, -1);
        }
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.relative(lower(state) ? Dir.UP : Dir.DOWN));
        if (sourceBlock != block && signal != POWERED.is(state)) {
            if (signal != OPEN.is(state)) {
                playSound(null, level, pos, signal);
                level.gameEvent(signal ? "minecraft:block_open" : "minecraft:block_close", pos, null, -1);
            }
            level.setBlock(pos, OPEN.with(POWERED.with(state, signal), signal), 2);
        }
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        int bs = level.getBlockState(below);
        return lower(state) ? B.isFaceSturdy(bs, level, below, Dir.UP) : B.is(bs, block);
    }

    private void playSound(BlockActor entity, BlockLevel level, Pos pos, boolean open) {
        level.playSound(entity, pos, open ? type.doorOpen() : type.doorClose(), "blocks", 1.0F, B.openPitch(level));
    }

    /** isWoodenDoor: 손으로 열 수 있는 문인가. */
    public static boolean isWoodenDoor(int state) { return B.behavior(state) instanceof DoorBlock d && d.type.canOpenByHand(); }
}
