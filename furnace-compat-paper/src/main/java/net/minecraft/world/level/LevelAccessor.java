package net.minecraft.world.level;

import net.minecraft.world.level.chunk.ChunkSource;

/** 월드 읽기·쓰기(Mojang 이름, N2a). */
public interface LevelAccessor extends LevelReader, LevelWriter {

    ChunkSource getChunkSource();
}
