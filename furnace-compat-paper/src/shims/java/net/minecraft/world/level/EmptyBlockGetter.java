package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** 아무것도 없는 세계(Mojang 이름 shim, N2b-6): 어디든 공기·빈 유체, 높이 0. 바닐라와 같이 상수가 하나뿐인 enum. */
enum EmptyBlockGetter {
    @Override
    public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }

    /** 바닐라: 블록 엔티티가 없다. */
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return null; }

    @Override
    public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }

    @Override
    public BlockState getBlockStateIfLoaded(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }

    @Override
    public int getHeight() { return 0; }

    @Override
    public int getMinBuildHeight() { return 0; }
}
