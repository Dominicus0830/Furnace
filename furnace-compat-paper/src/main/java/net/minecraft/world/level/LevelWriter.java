package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** 월드 쓰기(Mojang 이름, N2a). 플래그는 바닐라 Block.UPDATE_* 와 같은 뜻. */
public interface LevelWriter {

    boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth);

    default boolean setBlock(BlockPos pos, BlockState state, int flags) { return setBlock(pos, state, flags, 512); }

    boolean removeBlock(BlockPos pos, boolean moved);
}
