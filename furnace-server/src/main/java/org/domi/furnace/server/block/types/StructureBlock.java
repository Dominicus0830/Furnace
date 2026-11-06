// 바닐라 StructureBlock: 구조물 블록(저장·불러오기·모서리·데이터). 모든 동작은 블록 엔티티(StructureBlockEntity).
// 편집 화면은 클라이언트가 블록 엔티티 데이터로 직접 연다(서버는 권한 있는 크리에이티브면 상호작용을 소비만 한다). 저장·불러오기 신호는 neighborChanged 가 처리한다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.StructureBlockEntity;
import org.domi.furnace.server.entity.ItemStack;

public class StructureBlock extends BaseEntityBlock {

    public StructureBlock(int block) { super(block); }

    @Override
    public BlockEntity newBlockEntity(Pos pos, int state) { return new StructureBlockEntity(pos, state); }

    /** usedBy: 블록 엔티티가 있고 권한 있는 크리에이티브면 소비(화면은 클라이언트가 연다), 아니면 넘긴다. */
    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        return level.getBlockEntity(pos) instanceof StructureBlockEntity && player.canUseGameMasterBlocks() ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    /** setPlacedBy: 놓은 이의 이름이 작성자. */
    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        if (placer != null && level.getBlockEntity(pos) instanceof StructureBlockEntity be && placer.handle() instanceof org.domi.furnace.server.entity.Player p)
            be.createdBy(p.displayName().getString());
    }

    /** 신호가 새로 들어오면 모드별 동작(저장 → 메모리 템플릿만 갱신, 불러오기 → 놓기, 모서리 → 저장소에서 빼기), 꺼지면 powered 해제. */
    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (!(level.getBlockEntity(pos) instanceof StructureBlockEntity be)) return;
        boolean signal = level.hasNeighborSignal(pos);
        boolean was = be.isPowered();
        if (signal && !was) {
            be.setPowered(true);
            switch (be.mode()) {
                case SAVE -> be.saveStructure(level, false);
                case LOAD -> be.loadStructure(level);
                case CORNER -> be.unloadStructure(level);
                case DATA -> { }
            }
        } else if (!signal && was) {
            be.setPowered(false);
        }
    }
}
