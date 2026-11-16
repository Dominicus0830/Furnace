package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.api.world.BlockType;
import org.domi.furnace.api.world.BlockTypes;
import org.domi.furnace.compat.nms.NmsBlockEntityClasses;
import org.domi.furnace.compat.nms.NmsBlockStates;

/**
 * 호환 모듈 전용 - 블록 엔티티가 있는 블록(바닐라 EntityBlock 구현 블록)의 shim. 나머지 동작은 {@link Block} 그대로다.
 * 만드는 블록 엔티티의 클래스는 종류 표({@link NmsBlockEntityClasses} — 바닐라 1.21.1 계층)를 따라 바닐라와 같다.
 */
public final class FurnaceEntityBlock extends Block implements EntityBlock {

    public FurnaceEntityBlock(BlockType type, BlockTypes types) { super(type, types); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        var bridge = NmsBlockStates.bridge();
        String type = bridge.blockEntityType(state.furnaceStateId());
        if (type == null || bridge.defaultBlockEntityNbt(state.furnaceStateId()) == null) return null;   // 움직이는 피스톤: 바닐라도 null
        return NmsBlockEntityClasses.create(type, pos, state);
    }
}
