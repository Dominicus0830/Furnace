package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

/** 빛 엔진 공통 면(Mojang 이름 shim, T-205) — 바닐라 1.21.1 그대로의 모양. */
public interface LightEventListener {

    void checkBlock(BlockPos pos);

    boolean hasLightWork();

    int runLightUpdates();

    default void updateSectionStatus(BlockPos pos, boolean notReady) { updateSectionStatus(SectionPos.of(pos), notReady); }

    void updateSectionStatus(SectionPos pos, boolean notReady);

    void setLightEnabled(ChunkPos pos, boolean retainData);

    void propagateLightSources(ChunkPos pos);
}
