// 바닐라 FenceGateBlock: 열고 닫는 울타리 문. 양옆이 담장이면 내려앉는다(in_wall). 레드스톤 신호로 열린다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockSetType;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class FenceGateBlock extends HorizontalDirectionalBlock {

    static final Prop OPEN = B.prop("open"), POWERED = B.prop("powered"), IN_WALL = B.prop("in_wall");
    private final String closeSound, openSound;

    public FenceGateBlock(int block) {
        super(block);
        String[] s = BlockSetType.fenceGateSounds(block);
        closeSound = s[0];
        openSound = s[1];
    }

    private static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (Dir.axis(Dir.clockWise(facing(state))) != Dir.axis(dir)) return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        boolean wall = isWall(neighborState) || isWall(level.getBlockState(pos.relative(Dir.opposite(dir))));
        return IN_WALL.with(state, wall);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        boolean powered = level.hasNeighborSignal(pos);
        int facing = ctx.horizontalDirection();
        int axis = Dir.axis(facing);
        boolean wall = axis == Dir.AXIS_Z && (isWall(level.getBlockState(pos.west())) || isWall(level.getBlockState(pos.east())))
                || axis == Dir.AXIS_X && (isWall(level.getBlockState(pos.north())) || isWall(level.getBlockState(pos.south())));
        int s = FACING.with(defaultState(), Dir.NAME[facing]);
        s = OPEN.with(s, powered);
        s = POWERED.with(s, powered);
        return IN_WALL.with(s, wall);
    }

    private static boolean isWall(int state) { return B.isTag(state, "minecraft:walls"); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (OPEN.is(state)) {
            state = OPEN.with(state, false);
            level.setBlock(pos, state, 10);
        } else {
            int dir = player.direction();
            if (facing(state) == Dir.opposite(dir)) state = FACING.with(state, Dir.NAME[dir]);
            state = OPEN.with(state, true);
            level.setBlock(pos, state, 10);
        }
        boolean open = OPEN.is(state);
        level.playSound(player, pos, open ? openSound : closeSound, "blocks", 1.0F, B.openPitch(level));
        level.gameEvent(open ? "minecraft:block_open" : "minecraft:block_close", pos, player, -1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onExplosionHit(int state, BlockLevel level, Pos pos, Explosion explosion) {
        if (explosion.canTriggerBlocks() && !POWERED.is(state)) {
            state = OPEN.cycle(state);
            level.setBlock(pos, state, 10);
            level.playSound(null, pos, OPEN.is(state) ? openSound : closeSound, "blocks", 1.0F, B.openPitch(level));
            level.gameEvent(OPEN.is(state) ? "minecraft:block_open" : "minecraft:block_close", pos, null, -1);
        }
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos);
        if (POWERED.is(state) != signal) {
            level.setBlock(pos, OPEN.with(POWERED.with(state, signal), signal), 2);
            if (OPEN.is(state) != signal) {
                level.playSound(null, pos, signal ? openSound : closeSound, "blocks", 1.0F, B.openPitch(level));
                level.gameEvent(signal ? "minecraft:block_open" : "minecraft:block_close", pos, null, -1);
            }
        }
    }

    /** connectsToDirection(state, dir): 문짝 축이 dir 의 시계 방향 축과 같은가(울타리·벽 연결용). */
    public static boolean connectsToDirection(int state, int dir) { return Dir.axis(facing(state)) == Dir.axis(Dir.clockWise(dir)); }
}
