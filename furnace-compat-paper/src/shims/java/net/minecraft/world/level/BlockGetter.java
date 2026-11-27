package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * 블록 읽기(Mojang 이름, N2a). 블록 엔티티 읽기 {@link #getBlockEntity}(T-198 — 월드·청크는 서버 블록 엔티티 보기, 빈 판은 null).
 * Paper 가 더한 {@link #getBlockStateIfLoaded}: 청크가 로드돼 있지 않으면 null(불러오지 않는다).
 */
interface BlockGetter {

    /** 바닐라: 그 칸의 블록 엔티티(없으면 null). 월드(Level)는 IMMEDIATE, 청크는 CHECK 다(바닐라 그대로). */
    net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos);

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
