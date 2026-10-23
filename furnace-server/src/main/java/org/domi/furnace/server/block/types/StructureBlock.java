// 바닐라 StructureBlock: 구조물 블록(저장·불러오기·모서리·데이터). 모든 동작은 블록 엔티티(StructureBlockEntity).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class StructureBlock extends BaseEntityBlock {

    public StructureBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(블록엔티티·메뉴): StructureBlockEntity.usedBy — 권한 있는 크리에이티브면 화면(CONSUME), 아니면 PASS.
        return player.canUseGameMasterBlocks() ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        // TODO(블록엔티티): 신호가 새로 들어오면 모드별 동작(저장 → saveStructure, 불러오기 → placeStructure, 모서리 → unloadStructure).
    }
}
