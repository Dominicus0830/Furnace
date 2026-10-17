// 바닐라 BeaconBlock: 신호기. 쓰면 신호기 화면. 피라미드·빛줄기는 블록 엔티티 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class BeaconBlock extends BaseEntityBlock {

    public BeaconBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴·블록엔티티): 신호기 화면 + 통계 interact_with_beacon
        return InteractionResult.CONSUME;
    }
}
