package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 블록 엔티티를 가진 블록(Mojang 이름 shim, N2b-6). 바닐라 getTicker·getListener 는 틱·게임 이벤트 shim 이 없어 두지 않았다.
 * 바닐라와 같은 블록들(서버의 블록 엔티티 있음 표)만 구현한다.
 */
public interface EntityBlock {

    BlockEntity newBlockEntity(BlockPos pos, BlockState state);
}
