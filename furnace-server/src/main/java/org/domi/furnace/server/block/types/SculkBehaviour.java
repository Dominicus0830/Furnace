// 바닐라 SculkBehaviour: 스컬크 퍼짐 커서가 머무는 블록의 동작. 바닐라에서 이 인터페이스를 구현하는 블록은 SculkBlock·SculkVeinBlock 뿐이고,
// 그 밖의 블록(공기·물·감지체·비명체·촉매 포함)은 DEFAULT 를 쓴다.
package org.domi.furnace.server.block.types;

import java.util.Collection;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public interface SculkBehaviour {

    SculkBehaviour DEFAULT = new SculkBehaviour() {
        @Override
        public boolean attemptSpreadVein(BlockLevel level, Pos pos, int state, Collection<Integer> directions, boolean markForPostprocessing) {
            if (directions == null) {
                return SculkBehaviour.vein().getSameSpaceSpreader().spreadAll(level.getBlockState(pos), level, pos, markForPostprocessing) > 0L;
            } else if (!directions.isEmpty()) {
                return !B.isAir(state) && !Fluids.isWater(B.fluid(state)) ? false : SculkVeinBlock.regrow(level, pos, state, directions);
            }
            return SculkBehaviour.super.attemptSpreadVein(level, pos, state, directions, markForPostprocessing);
        }

        @Override
        public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, BlockLevel level, Pos pos, RandomSource random, SculkSpreader spreader,
                                    boolean shouldConvertBlocks) {
            return cursor.getDecayDelay() > 0 ? cursor.getCharge() : 0;
        }

        @Override
        public int updateDecayDelay(int decayDelay) { return Math.max(decayDelay - 1, 0); }
    };

    /** Blocks.SCULK_VEIN 의 동작. */
    static SculkVeinBlock vein() { return (SculkVeinBlock) B.behavior(B.defaultState(B.id("sculk_vein"))); }

    /** 블록의 SculkBehaviour(없으면 DEFAULT). */
    static SculkBehaviour of(int state) { return B.behavior(state) instanceof SculkBehaviour sb ? sb : DEFAULT; }

    static boolean isSculkBehaviour(int state) { return B.behavior(state) instanceof SculkBehaviour; }

    default int getSculkSpreadDelay() { return 1; }

    default void onDischarged(BlockLevel level, int state, Pos pos, RandomSource random) {}

    default boolean attemptSpreadVein(BlockLevel level, Pos pos, int state, Collection<Integer> directions, boolean markForPostprocessing) {
        return vein().getSpreader().spreadAll(state, level, pos, markForPostprocessing) > 0L;
    }

    default boolean canChangeBlockStateOnSpread() { return true; }

    default int updateDecayDelay(int decayDelay) { return 1; }

    int attemptUseCharge(SculkSpreader.ChargeCursor cursor, BlockLevel level, Pos pos, RandomSource random, SculkSpreader spreader,
                         boolean shouldConvertBlocks);
}
