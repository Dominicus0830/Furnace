// 바닐라 SculkCatalystBlock: 스컬크 촉매(bloom). 근처에서 몹이 죽으면 블록 엔티티가 스컬크를 퍼뜨리고 bloom 을 켠다(다음 예약 틱에 끔).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SculkCatalystBlock extends BaseEntityBlock {

    static final Prop PULSE = B.prop("bloom");

    public SculkCatalystBlock(int block) { super(block); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (PULSE.is(state)) level.setBlock(pos, PULSE.with(state, false), 3);
    }

    // TODO(블록 엔티티): SculkCatalystBlockEntity — 죽음 게임 이벤트를 듣고 SculkSpreader 로 퍼뜨린다. TODO(3-4b): 경험치 5.
}
