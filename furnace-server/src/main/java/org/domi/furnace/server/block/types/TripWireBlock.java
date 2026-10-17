// 바닐라 TripWireBlock: 덫줄. 이웃 덫줄·마주 보는 덫걸이에 이어지고, 놓이거나 없어지면 양 끝 덫걸이(남·서 쪽으로 41칸까지)를 다시 계산한다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class TripWireBlock extends BlockBehavior {

    static final Prop POWERED = B.prop("powered"), ATTACHED = B.prop("attached"), DISARMED = B.prop("disarmed"),
            NORTH = B.prop("north"), EAST = B.prop("east"), SOUTH = B.prop("south"), WEST = B.prop("west");
    private static final Prop[] BY_DIR = {null, null, NORTH, SOUTH, WEST, EAST};

    private final int hook;

    public TripWireBlock(int block) {
        super(block);
        this.hook = B.id(Blocks.get().params(block).get("hook").getAsString());
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        int s = defaultState();
        s = NORTH.with(s, shouldConnectTo(level.getBlockState(pos.north()), Dir.NORTH));
        s = EAST.with(s, shouldConnectTo(level.getBlockState(pos.east()), Dir.EAST));
        s = SOUTH.with(s, shouldConnectTo(level.getBlockState(pos.south()), Dir.SOUTH));
        return WEST.with(s, shouldConnectTo(level.getBlockState(pos.west()), Dir.WEST));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.horizontal(dir) ? BY_DIR[dir].with(state, shouldConnectTo(neighborState, dir)) : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, block)) updateSource(level, pos, state);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!moved && !B.is(newState, block)) updateSource(level, pos, POWERED.with(state, true));
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (B.holds(player.itemInHand(BlockActor.MAIN_HAND), "shears")) {
            level.setBlock(pos, DISARMED.with(state, true), BlockLevel.UPDATE_INVISIBLE);
            level.gameEvent("minecraft:shear", pos, player, -1);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** updateSource: 남·서 쪽으로 덫줄을 따라가 이쪽을 바라보는 덫걸이를 다시 계산(이 칸은 state 로 본다). */
    private void updateSource(BlockLevel level, Pos pos, int state) {
        for (int dir : new int[] {Dir.SOUTH, Dir.WEST}) {
            for (int i = 1; i < 42; i++) {
                Pos p = pos.relative(dir, i);
                int s = level.getBlockState(p);
                if (B.is(s, hook)) {
                    if (Dir.byName(TripWireHookBlock.FACING.valueName(s)) == Dir.opposite(dir)) {
                        TripWireHookBlock.calculateState(level, p, s, false, true, i, state);
                    }
                    break;
                }
                if (!B.is(s, block)) break;
            }
        }
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (POWERED.is(level.getBlockState(pos))) checkPressed(level, pos);
    }

    /** checkPressed: 덫줄 모양 안에 (밟기를 무시하지 않는) 엔티티가 있으면 켠다. */
    private void checkPressed(BlockLevel level, Pos pos) {
        int s = level.getBlockState(pos);
        boolean was = POWERED.is(s);
        // TODO(엔티티): 모양 안의 엔티티(isIgnoringBlockTriggers 제외) 조회 — 엔티티가 들어오기 전에는 늘 비어 있다. entityInside 도 같이.
        boolean pressed = false;
        if (pressed != was) {
            s = POWERED.with(s, pressed);
            level.setBlock(pos, s, 3);
            updateSource(level, pos, s);
        }
        if (pressed) level.scheduleTick(pos, block, 10);
    }

    /** shouldConnectTo: 덫걸이는 이쪽을 바라볼 때만, 덫줄은 늘. */
    public boolean shouldConnectTo(int state, int dir) {
        if (B.is(state, hook)) return Dir.byName(TripWireHookBlock.FACING.valueName(state)) == Dir.opposite(dir);
        return B.is(state, block);
    }
}
