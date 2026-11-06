// 바닐라 MultifaceSpreader: 다면 블록이 한 면에서 이웃 면·옆 칸·모서리 너머로 번지는 규칙(뼛가루·스컬크 퍼짐).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public final class MultifaceSpreader {

    /** SpreadType: 같은 칸의 다른 면 / 옆 칸의 같은 면 / 모서리를 돌아 너머 칸. */
    public enum SpreadType {
        SAME_POSITION, SAME_PLANE, WRAP_AROUND;

        SpreadPos getSpreadPos(Pos pos, int dir, int face) {
            return switch (this) {
                case SAME_POSITION -> new SpreadPos(pos, dir);
                case SAME_PLANE -> new SpreadPos(pos.relative(dir), face);
                case WRAP_AROUND -> new SpreadPos(pos.relative(dir).relative(face), Dir.opposite(dir));
            };
        }
    }

    public static final SpreadType[] DEFAULT_SPREAD_ORDER = {SpreadType.SAME_POSITION, SpreadType.SAME_PLANE, SpreadType.WRAP_AROUND};

    public record SpreadPos(Pos pos, int face) {}

    /** SpreadConfig. */
    public interface SpreadConfig {
        /** getStateForPlacement(현재 상태, level, pos, 면) — -1 이면 못 놓음. */
        int getStateForPlacement(int current, BlockLevel level, Pos pos, int face);

        boolean canSpreadInto(BlockLevel level, Pos pos, SpreadPos spread);

        default SpreadType[] getSpreadTypes() { return DEFAULT_SPREAD_ORDER; }

        default boolean hasFace(int state, int dir) { return MultifaceBlock.hasFace(state, dir); }

        default boolean isOtherBlockValidAsSource(int state) { return false; }

        default boolean canSpreadFrom(int state, int dir) { return isOtherBlockValidAsSource(state) || hasFace(state, dir); }

        default boolean placeBlock(BlockLevel level, SpreadPos spread, int state, boolean markPostProcessing) {
            int ns = getStateForPlacement(state, level, spread.pos(), spread.face());
            if (ns < 0) return false;
            // markPostProcessing 은 지형 생성 중에만 쓴다(청크 후처리 표시).
            return level.setBlock(spread.pos(), ns, 2);
        }
    }

    /** DefaultSpreaderConfig. */
    public static class DefaultSpreaderConfig implements SpreadConfig {
        protected final MultifaceBlock block;

        public DefaultSpreaderConfig(MultifaceBlock block) { this.block = block; }

        @Override
        public int getStateForPlacement(int current, BlockLevel level, Pos pos, int face) { return block.getStateForPlacement(current, level, pos, face); }

        protected boolean stateCanBeReplaced(BlockLevel level, Pos pos, Pos spreadPos, int face, int state) {
            return B.isAir(state) || B.is(state, block.block()) || B.is(state, WATER) && Fluids.isSource(B.fluid(state));
        }

        @Override
        public boolean canSpreadInto(BlockLevel level, Pos pos, SpreadPos spread) {
            int s = level.getBlockState(spread.pos());
            return stateCanBeReplaced(level, pos, spread.pos(), spread.face(), s) && block.isValidStateForPlacement(level, s, spread.pos(), spread.face());
        }
    }

    private static final int WATER = B.id("water");

    @FunctionalInterface
    interface SpreadPredicate {
        boolean test(BlockLevel level, Pos pos, SpreadPos spread);
    }

    private final SpreadConfig config;

    public MultifaceSpreader(MultifaceBlock block) { this(new DefaultSpreaderConfig(block)); }

    public MultifaceSpreader(SpreadConfig config) { this.config = config; }

    /** canSpreadInAnyDirection(state, level, pos, face). */
    public boolean canSpreadInAnyDirection(int state, BlockLevel level, Pos pos, int face) {
        for (int d : Dir.ALL) if (getSpreadFromFaceTowardDirection(state, level, pos, face, d, config::canSpreadInto) != null) return true;
        return false;
    }

    /** spreadFromRandomFaceTowardRandomDirection: 섞은 면 순서로, 번질 수 있는 첫 면에서 섞은 방향 순서로 번진다. */
    public SpreadPos spreadFromRandomFaceTowardRandomDirection(int state, BlockLevel level, Pos pos, RandomSource random) {
        for (int face : allShuffled(random)) {
            if (!config.canSpreadFrom(state, face)) continue;
            SpreadPos sp = spreadFromFaceTowardRandomDirection(state, level, pos, face, random, false);
            if (sp != null) return sp;
        }
        return null;
    }

    /** spreadAll(state, level, pos, markPostProcessing): 번질 수 있는 모든 면·방향으로 번진 수. */
    public long spreadAll(int state, BlockLevel level, Pos pos, boolean markPostProcessing) {
        long n = 0;
        for (int face : Dir.ALL) {
            if (!config.canSpreadFrom(state, face)) continue;
            for (int d : Dir.ALL) if (spreadFromFaceTowardDirection(state, level, pos, face, d, markPostProcessing) != null) n++;
        }
        return n;
    }

    public SpreadPos spreadFromFaceTowardRandomDirection(int state, BlockLevel level, Pos pos, int face, RandomSource random, boolean markPostProcessing) {
        for (int d : allShuffled(random)) {
            SpreadPos sp = spreadFromFaceTowardDirection(state, level, pos, face, d, markPostProcessing);
            if (sp != null) return sp;
        }
        return null;
    }

    public SpreadPos spreadFromFaceTowardDirection(int state, BlockLevel level, Pos pos, int face, int dir, boolean markPostProcessing) {
        SpreadPos sp = getSpreadFromFaceTowardDirection(state, level, pos, face, dir, config::canSpreadInto);
        return sp == null ? null : spreadToFace(level, sp, markPostProcessing);
    }

    SpreadPos getSpreadFromFaceTowardDirection(int state, BlockLevel level, Pos pos, int face, int dir, SpreadPredicate predicate) {
        if (Dir.axis(dir) == Dir.axis(face)) return null;
        if (config.isOtherBlockValidAsSource(state) || config.hasFace(state, face) && !config.hasFace(state, dir)) {
            for (SpreadType t : config.getSpreadTypes()) {
                SpreadPos sp = t.getSpreadPos(pos, dir, face);
                if (predicate.test(level, pos, sp)) return sp;
            }
        }
        return null;
    }

    public SpreadPos spreadToFace(BlockLevel level, SpreadPos sp, boolean markPostProcessing) {
        int s = level.getBlockState(sp.pos());
        return config.placeBlock(level, sp, s, markPostProcessing) ? sp : null;
    }

    /** Direction.allShuffled(random) = Util.shuffledCopy(values(), random). */
    static int[] allShuffled(RandomSource random) {
        int[] a = Dir.ALL.clone();
        for (int i = a.length; i > 1; i--) {
            int j = random.nextInt(i);
            int t = a[i - 1];
            a[i - 1] = a[j];
            a[j] = t;
        }
        return a;
    }
}
