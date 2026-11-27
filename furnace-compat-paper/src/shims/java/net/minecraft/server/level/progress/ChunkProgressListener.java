package net.minecraft.server.level.progress;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 바닐라 청크 준비 진행 듣기(Mojang 이름, T-203). Furnace 는 플러그인이 만든 레벨({@code ServerLevel} 15 인자 생성자)에 넘긴 이 객체를 부르지 않는다 —
 * 그 레벨은 스폰 청크를 미리 준비하지 않는다(바닐라도 준비는 레벨 생성자가 아니라 MinecraftServer#prepareLevels 가 한다).
 */
interface ChunkProgressListener {

    void updateSpawnPos(ChunkPos spawnPos);

    void onStatusChange(ChunkPos pos, ChunkStatus status);

    void start();

    void stop();

    /** Paper 가 더한 메서드. */
    default void setChunkRadius(int radius) {}
}
