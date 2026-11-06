// 바닐라 TripWireHookBlock: 덫걸이. 마주 보는 덫걸이 사이(최대 41칸)를 덫줄로 이으면 걸리고(attached), 덫줄을 밟으면 신호.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public class TripWireHookBlock extends BlockBehavior {

    static final Prop FACING = B.prop("facing"), POWERED = B.prop("powered"), ATTACHED = B.prop("attached"), DISARMED = B.prop("disarmed");
    static final int TRIPWIRE_HOOK = B.id("tripwire_hook"), TRIPWIRE = B.id("tripwire");

    public TripWireHookBlock(int block) { super(block); }

    private static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int f = facing(state);
        Pos behind = pos.relative(Dir.opposite(f));
        return Dir.horizontal(f) && B.isFaceSturdy(level.getBlockState(behind), level, behind, f);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.opposite(dir) == facing(state) && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = ATTACHED.with(POWERED.with(defaultState(), false), false);
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d)) {
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
                if (B.canSurvive(s, ctx.level(), ctx.clickedPos())) return s;
            }
        }
        return -1;
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        calculateState(level, pos, state, false, false, -1, -1);
    }

    /** calculateState: 덫줄을 따라 맞은편 덫걸이를 찾고 걸림·신호를 다시 정한다(wireSource 칸은 wireState 로 본다). */
    public static void calculateState(BlockLevel level, Pos pos, int state, boolean beingRemoved, boolean canUpdate, int wireSource, int wireState) {
        if (!FACING.has(state)) return;
        int facing = facing(state);
        boolean wasAttached = ATTACHED.has(state) && ATTACHED.is(state);
        boolean wasPowered = POWERED.has(state) && POWERED.is(state);
        int block = B.block(state);
        boolean attached = !beingRemoved, powered = false;
        int other = 0;
        int[] wires = new int[42];
        for (int i = 1; i < 42; i++) {
            Pos p = pos.relative(facing, i);
            int s = level.getBlockState(p);
            if (B.is(s, TRIPWIRE_HOOK)) {
                if (facing(s) == Dir.opposite(facing)) other = i;
                break;
            }
            if (!B.is(s, TRIPWIRE) && i != wireSource) {
                wires[i] = -1;
                attached = false;
            } else {
                if (i == wireSource && wireState >= 0) s = wireState;
                boolean armed = !DISARMED.is(s);
                boolean wirePowered = POWERED.is(s);
                powered |= armed && wirePowered;
                wires[i] = s;
                if (i == wireSource) {
                    level.scheduleTick(pos, block, 10);
                    attached &= armed;
                }
            }
        }
        attached &= other > 1;
        powered &= attached;
        int ns = B.defaultState(block);
        if (ATTACHED.has(ns)) ns = ATTACHED.with(ns, attached);
        if (POWERED.has(ns)) ns = POWERED.with(ns, powered);
        if (other > 0) {
            Pos p = pos.relative(facing, other);
            int opp = Dir.opposite(facing);
            level.setBlock(p, FACING.with(ns, Dir.NAME[opp]), 3);
            notifyNeighbors(block, level, p, opp);
            emitState(level, p, attached, powered, wasAttached, wasPowered);
        }
        emitState(level, pos, attached, powered, wasAttached, wasPowered);
        if (!beingRemoved) {
            level.setBlock(pos, FACING.with(ns, Dir.NAME[facing]), 3);
            if (canUpdate) notifyNeighbors(block, level, pos, facing);
        }
        if (wasAttached != attached) {
            for (int i = 1; i < other; i++) {
                int s = wires[i];
                if (s > 0) level.setBlock(pos.relative(facing, i), ATTACHED.has(s) ? ATTACHED.with(s, attached) : s, 3);
            }
        }
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { calculateState(level, pos, state, false, true, -1, -1); }

    private static void emitState(BlockLevel level, Pos pos, boolean attached, boolean powered, boolean wasAttached, boolean wasPowered) {
        if (powered && !wasPowered) {
            level.playSound(null, pos, "minecraft:block.tripwire.click_on", "blocks", 0.4F, 0.6F);
            level.gameEvent("minecraft:block_activate", pos, null, -1);
        } else if (!powered && wasPowered) {
            level.playSound(null, pos, "minecraft:block.tripwire.click_off", "blocks", 0.4F, 0.5F);
            level.gameEvent("minecraft:block_deactivate", pos, null, -1);
        } else if (attached && !wasAttached) {
            level.playSound(null, pos, "minecraft:block.tripwire.attach", "blocks", 0.4F, 0.7F);
            level.gameEvent("minecraft:block_attach", pos, null, -1);
        } else if (!attached && wasAttached) {
            level.playSound(null, pos, "minecraft:block.tripwire.detach", "blocks", 0.4F, 1.2F / (level.random().nextFloat() * 0.2F + 0.9F));
            level.gameEvent("minecraft:block_detach", pos, null, -1);
        }
    }

    private static void notifyNeighbors(int block, BlockLevel level, Pos pos, int dir) {
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.relative(Dir.opposite(dir)), block);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!moved && !B.is(newState, block)) {
            boolean attached = ATTACHED.is(state), powered = POWERED.is(state);
            if (attached || powered) calculateState(level, pos, state, true, false, -1, -1);
            if (powered) {
                level.updateNeighborsAt(pos, block);
                level.updateNeighborsAt(pos.relative(Dir.opposite(facing(state))), block);
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) ? 15 : 0; }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) && facing(state) == dir ? 15 : 0; }

    @Override
    public boolean isSignalSource(int state) { return true; }
}
