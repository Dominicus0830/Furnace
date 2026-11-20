package net.minecraft.world.level;

import net.minecraft.world.level.chunk.ChunkSource;

/** 월드 읽기·쓰기(Mojang 이름, N2a). T-211: 바닐라 추상 메서드 중 난수원·서버 레벨(CraftBukkit 이 더한 getMinecraftWorld). */
public interface LevelAccessor extends LevelReader, LevelWriter {

    ChunkSource getChunkSource();

    /** 바닐라 LevelAccessor#getCurrentDifficultyAt(T-211 3단계). */
    net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(net.minecraft.core.BlockPos pos);

    /** 바닐라 LevelAccessor#getRandom. */
    net.minecraft.util.RandomSource getRandom();

    /** 바닐라 LevelAccessor#blockUpdated: 기본은 아무것도 하지 않는다(지형 생성 레벨). 서버 레벨은 이웃 갱신(Level). */
    default void blockUpdated(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block) {}

    /** CraftBukkit LevelAccessor#getMinecraftWorld: 이 레벨이 속한 서버 레벨. */
    net.minecraft.server.level.ServerLevel getMinecraftWorld();
}
