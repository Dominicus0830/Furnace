// 바닐라 VineBlock: 덩굴. 옆면·윗면 단단한 곳에 붙고(위 덩굴이 같은 면을 가져도 붙는다), 무작위 틱마다 옆·위·아래로 번진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class VineBlock extends BlockBehavior {

    static final Prop UP = B.prop("up"), NORTH = B.prop("north"), EAST = B.prop("east"), SOUTH = B.prop("south"), WEST = B.prop("west");
    /** 방향 번호 → 면 속성(DOWN 은 없다). */
    static final Prop[] FACE = {null, UP, NORTH, SOUTH, WEST, EAST};
    private static final int AIR = B.defaultState("air");

    public VineBlock(int block) { super(block); }

    static boolean has(int state, int dir) { return FACE[dir].is(state); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return hasFaces(getUpdatedState(state, level, pos)); }

    private static boolean hasFaces(int state) { return countFaces(state) > 0; }

    private static int countFaces(int state) {
        int n = 0;
        for (int d = Dir.UP; d <= Dir.EAST; d++) if (has(state, d)) n++;
        return n;
    }

    /** canSupportAtFace: 그 면 너머가 붙을 만하거나, 옆면이면 위 칸 덩굴이 같은 면을 가졌는가. */
    private boolean canSupportAtFace(BlockLevel level, Pos pos, int dir) {
        if (dir == Dir.DOWN) return false;
        Pos rel = pos.relative(dir);
        if (isAcceptableNeighbour(level, rel, dir)) return true;
        if (Dir.axis(dir) == Dir.AXIS_Y) return false;
        int above = level.getBlockState(pos.above());
        return B.is(above, block) && has(above, dir);
    }

    /** isAcceptableNeighbour = MultifaceBlock.canAttachTo(level, dir, pos, state). */
    public static boolean isAcceptableNeighbour(BlockLevel level, Pos pos, int dir) {
        return canAttachTo(level, dir, pos, level.getBlockState(pos));
    }

    /** MultifaceBlock.canAttachTo: 지지 모양이나 충돌 모양의 맞닿는 면이 꽉 찼는가. */
    public static boolean canAttachTo(BlockLevel level, int dir, Pos pos, int state) {
        int o = Dir.opposite(dir);
        return INFO.supportShape(state).isFaceFull(o) || B.behavior(state).getCollisionShape(state, level, pos, null).isFaceFull(o);
    }

    private int getUpdatedState(int state, BlockLevel level, Pos pos) {
        Pos above = pos.above();
        if (UP.is(state)) state = UP.with(state, isAcceptableNeighbour(level, above, Dir.DOWN));
        int aboveState = -1;
        for (int d : Dir.HORIZONTAL) {
            Prop p = FACE[d];
            if (p.is(state)) {
                boolean ok = canSupportAtFace(level, pos, d);
                if (!ok) {
                    if (aboveState < 0) aboveState = level.getBlockState(above);
                    ok = B.is(aboveState, block) && p.is(aboveState);
                }
                state = p.with(state, ok);
            }
        }
        return state;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.DOWN) return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        int s = getUpdatedState(state, level, pos);
        return !hasFaces(s) ? AIR : s;
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!level.gameRule("doVinesSpread")) return;
        if (random.nextInt(4) != 0) return;
        int dir = Dir.ALL[random.nextInt(6)];
        Pos abovePos = pos.above();
        if (Dir.horizontal(dir) && !has(state, dir)) {
            if (!canSpread(level, pos)) return;
            Pos rel = pos.relative(dir);
            int relState = level.getBlockState(rel);
            if (B.isAir(relState)) {
                int cw = Dir.clockWise(dir), ccw = Dir.counterClockWise(dir);
                boolean hasCw = has(state, cw), hasCcw = has(state, ccw);
                Pos cwPos = rel.relative(cw), ccwPos = rel.relative(ccw);
                if (hasCw && isAcceptableNeighbour(level, cwPos, cw)) {
                    B.spread(level, pos, rel, FACE[cw].with(defaultState(), true), 2);
                } else if (hasCcw && isAcceptableNeighbour(level, ccwPos, ccw)) {
                    B.spread(level, pos, rel, FACE[ccw].with(defaultState(), true), 2);
                } else {
                    int opp = Dir.opposite(dir);
                    if (hasCw && level.isEmptyBlock(cwPos) && isAcceptableNeighbour(level, pos.relative(cw), opp)) {
                        B.spread(level, pos, cwPos, FACE[opp].with(defaultState(), true), 2);
                    } else if (hasCcw && level.isEmptyBlock(ccwPos) && isAcceptableNeighbour(level, pos.relative(ccw), opp)) {
                        B.spread(level, pos, ccwPos, FACE[opp].with(defaultState(), true), 2);
                    } else if (random.nextFloat() < 0.05 && isAcceptableNeighbour(level, rel.above(), Dir.UP)) {
                        B.spread(level, pos, rel, UP.with(defaultState(), true), 2);
                    }
                }
            } else if (isAcceptableNeighbour(level, rel, dir)) {
                B.spread(level, pos, pos, FACE[dir].with(state, true), 2);
            }
            return;
        }
        if (dir == Dir.UP && pos.y() < level.maxBuildHeight() - 1) {
            if (canSupportAtFace(level, pos, dir)) {
                B.spread(level, pos, pos, UP.with(state, true), 2);
                return;
            }
            if (level.isEmptyBlock(abovePos)) {
                if (!canSpread(level, pos)) return;
                int s = state;
                for (int d : Dir.HORIZONTAL) {
                    if (random.nextBoolean() || !isAcceptableNeighbour(level, abovePos.relative(d), d)) s = FACE[d].with(s, false);
                }
                if (hasHorizontalConnection(s)) B.spread(level, pos, abovePos, s, 2);
                return;
            }
        }
        if (pos.y() > level.minBuildHeight()) {
            Pos below = pos.below();
            int belowState = level.getBlockState(below);
            if (B.isAir(belowState) || B.is(belowState, block)) {
                int base = B.isAir(belowState) ? defaultState() : belowState;
                int spread = copyRandomFaces(state, base, random);
                if (base != spread && hasHorizontalConnection(spread)) B.spread(level, pos, below, spread, 2);
            }
        }
    }

    private static int copyRandomFaces(int source, int target, RandomSource random) {
        for (int d : Dir.HORIZONTAL) {
            if (random.nextBoolean() && has(source, d)) target = FACE[d].with(target, true);
        }
        return target;
    }

    private static boolean hasHorizontalConnection(int state) {
        return NORTH.is(state) || EAST.is(state) || SOUTH.is(state) || WEST.is(state);
    }

    /** canSpread: 둘레 9×3×9 안 덩굴이 5개 미만. */
    private boolean canSpread(BlockLevel level, Pos pos) {
        int left = 5;
        for (int x = pos.x() - 4; x <= pos.x() + 4; x++) {
            for (int y = pos.y() - 1; y <= pos.y() + 1; y++) {
                for (int z = pos.z() - 4; z <= pos.z() + 4; z++) {
                    if (B.is(level.getBlockState(new Pos(x, y, z)), block) && --left <= 0) return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) {
        int s = ctx.level().getBlockState(ctx.clickedPos());
        return B.is(s, block) ? countFaces(s) < 5 : super.canBeReplaced(state, ctx);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int rel = ctx.level().getBlockState(ctx.clickedPos());
        boolean same = B.is(rel, block);
        int s = same ? rel : defaultState();
        for (int d : ctx.nearestLookingDirections()) {
            if (d != Dir.DOWN) {
                boolean already = same && has(rel, d);
                if (!already && canSupportAtFace(ctx.level(), ctx.clickedPos(), d)) return FACE[d].with(s, true);
            }
        }
        return same ? s : -1;
    }
}
