package net.minecraft.world.level.chunk.storage;

import net.minecraft.world.level.ChunkPos;

/** 청크 읽기·쓰기 실패 보고(Mojang 이름 shim) — 바닐라 1.21.1 과 같다({@code reportMisplacedChunk} 는 기본 구현이 로그만 남기는 것이라 같은 모양으로 둔다). */
public interface ChunkIOErrorReporter {

    void reportChunkLoadFailure(Throwable error, RegionStorageInfo info, ChunkPos pos);

    void reportChunkSaveFailure(Throwable error, RegionStorageInfo info, ChunkPos pos);

    default void reportMisplacedChunk(ChunkPos actual, ChunkPos expected, RegionStorageInfo info) {}
}
