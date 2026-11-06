// 바닐라 RedStoneWireBlock(1.21.1 기본 알고리즘): 레드스톤 가루. 옆(side)·벽 타기(up) 연결, 세기 = max(주변 신호, 이웃 가루 - 1).
// 세기가 바뀌면 자신과 여섯 이웃을 HashSet 순회 순서대로 갱신한다(바닐라와 같은 순서를 재현).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class RedStoneWireBlock extends BlockBehavior {

    static final Prop NORTH = B.prop("north"), EAST = B.prop("east"), SOUTH = B.prop("south"), WEST = B.prop("west"), POWER = B.prop("power");
    private static final Prop[] SIDE = {null, null, NORTH, SOUTH, WEST, EAST};
    private static final String UP = "up", SIDE_ = "side", NONE = "none";
    private static final int WIRE = B.id("redstone_wire"), HOPPER = B.id("hopper"), REPEATER = B.id("repeater"), OBSERVER = B.id("observer");
    private static final Prop FACING = B.prop("facing");
    private static final int[] VERTICAL = {Dir.UP, Dir.DOWN};

    /**
     * shouldSignal: 세기를 계산하는 동안 가루끼리 신호를 내지 않게 끄는 표시. 바닐라는 블록 객체 필드(한 스레드)지만
     * 여기서는 region 스레드마다 따로 계산하므로 스레드별로 둔다(Folia 와 같은 처리).
     */
    private static final ThreadLocal<boolean[]> NO_SIGNAL = ThreadLocal.withInitial(() -> new boolean[1]);

    private final int crossState;

    public RedStoneWireBlock(int block) {
        super(block);
        int s = defaultState();
        for (int d : Dir.HORIZONTAL) s = SIDE[d].with(s, SIDE_);
        this.crossState = s;
    }

    private static boolean shouldSignal() { return !NO_SIGNAL.get()[0]; }

    private static String side(int state, int dir) { return SIDE[dir].valueName(state); }

    private static boolean connected(int state, int dir) { return !NONE.equals(side(state, dir)); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return getConnectionState(ctx.level(), crossState, ctx.clickedPos()); }

    private int getConnectionState(BlockLevel level, int state, Pos pos) {
        boolean wasDot = isDot(state);
        state = getMissingConnections(level, POWER.withInt(defaultState(), POWER.intValue(state)), pos);
        if (wasDot && isDot(state)) return state;
        boolean n = connected(state, Dir.NORTH), s = connected(state, Dir.SOUTH), e = connected(state, Dir.EAST), w = connected(state, Dir.WEST);
        boolean nsOff = !n && !s, ewOff = !e && !w;
        if (!w && nsOff) state = WEST.with(state, SIDE_);
        if (!e && nsOff) state = EAST.with(state, SIDE_);
        if (!n && ewOff) state = NORTH.with(state, SIDE_);
        if (!s && ewOff) state = SOUTH.with(state, SIDE_);
        return state;
    }

    private int getMissingConnections(BlockLevel level, int state, Pos pos) {
        boolean open = !conductor(level, level.getBlockState(pos.above()), pos);
        for (int d : Dir.HORIZONTAL) {
            if (!connected(state, d)) state = SIDE[d].with(state, getConnectingSide(level, pos, d, open));
        }
        return state;
    }

    private static boolean conductor(BlockLevel level, int state, Pos pos) { return B.behavior(state).isRedstoneConductor(state, level, pos); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.DOWN) return !canSurviveOn(level, neighborPos, neighborState) ? B.defaultState("air") : state;
        if (dir == Dir.UP) return getConnectionState(level, state, pos);
        String side = getConnectingSide(level, pos, dir);
        if (!NONE.equals(side) == connected(state, dir) && !isCross(state)) return SIDE[dir].with(state, side);
        return getConnectionState(level, SIDE[dir].with(POWER.withInt(crossState, POWER.intValue(state)), side), pos);
    }

    private static boolean isCross(int state) {
        return connected(state, Dir.NORTH) && connected(state, Dir.SOUTH) && connected(state, Dir.EAST) && connected(state, Dir.WEST);
    }

    private static boolean isDot(int state) {
        return !connected(state, Dir.NORTH) && !connected(state, Dir.SOUTH) && !connected(state, Dir.EAST) && !connected(state, Dir.WEST);
    }

    @Override
    public void updateIndirectNeighbourShapes(int state, BlockLevel level, Pos pos, int flags, int recursionLeft) {
        for (int d : Dir.HORIZONTAL) {
            if (!NONE.equals(side(state, d)) && !B.is(level.getBlockState(pos.relative(d)), block)) {
                int o = Dir.opposite(d);
                Pos down = pos.relative(d).below();
                if (B.is(level.getBlockState(down), block)) {
                    Pos p = down.relative(o);
                    level.neighborShapeChanged(o, level.getBlockState(p), down, p, flags, recursionLeft);
                }
                Pos up = pos.relative(d).above();
                if (B.is(level.getBlockState(up), block)) {
                    Pos p = up.relative(o);
                    level.neighborShapeChanged(o, level.getBlockState(p), up, p, flags, recursionLeft);
                }
            }
        }
    }

    private String getConnectingSide(BlockLevel level, Pos pos, int dir) {
        return getConnectingSide(level, pos, dir, !conductor(level, level.getBlockState(pos.above()), pos));
    }

    private String getConnectingSide(BlockLevel level, Pos pos, int dir, boolean nonConductorAbove) {
        Pos rel = pos.relative(dir);
        int s = level.getBlockState(rel);
        if (nonConductorAbove) {
            boolean climbable = B.behavior(s) instanceof TrapDoorBlock || canSurviveOn(level, rel, s);
            if (climbable && shouldConnectTo(level.getBlockState(rel.above()), -1)) {
                return B.isFaceSturdy(s, level, rel, Dir.opposite(dir)) ? UP : SIDE_;
            }
        }
        return !shouldConnectTo(s, dir) && (conductor(level, s, rel) || !shouldConnectTo(level.getBlockState(rel.below()), -1)) ? NONE : SIDE_;
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        return canSurviveOn(level, below, level.getBlockState(below));
    }

    private static boolean canSurviveOn(BlockLevel level, Pos pos, int state) {
        return B.isFaceSturdy(state, level, pos, Dir.UP) || B.is(state, HOPPER);
    }

    private void updatePowerStrength(BlockLevel level, Pos pos, int state) {
        int target = calculateTargetStrength(level, pos);
        int old = POWER.intValue(state);
        if (old != target) target = level.gameEvents().blockRedstone(level.dimension(), pos, state, old, target);   // 플러그인 BlockRedstoneEvent
        if (old == target) return;
        if (level.getBlockState(pos) == state) level.setBlock(pos, POWER.withInt(state, target), 2);
        for (Pos p : hashSetOrder(pos)) level.updateNeighborsAt(p, block);
    }

    /**
     * Sets.newHashSet() 에 pos 와 Direction.values() 이웃을 넣고 순회하는 순서 — Vec3i.hashCode((y + z·31)·31 + x)를
     * HashMap 처럼 흩어(h ^ h>>>16) 16 칸 통으로 나눈 순서, 같은 통은 넣은 순서.
     */
    public static Pos[] hashSetOrder(Pos pos) {
        Pos[] in = new Pos[7];
        in[0] = pos;
        for (int i = 0; i < 6; i++) in[i + 1] = pos.relative(Dir.ALL[i]);
        Pos[] out = new Pos[7];
        int n = 0;
        for (int bucket = 0; bucket < 16; bucket++) {
            for (Pos p : in) {
                int h = (p.y() + p.z() * 31) * 31 + p.x();
                if (((h ^ (h >>> 16)) & 15) == bucket) out[n++] = p;
            }
        }
        return out;
    }

    private int calculateTargetStrength(BlockLevel level, Pos pos) {
        boolean[] flag = NO_SIGNAL.get();
        boolean was = flag[0];
        flag[0] = true;
        int best;
        try {
            best = level.getBestNeighborSignal(pos);
        } finally {
            flag[0] = was;
        }
        int wire = 0;
        if (best < 15) {
            for (int d : Dir.HORIZONTAL) {
                Pos rel = pos.relative(d);
                int s = level.getBlockState(rel);
                wire = Math.max(wire, getWireSignal(s));
                Pos above = pos.above();
                if (conductor(level, s, rel) && !conductor(level, level.getBlockState(above), above)) {
                    wire = Math.max(wire, getWireSignal(level.getBlockState(rel.above())));
                } else if (!conductor(level, s, rel)) {
                    wire = Math.max(wire, getWireSignal(level.getBlockState(rel.below())));
                }
            }
        }
        return Math.max(best, wire - 1);
    }

    private int getWireSignal(int state) { return B.is(state, block) ? POWER.intValue(state) : 0; }

    private void checkCornerChangeAt(BlockLevel level, Pos pos) {
        if (B.is(level.getBlockState(pos), block)) {
            level.updateNeighborsAt(pos, block);
            for (int d : Dir.ALL) level.updateNeighborsAt(pos.relative(d), block);
        }
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (B.is(oldState, block)) return;
        updatePowerStrength(level, pos, state);
        for (int d : VERTICAL) level.updateNeighborsAt(pos.relative(d), block);
        updateNeighborsOfNeighboringWires(level, pos);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (moved || B.is(newState, block)) return;
        super.onRemove(state, level, pos, newState, moved);
        for (int d : Dir.ALL) level.updateNeighborsAt(pos.relative(d), block);
        updatePowerStrength(level, pos, state);
        updateNeighborsOfNeighboringWires(level, pos);
    }

    private void updateNeighborsOfNeighboringWires(BlockLevel level, Pos pos) {
        for (int d : Dir.HORIZONTAL) checkCornerChangeAt(level, pos.relative(d));
        for (int d : Dir.HORIZONTAL) {
            Pos rel = pos.relative(d);
            if (conductor(level, level.getBlockState(rel), rel)) checkCornerChangeAt(level, rel.above());
            else checkCornerChangeAt(level, rel.below());
        }
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (canSurvive(state, level, pos)) {
            updatePowerStrength(level, pos, state);
        } else {
            level.dropResources(state, pos);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return !shouldSignal() ? 0 : getSignal(state, level, pos, dir); }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) {
        if (!shouldSignal() || dir == Dir.DOWN) return 0;
        int p = POWER.intValue(state);
        if (p == 0) return 0;
        return dir != Dir.UP && !connected(getConnectionState(level, state, pos), Dir.opposite(dir)) ? 0 : p;
    }

    /** shouldConnectTo(state, dir) — dir 이 -1 이면 바닐라의 null. */
    protected static boolean shouldConnectTo(int state, int dir) {
        if (B.is(state, WIRE)) return true;
        if (B.is(state, REPEATER)) {
            int f = Dir.byName(FACING.valueName(state));
            return f == dir || Dir.opposite(f) == dir;
        }
        if (B.is(state, OBSERVER)) return dir == Dir.byName(FACING.valueName(state));
        return B.behavior(state).isSignalSource(state) && dir != -1;
    }

    @Override
    public boolean isSignalSource(int state) { return shouldSignal(); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!player.mayBuild()) return InteractionResult.PASS;
        if (isCross(state) || isDot(state)) {
            int s = POWER.withInt(isCross(state) ? defaultState() : crossState, POWER.intValue(state));
            s = getConnectionState(level, s, pos);
            if (s != state) {
                level.setBlock(pos, s, 3);
                updatesOnShapeChange(level, pos, state, s);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    private void updatesOnShapeChange(BlockLevel level, Pos pos, int oldState, int newState) {
        for (int d : Dir.HORIZONTAL) {
            Pos rel = pos.relative(d);
            if (connected(oldState, d) != connected(newState, d) && conductor(level, level.getBlockState(rel), rel)) {
                level.updateNeighborsAtExceptFromFacing(rel, B.block(newState), Dir.opposite(d));
            }
        }
    }
}
