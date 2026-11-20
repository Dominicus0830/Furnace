package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** 월드 쓰기(Mojang 이름, N2a). 플래그는 바닐라 Block.UPDATE_* 와 같은 뜻. */
public interface LevelWriter {

    boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth);

    default boolean setBlock(BlockPos pos, BlockState state, int flags) { return setBlock(pos, state, flags, 512); }

    boolean removeBlock(BlockPos pos, boolean moved);

    /** 바닐라 LevelWriter#destroyBlock(pos, drop): 부수는 엔티티 없이. */
    default boolean destroyBlock(BlockPos pos, boolean drop) { return destroyBlock(pos, drop, null); }

    /** 바닐라 LevelWriter#destroyBlock(pos, drop, entity): 깊이 512. */
    default boolean destroyBlock(BlockPos pos, boolean drop, net.minecraft.world.entity.Entity breakingEntity) {
        return destroyBlock(pos, drop, breakingEntity, 512);
    }

    /** 바닐라 LevelWriter#destroyBlock(4인자, T-211 3단계). */
    boolean destroyBlock(BlockPos pos, boolean drop, net.minecraft.world.entity.Entity breakingEntity, int maxUpdateDepth);
}
