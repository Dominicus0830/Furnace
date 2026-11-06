// 바닐라 SculkVeinBlock: 스컬크 맥. 다면 블록 + 물에 잠김. 스컬크 촉매의 퍼짐에서 맥이 번지고 스컬크로 바뀐다(SculkBehaviour).
package org.domi.furnace.server.block.types;

import java.util.Collection;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class SculkVeinBlock extends MultifaceBlock implements SculkBehaviour {

    private static final int SCULK = B.id("sculk"), SCULK_CATALYST = B.id("sculk_catalyst"), SCULK_VEIN = B.id("sculk_vein"),
            WATER = B.id("water");
    private final MultifaceSpreader veinSpreader = new MultifaceSpreader(new SculkVeinSpreaderConfig(MultifaceSpreader.DEFAULT_SPREAD_ORDER));
    private final MultifaceSpreader sameSpaceSpreader = new MultifaceSpreader(new SculkVeinSpreaderConfig(MultifaceSpreader.SpreadType.SAME_POSITION));

    public SculkVeinBlock(int block) { super(block); }

    @Override
    public MultifaceSpreader getSpreader() { return veinSpreader; }

    public MultifaceSpreader getSameSpaceSpreader() { return sameSpaceSpreader; }

    /** regrow(level, pos, state, 면들): 붙을 수 있는 면으로 새 맥을 놓는다(원래 칸에 유체가 있으면 잠김). */
    public static boolean regrow(BlockLevel level, Pos pos, int state, Collection<Integer> dirs) {
        boolean any = false;
        int s = B.defaultState(SCULK_VEIN);
        for (int d : dirs) {
            Pos p = pos.relative(d);
            if (canAttachTo(level, d, p, level.getBlockState(p))) {
                s = FACE[d].with(s, true);
                any = true;
            }
        }
        if (!any) return false;
        if (!Fluids.isEmpty(B.fluid(state))) s = WATERLOGGED.with(s, true);
        level.setBlock(pos, s, 3);
        return true;
    }

    /** onDischarged: 스컬크에 붙은 면을 지우고, 면이 하나도 없으면 공기(유체가 있으면 물). */
    @Override
    public void onDischarged(BlockLevel level, int state, Pos pos, RandomSource random) {
        if (!B.is(state, block)) return;
        for (int d : Dir.ALL) {
            if (FACE[d].is(state) && B.is(level.getBlockState(pos.relative(d)), SCULK)) state = FACE[d].with(state, false);
        }
        if (!hasAnyFace(state)) state = B.defaultState(Fluids.isEmpty(level.getFluidState(pos)) ? B.id("air") : WATER);
        level.setBlock(pos, state, 3);
    }

    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, BlockLevel level, Pos pos, RandomSource random, SculkSpreader spreader,
                                boolean shouldConvertBlocks) {
        if (shouldConvertBlocks && attemptPlaceSculk(spreader, level, cursor.getPos(), random)) return cursor.getCharge() - 1;
        return random.nextInt(spreader.chargeDecayRate()) == 0 ? (int) Math.floor(cursor.getCharge() * 0.5F) : cursor.getCharge();
    }

    /** attemptPlaceSculk: 섞은 방향 순서로 붙은 면 너머 대체 가능 블록 하나를 스컬크로 바꾸고 거기서 맥을 번지게 한다. */
    private boolean attemptPlaceSculk(SculkSpreader spreader, BlockLevel level, Pos pos, RandomSource random) {
        int state = level.getBlockState(pos);
        String tag = spreader.replaceableBlocks();
        for (int d : MultifaceSpreader.allShuffled(random)) {
            if (!hasFace(state, d)) continue;
            Pos p = pos.relative(d);
            int bs = level.getBlockState(p);
            if (!B.isTag(bs, tag)) continue;
            int sculk = B.defaultState(SCULK);
            level.setBlock(p, sculk, 3);
            level.pushEntitiesUp(bs, sculk, p);
            level.playSound(null, p, "minecraft:block.sculk.spread", "blocks", 1.0F, 1.0F);
            veinSpreader.spreadAll(sculk, level, p, spreader.isWorldGeneration());
            int opp = Dir.opposite(d);
            for (int e : Dir.ALL) {
                if (e == opp) continue;
                Pos q = p.relative(e);
                int qs = level.getBlockState(q);
                if (B.is(qs, block)) onDischarged(level, qs, q, random);
            }
            return true;
        }
        return false;
    }

    /** hasSubstrateAccess: 맥이 붙은 면 너머가 #sculk_replaceable 인가. */
    public static boolean hasSubstrateAccess(BlockLevel level, int state, Pos pos) {
        if (!B.is(state, SCULK_VEIN)) return false;
        for (int d : Dir.ALL) {
            if (hasFace(state, d) && B.isTag(level.getBlockState(pos.relative(d)), "minecraft:sculk_replaceable")) return true;
        }
        return false;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) { return !ctx.holding(block) || super.canBeReplaced(state, ctx); }

    /** SculkVeinSpreaderConfig: 스컬크·촉매·물 원천 아닌 유체·#fire 를 피하고, 맥이 아닌 블록도 번짐 원천이 된다. */
    private final class SculkVeinSpreaderConfig extends MultifaceSpreader.DefaultSpreaderConfig {
        private final MultifaceSpreader.SpreadType[] types;

        SculkVeinSpreaderConfig(MultifaceSpreader.SpreadType... types) {
            super(SculkVeinBlock.this);
            this.types = types;
        }

        @Override
        protected boolean stateCanBeReplaced(BlockLevel level, Pos pos, Pos spreadPos, int face, int state) {
            int behind = level.getBlockState(spreadPos.relative(face));
            if (B.is(behind, SCULK) || B.is(behind, SCULK_CATALYST) || B.is(behind, B.id("moving_piston"))) return false;
            if (pos.distManhattan(spreadPos) == 2) {
                Pos p = pos.relative(Dir.opposite(face));
                if (B.isFaceSturdy(level.getBlockState(p), level, p, face)) return false;
            }
            int f = B.fluid(state);
            if (!Fluids.isEmpty(f) && !Fluids.isWaterSource(f)) return false;
            if (B.isTag(state, "minecraft:fire")) return false;
            return INFO.replaceable(state) || super.stateCanBeReplaced(level, pos, spreadPos, face, state);
        }

        @Override
        public MultifaceSpreader.SpreadType[] getSpreadTypes() { return types; }

        @Override
        public boolean isOtherBlockValidAsSource(int state) { return !B.is(state, SCULK_VEIN); }
    }
}
