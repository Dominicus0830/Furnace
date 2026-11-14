package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * 블록 읽기(Mojang 이름, N2a). 블록 엔티티 읽기(getBlockEntity)는 아직 없다 — BlockEntity shim 이 없다.
 * Paper 가 더한 {@link #getBlockStateIfLoaded}: 청크가 로드돼 있지 않으면 null(불러오지 않는다).
 */
public interface BlockGetter extends LevelHeightAccessor {

    BlockState getBlockState(BlockPos pos);

    FluidState getFluidState(BlockPos pos);

    /** Paper: 로드된 청크면 그 칸의 상태, 아니면 null. */
    BlockState getBlockStateIfLoaded(BlockPos pos);

    /** Paper: 로드된 청크면 그 칸의 유체, 아니면 null. */
    default FluidState getFluidIfLoaded(BlockPos pos) {
        BlockState s = getBlockStateIfLoaded(pos);
        return s == null ? null : s.getFluidState();
    }
}
